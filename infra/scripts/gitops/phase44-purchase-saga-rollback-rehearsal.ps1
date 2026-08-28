<#
.SYNOPSIS
  Rehearse whether the prior immutable Order and Flash Sale images can safely read Feature 044 data.

.DESCRIPTION
  This gate is intentionally read-only. It verifies the named prior ECR images, reads the status
  enums from the matching Git revision, and compares that enum surface with the expanded Feature
  044 schema plus aggregated terminal-state evidence from PostgreSQL. It never restores an image,
  changes Argo, alters Liquibase history, modifies a Kubernetes resource, or writes database data.

  A state unknown to a prior image is a hard stop: restoring that image could make a durable row
  unreadable. The operator must return to spec/plan approval; this script never invents a data
  conversion or destructive rollback policy.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidatePattern("^[0-9a-fA-F]{40}$")]
  [string]$PriorReleaseSha,
  [string]$AwsProfile = "flash-sale-terraform",
  [string]$AwsRegion = "ap-southeast-2",
  [string]$ExpectedAccountId = "090814040069",
  [string]$ExpectedClusterName = "flash-sale-dev",
  [string]$Namespace = "flash-sale",
  [ValidateRange(60, 900)]
  [int]$TimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw "Feature 044 rollback compatibility rehearsal requires PowerShell 7 or newer. Run it with pwsh -NoLogo -NoProfile -File."
}

$runDeadline = (Get-Date).AddSeconds($TimeoutSeconds)
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$orderStatusPath = "services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/OrderStatus.java"
$reservationStatusPath = "services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/domain/model/ReservationStatus.java"

