<#
.SYNOPSIS
  Validate and explicitly apply the two Feature 044 service-owned cloud migrations.

.DESCRIPTION
  This is the pre-promotion gate for the Order-owned Purchase Saga release. It resolves the exact
  `release-<develop SHA>` images from ECR, renders only the Order and Flash Sale migration Jobs,
  and performs a Kubernetes client dry-run. The default mode is validation-only. Add -Apply only
  after the delivery workflow has pushed both immutable images and before merging its image-promotion
  PR. The script never reads Secret data or prints Secret values.
#>
[CmdletBinding()]
param(
  [string]$AwsProfile = "flash-sale-terraform",
  [string]$AwsRegion = "ap-southeast-2",
  [string]$ExpectedAccountId = "090814040069",
  [string]$ExpectedClusterName = "flash-sale-dev",
  [string]$Namespace = "flash-sale",
  [ValidatePattern("^[0-9a-fA-F]{40}$")]
  [string]$ReleaseSha = "",
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 900,
  [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw "Feature 044 migration gate requires PowerShell 7 or newer. Run it with pwsh -NoLogo -NoProfile -File."
}

$runDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$overlayPath = Join-Path $repoRoot "infra\k8s\overlays\cloud-migrations\feature-044"
$temporaryDirectory = ""
$temporaryManifest = ""
$migrationJobs = @(
  [pscustomobject]@{ Name = "migrate-order-purchase-saga"; Service = "order-service"; Repository = "flash-sale/order-service" },
  [pscustomobject]@{ Name = "migrate-flash-sale-reservation-finalization"; Service = "flash-sale-service"; Repository = "flash-sale/flash-sale-service" }
)

function Get-RemainingTimeoutSeconds {
  $remaining = [int][Math]::Ceiling(($runDeadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) { throw "Feature 044 migration gate exceeded its $TimeoutSeconds-second execution budget." }
  return $remaining
}

function Get-BoundedText {
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return "no diagnostic output" }
  if ($Text.Length -le 2000) { return $Text.Trim() }
  return $Text.Substring(0, 2000).Trim() + "... [truncated]"
}

function Invoke-BoundedNativeProcess {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [string]$WorkingDirectory = ""
  )

  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $Command
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if (-not [string]::IsNullOrWhiteSpace($WorkingDirectory)) {
    $startInfo.WorkingDirectory = $WorkingDirectory
  }
  foreach ($argument in $Arguments) {
    $null = $startInfo.ArgumentList.Add([string]$argument)
  }

  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $started = $false
  try {
    $null = Get-RemainingTimeoutSeconds
    if (-not $process.Start()) { throw "Could not start '$Command'." }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    if (-not $process.WaitForExit((Get-RemainingTimeoutSeconds) * 1000)) {
      $process.Kill($true)
      $process.WaitForExit()
      throw "Native command '$Command' exceeded the Feature 044 execution budget."
    }
    $process.WaitForExit()
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    return [pscustomobject]@{
      ExitCode = $process.ExitCode
      StandardOutput = if ($null -eq $stdout) { "" } else { $stdout.Trim() }
      StandardError = if ($null -eq $stderr) { "" } else { $stderr.Trim() }
    }
  } finally {
    if ($started -and -not $process.HasExited) {
      $process.Kill($true)
      $process.WaitForExit()
    }
    $process.Dispose()
  }
}

function Invoke-RequiredCommand {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][string]$Description,
    [string]$WorkingDirectory = ""
  )

  $result = Invoke-BoundedNativeProcess -Command $Command -Arguments $Arguments -WorkingDirectory $WorkingDirectory
  if ($result.ExitCode -ne 0) {
    $diagnostic = if ([string]::IsNullOrWhiteSpace($result.StandardError)) { $result.StandardOutput } else { $result.StandardError }
    throw "$Description failed with exit code $($result.ExitCode): $(Get-BoundedText $diagnostic)"
  }
  return $result
}

function Get-RemoteDevelopSha {
  $result = Invoke-RequiredCommand -Command "git" -Arguments @("ls-remote", "origin", "refs/heads/develop") -Description "Remote develop SHA" -WorkingDirectory $repoRoot
  $match = [regex]::Match($result.StandardOutput, "(?m)^(?<sha>[0-9a-fA-F]{40})\s+refs/heads/develop$")
  if (-not $match.Success) { throw "Could not resolve the remote develop SHA." }
  return $match.Groups["sha"].Value.ToLowerInvariant()
}

function Resolve-ReleaseSha {
  $remoteDevelopSha = Get-RemoteDevelopSha
  if ([string]::IsNullOrWhiteSpace($ReleaseSha)) { return $remoteDevelopSha }
  $requestedSha = $ReleaseSha.ToLowerInvariant()
  if ($requestedSha -ne $remoteDevelopSha) {
    throw "ReleaseSha must match the current remote develop commit. Do not run a migration against an unrelated image tag."
  }
  return $requestedSha
}

