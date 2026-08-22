<#
.SYNOPSIS
  Validate or explicitly transfer Argo CD ownership from the Product pilot to the full cloud overlay.

.DESCRIPTION
  Default mode validates local manifests and live prerequisites without changing the cluster.
  -Apply additionally requires the Phase 19 desired state to be present on origin/develop, suspends
  the historical pilot, reconciles flash-sale-cloud, verifies all eight application Deployments,
  and removes only the finalizer-free historical Application object. If cutover fails, the script
  restores the committed pilot Application. Secret values are never read or printed.
#>
[CmdletBinding()]
param(
  [switch]$Apply,
  [string]$ArgoNamespace = "argocd",
  [string]$WorkloadNamespace = "flash-sale",
  [string]$ExpectedClusterName = "flash-sale-dev",
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$ArgoPath = Join-Path $RepoRoot "infra\k8s\argocd"
$CloudOverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$CloudApplicationManifest = Join-Path $ArgoPath "application-cloud.yaml"
$PilotApplicationManifest = Join-Path $ArgoPath "application-dev-pilot.yaml"
$CloudApplicationName = "flash-sale-cloud"
$PilotApplicationName = "dev-pilot"
$ExpectedProductTag = "pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72"
$ExpectedProductImage = "090814040069.dkr.ecr.ap-southeast-2.amazonaws.com/flash-sale/product-service:$ExpectedProductTag"
$ApplicationDeployments = @(
  "api-gateway",
  "authentication-service",
  "product-service",
  "campaign-service",
  "flash-sale-service",
  "inventory-service",
  "order-service",
  "payment-service"
)

function Invoke-Native {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments
  )
  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "$Command failed with exit code $LASTEXITCODE."
  }
}

function Get-NativeText {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [switch]$AllowFailure
  )
  $output = & $Command @Arguments 2>$null
  $exitCode = $LASTEXITCODE
  if ($exitCode -ne 0 -and -not $AllowFailure) {
    throw "$Command failed with exit code $exitCode."
  }
  if ($exitCode -ne 0) { return "" }
  return ($output -join [Environment]::NewLine).Trim()
}

function Test-ApplicationExists {
  param([Parameter(Mandatory)][string]$Name)
  $resource = Get-NativeText "kubectl" @(
    "-n", $ArgoNamespace, "get", "application", $Name,
    "--ignore-not-found", "-o", "name"
  ) -AllowFailure
  return -not [string]::IsNullOrWhiteSpace($resource)
}

function Assert-NoResourceDeletionFinalizer {
  param([Parameter(Mandatory)][string]$Name)
  if (-not (Test-ApplicationExists $Name)) { return }
  $finalizers = Get-NativeText "kubectl" @(
    "-n", $ArgoNamespace, "get", "application", $Name,
    "-o", "jsonpath={.metadata.finalizers[*]}"
  )
  if (-not [string]::IsNullOrWhiteSpace($finalizers)) {
    throw "Application '$Name' has finalizer '$finalizers'. Refusing an ownership change that could cascade-delete resources."
  }
}

function Wait-ApplicationHealthy {
  param([Parameter(Mandatory)][string]$Name)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  $lastState = ""
  while ((Get-Date) -lt $deadline) {
    $state = Get-NativeText "kubectl" @(
      "-n", $ArgoNamespace, "get", "application", $Name,
      "-o", "jsonpath={.status.sync.status}|{.status.health.status}"
    ) -AllowFailure
    if ($state -ne $lastState -and -not [string]::IsNullOrWhiteSpace($state)) {
      Write-Output "Application $Name state: $state"
      $lastState = $state
    }
    if ($state -eq "Synced|Healthy") { return }
    Start-Sleep -Seconds 5
  }
  throw "Application '$Name' did not become Synced|Healthy within $TimeoutSeconds seconds."
}

foreach ($requiredFile in @(
    (Join-Path $ArgoPath "kustomization.yaml"),
    $CloudApplicationManifest,
    $PilotApplicationManifest,
    (Join-Path $CloudOverlayPath "kustomization.yaml")
  )) {
  if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
    throw "Required Phase 19 file is missing: $requiredFile"
  }
}

$context = Get-NativeText "kubectl" @("config", "current-context")
if ($context -notmatch [regex]::Escape($ExpectedClusterName)) {
  throw "kubectl context '$context' does not target expected cluster '$ExpectedClusterName'."
}
Write-Output "kubectl context: $context"

$null = Get-NativeText "kubectl" @("kustomize", $CloudOverlayPath)
$null = Get-NativeText "kubectl" @("apply", "--dry-run=client", "-k", $CloudOverlayPath)
$null = Get-NativeText "kubectl" @("apply", "--dry-run=client", "-k", $ArgoPath)

$renderedArgo = Get-NativeText "kubectl" @("kustomize", $ArgoPath)
$applicationCount = ([regex]::Matches($renderedArgo, "(?m)^kind:\s*Application\s*$")).Count
if ($applicationCount -ne 1 -or $renderedArgo -notmatch "name:\s*flash-sale-cloud") {
  throw "Argo overlay must render exactly one flash-sale-cloud Application."
}
if ($renderedArgo -notmatch "path:\s*infra/k8s/overlays/cloud") {
  throw "flash-sale-cloud must target the canonical cloud overlay."
}
if ($renderedArgo -notmatch "prune:\s*false") {
  throw "flash-sale-cloud must keep automatic pruning disabled."
}

$renderedCloud = Get-NativeText "kubectl" @("kustomize", $CloudOverlayPath)
if ($renderedCloud -notmatch [regex]::Escape($ExpectedProductImage)) {
  throw "Cloud overlay must preserve Product image '$ExpectedProductImage'."
}

