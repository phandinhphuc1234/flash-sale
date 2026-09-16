#Requires -Version 7.0
<#
.SYNOPSIS
  Test tai Flash Sale -> Kafka -> Order; mac dinh chi kiem tra, khong gui traffic.
.DESCRIPTION
  -Run bat dau traffic tren fixture dung rieng. Token la JWT cua shopper khac nhau.
  Runner khong tao user/stock, khong doi flag, khong goi Stripe hay database.
  Moi muc tai la mot process k6 co deadline; dung neu mat correctness/Order hoac generator drop.
  Doc load-tests/flash-sale-to-order-stress/README.md truoc khi chay that.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][Guid]$CampaignId,
  [Parameter(Mandatory)][Guid]$VariantId,
  [Parameter(Mandatory)][ValidateRange(1, 1000000)][int]$ExpectedAllocation,
  [ValidateSet('NewOrders', 'Replay', 'SoldOut')][string]$Scenario = 'NewOrders',
  [string]$RatesCsv = '1,5',
  [ValidateRange(1, 120)][int]$StageSeconds = 10,
  [ValidateRange(0, 30)][int]$WarmupSeconds = 5,
  [ValidateRange(0, 30)][int]$CooldownSeconds = 5,
  [ValidateRange(1, 120)][int]$OrderTimeoutSeconds = 30,
  [ValidateRange(100, 10000)][int]$PollIntervalMs = 1000,
  [ValidateRange(1, 10)][int]$DuplicateCheckSeconds = 1,
  [ValidateRange(1, 30)][int]$RequestTimeoutSeconds = 5,
  [ValidateRange(1, 500)][int]$VirtualUsers = 50,
  [ValidateRange(1, 60000)][int]$AdmissionP95LimitMs = 300,
  [ValidateRange(1, 120000)][int]$AdmissionP99LimitMs = 700,
  [ValidateRange(1, 120000)][int]$OrderP95LimitMs = 5000,
  [ValidateRange(1, 5)][int]$ConsecutiveBreaches = 2,
  [ValidateRange(60, 3600)][int]$TimeoutSeconds = 900,
  [string]$GatewayBaseUrl = 'http://127.0.0.1:18080',
  [string]$ShopperTokenFile = 'load-tests/flash-sale-to-order-stress/shopper-tokens.json',
  [switch]$AllowRemote,
  [switch]$Run
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path

