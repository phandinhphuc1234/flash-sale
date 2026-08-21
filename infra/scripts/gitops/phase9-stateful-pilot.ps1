<#
.SYNOPSIS
    Validates or explicitly applies the Phase 9 Product/PostgreSQL pilot overlay.

.DESCRIPTION
    The default mode checks the current kubectl context, verifies only the names of the external
    Secret objects, renders the six-resource pilot overlay, and runs a Kubernetes client-side
    dry-run. It never mutates AWS or Kubernetes in default mode.

    -Apply applies only infra/k8s/overlays/dev-pilot, waits for PostgreSQL and Product Service,
    and prints status. It never runs terraform apply, writes Secret values, deletes Pods, or deletes
    PVCs. The explicit -Image is a live image override; update the overlay before Argo CD adopts it.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[^:]+/.+:[^:]+$')]
    [string]$Image,

    [switch]$Apply,

    [string]$Namespace = 'flash-sale',

    [string]$OverlayPath
)

$ErrorActionPreference = 'Stop'

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE."
    }
}

function Invoke-NativeCapture {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $output = & $Command @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE.`n$($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine)
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if (-not $OverlayPath) {
    $OverlayPath = Join-Path $repoRoot 'infra\k8s\overlays\dev-pilot'
}

if (-not (Test-Path (Join-Path $OverlayPath 'kustomization.yaml'))) {
    throw "Pilot overlay not found: $OverlayPath"
}

$context = Invoke-NativeCapture 'kubectl' @('config', 'current-context')
Write-Host "kubectl context: $context"

# Only resource names are read; Secret data is never requested or printed.
foreach ($secretName in @('product-postgres-credentials', 'flash-sale-secrets')) {
    Invoke-Native 'kubectl' @('get', 'secret', $secretName, '--namespace', $Namespace, '--output', 'name')
}

$rendered = Invoke-NativeCapture 'kubectl' @('kustomize', $OverlayPath)
$resourceCount = ([regex]::Matches($rendered, '(?m)^kind: ')).Count
if ($resourceCount -ne 6) {
    throw "Expected six pilot resources, rendered $resourceCount."
}
Write-Host 'Rendered six pilot resources.'

Invoke-Native 'kubectl' @('apply', '--dry-run=client', '-k', $OverlayPath)
Write-Host 'Client-side dry-run passed. No live resource was changed.'

if (-not $Apply) {
    Write-Host 'Validation-only mode complete. Add -Apply to mutate the pilot namespace.'
    exit 0
}

Invoke-Native 'kubectl' @('apply', '-k', $OverlayPath)

# The overlay contains the reviewed immutable tag. This explicit override is useful for a new
# already-pushed tag during the manual pilot; update Git before a future Argo CD reconciliation.
Invoke-Native 'kubectl' @(
    'set', 'image',
    'deployment/product-service',
    "product-service=$Image",
    '--namespace', $Namespace
)

Invoke-Native 'kubectl' @(
    'rollout', 'status', 'statefulset/product-postgres',
    '--namespace', $Namespace,
    '--timeout=180s'
)
Invoke-Native 'kubectl' @(
    'rollout', 'status', 'deployment/product-service',
    '--namespace', $Namespace,
    '--timeout=180s'
)
Invoke-Native 'kubectl' @('get', 'pods,svc,pvc', '--namespace', $Namespace)
Write-Host 'Phase 9 Product pilot apply completed.'
