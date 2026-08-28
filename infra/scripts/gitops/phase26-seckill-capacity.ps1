<#
.SYNOPSIS
  Run a bounded, adaptive arrival-rate capacity probe for the Flash Sale reservation path.

.DESCRIPTION
  The default mode is validation-only. -Run is required before k6 is started. A run executes a
  warm-up and then increasing constant-arrival-rate stages. It stops on an immediate correctness
  or workload danger signal, or after the configured number of consecutive latency/error breaches.

  The runner deliberately accepts a pre-created, ignored shopper token file. It never creates
  users, reads a Kubernetes Secret, queries a database, mutates Kubernetes, or writes token values
  to the result report. Each arrival gets a distinct token index across the entire run so a
  purchase-limit response cannot be mistaken for capacity evidence.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [Guid]$CampaignId,
  [Parameter(Mandatory)]
  [Guid]$VariantId,
  [ValidateSet('gateway', 'direct-service')]
  [string]$Route = 'gateway',
  [string]$GatewayBaseUrl = 'http://127.0.0.1:18080',
  [string]$FlashSaleBaseUrl = 'http://127.0.0.1:18084',
  [string]$ShopperTokenFile = 'load-tests/flashsale-service/shopper-tokens.json',
  [ValidateRange(1, 500)]
  [int]$StartRate = 25,
  [ValidateRange(1, 500)]
  [int]$StepRate = 25,
  [ValidateRange(1, 2000)]
  [int]$MaxRate = 500,
  [switch]$AllowHigherHardCap,
  [ValidateRange(1, 300)]
  [int]$WarmupRate = 10,
  [ValidateRange(1, 600)]
  [int]$WarmupDurationSeconds = 15,
  [ValidateRange(5, 300)]
  [int]$StageDurationSeconds = 30,
  [ValidateRange(0, 300)]
  [int]$CooldownSeconds = 20,
  [ValidateRange(1, 10)]
  [int]$ConsecutiveBreaches = 2,
  [ValidateRange(1, 100000000)]
  [int]$ExpectedAllocation = 100000,
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 900,
  [ValidateRange(0, 5000)]
  [int]$PreAllocatedVUs = 0,
  [ValidateRange(0, 20000)]
  [int]$MaxVUs = 0,
  [ValidateRange(0, 1)]
  [double]$ExpectedOutcomeRate = 0.99,
  [ValidateRange(1, 60000)]
  [int]$P95LimitMs = 300,
  [ValidateRange(1, 120000)]
  [int]$P99LimitMs = 700,
  [ValidateRange(0, 1)]
  [double]$ErrorRateLimit = 0.01,
  [string]$OutputDirectory = 'load-tests/flashsale-service/results',
  [switch]$Run
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

if ($PSVersionTable.PSEdition -ne 'Core' -or $PSVersionTable.PSVersion.Major -lt 7) {
  throw 'Phase 26 requires PowerShell 7 (pwsh).'
}
if ($StartRate -gt $MaxRate) { throw 'StartRate must be less than or equal to MaxRate.' }
if ($WarmupRate -gt $MaxRate) { throw 'WarmupRate must be less than or equal to MaxRate.' }
if ($StepRate -le 0) { throw 'StepRate must be positive.' }
if ($P99LimitMs -lt $P95LimitMs) { throw 'P99LimitMs must be greater than or equal to P95LimitMs.' }
if ($MaxRate -gt 500 -and -not $AllowHigherHardCap) {
  throw 'MaxRate above the default 500 RPS hard cap requires -AllowHigherHardCap.'
}
if ([string]::IsNullOrWhiteSpace($ShopperTokenFile)) { throw 'ShopperTokenFile must not be empty.' }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$profilePath = Join-Path $repoRoot 'load-tests\flashsale-service\adaptive-arrival-rate.js'
$tokenPath = if ([IO.Path]::IsPathRooted($ShopperTokenFile)) {
  [IO.Path]::GetFullPath($ShopperTokenFile)
} else {
  [IO.Path]::GetFullPath((Join-Path $repoRoot $ShopperTokenFile))
}
$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) {
  [IO.Path]::GetFullPath($OutputDirectory)
} else {
  [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory))
}
$staticTestPath = Join-Path $repoRoot 'infra\scripts\gitops\tests\phase26-seckill-capacity.tests.ps1'
$baseUrl = if ($Route -eq 'gateway') { $GatewayBaseUrl.TrimEnd('/') } else { $FlashSaleBaseUrl.TrimEnd('/') }

