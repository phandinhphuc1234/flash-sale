<##
.SYNOPSIS
  Verify the cloud release artifact without changing the cluster or registry.

.DESCRIPTION
  Phase 21 treats the EKS cloud environment as the staging-equivalent target. It verifies Argo
  ownership/health, nine application Deployments, ECR manifest digests versus running Pod image IDs,
  the explicitly expected Payment runtime state, and the existing localhost-only Gateway smoke
  contract. Before Stripe enablement the expected state is disabled; after the reviewed Phase 24
  rollout it may be enabled. The script never reads Kubernetes Secret values and has no -Apply mode.
##>
[CmdletBinding()]
param(
  [string]$AwsProfile = "flash-sale-terraform",
  [string]$AwsRegion = "ap-southeast-2",
  [string]$ExpectedClusterName = "flash-sale-dev",
  [string]$Namespace = "flash-sale",
  [string]$ArgoNamespace = "argocd",
  [string]$ArgoApplication = "flash-sale-cloud",
  [ValidateSet("disabled", "enabled")]
  [string]$PaymentRuntimeState = "disabled",
  [ValidateRange(1024, 65535)]
  [int]$SmokePort = 28080,
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$RunDeadline = (Get-Date).AddSeconds($TimeoutSeconds)

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$CloudOverlayPath = Join-Path $RepoRoot "infra\k8s\overlays\cloud"
$SmokeScriptPath = Join-Path $RepoRoot "infra\scripts\gitops\phase18-gateway-smoke.ps1"
$ExpectedArgoTargetRevision = "develop"
$ExpectedServices = @(
  "api-gateway",
  "authentication-service",
  "product-service",
  "campaign-service",
  "flash-sale-service",
  "inventory-service",
  "order-service",
  "payment-service",
  "cart-service"
)
$ExpectedPaymentFlags = @(
  "PAYMENT_ACCEPTANCE_ENABLED",
  "PAYMENT_CHECKOUT_ENABLED",
  "STRIPE_ENABLED",
  "PAYMENT_CONSUMER_ENABLED",
  "PAYMENT_OUTBOX_PUBLISHER_ENABLED",
  "PAYMENT_RECOVERY_ENABLED",
  "PAYMENT_WEBHOOK_PROCESSING_ENABLED"
)

function Get-RemainingTimeoutSeconds {
  $remaining = [int][Math]::Ceiling(($RunDeadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) {
    throw "Phase 21 exceeded its $TimeoutSeconds-second execution budget."
  }
  return $remaining
}

function Get-BoundedDiagnostic {
  param([AllowEmptyString()][string]$Text)
  if ([string]::IsNullOrWhiteSpace($Text)) { return "no diagnostic output" }
  if ($Text.Length -le 2000) { return $Text }
  return $Text.Substring(0, 2000) + "... [truncated]"
}

function ConvertTo-WindowsProcessArgument {
  param([AllowNull()][string]$Argument)
  if ($null -eq $Argument -or $Argument.Length -eq 0) { return '""' }
  if ($Argument -notmatch '[\s"]') { return $Argument }

  # ProcessStartInfo.Arguments is the compatibility path for Windows PowerShell/.NET Framework.
  # Escape embedded quotes and trailing backslashes according to Windows command-line parsing.
  $escaped = $Argument -replace '(\\*)"', '$1$1\"'
  $escaped = $escaped -replace '(\\+)$', '$1$1'
  return '"' + $escaped + '"'
}

function Invoke-BoundedNativeProcess {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments
  )
  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $Command
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  if ($null -ne $startInfo.GetType().GetProperty("ArgumentList")) {
    foreach ($argument in $Arguments) {
      $null = $startInfo.ArgumentList.Add([string]$argument)
    }
  } else {
    $startInfo.Arguments = (($Arguments | ForEach-Object {
        ConvertTo-WindowsProcessArgument -Argument ([string]$_)
      }) -join " ")
  }
  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $started = $false
  try {
    $null = Get-RemainingTimeoutSeconds
    if (-not $process.Start()) {
      throw "Could not start native command '$Command'."
    }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $remainingMilliseconds = (Get-RemainingTimeoutSeconds) * 1000
    if (-not $process.WaitForExit($remainingMilliseconds)) {
      $process.Kill($true)
      $process.WaitForExit()
      throw "Native command '$Command' exceeded the Phase 21 execution budget."
    }
    $process.WaitForExit()
    $stdoutRaw = $stdoutTask.GetAwaiter().GetResult()
    $stderrRaw = $stderrTask.GetAwaiter().GetResult()
    return [pscustomobject]@{
      ExitCode = $process.ExitCode
      StandardOutput = if ($null -eq $stdoutRaw) { "" } else { $stdoutRaw.Trim() }
      StandardError = if ($null -eq $stderrRaw) { "" } else { $stderrRaw.Trim() }
    }
  } finally {
    if ($started -and -not $process.HasExited) {
      $process.Kill($true)
      $process.WaitForExit()
    }
    $process.Dispose()
  }
}

function Get-NativeText {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments
  )
  $result = Invoke-BoundedNativeProcess -Command $Command -Arguments $Arguments
  if ($result.ExitCode -ne 0) {
    throw "$Command failed with exit code $($result.ExitCode): $(Get-BoundedDiagnostic $result.StandardError)"
  }
  return $result.StandardOutput
}

