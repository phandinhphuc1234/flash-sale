<#
.SYNOPSIS
    Validates or explicitly bootstraps the internal Argo CD dev-pilot GitOps loop.

.DESCRIPTION
    Default mode renders the source-controlled Argo CD Application and prints the pinned official
    install URL. It does not contact the cluster with an apply operation. -Apply creates/updates the
    argocd namespace, applies the pinned non-HA Argo CD manifest, waits for core workloads, checks
    that argocd-server is ClusterIP, applies the dev-pilot Application, and waits for Synced/Healthy.

    The script never reads or prints the Argo CD admin password, never writes Secret values, never
    deletes the Product PVC, and never runs Terraform.
#>
[CmdletBinding()]
param(
    [switch]$Apply,

    [string]$Namespace = 'argocd',

    [string]$ArgoVersion = 'v3.4.2'
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
$argocdPath = Join-Path $repoRoot 'infra\k8s\argocd'
$applicationPath = Join-Path $argocdPath 'application-dev-pilot.yaml'
$installUrl = "https://raw.githubusercontent.com/argoproj/argo-cd/$ArgoVersion/manifests/install.yaml"

if (-not (Test-Path $applicationPath)) {
    throw "Argo CD Application manifest not found: $applicationPath"
}

$context = Invoke-NativeCapture 'kubectl' @('config', 'current-context')
Write-Host "kubectl context: $context"
Write-Host "Pinned Argo CD install: $installUrl"

$rendered = Invoke-NativeCapture 'kubectl' @('kustomize', $argocdPath)
$applicationCount = ([regex]::Matches($rendered, '(?m)^kind:\s*Application')).Count
if ($applicationCount -ne 1) {
    throw "Expected exactly one Argo CD Application, rendered $applicationCount."
}
Write-Host 'Application manifest render: one dev-pilot Application.'

if (-not $Apply) {
    Write-Host 'Validation-only mode complete. No namespace, install manifest, or Application was applied.'
    exit 0
}

# Create/update the namespace without exposing or persisting any credential.
$namespaceYaml = Invoke-NativeCapture 'kubectl' @(
    'create', 'namespace', $Namespace,
    '--dry-run=client',
    '--output', 'yaml'
)
$namespaceYaml | kubectl apply --server-side -f -
if ($LASTEXITCODE -ne 0) {
    throw "Failed to apply the $Namespace namespace."
}

# The upstream manifest is pinned above; no floating stable/latest URL is used.
Invoke-Native 'kubectl' @(
    'apply',
    '--namespace', $Namespace,
    '--server-side',
    '--force-conflicts',
    '--filename', $installUrl
)

Invoke-Native 'kubectl' @(
    'wait',
    '--namespace', $Namespace,
    '--for=condition=Available',
    'deployment',
    '--all',
    '--timeout=300s'
)
Invoke-Native 'kubectl' @(
    'rollout', 'status', 'statefulset/argocd-application-controller',
    '--namespace', $Namespace,
    '--timeout=300s'
)

$serverType = Invoke-NativeCapture 'kubectl' @(
    'get', 'service', 'argocd-server',
    '--namespace', $Namespace,
    '--output', 'jsonpath={.spec.type}'
)
if ($serverType.Trim() -ne 'ClusterIP') {
    throw "argocd-server must remain ClusterIP, but is $serverType."
}
Write-Host 'argocd-server exposure: ClusterIP.'

# Apply only the source-controlled Application; the Application then reconciles dev-pilot.
Invoke-Native 'kubectl' @('apply', '--namespace', $Namespace, '--kustomize', $argocdPath)
Invoke-Native 'kubectl' @(
    'wait',
    '--namespace', $Namespace,
    '--for=jsonpath={.status.sync.status}=Synced',
    'applications.argoproj.io/dev-pilot',
    '--timeout=300s'
)
Invoke-Native 'kubectl' @(
    'wait',
    '--namespace', $Namespace,
    '--for=jsonpath={.status.health.status}=Healthy',
    'applications.argoproj.io/dev-pilot',
    '--timeout=300s'
)

Invoke-Native 'kubectl' @('get', 'pods,service', '--namespace', $Namespace)
Invoke-Native 'kubectl' @('get', 'application', 'dev-pilot', '--namespace', $Namespace)
Invoke-Native 'kubectl' @('get', 'pods,service,pvc', '--namespace', 'flash-sale')
Write-Host 'Argo CD dev-pilot bootstrap completed. The admin password was not read or printed.'