function Assert-AwsIdentity {
  $result = Invoke-RequiredCommand -Command "aws" -Arguments @(
    "sts", "get-caller-identity", "--profile", $AwsProfile, "--query", "Account", "--output", "text"
  ) -Description "AWS caller identity"
  $accountId = $result.StandardOutput.Trim()
  if ($accountId -ne $ExpectedAccountId) {
    throw "AWS profile '$AwsProfile' resolved to an unexpected account; credentials were not displayed."
  }
  Write-Host "AWS identity: PASS (account=$accountId region=$AwsRegion profile=$AwsProfile)"
  return $accountId
}

function Resolve-ImmutableImage {
  param(
    [Parameter(Mandatory)][string]$AccountId,
    [Parameter(Mandatory)][string]$Repository,
    [Parameter(Mandatory)][string]$Sha
  )

  $tag = "release-$Sha"
  $result = Invoke-RequiredCommand -Command "aws" -Arguments @(
    "ecr", "describe-images", "--repository-name", $Repository, "--image-ids", "imageTag=$tag",
    "--profile", $AwsProfile, "--region", $AwsRegion,
    "--query", "imageDetails[0].[imageDigest,imageTags]", "--output", "json"
  ) -Description "Immutable ECR image lookup for $Repository"
  try { $details = @($result.StandardOutput | ConvertFrom-Json -Depth 10) }
  catch { throw "ECR returned invalid image metadata for $Repository." }
  if ($details.Count -ne 2 -or [string]::IsNullOrWhiteSpace([string]$details[0])) {
    throw "ECR did not return an immutable digest for $Repository."
  }
  $imageTags = @($details[1])
  if ($imageTags -notcontains $tag) {
    throw "ECR image lookup for $Repository did not return the expected release tag."
  }
  return "$AccountId.dkr.ecr.$AwsRegion.amazonaws.com/${Repository}:$tag"
}

function Assert-KubernetesContext {
  $context = (Invoke-RequiredCommand -Command "kubectl" -Arguments @("config", "current-context") -Description "kubectl context").StandardOutput
  if ($context -notmatch [regex]::Escape(":cluster/$ExpectedClusterName")) {
    throw "kubectl context does not target the expected EKS cluster '$ExpectedClusterName'."
  }
  Write-Output "kubectl context: $context"
}

function Assert-KubernetesPrerequisites {
  # Keep the resource result separate from the `Namespace` parameter. PowerShell
  # variable names are case-insensitive, so assigning `$namespace` would
  # overwrite the parameter and make the comparison expect namespace/namespace/flash-sale.
  $namespaceResource = (Invoke-RequiredCommand -Command "kubectl" -Arguments @("get", "namespace", $Namespace, "-o", "name") -Description "Kubernetes namespace").StandardOutput
  if ($namespaceResource -ne "namespace/$Namespace") { throw "Namespace '$Namespace' is not available." }

  foreach ($resource in @("statefulset/postgres", "configmap/order-service-runtime-config", "configmap/flash-sale-service-runtime-config", "secret/order-secrets", "secret/flashsale-secrets")) {
    $null = Invoke-RequiredCommand -Command "kubectl" -Arguments @("-n", $Namespace, "get", $resource, "-o", "name") -Description "Migration prerequisite $resource"
  }
}

function Render-FeatureMigrationManifest {
  param(
    [Parameter(Mandatory)][string]$OrderImage,
    [Parameter(Mandatory)][string]$FlashSaleImage
  )

  $rendered = Invoke-RequiredCommand -Command "kubectl" -Arguments @("kustomize", $overlayPath) -Description "Feature 044 migration overlay render"
  $manifest = $rendered.StandardOutput
  foreach ($placeholder in @("order-service", "flash-sale-service")) {
    $matchCount = [regex]::Matches($manifest, "(?m)^\s*image:\s*$([regex]::Escape($placeholder))\s*$").Count
    if ($matchCount -ne 1) { throw "Expected exactly one migration image placeholder '$placeholder', found $matchCount." }
  }
  $manifest = $manifest.Replace("image: order-service", "image: $OrderImage")
  $manifest = $manifest.Replace("image: flash-sale-service", "image: $FlashSaleImage")
  if ($manifest -match "(?m)^\s*image:\s*(?:order-service|flash-sale-service)\s*$") {
    throw "A Feature 044 migration image placeholder was not replaced."
  }

  $temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("flash-sale-feature-044-migration-" + [Guid]::NewGuid().ToString("N"))
  $null = New-Item -ItemType Directory -Path $temporaryDirectory
  $temporaryManifest = Join-Path $temporaryDirectory "feature-044-migrations.yaml"
  Set-Content -LiteralPath $temporaryManifest -Value $manifest -Encoding utf8NoBOM -NoNewline
  return $temporaryManifest
}