if (-not (Test-Path -LiteralPath $profilePath -PathType Leaf)) {
  throw "Adaptive k6 profile is missing: $profilePath"
}
$profileText = Get-Content -Raw -LiteralPath $profilePath
foreach ($marker in @('constant-arrival-rate', 'adaptive_unexpected_errors', 'adaptive_expected_outcome_rate',
    'FLASHSALE_TOKEN_OFFSET', 'handleSummary')) {
  if ($profileText -notmatch [regex]::Escape($marker)) {
    throw "Adaptive k6 profile is missing required marker '$marker'."
  }
}

function Get-RemainingSeconds {
  $remaining = [int][Math]::Ceiling(($script:Deadline - (Get-Date)).TotalSeconds)
  if ($remaining -lt 1) { throw "Phase 26 exceeded its $TimeoutSeconds-second execution budget." }
  return $remaining
}

function Get-StageRates {
  $rates = [System.Collections.Generic.List[int]]::new()
  $rate = $StartRate
  while ($rate -lt $MaxRate) {
    $rates.Add($rate)
    $rate = [Math]::Min($MaxRate, $rate + $StepRate)
  }
  if ($rates.Count -eq 0 -or $rates[$rates.Count - 1] -ne $MaxRate) { $rates.Add($MaxRate) }
  return @($rates)
}

function Get-StageTokenBudget([int]$Rate, [int]$Duration) {
  # constant-arrival-rate may start one boundary iteration while the configured duration expires.
  # Reserve one extra scheduling second so adjacent stages never reuse a shopper identity.
  $budget = [long]$Rate * ([long]$Duration + 1)
  if ($budget -gt [int]::MaxValue) { throw 'Stage token budget exceeds the supported integer range.' }
  return [int]$budget
}

function Get-RequiredTokenCount([int[]]$Rates) {
  $required = [long](Get-StageTokenBudget $WarmupRate $WarmupDurationSeconds)
  foreach ($rate in $Rates) { $required += [long](Get-StageTokenBudget $rate $StageDurationSeconds) }
  if ($required -gt [int]::MaxValue) { throw 'Required token count exceeds the supported integer range.' }
  return [int]$required
}

function Test-K6CompletedWithSummary([int]$ExitCode) {
  # k6 uses 99 when a threshold fails but still writes the summary. The runner must parse that
  # evidence so its own consecutive-breach and immediate-danger rules remain authoritative.
  return $ExitCode -in @(0, 99)
}

