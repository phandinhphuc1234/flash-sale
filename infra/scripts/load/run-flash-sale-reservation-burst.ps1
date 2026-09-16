#Requires -Version 7.0
<#
.SYNOPSIS
  Send one bounded hot-SKU Flash Sale reservation burst.
.DESCRIPTION
  The default profile attempts 5,000 reservation arrivals per second for one second.
  It does not call Order or Payment and never seeds stock, changes flags, or reads a database.
  Run only against a disposable campaign/variant allocation prepared for this test.
  Use -AllowHighBurst to acknowledge that 5,000/s can saturate a local load generator.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][Guid]$CampaignId,
  [Parameter(Mandatory)][Guid]$VariantId,
  [Parameter(Mandatory)][ValidateRange(1, 1000000)][int]$ExpectedAllocation,
  [ValidateRange(1, 5000)][int]$Rate = 5000,
  [ValidateRange(1, 10)][int]$DurationSeconds = 1,
  [ValidateRange(1, 10000)][int]$VirtualUsers = 5000,
  [ValidateRange(1, 10000)][int]$MaxVirtualUsers = 6000,
  [ValidateRange(1, 30)][int]$RequestTimeoutSeconds = 5,
  [ValidateRange(30, 900)][int]$TimeoutSeconds = 180,
  [string]$GatewayBaseUrl = 'http://127.0.0.1:18080',
  [string]$ShopperTokenFile = 'load-tests/flash-sale-reservation-burst/shopper-tokens.json',
  [switch]$AllowRemote,
  [switch]$AllowHighBurst,
  [switch]$Run
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path