function Get-NativeJson {
  param(
    [Parameter(Mandatory)][string]$Command,
    [Parameter(Mandatory)][string[]]$Arguments,
    [Parameter(Mandatory)][string]$Description
  )
  $text = Get-NativeText -Command $Command -Arguments $Arguments
  if ([string]::IsNullOrWhiteSpace($text)) {
    throw "$Description returned empty JSON."
  }
  try {
    # Windows PowerShell 5.1 does not support ConvertFrom-Json -Depth. JSON parsing is
    # recursive by default, so the compatibility-safe invocation is sufficient here.
    return $text | ConvertFrom-Json
  } catch {
    throw "$Description returned invalid JSON: $($_.Exception.Message)"
  }
}

function Get-JsonPropertyValue {
  param(
    [Parameter(Mandatory)][object]$Object,
    [Parameter(Mandatory)][string]$PropertyName
  )
  if ($null -eq $Object) { return $null }
  $property = $Object.PSObject.Properties[$PropertyName]
  if ($null -eq $property) { return $null }
  return $property.Value
}

function Get-FirstReadyPod {
  param([Parameter(Mandatory)][string]$ServiceName)
  $pods = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "pods", "-l", "app.kubernetes.io/name=$ServiceName", "-o", "json"
  ) -Description "Pods for $ServiceName"
  foreach ($pod in @($pods.items)) {
    if ($pod.status.phase -ne "Running") { continue }
    foreach ($containerStatus in @($pod.status.containerStatuses)) {
      if ($containerStatus.ready -and -not [string]::IsNullOrWhiteSpace($containerStatus.imageID)) {
        return $containerStatus
      }
    }
  }
  throw "No ready running Pod with an image ID was found for $ServiceName."
}

function Get-EcrDigest {
  param(
    [Parameter(Mandatory)][string]$Repository,
    [Parameter(Mandatory)][string]$Tag
  )
  $details = Get-NativeJson -Command "aws" -Arguments @(
    "ecr", "describe-images", "--repository-name", $Repository,
    "--image-ids", "imageTag=$Tag", "--profile", $AwsProfile, "--region", $AwsRegion,
    "--output", "json"
  ) -Description "ECR manifest for ${Repository}:$Tag"
  $imageDetails = @($details.imageDetails)
  if ($imageDetails.Count -ne 1 -or [string]::IsNullOrWhiteSpace($imageDetails[0].imageDigest)) {
    throw "ECR did not return exactly one digest for ${Repository}:$Tag."
  }
  return $imageDetails[0].imageDigest
}

function Assert-ReleaseArtifact {
  param([Parameter(Mandatory)][string]$ServiceName)
  $deployment = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "deployment", $ServiceName, "-o", "json"
  ) -Description "Deployment $ServiceName"
  $desiredReplicas = [int](Get-JsonPropertyValue $deployment.spec "replicas")
  $availableReplicasValue = Get-JsonPropertyValue $deployment.status "availableReplicas"
  $availableReplicas = if ($null -eq $availableReplicasValue) { 0 } else { [int]$availableReplicasValue }
  if ($availableReplicas -lt $desiredReplicas) {
    throw "$ServiceName is not fully available: available=$availableReplicas desired=$desiredReplicas."
  }

  $container = @($deployment.spec.template.spec.containers)[0]
  if ($null -eq $container -or [string]::IsNullOrWhiteSpace($container.image)) {
    throw "$ServiceName has no selected container image."
  }
  $imageMatch = [regex]::Match($container.image, "^(?<registry>[^/]+)/(?<repository>.+):(?<tag>[^:]+)$")
  if (-not $imageMatch.Success) {
    throw "$ServiceName image '$($container.image)' is not a tagged registry image."
  }
  if ($imageMatch.Groups["tag"].Value -in @("latest", "")) {
    throw "$ServiceName uses a mutable or empty image tag '$($imageMatch.Groups["tag"].Value)'."
  }

  $repository = $imageMatch.Groups["repository"].Value
  $tag = $imageMatch.Groups["tag"].Value
  $ecrDigest = Get-EcrDigest -Repository $repository -Tag $tag
  $podContainer = Get-FirstReadyPod -ServiceName $ServiceName
  $podImageId = [string]$podContainer.imageID
  $podDigest = if ($podImageId -match "@(?<digest>sha256:[0-9a-fA-F]+)$") {
    $Matches["digest"]
  } else {
    throw "$ServiceName Pod image ID '$podImageId' has no sha256 digest."
  }
  if ($podDigest -ne $ecrDigest) {
    throw "$ServiceName digest mismatch: ECR=$ecrDigest Pod=$podDigest."
  }
  Write-Output ("Artifact {0}: available={1}/{2} tag={3} digest={4}" -f
    $ServiceName, $availableReplicas, $desiredReplicas, $tag, $ecrDigest)
}