# Validate workload and safety bounds before reading credentials or invoking any process.
if ($CampaignId -eq [Guid]::Empty -or $VariantId -eq [Guid]::Empty) { throw 'Fixture UUIDs must not be empty.' }
if ($RatesCsv -notmatch '^\d+(,\d+)*$') { throw 'RatesCsv must look like 1,5,10.' }
$rates = @($RatesCsv.Split(',') | ForEach-Object { [int]$_ })
for ($i = 0; $i -lt $rates.Count; $i++) {
  if ($rates[$i] -lt 1 -or $rates[$i] -gt 500 -or ($i -gt 0 -and $rates[$i] -le $rates[$i - 1])) {
    throw 'Rates must increase strictly within 1..500 arrivals/s.'
  }
}
if ($AdmissionP99LimitMs -lt $AdmissionP95LimitMs) { throw 'Admission p99 limit must be >= p95 limit.' }
if ($Scenario -eq 'SoldOut' -and ($rates.Count -ne 1 -or $WarmupSeconds -ne 0)) {
  throw 'SoldOut uses one rate and -WarmupSeconds 0 with its own fresh fixture.'
}
$uri = $null
if (-not [Uri]::TryCreate($GatewayBaseUrl, [UriKind]::Absolute, [ref]$uri) -or
    $uri.Scheme -notin @('http', 'https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
    $uri.AbsolutePath -ne '/') { throw 'GatewayBaseUrl must be an HTTP(S) origin without credentials/path/query.' }
$isLocal = $uri.Host -in @('localhost', '127.0.0.1', '[::1]', '::1')
if (-not $isLocal -and (-not $AllowRemote -or $uri.Scheme -ne 'https')) {
  throw 'Non-loopback traffic requires HTTPS and -AllowRemote; use local first.'
}
$stages = @()
if ($WarmupSeconds -gt 0) { $stages += @{ rate = 1; duration = $WarmupSeconds; warmup = $true } }
foreach ($rate in $rates) { $stages += @{ rate = $rate; duration = $StageSeconds; warmup = $false } }
# One extra second per stage covers boundary scheduling without reusing shopper identities.
$requiredTokens = [int](($stages | ForEach-Object { $_.rate * ($_.duration + 1) } | Measure-Object -Sum).Sum)
$plannedArrivals = [int](($stages | ForEach-Object { $_.rate * $_.duration } | Measure-Object -Sum).Sum)
if ($Scenario -ne 'SoldOut' -and $ExpectedAllocation -lt $requiredTokens) {
  throw "Fresh allocation must be >= $requiredTokens (includes warm-up and boundary headroom)."
}
if ($Scenario -eq 'SoldOut' -and $ExpectedAllocation -ge $plannedArrivals) {
  throw 'SoldOut needs positive fresh allocation smaller than planned arrivals.'
}
$graceSeconds = $OrderTimeoutSeconds + $RequestTimeoutSeconds * 3 + $DuplicateCheckSeconds + 5
$budget = [int](($stages | ForEach-Object { $_.duration + $graceSeconds + $CooldownSeconds + 30 } | Measure-Object -Sum).Sum)
if ($budget -gt $TimeoutSeconds) { throw "Increase TimeoutSeconds to at least $budget or shorten the ladder." }
Write-Host "FLASH_SALE_TO_ORDER_PLAN: scenario=$Scenario rates=$RatesCsv requiredFreshShoppers=$requiredTokens maxBudgetSeconds=$budget"
if (-not $Run) {
  Write-Host 'VALIDATION_ONLY=PASS. No HTTP request/process/credential read; add -Run only after preparing fixtures.'
  return
}

# Fail before traffic if credentials could enter Git or multiple tokens represent the same shopper.
$k6 = (Get-Command k6 -CommandType Application -ErrorAction Stop).Source
$tokenPath = [IO.Path]::GetFullPath($ShopperTokenFile, $repoRoot)
if (-not (Test-Path -LiteralPath $tokenPath -PathType Leaf)) { throw 'Shopper token file is missing.' }
$relativeTokenPath = [IO.Path]::GetRelativePath($repoRoot, $tokenPath)
if ($relativeTokenPath.StartsWith('..') -or [IO.Path]::IsPathRooted($relativeTokenPath)) {
  throw 'Keep the token file in the documented Git-ignored repository location.'
}
& git -C $repoRoot check-ignore --quiet -- $relativeTokenPath
if ($LASTEXITCODE -ne 0) { throw 'Shopper token file must be Git-ignored and untracked.' }
$tracked = & git -C $repoRoot ls-files -- $relativeTokenPath
if ($LASTEXITCODE -ne 0 -or $tracked) { throw 'Shopper token file must not be tracked.' }
try { $shopperTokens = Get-Content -Raw -LiteralPath $tokenPath | ConvertFrom-Json -NoEnumerate }
catch { throw 'Shopper token file must contain a JSON array; values withheld.' }
if ($shopperTokens -isnot [array] -or $shopperTokens.Count -lt $requiredTokens) {
  throw "Need at least $requiredTokens fresh shopper JWTs in a JSON array."
}
$subjects = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$minimumExpiry = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $budget + 60
foreach ($token in $shopperTokens) {
  try {
    if ($token -isnot [string] -or $token.Split('.').Count -ne 3) { throw 'shape' }
    $segment = $token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    $segment = $segment.PadRight($segment.Length + ((4 - $segment.Length % 4) % 4), '=')
    $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($segment)) | ConvertFrom-Json
    $subjectId = [Guid]::Parse($claims.sub)
    if ($subjectId -eq [Guid]::Empty -or -not $subjects.Add($subjectId.ToString()) -or
        [long]$claims.exp -lt $minimumExpiry) { throw 'identity_or_expiry' }
  } catch { throw 'JWTs must have distinct shopper UUID subjects and expiry beyond the run budget; values withheld.' }
}
$shopperTokens = $null
# This parses claims only; the real Gateway verifies signatures and permissions on each request.
$runId = [Guid]::NewGuid().ToString('N')
$outputDirectory = Join-Path $repoRoot "load-tests/flash-sale-to-order-stress/results/$runId"
New-Item -ItemType Directory -Path $outputDirectory | Out-Null
$profile = Join-Path $repoRoot 'load-tests/flash-sale-to-order-stress/flash-sale-to-order-stress.js'
$emptyK6Config = Join-Path $repoRoot 'load-tests/flash-sale-to-order-stress/k6-config.json'
$reportFile = Join-Path $outputDirectory 'report.json'
$sourceCommit = & git -C $repoRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0) { throw 'Could not identify source revision for the report.' }
$workingChanges = & git -C $repoRoot status --porcelain
if ($LASTEXITCODE -ne 0) { throw 'Could not identify working-tree status for the report.' }
$report = [ordered]@{
  schemaVersion = 1; test = 'flash-sale-to-order-stress'; runId = $runId
  sourceCommit = $sourceCommit.Trim(); workingTreeDirty = [bool]$workingChanges
  generatedAt = [DateTimeOffset]::UtcNow.ToString('o'); environment = $(if ($isLocal) { 'loopback' } else { 'remote' })
  gatewayOrigin = $uri.GetLeftPart([UriPartial]::Authority); campaignId = $CampaignId.ToString(); variantId = $VariantId.ToString()
  expectedFreshAllocation = $ExpectedAllocation; thresholds = @{
    admissionP95Ms = $AdmissionP95LimitMs; admissionP99Ms = $AdmissionP99LimitMs; orderVisibleP95Ms = $OrderP95LimitMs
    orderTimeoutSeconds = $OrderTimeoutSeconds; pollIntervalMs = $PollIntervalMs; duplicateCheckSeconds = $DuplicateCheckSeconds
    consecutiveBreaches = $ConsecutiveBreaches; virtualUsers = $VirtualUsers
  }
  outcome = 'runner_failure'; lastGoodRate = $null; firstBreachRate = $null; stages = @()
  databaseQueried = $false; kubernetesMutated = $false; stripeCalled = $false; operatorTokenFilePreserved = $true
}
$clock = [Diagnostics.Stopwatch]::StartNew()
$runDeadlineEpochMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + $budget * 1000
$offset = 0; $consumed = 0; $breaches = 0; $anyBreach = $false

