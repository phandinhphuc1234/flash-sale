[CmdletBinding()]
param(
    [string] $Namespace = "flash-sale",
    [switch] $RequireSecrets
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$overlayPath = Join-Path $repoRoot "infra\k8s\overlays\cloud"

if (-not (Test-Path -LiteralPath (Join-Path $overlayPath "kustomization.yaml") -PathType Leaf)) {
    throw "Cloud overlay is missing: $overlayPath"
}

$context = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($context)) {
    throw "kubectl has no current context. Connect to the cloud EKS cluster first."
}

kubectl get csidriver ebs.csi.aws.com --no-headers | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "AWS EBS CSI driver is not available in the current cluster."
}

kubectl get storageclass gp2 --no-headers | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "StorageClass gp2 is not available in the current cluster."
}

$rendered = kubectl kustomize $overlayPath
if ($LASTEXITCODE -ne 0) {
    throw "Cloud overlay cannot be rendered: $overlayPath"
}

$renderedText = $rendered -join [Environment]::NewLine
$expectedPlatformNames = @("postgres", "redis", "kafka", "schema-registry")
foreach ($platformName in $expectedPlatformNames) {
    $resourceNameLine = @($rendered | Where-Object {
        $_ -match "^\s+name:\s+$([regex]::Escape($platformName))\s*$"
    })
    if ($resourceNameLine.Count -eq 0) {
        throw "Cloud overlay is missing platform resource '$platformName'."
    }
}

$statefulSetCount = @($rendered | Where-Object { $_ -eq "kind: StatefulSet" }).Count
if ($statefulSetCount -ne 3) {
    throw "Expected 3 platform StatefulSets, found $statefulSetCount."
}

$platformStorageClassCount = @($rendered | Where-Object { $_ -match "^\s+storageClassName: gp2\s*$" }).Count
if ($platformStorageClassCount -ne 3) {
    throw "Expected 3 gp2 platform PVC templates, found $platformStorageClassCount."
}

if ($renderedText -match "(?m)^  type: (LoadBalancer|NodePort)$") {
    throw "Platform Services must remain internal; LoadBalancer/NodePort was rendered."
}

if ($RequireSecrets) {
    kubectl -n $Namespace get secret flash-sale-secrets --no-headers | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Required Secret '$Namespace/flash-sale-secrets' is missing. Secret data was not read."
    }
    Write-Output "Required Secret exists: $Namespace/flash-sale-secrets (data not read)"
}
else {
    Write-Output "Secret check deferred. Use -RequireSecrets before a live apply."
}

Write-Output "kubectl context: $context"
Write-Output "EBS CSI driver: available"
Write-Output "StorageClass gp2: available"
Write-Output "Cloud stateful overlay: renderable"
Write-Output "Platform resources: postgres, redis, kafka, schema-registry"
Write-Output "No Secret values were read or printed."