if ($MaxVirtualUsers -lt $VirtualUsers) { throw 'MaxVirtualUsers must be >= VirtualUsers.' }
if ($Run -and $Rate -gt 1000 -and -not $AllowHighBurst) {
  throw 'Rate above 1000/s requires explicit -AllowHighBurst. This can saturate a local generator.'
}
if ($Rate -gt 1000 -and $DurationSeconds -gt 1) {
  throw 'High burst rates are limited to one second per invocation.'
}
$uri = $null
if (-not [Uri]::TryCreate($GatewayBaseUrl, [UriKind]::Absolute, [ref]$uri) -or
    $uri.Scheme -notin @('http', 'https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
    $uri.AbsolutePath -ne '/') { throw 'GatewayBaseUrl must be an HTTP(S) origin without credentials/path/query.' }
$isLocal = $uri.Host -in @('localhost', '127.0.0.1', '[::1]', '::1')
if (-not $isLocal -and (-not $AllowRemote -or $uri.Scheme -ne 'https')) {
  throw 'Non-loopback traffic requires HTTPS and -AllowRemote.'
}
$plannedArrivals = $Rate * $DurationSeconds
if ($ExpectedAllocation -lt 1) { throw 'ExpectedAllocation must be positive.' }
$tokenPath = [IO.Path]::GetFullPath($ShopperTokenFile, $repoRoot)
$relativeTokenPath = [IO.Path]::GetRelativePath($repoRoot, $tokenPath)
if ($relativeTokenPath.StartsWith('..') -or [IO.Path]::IsPathRooted($relativeTokenPath)) {
  throw 'Keep the shopper token file inside the repository load-test directory.'
}

Write-Host "FLASH_SALE_RESERVATION_BURST_PLAN: rate=$Rate/s duration=${DurationSeconds}s plannedArrivals=$plannedArrivals allocation=$ExpectedAllocation"
Write-Host 'Scope: reservation endpoint only; no Order, Payment, database query, or stock mutation by this runner.'
if (-not $Run) {
  Write-Host 'VALIDATION_ONLY=PASS. Add -Run -AllowHighBurst after preparing a disposable fixture and unique shopper JWTs.'
  return
}

$k6 = (Get-Command k6 -CommandType Application -ErrorAction Stop).Source
if (-not (Test-Path -LiteralPath $tokenPath -PathType Leaf)) { throw 'Shopper token file is missing.' }
& git -C $repoRoot check-ignore --quiet -- $relativeTokenPath
if ($LASTEXITCODE -ne 0) { throw 'Shopper token file must be Git-ignored.' }
$tracked = & git -C $repoRoot ls-files -- $relativeTokenPath
if ($LASTEXITCODE -ne 0 -or $tracked) { throw 'Shopper token file must not be tracked.' }
try { $tokenValues = Get-Content -Raw -LiteralPath $tokenPath | ConvertFrom-Json -NoEnumerate }
catch { throw 'Shopper token file must contain a JSON array; values withheld.' }
if ($tokenValues -isnot [array] -or $tokenValues.Count -lt $plannedArrivals) {
  throw "Need at least $plannedArrivals distinct shopper JWTs for this burst; values withheld."
}
$subjects = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$minimumExpiry = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $TimeoutSeconds + 60
foreach ($token in $tokenValues) {
  try {
    if ($token -isnot [string] -or $token.Split('.').Count -ne 3) { throw 'shape' }
    $segment = $token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    $segment = $segment.PadRight($segment.Length + ((4 - $segment.Length % 4) % 4), '=')
    $claims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($segment)) | ConvertFrom-Json
    if (-not $subjects.Add([string]$claims.sub) -or [long]$claims.exp -lt $minimumExpiry) { throw 'identity_or_expiry' }
  } catch { throw 'JWTs must have distinct subjects and sufficient expiry; values withheld.' }
}
$tokenValues = $null
$runId = [Guid]::NewGuid().ToString('N')
$outputDirectory = Join-Path $repoRoot "load-tests/flash-sale-reservation-burst/results/$runId"
New-Item -ItemType Directory -Path $outputDirectory | Out-Null
$summaryFile = Join-Path $outputDirectory 'report.json'
$profile = Join-Path $repoRoot 'load-tests/flash-sale-reservation-burst/flash-sale-reservation-burst.js'
$settings = @{
  runId = $runId; baseUrl = $uri.GetLeftPart([UriPartial]::Authority)
  campaignId = $CampaignId.ToString(); variantId = $VariantId.ToString()
  rate = $Rate; duration = $DurationSeconds; expectedAllocation = $ExpectedAllocation
  vus = $VirtualUsers; maxVUs = $MaxVirtualUsers; requestSeconds = $RequestTimeoutSeconds
}
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = $k6; $start.WorkingDirectory = $repoRoot
$start.UseShellExecute = $false; $start.CreateNoWindow = $true
$start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
$start.Environment['K6_NO_USAGE_REPORT'] = 'true'
$start.Environment['FSB_RUN'] = '1'
$start.Environment['FSB_SETTINGS'] = ($settings | ConvertTo-Json -Compress)
$start.Environment['FSB_TOKEN_FILE'] = $tokenPath.Replace('\', '/')
$start.Environment['FSB_SUMMARY_FILE'] = $summaryFile.Replace('\', '/')
foreach ($argument in @('run', '--quiet', $profile)) { $start.ArgumentList.Add($argument) }
$process = [Diagnostics.Process]::new(); $process.StartInfo = $start
$started = $false
try {
  if (-not $process.Start()) { throw 'Could not start k6.' }
  $started = $true
  $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
  if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
    throw "k6 exceeded the $TimeoutSeconds-second process deadline; inspect the fixture before retrying."
  }
  $out = $stdout.GetAwaiter().GetResult(); $err = $stderr.GetAwaiter().GetResult()
  if (-not (Test-Path -LiteralPath $summaryFile)) { throw "k6 exited without a sanitized report (exit=$($process.ExitCode))." }
  $report = Get-Content -Raw -LiteralPath $summaryFile | ConvertFrom-Json
  $counters = $report.counters
  Write-Host "Burst outcome: accepted=$($counters.acceptedReservations) soldOut=$($counters.soldOut) pending=$($counters.acceptancePending) unexpected=$($counters.unexpectedResponses) transportErrors=$($counters.transportErrors) dropped=$($counters.droppedIterations)"
  Write-Host "Latency: p95=$($report.latencyMs.p95)ms p99=$($report.latencyMs.p99)ms"
  if ([int]$counters.acceptedReservations -gt $ExpectedAllocation) { throw 'OVERSSELL detected: accepted reservations exceed ExpectedAllocation.' }
  if ($process.ExitCode -ne 0 -or [int]$counters.droppedIterations -gt 0 -or [int]$counters.unexpectedResponses -gt 0 -or [int]$counters.transportErrors -gt 0) {
    throw "Burst did not pass guardrails (k6Exit=$($process.ExitCode)); inspect $summaryFile."
  }
  if ([int]$counters.acceptancePending -gt 0) { throw "Flash Sale returned acceptance-pending for $($counters.acceptancePending) requests; inspect $summaryFile." }
  Write-Host 'FLASH_SALE_RESERVATION_BURST=PASS (tested burst only; not a system capacity limit).'
} finally {
  if ($started -and -not $process.HasExited) { $process.Kill($true); $null = $process.WaitForExit(5000) }
  $process.Dispose()
  Write-Host "Report: $summaryFile"
  Write-Host 'Fixture and token file were preserved. Verify stock/reservation invariants before cleanup or retry.'
}
