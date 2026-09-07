<#
.SYNOPSIS
  Validate or explicitly apply the Feature 049 expand-first migrations.

.DESCRIPTION
  Resolves the exact release-<remote develop SHA> Cart, Inventory, and Order images from ECR,
  renders a temporary three-Job manifest, and runs the Jobs sequentially in Cart -> Inventory ->
  Order order. The default is read-only. -Apply is an explicit cloud mutation and never deletes,
  reruns, or prints Secret values.
#>
[CmdletBinding()]
param(
  [string]$AwsProfile = "flash-sale-terraform",
  [string]$AwsRegion = "ap-southeast-2",
  [string]$ExpectedAccountId = "090814040069",
  [string]$ExpectedClusterName = "flash-sale-dev",
  [string]$Namespace = "flash-sale",
  [ValidatePattern("^[0-9a-fA-F]{40}$")][string]$ReleaseSha = "",
  [ValidateRange(60, 1800)][int]$TimeoutSeconds = 900,
  [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
if ($PSVersionTable.PSVersion.Major -lt 7) { throw "Feature 049 migration gate requires PowerShell 7 or newer." }

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$overlayPath = Join-Path $repoRoot "infra\k8s\overlays\cloud-migrations\feature-049"
$temporaryDirectory = ""
$temporaryManifest = ""
$jobs = @(
  [pscustomobject]@{ Name = "migrate-cart-checkout"; Repository = "flash-sale/cart-service" },
  [pscustomobject]@{ Name = "migrate-inventory-regular-hold"; Repository = "flash-sale/inventory-service" },
  [pscustomobject]@{ Name = "migrate-order-regular-purchase"; Repository = "flash-sale/order-service" }
)

function Get-RemainingSeconds {
  $remaining = [int][Math]::Ceiling(($deadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) { throw "Feature 049 migration gate exceeded its $TimeoutSeconds-second budget." }
  return $remaining
}

function Get-BoundedText { param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return "no diagnostic output" }
  if ($Text.Length -le 2000) { return $Text.Trim() }
  return $Text.Substring(0, 2000).Trim() + "... [truncated]"
}

function Invoke-Native {
  param([Parameter(Mandatory)][string]$Command,[Parameter(Mandatory)][string[]]$Arguments,[string]$WorkingDirectory = "")
  $info = [Diagnostics.ProcessStartInfo]::new()
  $info.FileName = $Command; $info.UseShellExecute = $false; $info.CreateNoWindow = $true
  $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
  if ($WorkingDirectory) { $info.WorkingDirectory = $WorkingDirectory }
  foreach ($argument in $Arguments) { $null = $info.ArgumentList.Add([string]$argument) }
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $info; $started = $false
  try {
    $null = Get-RemainingSeconds
    if (-not $process.Start()) { throw "Could not start '$Command'." }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit((Get-RemainingSeconds) * 1000)) { $process.Kill($true); $process.WaitForExit(); throw "'$Command' exceeded the Feature 049 budget." }
    $process.WaitForExit()
    [pscustomobject]@{ ExitCode = $process.ExitCode; StandardOutput = $stdoutTask.GetAwaiter().GetResult().Trim(); StandardError = $stderrTask.GetAwaiter().GetResult().Trim() }
  } finally {
    if ($started -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
    $process.Dispose()
  }
}

function Invoke-Required {
  param([Parameter(Mandatory)][string]$Command,[Parameter(Mandatory)][string[]]$Arguments,[Parameter(Mandatory)][string]$Description,[string]$WorkingDirectory = "")
  $result = Invoke-Native -Command $Command -Arguments $Arguments -WorkingDirectory $WorkingDirectory
  if ($result.ExitCode -ne 0) {
    $diagnostic = if ($result.StandardError) { $result.StandardError } else { $result.StandardOutput }
    throw "$Description failed with exit code $($result.ExitCode): $(Get-BoundedText $diagnostic)"
  }
  return $result
}

function Get-RemoteDevelopSha {
  $result = Invoke-Required -Command "git" -Arguments @("ls-remote","origin","refs/heads/develop") -Description "Remote develop SHA" -WorkingDirectory $repoRoot
  $match = [regex]::Match($result.StandardOutput, "(?m)^(?<sha>[0-9a-fA-F]{40})\s+refs/heads/develop$")
  if (-not $match.Success) { throw "Could not resolve origin/develop SHA." }
  return $match.Groups["sha"].Value.ToLowerInvariant()
}

function Resolve-ReleaseSha {
  $remote = Get-RemoteDevelopSha
  if (-not $ReleaseSha) { return $remote }
  if ($ReleaseSha.ToLowerInvariant() -ne $remote) { throw "ReleaseSha must match current origin/develop; use the reviewed immutable release." }
  return $remote
}

function Assert-AwsIdentity {
  $result = Invoke-Required -Command "aws" -Arguments @("sts","get-caller-identity","--profile",$AwsProfile,"--query","Account","--output","text") -Description "AWS caller identity"
  $account = $result.StandardOutput.Trim()
  if ($account -ne $ExpectedAccountId) { throw "AWS profile resolved to an unexpected account; credentials were not displayed." }
  Write-Host "AWS identity: PASS (account=$account region=$AwsRegion profile=$AwsProfile)"
  return $account
}

function Resolve-ImmutableImage {
  param([Parameter(Mandatory)][string]$AccountId,[Parameter(Mandatory)][string]$Repository,[Parameter(Mandatory)][string]$Sha)
  $tag = "release-$Sha"
  $result = Invoke-Required -Command "aws" -Arguments @("ecr","describe-images","--repository-name",$Repository,"--image-ids","imageTag=$tag","--profile",$AwsProfile,"--region",$AwsRegion,"--query","imageDetails[0].[imageDigest,imageTags]","--output","json") -Description "ECR image lookup for $Repository"
  try { $details = @($result.StandardOutput | ConvertFrom-Json -Depth 10) } catch { throw "ECR returned invalid image metadata for $Repository." }
  if ($details.Count -ne 2 -or [string]::IsNullOrWhiteSpace([string]$details[0]) -or @($details[1]) -notcontains $tag) { throw "ECR did not return immutable tag $tag for $Repository." }
  return "$AccountId.dkr.ecr.$AwsRegion.amazonaws.com/${Repository}:$tag"
}

function Assert-Cluster {
  $context = (Invoke-Required -Command "kubectl" -Arguments @("config","current-context") -Description "kubectl context").StandardOutput
  if ($context -notmatch [regex]::Escape(":cluster/$ExpectedClusterName")) { throw "kubectl context is not EKS cluster '$ExpectedClusterName'." }
  Write-Output "kubectl context: $context"
  $null = Invoke-Required -Command "kubectl" -Arguments @("get","namespace",$Namespace,"-o","name") -Description "Kubernetes namespace"
  foreach ($resource in @("statefulset/postgres","configmap/cart-service-runtime-config","configmap/inventory-service-runtime-config","configmap/order-service-runtime-config","secret/cart-secrets","secret/inventory-secrets","secret/order-secrets")) {
    $null = Invoke-Required -Command "kubectl" -Arguments @("-n",$Namespace,"get",$resource,"-o","name") -Description "Migration prerequisite $resource"
  }
}

function Render-Manifest {
  param([Parameter(Mandatory)][hashtable]$Images)
  $rendered = Invoke-Required -Command "kubectl" -Arguments @("kustomize",$overlayPath) -Description "Feature 049 migration overlay render"
  $manifest = $rendered.StandardOutput
  foreach ($name in @("cart-service","inventory-service","order-service")) {
    if ([regex]::Matches($manifest,"(?m)^\s*image:\s*$([regex]::Escape($name))\s*$").Count -ne 1) { throw "Expected one image placeholder for $name." }
    $manifest = $manifest.Replace("image: $name", "image: $($Images[$name])")
  }
  if ($manifest -match "(?m)^\s*image:\s*(cart-service|inventory-service|order-service)\s*$") { throw "A migration image placeholder was not replaced." }
  $temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ("flash-sale-feature-049-migration-" + [Guid]::NewGuid().ToString("N"))
  $null = New-Item -ItemType Directory -Path $temporaryDirectory
  $temporaryManifest = Join-Path $temporaryDirectory "feature-049-migrations.yaml"
  Set-Content -LiteralPath $temporaryManifest -Value $manifest -Encoding utf8NoBOM -NoNewline
  return $temporaryManifest
}

function Get-JobDiagnostics { param([Parameter(Mandatory)][string]$JobName)
  $job = Invoke-Native -Command "kubectl" -Arguments @("-n",$Namespace,"get","job",$JobName,"-o","custom-columns=NAME:.metadata.name,SUCCEEDED:.status.succeeded,FAILED:.status.failed,ACTIVE:.status.active","--no-headers")
  $pods = Invoke-Native -Command "kubectl" -Arguments @("-n",$Namespace,"get","pods","-l","job-name=$JobName","-o","custom-columns=POD:.metadata.name,PHASE:.status.phase,REASON:.status.containerStatuses[0].state.waiting.reason","--no-headers")
  $jobText = if ($job.ExitCode -eq 0) { $job.StandardOutput } else { "job status unavailable" }
  $podText = if ($pods.ExitCode -eq 0) { $pods.StandardOutput } else { "pod status unavailable" }
  return Get-BoundedText "job=[$jobText]; pods=[$podText]"
}

try {
  if (-not (Test-Path (Join-Path $overlayPath "kustomization.yaml"))) { throw "Feature 049 migration overlay not found." }
  $account = Assert-AwsIdentity
  $sha = Resolve-ReleaseSha
  $images = @{}
  foreach ($item in $jobs) {
    $images[$item.Repository.Split('/')[1]] = Resolve-ImmutableImage -AccountId $account -Repository $item.Repository -Sha $sha
  }
  Write-Output "Immutable Feature 049 images: PASS (Cart, Inventory, and Order release tag resolved; SHA withheld)."
  Assert-Cluster
  $temporaryManifest = Render-Manifest -Images $images
  $null = Invoke-Required -Command "kubectl" -Arguments @("apply","--dry-run=client","--validate=false","-f",$temporaryManifest) -Description "Feature 049 migration client dry-run"
  Write-Output "Feature 049 migration manifest: PASS (three Jobs rendered and client dry-run passed)."
  foreach ($job in $jobs) {
    $existing = (Invoke-Required -Command "kubectl" -Arguments @("-n",$Namespace,"get","job",$job.Name,"--ignore-not-found","-o","name") -Description "Existing migration Job lookup").StandardOutput
    if ($existing) { throw "Migration Job '$($job.Name)' already exists; this gate never deletes or reruns Jobs." }
  }
  Write-Output "Migration release gate: PASS (Cart then Inventory then Order; no Job created yet)."
  Write-Output "Secret values were not read or printed."
  if (-not $Apply) { Write-Output "Validation-only mode: no Kubernetes Job, ECR image, Argo, or database state changed."; exit 0 }
  $null = Invoke-Required -Command "kubectl" -Arguments @("apply","-f",$temporaryManifest) -Description "Feature 049 migration Job apply"
  foreach ($job in $jobs) {
    Write-Output "Waiting for $($job.Name)..."
    $wait = Invoke-Native -Command "kubectl" -Arguments @("-n",$Namespace,"wait","--for=condition=complete","job/$($job.Name)","--timeout=$((Get-RemainingSeconds))s")
    if ($wait.ExitCode -ne 0) { throw "Migration Job '$($job.Name)' did not complete: $(Get-JobDiagnostics $job.Name)" }
    Write-Output "Migration Job $($job.Name): PASS"
  }
  Write-Output "Feature 049 migration release gate: APPLY PASS"
  Write-Output "Cart, Inventory, and Order migrations completed sequentially. Secret values were not read or printed."
} finally {
  if ($temporaryDirectory -and (Test-Path $temporaryDirectory)) { Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force }
}
