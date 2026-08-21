<#
.SYNOPSIS
    Rehearses the Phase 11 Product image delivery and GitOps promotion locally.
.DESCRIPTION
    Runs the Product Service Maven reactor, builds an immutable commit-derived image, and optionally
    pushes it to the existing ECR repository. -UpdateOverlay explicitly changes only the Product
    pilot image tag and validates the resulting Kustomize overlay. The script never commits or pushes
    Git changes, never runs kubectl apply, and never reads or writes Secret values.
.SAFETY
    With no switches, the script only verifies and builds locally. -Push changes ECR. -UpdateOverlay
    changes the local desired-state file and must be reviewed before a manual commit.
#>
[CmdletBinding()]
param(
    [string]$AwsProfile = "flash-sale-terraform",
    [string]$AwsRegion = "ap-southeast-2",
    [string]$Repository = "flash-sale/product-service",
    [string]$Tag = "",
    [switch]$Push,
    [switch]$UpdateOverlay
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$dockerfile = Join-Path $repoRoot "services\product-service\Dockerfile"
$overlayFile = Join-Path $repoRoot "infra\k8s\overlays\dev-pilot\kustomization.yaml"

if (-not (Test-Path $mavenWrapper)) { throw "Maven wrapper not found: $mavenWrapper" }
if (-not (Test-Path $dockerfile)) { throw "Product Dockerfile not found: $dockerfile" }
if (-not (Test-Path $overlayFile)) { throw "Dev pilot overlay not found: $overlayFile" }

if (-not $Tag) {
    $Tag = "pilot-" + (& git -C $repoRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $Tag) {
        throw "Could not derive an image tag from the current Git commit."
    }
}
if ($Tag -notmatch '^[a-z0-9][a-z0-9._-]{0,127}$') {
    throw "Image tag contains unsupported characters: $Tag"
}
if ($Repository -notmatch '^[a-z0-9][a-z0-9/_-]+$') {
    throw "Repository must be an ECR repository path without credentials."
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [string[]]$Arguments = @()
    )
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE."
    }
}

Write-Host "Phase 11 Product delivery rehearsal"
Write-Host "Source commit: $((& git -C $repoRoot rev-parse HEAD).Trim())"
Write-Host "Image tag: $Tag"

# Verify the same Maven reactor that the hosted workflow verifies before any remote mutation.
Invoke-Native $mavenWrapper @("--batch-mode", "--no-transfer-progress", "-pl", "services/product-service", "-am", "verify")

# The image is built locally in every mode. This proves the Docker context without pushing remotely.
$localImage = "$Repository`:$Tag"
Invoke-Native "docker" @("build", "--file", $dockerfile, "--tag", $localImage, $repoRoot)
Write-Host "Built local image: $localImage"

$remoteImage = $localImage
if ($Push) {
    # Derive the account from the selected AWS profile; no account credential is persisted.
    $accountId = (& aws sts get-caller-identity --profile $AwsProfile --query Account --output text).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $accountId) {
        throw "Could not resolve the AWS account for the selected profile."
    }
    $registry = "$accountId.dkr.ecr.$AwsRegion.amazonaws.com"
    $remoteImage = "$registry/$Repository`:$Tag"

    $env:AWS_PROFILE = $AwsProfile
    $env:AWS_REGION = $AwsRegion
    $loginPassword = & aws ecr get-login-password --region $AwsRegion --profile $AwsProfile
    if ($LASTEXITCODE -ne 0) { throw "ECR login password request failed." }
    $loginPassword | & docker login --username AWS --password-stdin $registry
    if ($LASTEXITCODE -ne 0) { throw "Docker login to ECR failed." }

    Invoke-Native "docker" @("tag", $localImage, $remoteImage)
    Invoke-Native "docker" @("push", $remoteImage)
    Write-Host "Pushed immutable image: $remoteImage"
}
else {
    Write-Host "No ECR push requested. Re-run with -Push after reviewing the image."
}

if ($UpdateOverlay) {
    $original = [IO.File]::ReadAllText($overlayFile)
    $pattern = '(?m)^(\s*newTag:\s*)\S+\s*$'
    $updated = [Text.RegularExpressions.Regex]::Replace($original, $pattern, '${1}' + $Tag, 1)
    if ($updated -eq $original) {
        throw "The overlay already contains this image tag or has no single newTag entry."
    }

    try {
        [IO.File]::WriteAllText($overlayFile, $updated, [Text.UTF8Encoding]::new($false))
        & kubectl kustomize (Join-Path $repoRoot "infra\k8s\overlays\dev-pilot") | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Kustomize rendering failed after updating the overlay." }
        Write-Host "Updated and rendered: $overlayFile"
        Write-Host "Review the diff, then commit/push manually."
    }
    catch {
        [IO.File]::WriteAllText($overlayFile, $original, [Text.UTF8Encoding]::new($false))
        throw
    }
}
else {
    Write-Host "No overlay update requested. Use -UpdateOverlay only after reviewing the image."
}

Write-Output $remoteImage