function Get-RemainingTimeoutSeconds {
  $remaining = [int][Math]::Ceiling(($runDeadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) {
    throw "Feature 044 rollback compatibility rehearsal exceeded its $TimeoutSeconds-second execution budget."
  }
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

function Assert-AwsIdentity {
  $result = Invoke-RequiredCommand -Command "aws" -Arguments @(
    "sts", "get-caller-identity", "--profile", $AwsProfile, "--query", "Account", "--output", "text"
  ) -Description "AWS caller identity"
  $accountId = $result.StandardOutput.Trim()
  if ($accountId -ne $ExpectedAccountId) {
    throw "AWS profile '$AwsProfile' resolved to an unexpected account; credentials were not displayed."
  }
  # Host output is intentionally separate from the success-output pipeline so callers receive
  # exactly one scalar account ID instead of an array containing both the log line and the ID.
  Write-Host "AWS identity: PASS (account=$accountId region=$AwsRegion profile=$AwsProfile)"
  return $accountId
}

function Assert-KubernetesContext {
  $context = (Invoke-RequiredCommand -Command "kubectl" -Arguments @("config", "current-context") -Description "kubectl context").StandardOutput
  if ($context -notmatch [regex]::Escape(":cluster/$ExpectedClusterName")) {
    throw "kubectl context does not target the expected EKS cluster '$ExpectedClusterName'."
  }
  Write-Output "kubectl context: $context"
}

function Resolve-ImmutableImage {
  param(
    [Parameter(Mandatory)][string]$AccountId,
    [Parameter(Mandatory)][string]$Repository,
    [Parameter(Mandatory)][string]$Sha
  )

  $tag = "release-$($Sha.ToLowerInvariant())"
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
  if (@($details[1]) -notcontains $tag) {
    throw "ECR image lookup for $Repository did not return the expected release tag."
  }
  return "$AccountId.dkr.ecr.$AwsRegion.amazonaws.com/${Repository}:$tag"
}

function Get-DeployedReleaseSha {
  param([Parameter(Mandatory)][string]$DeploymentName)

  $image = (Invoke-RequiredCommand -Command "kubectl" -Arguments @(
    "-n", $Namespace, "get", "deployment", $DeploymentName,
    "-o", "jsonpath={.spec.template.spec.containers[0].image}"
  ) -Description "Deployed $DeploymentName image lookup").StandardOutput.Trim()
  $match = [regex]::Match($image, ":release-(?<sha>[0-9a-fA-F]{40})$")
  if (-not $match.Success) {
    throw "Deployment '$DeploymentName' is not using a reviewed immutable release-<SHA> image. Do not rehearse rollback against an ambiguous tag."
  }
  return $match.Groups["sha"].Value.ToLowerInvariant()
}

function Get-GitSourceAtRelease {
  param(
    [Parameter(Mandatory)][string]$ReleaseSha,
    [Parameter(Mandatory)][string]$RepositoryPath
  )

  $object = "$($ReleaseSha.ToLowerInvariant()):$RepositoryPath"
  $null = Invoke-RequiredCommand -Command "git" -Arguments @("cat-file", "-e", $object) -Description "Git source lookup for prior immutable release" -WorkingDirectory $repoRoot
  return (Invoke-RequiredCommand -Command "git" -Arguments @("show", $object) -Description "Git source read for prior immutable release" -WorkingDirectory $repoRoot).StandardOutput
}

function Get-EnumValues {
  param(
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$TypeName
  )

  $declaration = [regex]::Match($Source, "enum\s+$([regex]::Escape($TypeName))\s*\{(?<body>.*?)\}", [Text.RegularExpressions.RegexOptions]::Singleline)
  if (-not $declaration.Success) { throw "Could not read enum '$TypeName' from the prior release source." }
  $values = @(
    [regex]::Matches($declaration.Groups["body"].Value, "(?m)^\s*(?<name>[A-Z][A-Z0-9_]*)\s*(?=,|;|$)") |
      ForEach-Object { $_.Groups["name"].Value }
  )
  if ($values.Count -eq 0) { throw "Prior release enum '$TypeName' has no readable values." }
  return $values
}

function Invoke-ReadOnlyPostgresQuery {
  param(
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$Query,
    [Parameter(Mandatory)][string]$Description
  )

  $transaction = "BEGIN TRANSACTION READ ONLY;`n$Query`nCOMMIT;"
  $shellCommand = 'exec psql -X -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -A -t -F "|" -c "$2"'
  $result = Invoke-RequiredCommand -Command "kubectl" -Arguments @(
    "-n", $Namespace, "exec", "statefulset/postgres", "--", "sh", "-ec", $shellCommand, "sh", $Database, $transaction
  ) -Description $Description
  return @($result.StandardOutput -split "\r?\n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Assert-ExpandedFeatureSchema {
  $orderRows = Invoke-ReadOnlyPostgresQuery -Database "order_db" -Description "Order expanded-schema read-only check" -Query @'
SELECT CASE WHEN to_regclass('public.purchase_sagas') IS NOT NULL
                  AND to_regclass('public.purchase_saga_inbox') IS NOT NULL
            THEN 'expanded' ELSE 'missing' END;
'@
  if ($orderRows -notcontains "expanded") { throw "Order Feature 044 schema is not present; do not assess image rollback compatibility." }

  $flashSaleRows = Invoke-ReadOnlyPostgresQuery -Database "flashsale_db" -Description "Flash Sale expanded-schema read-only check" -Query @'
SELECT CASE WHEN to_regclass('public.reservation_command_inbox') IS NOT NULL
                  AND EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'flash_sale_reservations'
                      AND column_name = 'redis_reconciled_at')
                  AND EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'flash_sale_reservations'
                      AND column_name = 'finalized_at')
                  AND EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'flash_sale_outbox_events'
                      AND column_name = 'causation_id')
            THEN 'expanded' ELSE 'missing' END;
'@
  if ($flashSaleRows -notcontains "expanded") { throw "Flash Sale Feature 044 schema is not present; do not assess image rollback compatibility." }
  Write-Output "Expanded Feature 044 schema: PASS (Order and Flash Sale additive state is present)."
}

function Get-TerminalStatusCounts {
  param(
    [Parameter(Mandatory)][string]$Database,
    [Parameter(Mandatory)][string]$Table,
    [Parameter(Mandatory)][string[]]$TerminalStates,
    [Parameter(Mandatory)][string]$Description
  )

  $quotedStates = ($TerminalStates | ForEach-Object { "'$_'" }) -join ", "
  $query = "SELECT status || '|' || count(*) FROM $Table WHERE status IN ($quotedStates) GROUP BY status ORDER BY status;"
  $rows = Invoke-ReadOnlyPostgresQuery -Database $Database -Query $query -Description $Description
  $counts = [ordered]@{}
  foreach ($row in $rows) {
    $parts = $row -split "\|", 2
    if ($parts.Count -ne 2 -or $parts[0] -notmatch "^[A-Z_]+$" -or $parts[1] -notmatch "^[0-9]+$") {
      throw "$Description returned an unexpected aggregate row."
    }
    $counts[$parts[0]] = [int64]$parts[1]
  }
  if ($counts.Count -eq 0) {
    throw "$Description found no representative terminal rows. Run the approved Feature 044 terminal scenarios before attempting rollback rehearsal."
  }
  return $counts
}

