<#
.SYNOPSIS
    Tests, builds, and optionally publishes the Product Service pilot image to ECR.
.DESCRIPTION
    Runs the Product Service Maven reactor, logs into the configured ECR registry,
    builds the repository Dockerfile, and pushes only when -Push is supplied.
    The default tag is derived from the current Git commit for ECR immutability.
.SAFETY
    Without -Push, the image remains local. Reusing an existing immutable ECR tag will fail.
#>
[CmdletBinding()]
param(
    [string]$AwsProfile = "flash-sale-terraform",
    [string]$AwsRegion = "ap-southeast-2",
    [string]$Repository = "flash-sale/product-service",
    [string]$Tag = "",
    [switch]$Push
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$registry = "090814040069.dkr.ecr." + $AwsRegion + ".amazonaws.com"
if (-not $Tag) {
    $shortSha = (& git -C $repoRoot rev-parse --short HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $shortSha) {
        throw "Could not determine the current Git commit."
    }
    $Tag = "pilot-" + $shortSha
}
$image = $registry + "/" + $Repository + ":" + $Tag
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$dockerfile = Join-Path $repoRoot "services\product-service\Dockerfile"

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $false)]
        [string[]]$Arguments = @()
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath exited with code $LASTEXITCODE."
    }
}

# Select the AWS identity for ECR authentication without persisting credentials.
$env:AWS_PROFILE = $AwsProfile
$env:AWS_REGION = $AwsRegion

# Verify the Product Service and its required reactor dependencies before containerizing.
Invoke-Native $mavenWrapper @("-pl", "services/product-service", "-am", "verify")

# Request a short-lived ECR login token and pipe it directly to Docker.
$loginPassword = & aws ecr get-login-password --region $AwsRegion --profile $AwsProfile
if ($LASTEXITCODE -ne 0) {
    throw "AWS ECR login password request failed."
}
$loginPassword | & docker login --username AWS --password-stdin $registry
if ($LASTEXITCODE -ne 0) {
    throw "Docker login to ECR failed."
}

# Build the multi-stage Java 21 image from the repository root.
Invoke-Native "docker" @("build", "-f", $dockerfile, "-t", $image, $repoRoot)

if ($Push) {
    # Publishing is explicit because it changes the remote ECR repository.
    Invoke-Native "docker" @("push", $image)
    Write-Host "Pushed image: $image"
} else {
    Write-Host "Image built locally: $image"
    Write-Host "Re-run with -Push to publish it to ECR."
}

Write-Output $image