function Read-TokenCount {
  if (-not (Test-Path -LiteralPath $tokenPath -PathType Leaf)) {
    throw "Shopper token file is missing: $ShopperTokenFile. Provide a local ignored JSON array before -Run."
  }
  & git check-ignore --quiet -- $tokenPath
  if ($LASTEXITCODE -ne 0) {
    throw 'Refusing to use a shopper token file that is not ignored by Git.'
  }
  try {
    $tokens = Get-Content -Raw -LiteralPath $tokenPath | ConvertFrom-Json
  } catch {
    throw 'Shopper token file is not valid JSON.'
  }
  $items = @($tokens)
  if ($items.Count -eq 0 -or @($items | Where-Object { $_ -isnot [string] -or [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
    throw 'Shopper token file must contain a non-empty JSON array of access-token strings.'
  }
  return $items.Count
}

function Invoke-BoundedK6 {
  param(
    [Parameter(Mandatory)][int]$Rate,
    [Parameter(Mandatory)][int]$Duration,
    [Parameter(Mandatory)][int]$TokenOffset,
    [Parameter(Mandatory)][string]$SummaryFile,
    [Parameter(Mandatory)][string]$RunLabel
  )
  $vus = if ($PreAllocatedVUs -gt 0) { $PreAllocatedVUs } else { [Math]::Max(25, $Rate) }
  $maxVus = if ($MaxVUs -gt 0) { $MaxVUs } else { [Math]::Max($vus, $Rate * 4) }
  if ($maxVus -lt $vus) { throw 'MaxVUs must be greater than or equal to PreAllocatedVUs.' }

  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = 'k6'
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  foreach ($argument in @('run', '--quiet', $profilePath)) {
    $null = $startInfo.ArgumentList.Add([string]$argument)
  }
  $environment = $startInfo.Environment
  $environment['FLASHSALE_BASE_URL'] = $baseUrl
  $environment['FLASHSALE_ROUTE'] = $Route
  $environment['FLASHSALE_CAMPAIGN_ID'] = [string]$CampaignId
  $environment['FLASHSALE_VARIANT_ID'] = [string]$VariantId
  $environment['FLASHSALE_EXPECTED_ALLOCATION'] = [string]$ExpectedAllocation
  $environment['FLASHSALE_RATE'] = [string]$Rate
  $environment['FLASHSALE_STAGE_DURATION_SECONDS'] = [string]$Duration
  $environment['FLASHSALE_PREALLOCATED_VUS'] = [string]$vus
  $environment['FLASHSALE_MAX_VUS'] = [string]$maxVus
  $environment['FLASHSALE_EXPECTED_OUTCOME_RATE'] = [string]$ExpectedOutcomeRate
  $environment['FLASHSALE_SHOPPER_TOKENS_FILE'] = $tokenPath
  $environment['FLASHSALE_TOKEN_OFFSET'] = [string]$TokenOffset
  $environment['FLASHSALE_RUN_LABEL'] = $RunLabel
  $environment['FLASHSALE_K6_SUMMARY_FILE'] = $SummaryFile

  $process = [System.Diagnostics.Process]::new()
  $process.StartInfo = $startInfo
  $started = $false
  try {
    $null = Get-RemainingSeconds
    if (-not $process.Start()) { throw 'Could not start k6.' }
    $started = $true
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $stageDeadline = (Get-Date).AddSeconds([Math]::Min((Get-RemainingSeconds), $Duration + 120))
    while (-not $process.HasExited -and (Get-Date) -lt $stageDeadline) {
      Start-Sleep -Milliseconds 250
    }
    if (-not $process.HasExited) {
      $process.Kill($true)
      $process.WaitForExit()
      throw "k6 stage '$RunLabel' exceeded its bounded stage deadline."
    }
    $process.WaitForExit()
    $stdout = [string]$stdoutTask.GetAwaiter().GetResult()
    $stderr = [string]$stderrTask.GetAwaiter().GetResult()
    return [pscustomobject]@{
      ExitCode = $process.ExitCode
      StandardOutput = $stdout
      StandardError = $stderr
    }
  } finally {
    if ($started -and -not $process.HasExited) {
      $process.Kill($true)
      $process.WaitForExit()
    }
    $process.Dispose()
  }
}

function Get-MetricValues([object]$Summary, [string]$Name) {
  $metricProperty = $Summary.metrics.PSObject.Properties[$Name]
  if ($null -eq $metricProperty) { return $null }
  return $metricProperty.Value.values
}

function Get-MetricNumber([object]$Summary, [string]$Name, [string]$Property, [double]$Default = 0) {
  $values = Get-MetricValues $Summary $Name
  if ($null -eq $values) { return $Default }
  # PowerShell variable names are case-insensitive. Do not name this local `$property`, because it
  # would overwrite the typed `$Property` parameter and coerce PSPropertyInfo back to a string.
  $metricValueProperty = $values.PSObject.Properties[$Property]
  if ($null -eq $metricValueProperty) { return $Default }
  return [double]$metricValueProperty.Value
}

function Convert-StageSummary([object]$Summary, [int]$Rate, [int]$Duration, [string]$RunLabel) {
  $http = Get-MetricValues $Summary 'http_reqs'
  $httpCount = if ($null -eq $http) { 0 } else { [int]$http.count }
  $winnerP95 = Get-MetricNumber $Summary 'adaptive_winner_http_duration' 'p(95)' 0
  $winnerP99 = Get-MetricNumber $Summary 'adaptive_winner_http_duration' 'p(99)' 0
  $replayP95 = Get-MetricNumber $Summary 'adaptive_replay_http_duration' 'p(95)' 0
  $replayP99 = Get-MetricNumber $Summary 'adaptive_replay_http_duration' 'p(99)' 0
  $totalP95 = Get-MetricNumber $Summary 'adaptive_http_duration' 'p(95)' 0
  $totalP99 = Get-MetricNumber $Summary 'adaptive_http_duration' 'p(99)' 0
  $unexpected = [int](Get-MetricNumber $Summary 'adaptive_unexpected_errors' 'count' 0)
  $dropped = [int](Get-MetricNumber $Summary 'dropped_iterations' 'count' 0)
  $winners = [int](Get-MetricNumber $Summary 'adaptive_successful_winners' 'count' 0)
  $replayCount = [int](Get-MetricNumber $Summary 'adaptive_replays' 'count' 0)
  $outcomeRate = Get-MetricNumber $Summary 'adaptive_expected_outcome_rate' 'rate' 0
  $checksValues = Get-MetricValues $Summary 'checks'
  $checksCount = if ($null -eq $checksValues) { 0 } else { [int](Get-MetricNumber $Summary 'checks' 'count' 0) }
  # Sold-out and acceptance-pending outcomes do not execute a k6 check. Treat a stage with no
  # checks as neutral; malformed 202/replay responses still increment adaptive_unexpected_errors.
  $checksRate = if ($checksCount -eq 0) { 1 } else { Get-MetricNumber $Summary 'checks' 'rate' 0 }
  $httpFailedRate = Get-MetricNumber $Summary 'http_req_failed' 'rate' 0
  # Use all reservation HTTP calls for the guardrail, including sold-out/pending responses.
  $p95 = $totalP95
  $p99 = $totalP99
  $breached = @()
  if ($p95 -gt $P95LimitMs) { $breached += 'p95' }
  if ($p99 -gt $P99LimitMs) { $breached += 'p99' }
  if ($httpFailedRate -gt $ErrorRateLimit) { $breached += 'http_error_rate' }
  if ($outcomeRate -lt $ExpectedOutcomeRate) { $breached += 'expected_outcome_rate' }
  if ($checksCount -gt 0 -and $checksRate -lt 1) { $breached += 'check_failure' }
  $danger = @()
  if ($unexpected -gt 0) { $danger += 'unexpected_response' }
  if ($dropped -gt 0) { $danger += 'dropped_iterations' }
  if ($winners -gt $ExpectedAllocation) { $danger += 'allocation_oversell' }
  if ($replayCount -ne $winners) { $danger += 'replay_count_mismatch' }
  [pscustomobject]@{
    stage = $RunLabel
    rateRps = $Rate
    durationSeconds = $Duration
    requests = $httpCount
    winners = $winners
    replays = $replayCount
    soldOut = [int](Get-MetricNumber $Summary 'adaptive_sold_out' 'count' 0)
    acceptancePending = [int](Get-MetricNumber $Summary 'adaptive_acceptance_pending' 'count' 0)
    unexpectedErrors = $unexpected
    droppedIterations = $dropped
    expectedOutcomeRate = [Math]::Round($outcomeRate, 6)
    httpErrorRate = [Math]::Round($httpFailedRate, 6)
    checksRate = [Math]::Round($checksRate, 6)
    p95Ms = [Math]::Round($p95, 3)
    p99Ms = [Math]::Round($p99, 3)
    winnerP95Ms = [Math]::Round($winnerP95, 3)
    winnerP99Ms = [Math]::Round($winnerP99, 3)
    replayP95Ms = [Math]::Round($replayP95, 3)
    replayP99Ms = [Math]::Round($replayP99, 3)
    breachReasons = @($breached)
    dangerReasons = @($danger)
  }
}

function Write-SanitizedReport([string]$Outcome, [string]$StopReason, [object[]]$Stages, [int]$LastGoodRate,
    [Nullable[int]]$FirstBreachRate, [int]$TokenCount, [string]$RunId, [bool]$RawStageSummariesRemoved) {
  New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
  $report = [ordered]@{
    schemaVersion = 1
    feature = '046-seckill-capacity-gate'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    runId = $RunId
    route = $Route
    baseUrl = $baseUrl
    campaignId = [string]$CampaignId
    variantId = [string]$VariantId
    thresholds = [ordered]@{
      p95LimitMs = $P95LimitMs
      p99LimitMs = $P99LimitMs
      httpErrorRateLimit = $ErrorRateLimit
      expectedOutcomeRate = $ExpectedOutcomeRate
      consecutiveBreaches = $ConsecutiveBreaches
      hardCapRps = $MaxRate
    }
    outcome = $Outcome
    stopReason = $StopReason
    lastGoodRate = if ($LastGoodRate -gt 0) { $LastGoodRate } else { $null }
    firstBreachRate = if ($null -ne $FirstBreachRate) { $FirstBreachRate } else { $null }
    tokenCount = $TokenCount
    stages = @($Stages)
    cleanup = [ordered]@{
      tokenFilePreserved = $true
      rawStageSummariesRemoved = $RawStageSummariesRemoved
      kubernetesMutated = $false
      databaseQueried = $false
    }
  }
  $reportFile = Join-Path $outputPath "adaptive-$RunId.json"
  $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportFile -Encoding UTF8
  Write-Output "Sanitized report: $reportFile"
}

if (Test-Path -LiteralPath $staticTestPath -PathType Leaf) {
  & pwsh -NoLogo -NoProfile -File $staticTestPath
  if ($LASTEXITCODE -ne 0) { throw 'Phase 26 static contract tests failed.' }
}

$rates = Get-StageRates
$requiredTokens = Get-RequiredTokenCount $rates
Write-Output "Phase 26 static gate: PASS (rates=$($rates -join ',') hardCap=$MaxRate RPS)."
Write-Output "Guardrails: p95<$P95LimitMs ms, p99<$P99LimitMs ms, HTTP errors<=$ErrorRateLimit, expected outcomes>=$ExpectedOutcomeRate."
Write-Output "Token requirement for this ladder: $requiredTokens unique tokens (values withheld)."
if (-not $Run) {
  Write-Output 'Validation-only mode: k6 was not started and no cluster, database, Kafka, Redis, ECR, or Secret state was changed.'
  exit 0
}

$tokenCount = Read-TokenCount
if ($tokenCount -lt $requiredTokens) {
  throw "Shopper token file contains $tokenCount tokens; this ladder requires at least $requiredTokens unique tokens. Reduce the cap/duration or provide more disposable shoppers."
}

$runId = "$(Get-Date -Format 'yyyyMMddTHHmmssZ')-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
$script:Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$stageResults = [System.Collections.Generic.List[object]]::new()
$lastGoodRate = 0
$firstBreachRate = $null
$stopReason = 'hard_cap_reached'
$outcome = 'at_or_above_max_tested'
$tokenOffset = 0
$consecutiveBreachesSeen = 0
$tempSummaryFiles = [System.Collections.Generic.List[string]]::new()

try {
  $warmupSummary = Join-Path $outputPath ".$runId-warmup-summary.json"
  $tempSummaryFiles.Add($warmupSummary)
  $warmupLabel = "${runId}-warmup"
  Write-Output "Warm-up: $WarmupRate RPS for ${WarmupDurationSeconds}s."
  $warmupResult = Invoke-BoundedK6 -Rate $WarmupRate -Duration $WarmupDurationSeconds -TokenOffset $tokenOffset `
    -SummaryFile $warmupSummary -RunLabel $warmupLabel
  if (-not (Test-K6CompletedWithSummary $warmupResult.ExitCode)) {
    $stopReason = "warmup_k6_exit_$($warmupResult.ExitCode)"
    $outcome = 'stopped_on_danger'
    throw "Warm-up k6 exited with code $($warmupResult.ExitCode)."
  }
  if (-not (Test-Path -LiteralPath $warmupSummary -PathType Leaf)) { throw 'Warm-up did not produce a summary.' }
  $warmup = Get-Content -Raw -LiteralPath $warmupSummary | ConvertFrom-Json
  $warmupStage = Convert-StageSummary $warmup $WarmupRate $WarmupDurationSeconds 'warmup'
  if ($warmupResult.ExitCode -eq 99 -and $warmupStage.dangerReasons.Count -eq 0 `
      -and $warmupStage.breachReasons.Count -eq 0) {
    throw 'Warm-up reported an unclassified k6 threshold breach.'
  }
  if ($warmupStage.dangerReasons.Count -gt 0) {
    $stopReason = "warmup_danger:$($warmupStage.dangerReasons -join ',')"
    $outcome = 'stopped_on_danger'
    throw "Warm-up danger signal: $($warmupStage.dangerReasons -join ', ')."
  }
  $tokenOffset += Get-StageTokenBudget $WarmupRate $WarmupDurationSeconds
  if ($CooldownSeconds -gt 0) { Start-Sleep -Seconds ([Math]::Min($CooldownSeconds, (Get-RemainingSeconds))) }

  foreach ($rate in $rates) {
    Get-RemainingSeconds | Out-Null
    $summaryFile = Join-Path $outputPath ".$runId-$rate-summary.json"
    $tempSummaryFiles.Add($summaryFile)
    $label = "${runId}-${rate}rps"
    Write-Output "Stage: $rate RPS for ${StageDurationSeconds}s."
    $stageResult = Invoke-BoundedK6 -Rate $rate -Duration $StageDurationSeconds -TokenOffset $tokenOffset `
      -SummaryFile $summaryFile -RunLabel $label
    if (-not (Test-K6CompletedWithSummary $stageResult.ExitCode)) {
      $stopReason = "k6_exit_$($stageResult.ExitCode)"
      $outcome = 'stopped_on_danger'
      throw "Stage $rate RPS k6 exited with code $($stageResult.ExitCode)."
    }
    if (-not (Test-Path -LiteralPath $summaryFile -PathType Leaf)) {
      $stopReason = 'missing_summary'
      $outcome = 'stopped_on_danger'
      throw "Stage $rate RPS did not produce a summary."
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile | ConvertFrom-Json
    $stage = Convert-StageSummary $summary $rate $StageDurationSeconds $label
    if ($stageResult.ExitCode -eq 99 -and $stage.dangerReasons.Count -eq 0 `
        -and $stage.breachReasons.Count -eq 0) {
      throw "Stage $rate RPS reported an unclassified k6 threshold breach."
    }
    $stageResults.Add($stage)
    Write-Output ("Stage result: rate={0} requests={1} winners={2} replays={3} p95={4}ms p99={5}ms errors={6} dropped={7} breaches={8} danger={9}." -f $rate, $stage.requests, $stage.winners,
      $stage.replays, $stage.p95Ms, $stage.p99Ms, $stage.unexpectedErrors, $stage.droppedIterations,
      ($stage.breachReasons -join ','), ($stage.dangerReasons -join ','))
    $tokenOffset += Get-StageTokenBudget $rate $StageDurationSeconds

    if ($stage.dangerReasons.Count -gt 0) {
      $firstBreachRate = $rate
      $stopReason = "danger:$($stage.dangerReasons -join ',')"
      $outcome = 'stopped_on_danger'
      break
    }
    if ($stage.breachReasons.Count -gt 0) {
      $consecutiveBreachesSeen++
      if ($null -eq $firstBreachRate) { $firstBreachRate = $rate }
      if ($consecutiveBreachesSeen -ge $ConsecutiveBreaches) {
        $stopReason = "consecutive_breaches_$ConsecutiveBreaches"
        $outcome = 'stopped_on_breach'
        break
      }
    } else {
      $consecutiveBreachesSeen = 0
      $lastGoodRate = $rate
    }
    if ($CooldownSeconds -gt 0) { Start-Sleep -Seconds ([Math]::Min($CooldownSeconds, (Get-RemainingSeconds))) }
  }
} catch {
  if ($outcome -eq 'at_or_above_max_tested') {
    $outcome = 'stopped_on_danger'
    if ($stopReason -eq 'hard_cap_reached') { $stopReason = 'runner_failure' }
  }
  throw
} finally {
  $rawSummariesRemoved = $true
  foreach ($temporaryFile in $tempSummaryFiles) {
    if (Test-Path -LiteralPath $temporaryFile -PathType Leaf) {
      Remove-Item -LiteralPath $temporaryFile -Force -ErrorAction SilentlyContinue
      if (Test-Path -LiteralPath $temporaryFile -PathType Leaf) {
        $rawSummariesRemoved = $false
      }
    }
  }
  Write-SanitizedReport -Outcome $outcome -StopReason $stopReason -Stages @($stageResults) `
    -LastGoodRate $lastGoodRate -FirstBreachRate $firstBreachRate -TokenCount $tokenCount -RunId $runId `
    -RawStageSummariesRemoved $rawSummariesRemoved
}

if ($outcome -eq 'stopped_on_danger') {
  Write-Error "Phase 26 adaptive capacity probe: FAIL ($stopReason)."
  exit 2
}
Write-Output "Phase 26 adaptive capacity probe: PASS ($outcome)."