function Assert-StatusesSupportedByPriorImage {
  param(
    [Parameter(Mandatory)][string]$AggregateName,
    [Parameter(Mandatory)][System.Collections.IDictionary]$ObservedCounts,
    [Parameter(Mandatory)][string[]]$PriorEnumValues
  )

  $unsupported = @($ObservedCounts.Keys | Where-Object { $PriorEnumValues -notcontains $_ })
  if ($unsupported.Count -gt 0) {
    $safeStates = $unsupported -join ", "
    throw "Incompatible persisted terminal state for the prior $AggregateName image: $safeStates. Do not restore prior images. Preserve all durable rows and return to spec/plan approval; this gate will not invent data rollback semantics."
  }
}

try {
  $priorSha = $PriorReleaseSha.ToLowerInvariant()
  $accountId = Assert-AwsIdentity
  Assert-KubernetesContext

  foreach ($resource in @("statefulset/postgres", "deployment/order-service", "deployment/flash-sale-service")) {
    $null = Invoke-RequiredCommand -Command "kubectl" -Arguments @("-n", $Namespace, "get", $resource, "-o", "name") -Description "Rollback rehearsal prerequisite $resource"
  }

  $currentOrderSha = Get-DeployedReleaseSha -DeploymentName "order-service"
  $currentFlashSaleSha = Get-DeployedReleaseSha -DeploymentName "flash-sale-service"
  if ($priorSha -eq $currentOrderSha -and $priorSha -eq $currentFlashSaleSha) {
    throw "PriorReleaseSha is the currently deployed release. Supply the previous immutable image set, not the active Feature 044 release."
  }
  $activeReleaseShape = if ($currentOrderSha -eq $currentFlashSaleSha) {
    "one common release"
  } else {
    "reviewed per-service releases after targeted hotfix promotion"
  }
  Write-Output "Active Feature 044 image set: PASS (Order and Flash Sale use $activeReleaseShape; SHAs withheld)."

  $null = Resolve-ImmutableImage -AccountId $accountId -Repository "flash-sale/order-service" -Sha $priorSha
  $null = Resolve-ImmutableImage -AccountId $accountId -Repository "flash-sale/flash-sale-service" -Sha $priorSha
  Write-Output "Prior immutable image set: PASS (Order and Flash Sale ECR digests resolved; SHA withheld)."

  $priorOrderStatuses = Get-EnumValues -Source (Get-GitSourceAtRelease -ReleaseSha $priorSha -RepositoryPath $orderStatusPath) -TypeName "OrderStatus"
  $priorReservationStatuses = Get-EnumValues -Source (Get-GitSourceAtRelease -ReleaseSha $priorSha -RepositoryPath $reservationStatusPath) -TypeName "ReservationStatus"
  Write-Output "Prior image enum surface: PASS (Order states=$($priorOrderStatuses.Count); Flash Sale states=$($priorReservationStatuses.Count))."

  Assert-ExpandedFeatureSchema
  $orderTerminalRows = Get-TerminalStatusCounts -Database "order_db" -Table "orders" -TerminalStates @("CONFIRMED", "CANCELLED", "EXPIRED") -Description "Order terminal-row read-only check"
  $reservationTerminalRows = Get-TerminalStatusCounts -Database "flashsale_db" -Table "flash_sale_reservations" -TerminalStates @("CONFIRMED", "RELEASED", "EXPIRED") -Description "Flash Sale terminal-row read-only check"
  Write-Output "Representative terminal rows: PASS (Order groups=$($orderTerminalRows.Count); Flash Sale groups=$($reservationTerminalRows.Count); no row identity was read)."

  Assert-StatusesSupportedByPriorImage -AggregateName "Order" -ObservedCounts $orderTerminalRows -PriorEnumValues $priorOrderStatuses
  Assert-StatusesSupportedByPriorImage -AggregateName "Flash Sale reservation" -ObservedCounts $reservationTerminalRows -PriorEnumValues $priorReservationStatuses

  Write-Output "Feature 044 rollback compatibility rehearsal: PASS"
  Write-Output "The named prior images can read the representative terminal states. Actual T072 rollback still requires disabling Order command production, recording drain evidence, a reviewed GitOps image promotion revert, and Argo verification."
  Write-Output "No Kubernetes Deployment, ConfigMap, Git, database, Kafka, Schema Registry, Redis, or ECR state was changed."
} catch {
  Write-Output "Rollback rehearsal stopped before cloud mutation. No Kubernetes Deployment, ConfigMap, Git, database, Kafka, Schema Registry, Redis, or ECR state was changed."
  throw
}