foreach ($deploymentName in $ApplicationDeployments) {
  $available = Get-NativeText "kubectl" @(
    "-n", $WorkloadNamespace, "get", "deployment", $deploymentName,
    "-o", "jsonpath={.status.availableReplicas}"
  )
  if ([string]::IsNullOrWhiteSpace($available) -or [int]$available -lt 1) {
    throw "Deployment '$WorkloadNamespace/$deploymentName' is not available."
  }
}

$pilotExists = Test-ApplicationExists $PilotApplicationName
if ($pilotExists) {
  Assert-NoResourceDeletionFinalizer $PilotApplicationName
  $pilotPath = Get-NativeText "kubectl" @(
    "-n", $ArgoNamespace, "get", "application", $PilotApplicationName,
    "-o", "jsonpath={.spec.source.path}"
  )
  if ($pilotPath -ne "infra/k8s/overlays/dev-pilot") {
    throw "Historical Application '$PilotApplicationName' has unexpected source path '$pilotPath'."
  }
}

Write-Output "Phase 19 prerequisites passed."
Write-Output "Cloud Application: one owner, self-heal enabled, prune disabled."
Write-Output "Product image preserved: $ExpectedProductTag"
Write-Output "Secret values were not read or printed."

if (-not $Apply) {
  Write-Output "Validation-only mode: no Argo CD or workload resource was changed."
  exit 0
}

# Argo reads targetRevision develop. Refuse cutover until the reviewed Phase 19 desired state exists
# on origin/develop; otherwise Argo could reconcile an older Product image.
$remoteCloudKustomization = Get-NativeText "git" @(
  "show", "origin/develop:infra/k8s/overlays/cloud/kustomization.yaml"
) -AllowFailure
$remoteCloudApplication = Get-NativeText "git" @(
  "show", "origin/develop:infra/k8s/argocd/application-cloud.yaml"
) -AllowFailure
if ($remoteCloudKustomization -notmatch [regex]::Escape($ExpectedProductTag) -or
    $remoteCloudApplication -notmatch "path:\s*infra/k8s/overlays/cloud") {
  throw "Phase 19 is not present on origin/develop. Merge the reviewed PR, fetch develop, then rerun -Apply."
}

$pilotSuspended = $false
try {
  if ($pilotExists) {
    $suspendPatch = @{
      spec = @{
        syncPolicy = @{
          automated = $null
        }
      }
    } | ConvertTo-Json -Depth 5 -Compress
    Invoke-Native "kubectl" @(
      "-n", $ArgoNamespace, "patch", "application", $PilotApplicationName,
      "--type=merge", "--patch", $suspendPatch
    )
    $pilotSuspended = $true
    Write-Output "Historical pilot auto-sync suspended."
  }

  Invoke-Native "kubectl" @("apply", "-k", $ArgoPath)
  Invoke-Native "kubectl" @(
    "-n", $ArgoNamespace, "annotate", "application", $CloudApplicationName,
    "argocd.argoproj.io/refresh=hard", "--overwrite"
  )
  Wait-ApplicationHealthy $CloudApplicationName

  foreach ($deploymentName in $ApplicationDeployments) {
    Invoke-Native "kubectl" @(
      "-n", $WorkloadNamespace, "rollout", "status", "deployment/$deploymentName",
      ("--timeout=" + $TimeoutSeconds + "s")
    )
  }

  $runningProductImage = Get-NativeText "kubectl" @(
    "-n", $WorkloadNamespace, "get", "deployment", "product-service",
    "-o", "jsonpath={.spec.template.spec.containers[0].image}"
  )
  if ($runningProductImage -ne $ExpectedProductImage) {
    throw "Product image changed during cutover. Expected '$ExpectedProductImage', got '$runningProductImage'."
  }

  $cloudPath = Get-NativeText "kubectl" @(
    "-n", $ArgoNamespace, "get", "application", $CloudApplicationName,
    "-o", "jsonpath={.spec.source.path}"
  )
  $cloudPrune = Get-NativeText "kubectl" @(
    "-n", $ArgoNamespace, "get", "application", $CloudApplicationName,
    "-o", "jsonpath={.spec.syncPolicy.automated.prune}"
  )
  if ($cloudPath -ne "infra/k8s/overlays/cloud" -or $cloudPrune -ne "false") {
    throw "Cloud Application ownership or no-prune policy does not match the approved plan."
  }

  if ($pilotExists) {
    Assert-NoResourceDeletionFinalizer $PilotApplicationName
    Invoke-Native "kubectl" @(
      "-n", $ArgoNamespace, "delete", "application", $PilotApplicationName,
      "--wait=true"
    )
    Write-Output "Historical pilot Application object retired without cascading resource deletion."
  }
} catch {
  $cutoverFailure = $_
  Write-Output "Phase 19 cutover failed. Restoring historical pilot ownership."
  if (Test-ApplicationExists $CloudApplicationName) {
    Assert-NoResourceDeletionFinalizer $CloudApplicationName
    Invoke-Native "kubectl" @(
      "-n", $ArgoNamespace, "delete", "application", $CloudApplicationName,
      "--wait=true"
    )
  }
  if ($pilotExists -and $pilotSuspended) {
    Invoke-Native "kubectl" @("apply", "-f", $PilotApplicationManifest)
    Invoke-Native "kubectl" @(
      "-n", $ArgoNamespace, "annotate", "application", $PilotApplicationName,
      "argocd.argoproj.io/refresh=hard", "--overwrite"
    )
  }
  throw $cutoverFailure
}

Invoke-Native "kubectl" @("-n", $ArgoNamespace, "get", "applications")
Invoke-Native "kubectl" @("-n", $WorkloadNamespace, "get", "deployments")
Write-Output "Phase 19 complete: flash-sale-cloud is the single active desired-state owner."
