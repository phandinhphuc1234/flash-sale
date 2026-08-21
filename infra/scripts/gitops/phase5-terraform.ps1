<#
.SYNOPSIS
    Runs the Phase 5 Terraform workflow for the shared AWS foundation and EKS cluster.
.DESCRIPTION
    Initializes Terraform, executes a plan, and optionally runs apply when -Apply is supplied.
    The script reads terraform.tfvars locally when present and never creates that file.
.SAFETY
    Plan is the default. Terraform apply still asks for its normal confirmation.
#>
[CmdletBinding()]
param(
    [string]$AwsProfile = "flash-sale-terraform",
    [string]$AwsRegion = "ap-southeast-2",
    [string]$VarFile = "",
    [switch]$Apply
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$terraformDir = Join-Path $repoRoot "infra\terraform"

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

# Select the AWS identity used by Terraform without storing credentials in this repository.
$env:AWS_PROFILE = $AwsProfile
$env:AWS_REGION = $AwsRegion

$selectedVarFile = $null
if ($VarFile) {
    $selectedVarFile = (Resolve-Path (Join-Path $repoRoot $VarFile)).Path
} else {
    $defaultVarFile = Join-Path $terraformDir "terraform.tfvars"
    if (Test-Path $defaultVarFile) {
        $selectedVarFile = (Resolve-Path $defaultVarFile).Path
    }
}

Push-Location $terraformDir
try {
    Write-Host "Terraform directory: $terraformDir"
    Write-Host "AWS profile: $AwsProfile / region: $AwsRegion"

    # Initialize the local Terraform working directory and remote state connection.
    Invoke-Native "terraform" @("init", "-input=false")

    $planArguments = @("plan", "-input=false")
    if ($selectedVarFile) {
        $planArguments += "-var-file=$selectedVarFile"
    } else {
        Write-Warning "No terraform.tfvars found. Required variables may make plan fail."
    }

    # Always show the proposed infrastructure delta before an optional apply.
    Invoke-Native "terraform" $planArguments

    if ($Apply) {
        # Apply is intentionally opt-in and remains interactive for a final review.
        Write-Host "Apply requested. Terraform will ask for confirmation."
        $applyArguments = @("apply", "-input=false")
        if ($selectedVarFile) {
            $applyArguments += "-var-file=$selectedVarFile"
        }
        Invoke-Native "terraform" $applyArguments
    } else {
        Write-Host "Plan only. Re-run with -Apply after reviewing the plan."
    }
} finally {
    Pop-Location
}
