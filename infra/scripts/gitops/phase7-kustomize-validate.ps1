<#
.SYNOPSIS
    Validates the Phase 7 Kustomize development overlay without changing EKS.
.DESCRIPTION
    Renders the overlay, checks that the expected 18 resources are present, and runs
    kubectl client-side dry-run. This script never performs a live kubectl apply.
#>
[CmdletBinding()]
param(
    [string]$Overlay = "infra/k8s/overlays/dev"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$overlayPath = (Resolve-Path (Join-Path $repoRoot $Overlay)).Path

# Render desired state locally so malformed YAML or Kustomize references fail early.
$rendered = @(& kubectl kustomize $overlayPath)
if ($LASTEXITCODE -ne 0) {
    throw "kubectl kustomize failed."
}

# The bootstrap contract is one Namespace, one ConfigMap, eight Deployments, and eight Services.
$kindCount = @($rendered | Select-String -Pattern "^kind: (Namespace|ConfigMap|Deployment|Service)$").Count
Write-Host "Rendered resource count: $kindCount (expected 18)."
if ($kindCount -ne 18) {
    throw "Unexpected rendered resource count."
}

# Validate object shape against the Kubernetes client without mutating the cluster.
& kubectl apply --dry-run=client -k $overlayPath
if ($LASTEXITCODE -ne 0) {
    throw "Kubernetes client-side dry-run failed."
}

Write-Host "Phase 7 Kustomize validation passed. No live resources were applied."
