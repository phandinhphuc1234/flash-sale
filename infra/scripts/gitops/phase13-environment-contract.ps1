[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $Environment
)

$ErrorActionPreference = "Stop"

# This script validates environment names and paths only. It deliberately never
# reads .env, PEM files, Kubernetes Secret manifests, or provider credentials.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$supportedEnvironments = @("local", "cloud")

if ($supportedEnvironments -notcontains $Environment) {
    throw "Unsupported environment '$Environment'. Supported environments are: local, cloud."
}

$environmentDefinitions = @{
    local = @{
        SourcePath = Join-Path $repoRoot "infra\docker"
        Runtime = "Docker Compose"
        Reconciler = "Manual docker compose commands"
    }
    cloud = @{
        SourcePath = Join-Path $repoRoot "infra\k8s\overlays\cloud"
        Runtime = "AWS EKS"
        Reconciler = "Argo CD (later phase)"
    }
}

$definition = $environmentDefinitions[$Environment]
if (-not (Test-Path -LiteralPath $definition.SourcePath -PathType Container)) {
    throw "Source-of-truth path is missing: $($definition.SourcePath)"
}

$composePath = Join-Path $repoRoot "infra\docker\compose.yml"
$cloudOverlayPath = Join-Path $repoRoot "infra\k8s\overlays\cloud\kustomization.yaml"
$ignoredEnvPath = Join-Path $repoRoot "infra\docker\.env"
$secretKeys = @(
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "REDIS_PASSWORD",
    "RATE_LIMIT_KEY_HMAC_SECRET",
    "AUTH_THROTTLE_HMAC_SECRET",
    "JWT_PUBLIC_KEY_PEM",
    "JWT_PRIVATE_KEY_PEM",
    "CAMPAIGN_CLIENT_SECRET",
    "FLASHSALE_CLIENT_SECRET",
    "STRIPE_SECRET_KEY",
    "STRIPE_PUBLISHABLE_KEY",
    "STRIPE_WEBHOOK_SECRET"
)

if ($Environment -eq "local" -and -not (Test-Path -LiteralPath $composePath -PathType Leaf)) {
    throw "Local Compose source is missing: $composePath"
}

if ($Environment -eq "cloud" -and -not (Test-Path -LiteralPath $cloudOverlayPath -PathType Leaf)) {
    throw "Cloud Kustomize source is missing: $cloudOverlayPath"
}

Push-Location $repoRoot
try {
    git check-ignore --quiet -- infra/docker/.env
    if ($LASTEXITCODE -ne 0) {
        throw "infra/docker/.env is not ignored. Refusing to continue until secret storage is safe."
    }
}
finally {
    Pop-Location
}

Write-Output "Environment: $Environment"
Write-Output "Source of truth: $($definition.SourcePath)"
Write-Output "Runtime: $($definition.Runtime)"
Write-Output "Reconciler: $($definition.Reconciler)"
Write-Output "Secret values: not read or printed"
Write-Output "Required secret names:"
$secretKeys | ForEach-Object { Write-Output "  - $_" }