function Assert-ArgoApplication {
  $application = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $ArgoNamespace, "get", "application", $ArgoApplication, "-o", "json"
  ) -Description "Argo Application $ArgoApplication"
  $syncStatus = [string](Get-JsonPropertyValue $application.status.sync "status")
  $healthStatus = [string](Get-JsonPropertyValue $application.status.health "status")
  $targetRevision = [string](Get-JsonPropertyValue $application.spec.source "targetRevision")
  $liveRevision = [string](Get-JsonPropertyValue $application.status.sync "revision")
  Write-Output "Argo $ArgoApplication`: sync=$syncStatus health=$healthStatus target=$targetRevision revision=$liveRevision"
  if ($syncStatus -ne "Synced" -or $healthStatus -ne "Healthy") {
    throw "Argo Application $ArgoApplication is not Synced and Healthy."
  }
  if ($targetRevision -ne $ExpectedArgoTargetRevision) {
    throw "Argo Application $ArgoApplication targets '$targetRevision', expected '$ExpectedArgoTargetRevision'."
  }
  if ([string]::IsNullOrWhiteSpace($liveRevision)) {
    throw "Argo Application $ArgoApplication has no live revision."
  }
}

function Assert-PaymentFlags {
  $expectedValue = if ($PaymentRuntimeState -eq "enabled") { "true" } else { "false" }
  $config = Get-NativeJson -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "configmap", "payment-service-runtime-config", "-o", "json"
  ) -Description "Payment runtime ConfigMap"
  foreach ($flag in $ExpectedPaymentFlags) {
    $value = [string](Get-JsonPropertyValue $config.data $flag)
    if ($value.ToLowerInvariant() -ne $expectedValue) {
      throw "Payment runtime flag $flag is '$value'; Phase 21 expected the reviewed '$PaymentRuntimeState' state."
    }
  }
  Write-Output "Payment flags: $($ExpectedPaymentFlags.Count)/$($ExpectedPaymentFlags.Count) $PaymentRuntimeState."
}

if (-not (Test-Path -LiteralPath $CloudOverlayPath)) { throw "Cloud overlay not found: $CloudOverlayPath" }
if (-not (Test-Path -LiteralPath $SmokeScriptPath)) { throw "Gateway smoke script not found: $SmokeScriptPath" }

$context = Get-NativeText -Command "kubectl" -Arguments @("config", "current-context")
Write-Output "kubectl context: $context"
if ($context -notmatch [regex]::Escape($ExpectedClusterName)) {
  throw "kubectl context '$context' does not target expected cluster '$ExpectedClusterName'."
}

$null = Get-NativeText -Command "kubectl" -Arguments @("kustomize", $CloudOverlayPath)
$null = Get-NativeText -Command "kubectl" -Arguments @("apply", "--dry-run=client", "-k", $CloudOverlayPath)
Assert-ArgoApplication
foreach ($service in $ExpectedServices) { Assert-ReleaseArtifact -ServiceName $service }
Assert-PaymentFlags

$smokeResult = Invoke-BoundedNativeProcess -Command "pwsh" -Arguments @(
  "-NoLogo", "-NoProfile", "-File", $SmokeScriptPath, "-Run",
  "-LocalPort", "$SmokePort", "-TimeoutSeconds", "120"
)
if (-not [string]::IsNullOrWhiteSpace($smokeResult.StandardOutput)) { Write-Output $smokeResult.StandardOutput }
if ($smokeResult.ExitCode -ne 0) {
  throw "Gateway smoke failed with exit code $($smokeResult.ExitCode): $(Get-BoundedDiagnostic $smokeResult.StandardError)"
}
if ($smokeResult.StandardOutput -notmatch "Gateway smoke passed: readiness=200 catalog=200 admin=401") {
  throw "Gateway smoke did not report the required readiness=200 catalog=200 admin=401 result."
}

Write-Output "Phase 21 cloud release verification: PASS"
Write-Output "Secret values were not read or printed. No Kubernetes or ECR state was changed."
