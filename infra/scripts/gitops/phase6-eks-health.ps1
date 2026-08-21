<#
.SYNOPSIS
    Connects kubectl to the EKS cluster and records the Phase 6 platform health checks.
.DESCRIPTION
    Refreshes the local kubeconfig, then reads nodes, system Pods, and StorageClasses.
    It does not create, update, or delete Kubernetes resources.
#>
[CmdletBinding()]
param(
    [string]$AwsProfile = "flash-sale-terraform",
    [string]$AwsRegion = "ap-southeast-2",
    [string]$ClusterName = "flash-sale-dev"
)

$ErrorActionPreference = "Stop"

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

# Select the AWS profile used to generate the local kubeconfig context.
$env:AWS_PROFILE = $AwsProfile
$env:AWS_REGION = $AwsRegion

# Point kubectl at the named EKS cluster using the selected AWS profile.
Invoke-Native "aws" @(
    "eks", "update-kubeconfig",
    "--region", $AwsRegion,
    "--name", $ClusterName,
    "--profile", $AwsProfile
)

Write-Host "Current Kubernetes context:"
Invoke-Native "kubectl" @("config", "current-context")

# Verify worker-node readiness and capacity information.
Write-Host "EKS nodes:"
Invoke-Native "kubectl" @("get", "nodes", "-o", "wide")

# Check the AWS VPC CNI, CoreDNS, and kube-proxy system workloads.
Write-Host "System workloads:"
Invoke-Native "kubectl" @("get", "pods", "-A")

# Confirm a dynamic storage class exists for the later PostgreSQL pilot.
Write-Host "Storage classes:"
Invoke-Native "kubectl" @("get", "storageclass")
