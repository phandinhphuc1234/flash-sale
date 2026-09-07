<#
.SYNOPSIS
  Validate and optionally deploy the compact Phase 25 seckill observability stack.

.DESCRIPTION
  Default execution is validation-only and performs no Kubernetes mutation. Explicit -Apply is
  allowed only from a local commit already merged to origin/develop. It provisions the Grafana
  administrator Secret without printing it, applies the isolated Argo CD Application, waits with a
  bounded deadline, and verifies Prometheus targets/rules plus Grafana health.
#>
[CmdletBinding()]
param(
  [string]$ExpectedClusterName = "flash-sale-dev",
  [string]$ApplicationNamespace = "flash-sale",
  [string]$MonitoringNamespace = "monitoring",
  [string]$ArgoNamespace = "argocd",
  [string]$ArgoApplication = "flash-sale-observability",
  [string]$GrafanaAdminUser = "admin",
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600,
  [ValidateRange(1024, 65535)]
  [int]$PrometheusLocalPort = 29090,
  [ValidateRange(1024, 65535)]
  [int]$GrafanaLocalPort = 23000,
  [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($PSVersionTable.PSEdition -ne "Core" -or $PSVersionTable.PSVersion.Major -lt 7) {
  throw "Phase 25 requires PowerShell 7 (pwsh)."
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$monitoringPath = Join-Path $repoRoot "infra\monitoring"
$argoPath = Join-Path $repoRoot "infra\k8s\argocd\observability"
$staticTestPath = Join-Path $repoRoot "infra\scripts\gitops\tests\phase25-seckill-observability.tests.ps1"
$expectedJobs = @(
  "api-gateway", "authentication-service", "product-service", "campaign-service",
  "flash-sale-service", "inventory-service", "order-service", "payment-service", "cart-service"
)

function Get-RemainingSeconds {
  $remaining = [int][Math]::Ceiling(($deadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) { throw "Phase 25 exceeded its $TimeoutSeconds-second execution budget." }
  return $remaining
}

function Invoke-NativeText {
  param([Parameter(Mandatory)][string]$Command, [Parameter(Mandatory)][string[]]$Arguments)
  $output = (& $Command @Arguments 2>&1) -join "`n"
  if ($LASTEXITCODE -ne 0) {
    $bounded = if ($output.Length -le 2000) { $output } else { $output.Substring(0, 2000) + "... [truncated]" }
    throw "$Command failed: $bounded"
  }
  return $output.Trim()
}

function Wait-ArgoHealthy {
  while ((Get-Date) -lt $deadline) {
    $stateText = Invoke-NativeText "kubectl" @(
      "-n", $ArgoNamespace, "get", "application", $ArgoApplication,
      "-o", "jsonpath={.status.sync.status}|{.status.health.status}"
    )
    if ($stateText -eq "Synced|Healthy") {
      Write-Output "Argo ${ArgoApplication}: Synced / Healthy."
      return
    }
    Start-Sleep -Seconds 5
  }
  throw "Argo Application did not become Synced/Healthy within the Phase 25 budget."
}

function Start-KubectlPortForward {
  param([Parameter(Mandatory)][string]$Service, [Parameter(Mandatory)][int]$LocalPort,
    [Parameter(Mandatory)][int]$RemotePort)
  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = "kubectl"
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  foreach ($argument in @("-n", $MonitoringNamespace, "port-forward", "service/$Service", "${LocalPort}:$RemotePort")) {
    $null = $startInfo.ArgumentList.Add($argument)
  }
  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  if (-not $process.Start()) { throw "Could not start port-forward for $Service." }
  return $process
}

function Wait-HttpJson {
  param([Parameter(Mandatory)][string]$Uri, [Parameter(Mandatory)][string]$Description)
  while ((Get-Date) -lt $deadline) {
    try { return Invoke-RestMethod -Uri $Uri -TimeoutSec 5 } catch { Start-Sleep -Seconds 2 }
  }
  throw "$Description did not become reachable within the Phase 25 budget."
}

function Stop-ChildProcess([System.Diagnostics.Process]$Process) {
  if ($null -eq $Process) { return }
  try {
    if (-not $Process.HasExited) { $Process.Kill($true); $Process.WaitForExit() }
  } finally { $Process.Dispose() }
}

function New-GrafanaAdminSecret {
  $securePassword = Read-Host "Grafana admin password (memory only)" -AsSecureString
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
  $plainPassword = $null
  try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrWhiteSpace($plainPassword)) { throw "Grafana password must not be empty." }
    $secret = @{
      apiVersion = "v1"
      kind = "Secret"
      metadata = @{ name = "grafana-admin"; namespace = $MonitoringNamespace }
      type = "Opaque"
      data = @{
        "admin-user" = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($GrafanaAdminUser))
        "admin-password" = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($plainPassword))
      }
    } | ConvertTo-Json -Depth 6 -Compress
    $secret | & kubectl apply -f -
    if ($LASTEXITCODE -ne 0) { throw "kubectl could not apply the Grafana Secret." }
  } finally {
    if ($pointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    $plainPassword = $null
    $securePassword = $null
  }
}

& pwsh -NoLogo -NoProfile -File $staticTestPath
if ($LASTEXITCODE -ne 0) { throw "Phase 25 static contract tests failed." }

$rendered = Invoke-NativeText "kubectl" @("kustomize", $monitoringPath)
if ($rendered -match 'type: (LoadBalancer|NodePort)') { throw "Rendered monitoring contains a public Service." }
Invoke-NativeText "kubectl" @("apply", "--dry-run=client", "--validate=false", "-k", $monitoringPath) | Write-Output
Invoke-NativeText "kubectl" @("apply", "--dry-run=client", "--validate=false", "-k", $argoPath) | Write-Output

$context = Invoke-NativeText "kubectl" @("config", "current-context")
if ($context -notmatch [regex]::Escape($ExpectedClusterName)) {
  throw "kubectl context '$context' does not target '$ExpectedClusterName'."
}
Write-Output "Phase 25 manifest gate: PASS (9 targets; Prometheus/Grafana private and pinned)."
Write-Output "kubectl context: $context"

if (-not $Apply) {
  Write-Output "Validation-only mode: no Kubernetes resource or Secret was changed."
  exit 0
}

Invoke-NativeText "git" @("fetch", "origin", "develop") | Out-Null
$head = Invoke-NativeText "git" @("rev-parse", "HEAD")
$develop = Invoke-NativeText "git" @("rev-parse", "origin/develop")
if ($head -ne $develop) {
  throw "Live apply requires the checked-out commit to equal origin/develop. Merge and pull the reviewed PR first."
}
$remoteApp = Invoke-NativeText "git" @("show", "origin/develop:infra/k8s/argocd/observability/application-observability.yaml")
if ($remoteApp -notmatch 'path:\s*infra/monitoring') { throw "Phase 25 is not present on origin/develop." }

Invoke-NativeText "kubectl" @("apply", "-f", (Join-Path $monitoringPath "namespace.yaml")) | Write-Output
$secretName = Invoke-NativeText "kubectl" @("-n", $MonitoringNamespace, "get", "secret", "grafana-admin", "--ignore-not-found", "-o", "name")
if ([string]::IsNullOrWhiteSpace($secretName)) {
  New-GrafanaAdminSecret
  Write-Output "Grafana administrator Secret created without displaying its value."
} else {
  Write-Output "Existing Grafana administrator Secret reused; value was not read."
}

Invoke-NativeText "kubectl" @("apply", "-k", $argoPath) | Write-Output
Invoke-NativeText "kubectl" @("-n", $ArgoNamespace, "annotate", "application", $ArgoApplication,
  "argocd.argoproj.io/refresh=hard", "--overwrite") | Write-Output
Wait-ArgoHealthy

foreach ($deployment in @("prometheus", "grafana")) {
  $remaining = Get-RemainingSeconds
  Invoke-NativeText "kubectl" @("-n", $MonitoringNamespace, "rollout", "status",
    "deployment/$deployment", "--timeout=${remaining}s") | Write-Output
}

$servicesJsonText = Invoke-NativeText "kubectl" @("-n", $MonitoringNamespace, "get", "service",
  "prometheus", "grafana", "-o", "json")
try {
  $servicesJson = $servicesJsonText | ConvertFrom-Json
} catch {
  throw "kubectl returned invalid Service JSON: $($_.Exception.Message)"
}
foreach ($service in @("prometheus", "grafana")) {
  $serviceResource = @($servicesJson.items | Where-Object { $_.metadata.name -eq $service })
  if ($serviceResource.Count -ne 1 -or $serviceResource[0].spec.type -ne "ClusterIP") {
    throw "$service is not a private ClusterIP Service."
  }
}

$prometheusForward = $null
$grafanaForward = $null
try {
  $prometheusForward = Start-KubectlPortForward "prometheus" $PrometheusLocalPort 9090
  $targetsResponse = Wait-HttpJson "http://127.0.0.1:$PrometheusLocalPort/api/v1/targets" "Prometheus targets API"
  $activeTargets = @($targetsResponse.data.activeTargets | Where-Object { $_.labels.job -in $expectedJobs })
  $healthyJobs = @($activeTargets | Where-Object { $_.health -eq "up" } | ForEach-Object { $_.labels.job } | Sort-Object -Unique)
  if ($healthyJobs.Count -ne $expectedJobs.Count) {
    $missing = @($expectedJobs | Where-Object { $_ -notin $healthyJobs })
    throw "Prometheus targets are not all healthy: $($missing -join ', ')."
  }
  $rulesResponse = Wait-HttpJson "http://127.0.0.1:$PrometheusLocalPort/api/v1/rules" "Prometheus rules API"
  $ruleGroups = @($rulesResponse.data.groups | ForEach-Object { $_.name })
  foreach ($group in @("purchase-saga", "payment-service")) {
    if ($group -notin $ruleGroups) { throw "Prometheus rule group '$group' is not loaded." }
  }

  $grafanaForward = Start-KubectlPortForward "grafana" $GrafanaLocalPort 3000
  $grafanaHealth = Wait-HttpJson "http://127.0.0.1:$GrafanaLocalPort/api/health" "Grafana health API"
  if ($grafanaHealth.database -ne "ok") { throw "Grafana database health is not ok." }
  $dashboardConfig = Invoke-NativeText "kubectl" @("-n", $MonitoringNamespace, "get", "configmap",
    "-l", "app.kubernetes.io/component=observability", "-o", "json")
  if ($dashboardConfig -notmatch 'seckill-overview\.json') { throw "Seckill dashboard ConfigMap is not present." }
} finally {
  Stop-ChildProcess $grafanaForward
  Stop-ChildProcess $prometheusForward
}

Write-Output "Prometheus targets: PASS (9/9 UP)."
Write-Output "Prometheus rules: PASS (purchase-saga, payment-service)."
Write-Output "Grafana: PASS (healthy; Seckill Overview provisioned from Git)."
Write-Output "Phase 25 seckill observability: PASS"
Write-Output "No Secret value was read or printed; application resources were not mutated."