function Write-Report {
  $report.elapsedSeconds = [math]::Round($clock.Elapsed.TotalSeconds, 3)
  $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $reportFile -Encoding utf8
}
function Invoke-K6Stage($Settings, [string]$SummaryFile) {
  $start = [Diagnostics.ProcessStartInfo]::new()
  $start.FileName = $k6; $start.WorkingDirectory = $repoRoot
  $start.UseShellExecute = $false; $start.CreateNoWindow = $true
  $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
  # Do not inherit HTTP debug/output exporters or stale k6 test configuration from another run.
  foreach ($key in @($start.Environment.Keys)) {
    if ($key.StartsWith('K6_', [StringComparison]::OrdinalIgnoreCase) -or $key.StartsWith('FSO_')) {
      $start.Environment.Remove($key) | Out-Null
    }
  }
  $start.Environment['K6_NO_USAGE_REPORT'] = 'true'
  $start.Environment['FSO_RUN'] = '1'
  $start.Environment['FSO_SETTINGS'] = ($Settings | ConvertTo-Json -Compress)
  $start.Environment['FSO_TOKEN_FILE'] = $tokenPath.Replace('\', '/')
  $start.Environment['FSO_SUMMARY_FILE'] = $SummaryFile.Replace('\', '/')
  foreach ($argument in @('run', '--quiet', '--config', $emptyK6Config, $profile)) { $start.ArgumentList.Add($argument) }
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
  $started = $false
  try {
    if (-not $process.Start()) { throw 'Could not start k6.' }
    $started = $true
    # Drain both pipes asynchronously so a full stderr pipe cannot deadlock the child.
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    $stageClock = [Diagnostics.Stopwatch]::StartNew()
    while (-not $process.WaitForExit(500)) {
      if ($clock.Elapsed.TotalSeconds -ge $budget -or
          $stageClock.Elapsed.TotalSeconds -ge $Settings.duration + $graceSeconds + 30) {
        throw 'k6 deadline exceeded; in-flight outcomes are unknown. Inspect fixtures before another run.'
      }
    }
    $null = $stdout.GetAwaiter().GetResult(); $null = $stderr.GetAwaiter().GetResult()
    return $process.ExitCode
  } finally {
    if ($started -and -not $process.HasExited) { $process.Kill($true); $null = $process.WaitForExit(5000) }
    $process.Dispose()
  }
}

try {
  foreach ($stage in $stages) {
    $settings = @{
      runId = $runId; baseUrl = $uri.GetLeftPart([UriPartial]::Authority); scenario = $Scenario
      runDeadlineEpochMs = $runDeadlineEpochMs
      campaignId = $CampaignId.ToString(); variantId = $VariantId.ToString(); tokenOffset = $offset
      rate = $stage.rate; duration = $stage.duration; vus = $VirtualUsers; allocation = $ExpectedAllocation - $consumed
      observeSeconds = $OrderTimeoutSeconds; requestSeconds = $RequestTimeoutSeconds
      pollMs = $PollIntervalMs; settleSeconds = $DuplicateCheckSeconds
      admissionP95 = $AdmissionP95LimitMs; admissionP99 = $AdmissionP99LimitMs; orderP95 = $OrderP95LimitMs
    }
    $summaryFile = Join-Path $outputDirectory "stage-$($report.stages.Count).json"
    Write-Host "Starting bounded stage: arrivals/s=$($stage.rate) seconds=$($stage.duration) warmup=$($stage.warmup)"
    $exitCode = Invoke-K6Stage $settings $summaryFile
    if (-not (Test-Path -LiteralPath $summaryFile)) { throw "k6 exited ($exitCode) without a summary; no capacity conclusion." }
    $result = Get-Content -Raw -LiteralPath $summaryFile | ConvertFrom-Json -AsHashtable
    $result.warmup = $stage.warmup; $result.k6ExitCode = $exitCode
    $report.stages += $result
    $consumed += $result.acceptedUniqueReservations
    $offset += $stage.rate * ($stage.duration + 1)
    Write-Host "Stage: $($result.outcome); accepted=$($result.acceptedUniqueReservations) orders=$($result.observedOrders) reads=$($result.observerReadRequests)"
    if ($result.outcome -eq 'danger' -or $exitCode -ne 0) {
      $report.outcome = 'stopped_on_danger'; break
    }
    if ($result.outcome -eq 'breach') {
      if ($stage.warmup) { $report.outcome = 'warmup_breach'; break }
      $anyBreach = $true; $breaches++
      if ($null -eq $report.firstBreachRate) { $report.firstBreachRate = $stage.rate }
      if ($breaches -ge $ConsecutiveBreaches) { $report.outcome = 'stopped_on_breach'; break }
    } else {
      $breaches = 0
      if (-not $stage.warmup) { $report.lastGoodRate = $stage.rate }
    }
    $report.outcome = $(if ($anyBreach) { 'completed_with_breaches' } else { 'passed_tested_rates_not_capacity_limit' })
    Write-Report
    if ($CooldownSeconds) { Start-Sleep -Seconds $CooldownSeconds }
  }
} catch {
  $report.outcome = 'runner_failure'
  throw
} finally {
  Write-Report
  Write-Host "Report: $reportFile"
  Write-Host 'Fixtures and token file preserved. Review reservations/Orders before cleanup or retry.'
}
if ($report.outcome -ne 'passed_tested_rates_not_capacity_limit') { throw "Benchmark did not pass: $($report.outcome). See sanitized report." }
Write-Host 'FLASH_SALE_TO_ORDER_STRESS=PASS (tested rates only; not the system capacity limit).'
