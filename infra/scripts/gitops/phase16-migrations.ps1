<#
.SYNOPSIS
  Validate and explicitly run the seven cloud database migration Jobs.

.DESCRIPTION
  Default mode is validation-only. It checks the current cluster, Phase 15 Secret names, and the
  migration-only Kustomize overlay without reading Secret data. -Apply creates the Jobs and waits
  for completion. Existing Jobs are preserved unless -ForceRerun is explicitly supplied.
#>
[CmdletBinding()]
param(
  [string]$Namespace = "flash-sale",
  [string]$OverlayPath = "",
  [ValidateRange(30, 1800)]
  [int]$TimeoutSeconds = 600,
  [switch]$Apply,
  [switch]$ForceRerun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
if ([string]::IsNullOrWhiteSpace($OverlayPath)) {
  $OverlayPath = Join-Path $repoRoot "infra\k8s\overlays\cloud-migrations"
}
if (-not (Test-Path -LiteralPath (Join-Path $OverlayPath "kustomization.yaml") -PathType Leaf)) {
  throw "Phase 16 migration overlay was not found: $OverlayPath"
}

function Invoke-Kubectl {
  param([Parameter(Mandatory = $true)][string[]]$Arguments)
  & kubectl @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "kubectl failed with exit code ${LASTEXITCODE}: kubectl $($Arguments -join ' ')"
  }
}

function Get-KubectlName {
  param([Parameter(Mandatory = $true)][string[]]$Arguments)
  $previousErrorActionPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = "SilentlyContinue"
    $result = (& kubectl @Arguments 2>$null)
    if ($LASTEXITCODE -ne 0) {
      return ""
    }
    return ($result -join "").Trim()
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }
}

$context = Get-KubectlName @("config", "current-context")
if ([string]::IsNullOrWhiteSpace($context)) {
  throw "kubectl has no current context. Connect to the cloud EKS cluster first."
}
Write-Output "kubectl context: $context"

$namespaceName = Get-KubectlName @("get", "namespace", $Namespace, "--ignore-not-found", "-o", "name")
if ([string]::IsNullOrWhiteSpace($namespaceName)) {
  throw "Namespace '$Namespace' does not exist."
}

# The migration Jobs need the Phase 14 platform. Only resource names are queried; no Secret data is read.
foreach ($platformResource in @(
    "statefulset/postgres",
    "statefulset/redis",
    "statefulset/kafka",
    "deployment/schema-registry"
  )) {
  $resourceName = Get-KubectlName @("-n", $Namespace, "get", $platformResource, "-o", "name")
  if ([string]::IsNullOrWhiteSpace($resourceName)) {
    throw "Required platform resource '$Namespace/$platformResource' is missing. Apply the Phase 14 platform first."
  }
}

foreach ($secretName in @(
    "platform-secrets",
    "authentication-secrets",
    "product-secrets",
    "campaign-secrets",
    "flashsale-secrets",
    "inventory-secrets",
    "order-secrets",
    "payment-secrets",
    "auth-jwt"
  )) {
  $secretResource = Get-KubectlName @("-n", $Namespace, "get", "secret", $secretName, "-o", "name")
  if ([string]::IsNullOrWhiteSpace($secretResource)) {
    throw "Required Secret '$Namespace/$secretName' is missing. Secret data was not read."
  }
}

foreach ($configMapName in @(
    "flash-sale-runtime-config",
    "authentication-service-runtime-config",
    "product-service-runtime-config",
    "campaign-service-runtime-config",
    "flash-sale-service-runtime-config",
    "inventory-service-runtime-config",
    "order-service-runtime-config",
    "payment-service-runtime-config"
  )) {
  $configMapResource = Get-KubectlName @("-n", $Namespace, "get", "configmap", $configMapName, "-o", "name")
  if ([string]::IsNullOrWhiteSpace($configMapResource)) {
    throw "Required ConfigMap '$Namespace/$configMapName' is missing. Apply the Phase 15 cloud config resources first."
  }
}

$rendered = (& kubectl kustomize $OverlayPath)
if ($LASTEXITCODE -ne 0) { throw "Migration overlay cannot be rendered: $OverlayPath" }
$renderedText = $rendered -join [Environment]::NewLine
$jobCount = @($rendered | Where-Object { $_ -eq "kind: Job" }).Count
if ($jobCount -ne 7) { throw "Expected 7 database migration Jobs, rendered $jobCount." }
if ($renderedText -match "flash-sale-secrets") {
  throw "Migration overlay must not reference legacy Secret flash-sale-secrets."
}
Invoke-Kubectl @("apply", "--dry-run=client", "-k", $OverlayPath)

$jobNames = @(
  "migrate-authentication-service",
  "migrate-product-service",
  "migrate-campaign-service",
  "migrate-flash-sale-service",
  "migrate-inventory-service",
  "migrate-order-service",
  "migrate-payment-service"
)
$existingJobs = @()
foreach ($jobName in $jobNames) {
  $jobResource = Get-KubectlName @("-n", $Namespace, "get", "job", $jobName, "-o", "name")
  if (-not [string]::IsNullOrWhiteSpace($jobResource)) { $existingJobs += $jobName }
}

if ($existingJobs.Count -gt 0 -and -not $ForceRerun) {
  throw "Migration Job(s) already exist: $($existingJobs -join ', '). Inspect them or rerun with -ForceRerun."
}

Write-Output "Migration prerequisites passed. Seven Jobs are renderable."
Write-Output "Secret values were not read or printed."
if (-not $Apply) {
  Write-Output "Validation-only mode: no migration Job was created."
  exit 0
}

if ($ForceRerun) {
  foreach ($jobName in $existingJobs) {
    Invoke-Kubectl @("-n", $Namespace, "delete", "job", $jobName, "--ignore-not-found")
  }
}

Invoke-Kubectl @("apply", "-k", $OverlayPath)
foreach ($jobName in $jobNames) {
  Write-Output "Waiting for $jobName..."
  Invoke-Kubectl @("-n", $Namespace, "wait", "--for=condition=complete", "job/$jobName", "--timeout=${TimeoutSeconds}s")
}

Invoke-Kubectl @("-n", $Namespace, "get", "jobs,pods", "-l", "app.kubernetes.io/component=migration")
Write-Output "Phase 16 database migrations completed."
