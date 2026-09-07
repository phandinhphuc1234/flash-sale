<#
.SYNOPSIS
  Validate and explicitly roll out the nine cloud application Deployments.

.DESCRIPTION
  Default mode is validation-only. It checks the target namespace, Phase 14 platform resources,
  Phase 15 ConfigMaps/Secret boundaries, completed Phase 16 migration Jobs, and the cloud Kustomize
  overlay without reading Secret data. -Apply applies the overlay and waits for each application
  Deployment. A failed rollout is left intact for operator inspection.
#>
[CmdletBinding()]
param(
  [string]$Namespace = "flash-sale",
  [string]$OverlayPath = "",
  [ValidateRange(30, 1800)]
  [int]$TimeoutSeconds = 600,
  [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
if ([string]::IsNullOrWhiteSpace($OverlayPath)) {
  $OverlayPath = Join-Path $repoRoot "infra\k8s\overlays\cloud"
}
if (-not (Test-Path -LiteralPath (Join-Path $OverlayPath "kustomization.yaml") -PathType Leaf)) {
  throw "Phase 17 cloud application overlay was not found: $OverlayPath"
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
    if ($LASTEXITCODE -ne 0) { return "" }
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

$namespaceResource = Get-KubectlName @("get", "namespace", $Namespace, "--ignore-not-found", "-o", "name")
if ([string]::IsNullOrWhiteSpace($namespaceResource)) {
  throw "Namespace '$Namespace' does not exist."
}

# Platform objects are checked by name only; this script never reads Secret data.
foreach ($platformResource in @(
    "statefulset/postgres",
    "statefulset/redis",
    "statefulset/kafka",
    "deployment/schema-registry"
  )) {
  $resource = Get-KubectlName @("-n", $Namespace, "get", $platformResource, "-o", "name")
  if ([string]::IsNullOrWhiteSpace($resource)) {
    throw "Required platform resource '$Namespace/$platformResource' is missing."
  }
}

foreach ($configMapName in @(
    "flash-sale-runtime-config",
    "api-gateway-runtime-config",
    "authentication-service-runtime-config",
    "product-service-runtime-config",
    "campaign-service-runtime-config",
    "flash-sale-service-runtime-config",
    "inventory-service-runtime-config",
    "order-service-runtime-config",
    "payment-service-runtime-config",
    "cart-service-runtime-config"
  )) {
  $resource = Get-KubectlName @("-n", $Namespace, "get", "configmap", $configMapName, "-o", "name")
  if ([string]::IsNullOrWhiteSpace($resource)) {
    throw "Required ConfigMap '$Namespace/$configMapName' is missing."
  }
}

foreach ($secretName in @(
    "platform-secrets",
    "gateway-secrets",
    "authentication-secrets",
    "auth-jwt",
    "product-secrets",
    "campaign-secrets",
    "flashsale-secrets",
    "inventory-secrets",
    "order-secrets",
    "payment-secrets",
    "cart-secrets"
  )) {
  $resource = Get-KubectlName @("-n", $Namespace, "get", "secret", $secretName, "-o", "name")
  if ([string]::IsNullOrWhiteSpace($resource)) {
    throw "Required Secret '$Namespace/$secretName' is missing. Secret data was not read."
  }
}

foreach ($jobName in @(
    "migrate-authentication-service",
    "migrate-product-service",
    "migrate-campaign-service",
    "migrate-flash-sale-service",
    "migrate-inventory-service",
    "migrate-order-service",
    "migrate-payment-service",
    "migrate-cart-service"
  )) {
  $jobResource = Get-KubectlName @("-n", $Namespace, "get", "job", $jobName, "-o", "name")
  if ([string]::IsNullOrWhiteSpace($jobResource)) {
    throw "Required completed migration Job '$Namespace/$jobName' is missing."
  }
  $completion = Get-KubectlName @("-n", $Namespace, "get", "job", $jobName, "-o", "jsonpath={.status.succeeded}")
  if ($completion -ne "1") {
    throw "Migration Job '$Namespace/$jobName' is not complete (succeeded=$completion)."
  }
}

$rendered = (& kubectl kustomize $OverlayPath)
if ($LASTEXITCODE -ne 0) { throw "Cloud application overlay cannot be rendered: $OverlayPath" }
$renderedText = $rendered -join [Environment]::NewLine
$applicationDeployments = @(
  "api-gateway",
  "authentication-service",
  "product-service",
  "campaign-service",
  "flash-sale-service",
  "inventory-service",
  "order-service",
  "payment-service",
  "cart-service"
)
foreach ($deploymentName in $applicationDeployments) {
  if ($renderedText -notmatch "(?m)^\s*name: $([regex]::Escape($deploymentName))\s*$") {
    throw "Cloud overlay does not render application Deployment '$deploymentName'."
  }
}
if ($renderedText -match "name: flash-sale-secrets") {
  throw "Cloud application overlay must not reference legacy Secret flash-sale-secrets."
}

Invoke-Kubectl @("apply", "--dry-run=client", "-k", $OverlayPath)
Write-Output "Phase 17 prerequisites passed. Nine application Deployments are renderable."
Write-Output "Secret values were not read or printed."
if (-not $Apply) {
  Write-Output "Validation-only mode: no application Deployment was changed."
  exit 0
}

Invoke-Kubectl @("apply", "-k", $OverlayPath)
try {
  foreach ($deploymentName in $applicationDeployments) {
    Write-Output "Waiting for $deploymentName..."
    Invoke-Kubectl @("-n", $Namespace, "rollout", "status", "deployment/$deploymentName", "--timeout=${TimeoutSeconds}s")
  }
} catch {
  Write-Output "Phase 17 rollout stopped. Existing Pods were preserved for inspection."
  Invoke-Kubectl @("-n", $Namespace, "get", "deployments,pods")
  throw
}

Invoke-Kubectl @("-n", $Namespace, "get", "deployments,pods")
Write-Output "Phase 17 cloud application rollout completed: 9/9 Deployments available."
