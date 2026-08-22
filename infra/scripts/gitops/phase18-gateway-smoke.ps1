<#
.SYNOPSIS
  Validate and optionally smoke-test the internal cloud API Gateway.

.DESCRIPTION
  Validation mode renders the cloud overlay and checks Gateway prerequisites without changing
  Kubernetes. -Run starts a temporary local kubectl port-forward, checks health and two route
  boundaries, then always stops the child process. Secret values are never read or printed.
#>
[CmdletBinding()]
param(
  [switch]$Run,
  [ValidateRange(1024, 65535)]
  [int]$LocalPort = 28080,
  [ValidateRange(10, 600)]
  [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$OverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$Namespace = "flash-sale"
$ServiceName = "api-gateway"

function Invoke-Kubectl {
  param([Parameter(Mandatory)][string[]]$Arguments)
  & kubectl @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "kubectl failed: kubectl $($Arguments -join ' ')"
  }
}

function Require-Resource {
  param(
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][string]$Description
  )
  & kubectl @Arguments *> $null
  if ($LASTEXITCODE -ne 0) {
    throw "Required resource is missing or unavailable: $Description"
  }
}

function Get-HttpStatus {
  param([Parameter(Mandatory)][string]$Uri)
  $statusText = & curl.exe --silent --show-error --output NUL --write-out "%{http_code}" --max-time 5 --url $Uri 2>$null
  if ($LASTEXITCODE -ne 0) { return 0 }
  $status = 0
  if ([int]::TryParse(($statusText -join "").Trim(), [ref]$status)) {
    Write-Verbose "HTTP probe $Uri returned $status."
    return $status
  }
  return 0
}

function Assert-LocalPortAvailable {
  param([Parameter(Mandatory)][int]$Port)
  $existingListener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1
  if ($null -ne $existingListener) {
    throw "Local port $Port is already in use. Choose an unused port with -LocalPort."
  }
  $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
  try {
    $listener.Start()
  } catch {
    throw "Local port $Port is already in use. Choose an unused port with -LocalPort."
  } finally {
    $listener.Stop()
  }
}

if (-not (Test-Path $OverlayPath)) {
  throw "Cloud overlay not found: $OverlayPath"
}

Write-Output ("kubectl context: " + ((kubectl config current-context) -join " "))
Invoke-Kubectl @("kustomize", $OverlayPath) *> $null
Invoke-Kubectl @("apply", "--dry-run=client", "-k", $OverlayPath) *> $null
Require-Resource @("-n", $Namespace, "get", "service/$ServiceName") "service/$ServiceName"
Require-Resource @(
  "-n", $Namespace, "rollout", "status", "deployment/$ServiceName",
  ("--timeout=" + $TimeoutSeconds + "s")
) "deployment/$ServiceName Ready"
Write-Output "Phase 18 prerequisites passed. Gateway overlay and Deployment are ready."

if (-not $Run) {
  Write-Output "Validation-only mode: no port-forward or Kubernetes resource was changed."
  exit 0
}

Assert-LocalPortAvailable $LocalPort

$TempBase = Join-Path ([IO.Path]::GetTempPath()) ("flash-sale-gateway-" + [guid]::NewGuid().ToString("N"))
$StdoutPath = "$TempBase.out.log"
$StderrPath = "$TempBase.err.log"
$PortForward = $null

try {
  $PortForward = Start-Process -FilePath "kubectl" -ArgumentList @("-n", $Namespace, "port-forward", "--address", "127.0.0.1", "service/$ServiceName", ("{0}:8080" -f $LocalPort)) -RedirectStandardOutput $StdoutPath -RedirectStandardError $StderrPath -WindowStyle Hidden -PassThru

  $Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $BaseUri = "http://127.0.0.1:$LocalPort"
  $ReadinessStatus = 0
  do {
    Start-Sleep -Milliseconds 500
    if ($PortForward.HasExited) {
      throw "Gateway port-forward exited before readiness became available."
    }
    $ReadinessStatus = Get-HttpStatus "$BaseUri/actuator/health/readiness"
    if ($ReadinessStatus -eq 200) { break }
  } while ((Get-Date) -lt $Deadline)

  if ($ReadinessStatus -ne 200) {
    throw "Gateway readiness expected HTTP 200, got HTTP $ReadinessStatus."
  }

  $PortOwner = Get-NetTCPConnection -LocalAddress "127.0.0.1" -LocalPort $LocalPort -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.OwningProcess -eq $PortForward.Id } |
    Select-Object -First 1
  if ($null -eq $PortOwner) {
    throw "Local port $LocalPort is not owned by the kubectl port-forward process started by this script."
  }
  Write-Verbose "Verified kubectl process $($PortForward.Id) owns 127.0.0.1:$LocalPort."

  $CatalogStatus = Get-HttpStatus "$BaseUri/api/v1/catalog/products?page=0&size=1"
  Write-Output "Gateway catalog status: $CatalogStatus"
  if ($CatalogStatus -ne 200) {
    throw "Gateway catalog route expected HTTP 200, got HTTP $CatalogStatus."
  }

  $AdminStatus = Get-HttpStatus "$BaseUri/api/v1/admin/catalog/products?page=0&size=1"
  Write-Output "Gateway admin status: $AdminStatus"
  if ($AdminStatus -notin @(401, 403)) {
    throw "Gateway admin route expected HTTP 401/403 without credentials, got HTTP $AdminStatus."
  }

  Write-Output "Gateway smoke passed: readiness=$ReadinessStatus catalog=$CatalogStatus admin=$AdminStatus."
} finally {
  if ($null -ne $PortForward -and -not $PortForward.HasExited) {
    Stop-Process -Id $PortForward.Id -Force -ErrorAction SilentlyContinue
  }
  Remove-Item -LiteralPath $StdoutPath, $StderrPath -Force -ErrorAction SilentlyContinue
}