function Get-JobDiagnostics {
  param([Parameter(Mandatory)][string]$JobName)

  $job = Invoke-BoundedNativeProcess -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "job", $JobName,
    "-o", "custom-columns=NAME:.metadata.name,SUCCEEDED:.status.succeeded,FAILED:.status.failed,ACTIVE:.status.active", "--no-headers"
  )
  $pods = Invoke-BoundedNativeProcess -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "pods", "-l", "job-name=$JobName",
    "-o", "custom-columns=POD:.metadata.name,PHASE:.status.phase,REASON:.status.containerStatuses[0].state.waiting.reason", "--no-headers"
  )
  $jobText = if ($job.ExitCode -eq 0) { $job.StandardOutput } else { "job status unavailable" }
  $podText = if ($pods.ExitCode -eq 0) { $pods.StandardOutput } else { "pod status unavailable" }
  return Get-BoundedText "job=[$jobText]; pods=[$podText]"
}

try {
  if (-not (Test-Path -LiteralPath (Join-Path $overlayPath "kustomization.yaml") -PathType Leaf)) {
    throw "Feature 044 migration overlay was not found: $overlayPath"
  }

  $accountId = Assert-AwsIdentity
  $resolvedSha = Resolve-ReleaseSha
  $orderImage = Resolve-ImmutableImage -AccountId $accountId -Repository "flash-sale/order-service" -Sha $resolvedSha
  $flashSaleImage = Resolve-ImmutableImage -AccountId $accountId -Repository "flash-sale/flash-sale-service" -Sha $resolvedSha
  Write-Output "Immutable Feature 044 images: PASS (Order and Flash Sale release tag resolved; SHA withheld)."

  Assert-KubernetesContext
  Assert-KubernetesPrerequisites
  $temporaryManifest = Render-FeatureMigrationManifest -OrderImage $orderImage -FlashSaleImage $flashSaleImage
  $null = Invoke-RequiredCommand -Command "kubectl" -Arguments @("apply", "--dry-run=client", "-f", $temporaryManifest) -Description "Feature 044 migration client dry-run"
  Write-Output "Feature 044 migration manifest: PASS (two service-owned Jobs rendered and client dry-run passed)."

  foreach ($migrationJob in $migrationJobs) {
    $existing = (Invoke-RequiredCommand -Command "kubectl" -Arguments @("-n", $Namespace, "get", "job", $migrationJob.Name, "--ignore-not-found", "-o", "name") -Description "Existing migration Job lookup").StandardOutput
    if (-not [string]::IsNullOrWhiteSpace($existing)) {
      throw "Migration Job '$($migrationJob.Name)' already exists. Inspect its status; this gate never deletes or reruns migration Jobs."
    }
  }

  Write-Output "Migration release gate: PASS (Order then Flash Sale; no Job has been created yet)."
  Write-Output "Secret values were not read or printed."
  if (-not $Apply) {
    Write-Output "Validation-only mode: no Kubernetes Job, ECR image, Argo state, or database state was changed."
    exit 0
  }

  $null = Invoke-RequiredCommand -Command "kubectl" -Arguments @("apply", "-f", $temporaryManifest) -Description "Feature 044 migration Job apply"
  foreach ($migrationJob in $migrationJobs) {
    Write-Output "Waiting for $($migrationJob.Name)..."
    $wait = Invoke-BoundedNativeProcess -Command "kubectl" -Arguments @(
      "-n", $Namespace, "wait", "--for=condition=complete", "job/$($migrationJob.Name)", "--timeout=$((Get-RemainingTimeoutSeconds))s"
    )
    if ($wait.ExitCode -ne 0) {
      throw "Migration Job '$($migrationJob.Name)' did not complete: $(Get-JobDiagnostics -JobName $migrationJob.Name)"
    }
    Write-Output "Migration Job $($migrationJob.Name): PASS"
  }

  $null = Invoke-RequiredCommand -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "jobs", "-l", "app.kubernetes.io/feature=purchase-saga"
  ) -Description "Feature 044 migration Job summary"
  Write-Output "Feature 044 migration release gate: APPLY PASS"
  Write-Output "Order and Flash Sale migrations completed sequentially. Secret values were not read or printed."
} finally {
  if (-not [string]::IsNullOrWhiteSpace($temporaryDirectory) -and (Test-Path -LiteralPath $temporaryDirectory)) {
    Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
  }
}
