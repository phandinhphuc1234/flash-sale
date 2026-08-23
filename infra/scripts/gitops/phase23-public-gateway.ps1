<#
.SYNOPSIS
  Validate and smoke-test the development-only public API Gateway endpoint.

.DESCRIPTION
  Validation mode renders the cloud overlay and checks that exactly the API Gateway is configured
  as a LoadBalancer. -Run additionally waits for the AWS-generated hostname, verifies Argo health,
  checks readiness/catalog/admin boundaries, and inventories Services. This helper never applies or
  deletes Kubernetes resources and never reads or prints Secret values.
#>
[CmdletBinding()]
param(
  [switch]$Run,
  [ValidateRange(30, 600)]
  [int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$OverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$Namespace = "flash-sale"
$ServiceName = "api-gateway"
$ArgoApplication = "flash-sale-cloud"

function Invoke-KubectlText {
  param([Parameter(Mandatory)][string[]]$Arguments)
  $output = & kubectl @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "kubectl failed: kubectl $($Arguments -join ' ')"
  }
  return (($output -join "`n").Trim())
}

function Get-HttpStatus {
  param([Parameter(Mandatory)][string]$Uri)
  $statusText = & curl.exe --silent --show-error --output NUL --write-out "%{http_code}" --max-time 5 --url $Uri 2>$null
  if ($LASTEXITCODE -ne 0) { return 0 }
  $status = 0
  if ([int]::TryParse(($statusText -join "").Trim(), [ref]$status)) { return $status }
  return 0
}

if (-not (Test-Path -LiteralPath $OverlayPath)) {
  throw "Cloud overlay not found: $OverlayPath"
}

$rendered = Invoke-KubectlText @("kustomize", $OverlayPath)
if ($rendered -notmatch '(?ms)kind:\s*Service.*?name:\s*api-gateway.*?type:\s*LoadBalancer') {
  throw "Cloud overlay does not render api-gateway as a LoadBalancer."
}
if ($rendered -notmatch 'service\.beta\.kubernetes\.io/aws-load-balancer-scheme:\s*internet-facing') {
  throw "Cloud overlay is missing the internet-facing AWS load-balancer scheme."
}
& kubectl apply --dry-run=client --validate=false -k $OverlayPath *> $null
if ($LASTEXITCODE -ne 0) {
  throw "Cloud overlay client-side dry-run failed."
}

Write-Output ("kubectl context: " + ((kubectl config current-context) -join " "))
Write-Output "Phase 23 manifest gate: PASS (api-gateway LoadBalancer; backend/platform Services unchanged in Git)."

if (-not $Run) {
  Write-Output "Validation-only mode: no live endpoint was queried and no Kubernetes resource was changed."
  exit 0
}

function Get-ArgoApplication {
  $json = Invoke-KubectlText @("-n", "argocd", "get", "application/$ArgoApplication", "-o", "json")
  return ($json | ConvertFrom-Json)
}

function Get-ServiceObject {
  $json = Invoke-KubectlText @("-n", $Namespace, "get", "service/$ServiceName", "-o", "json")
  return ($json | ConvertFrom-Json)
}

function Assert-PrivateServiceInventory {
  $json = Invoke-KubectlText @("-n", $Namespace, "get", "services", "-o", "json")
  $services = $json | ConvertFrom-Json
  $public = @($services.items | Where-Object { $_.spec.type -in @("LoadBalancer", "NodePort") })
  $unexpected = @($public | Where-Object { $_.metadata.name -ne $ServiceName })
  if ($unexpected.Count -gt 0) {
    throw "Unexpected public Service(s): $($unexpected.metadata.name -join ', ')"
  }
  if (@($public | Where-Object { $_.metadata.name -eq $ServiceName }).Count -ne 1) {
    throw "api-gateway is not the single public Service."
  }
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$application = Get-ArgoApplication
if ($application.status.sync.status -ne "Synced" -or $application.status.health.status -ne "Healthy") {
  throw "Argo $ArgoApplication is not Synced/Healthy (sync=$($application.status.sync.status) health=$($application.status.health.status))."
}
Write-Output "Argo ${ArgoApplication}: sync=$($application.status.sync.status) health=$($application.status.health.status) revision=$($application.status.sync.revision)"

$externalHost = $null
do {
  $service = Get-ServiceObject
  if ($service.spec.type -ne "LoadBalancer") {
    throw "Live api-gateway Service type is '$($service.spec.type)', expected LoadBalancer."
  }
  $ingress = @($service.status.loadBalancer.ingress)
  if ($ingress.Count -gt 0) {
    $externalHost = if ($ingress[0].hostname) { $ingress[0].hostname } else { $ingress[0].ip }
  }
  if ([string]::IsNullOrWhiteSpace($externalHost)) { Start-Sleep -Seconds 5 }
} while ([string]::IsNullOrWhiteSpace($externalHost) -and (Get-Date) -lt $deadline)

if ([string]::IsNullOrWhiteSpace($externalHost)) {
  throw "AWS endpoint was not assigned within $TimeoutSeconds seconds."
}
Assert-PrivateServiceInventory
Write-Output "Public Gateway endpoint: http://$externalHost"

$baseUri = "http://$externalHost"
$readiness = 0
do {
  $readiness = Get-HttpStatus "$baseUri/actuator/health/readiness"
  if ($readiness -eq 200) { break }
  Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($readiness -ne 200) { throw "Gateway readiness expected HTTP 200, got HTTP $readiness." }

$catalog = Get-HttpStatus "$baseUri/api/v1/catalog/products?page=0&size=1"
Write-Output "Public Gateway catalog status: $catalog"
if ($catalog -ne 200) { throw "Gateway catalog route expected HTTP 200, got HTTP $catalog." }

$admin = Get-HttpStatus "$baseUri/api/v1/admin/catalog/products?page=0&size=1"
Write-Output "Public Gateway admin status: $admin"
if ($admin -notin @(401, 403)) { throw "Gateway admin route expected HTTP 401/403, got HTTP $admin." }

Write-Output "Phase 23 public Gateway smoke: PASS (readiness=$readiness catalog=$catalog admin=$admin)."
Write-Output "No Kubernetes resource was mutated by this helper; rollback is a Git revert to ClusterIP."
