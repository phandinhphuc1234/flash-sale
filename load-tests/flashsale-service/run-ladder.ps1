[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [Guid] $CampaignId,

    [Parameter(Mandatory = $true)]
    [Guid] $VariantId,

    [ValidateSet('gateway', 'direct-service')]
    [string] $Route = 'gateway',

    [int[]] $Levels = @(100, 250, 500, 1000),

    [string] $LevelsCsv = '',

    [int] $WarmupRequests = 25,

    [int] $ExpectedAllocation = 2000,

    [string] $GatewayBaseUrl = 'http://127.0.0.1:18080',

    [string] $AuthBaseUrl = 'http://127.0.0.1:18081',

    [string] $FlashSaleBaseUrl = 'http://127.0.0.1:18084',

    [int] $AuthPauseMilliseconds = 30,

    [string] $OutputDirectory = 'load-tests/flashsale-service/results'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version Latest

if (-not [string]::IsNullOrWhiteSpace($LevelsCsv)) {
    try {
        $Levels = @($LevelsCsv -split ',' | ForEach-Object { [int]$_.Trim() })
    } catch {
        throw 'LevelsCsv must be comma-separated positive integers, for example 100,250,500,1000.'
    }
}

if ($Levels.Count -eq 0 -or @($Levels | Where-Object { $_ -le 0 }).Count -gt 0) {
    throw 'Levels must contain positive integers, for example -Levels 100,250,500,1000.'
}
if ($WarmupRequests -lt 0) {
    throw 'WarmupRequests cannot be negative.'
}
$totalRequests = $WarmupRequests + [int]($Levels | Measure-Object -Sum).Sum
if ($totalRequests -gt $ExpectedAllocation) {
    throw "The ladder needs $totalRequests units but ExpectedAllocation is only $ExpectedAllocation. Increase fixture allocation or reduce Levels."
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$loadTestDirectory = (Resolve-Path $PSScriptRoot).Path
$outputPath = Join-Path $repoRoot $OutputDirectory
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null

$baseUrl = if ($Route -eq 'gateway') { $GatewayBaseUrl.TrimEnd('/') } else { $FlashSaleBaseUrl.TrimEnd('/') }
$runTag = 'k6-ladder-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
$password = 'K6!LoadTestAa9'
$composeEnv = Join-Path $repoRoot 'infra\docker\.env'
$composeFile = Join-Path $repoRoot 'infra\docker\compose.yml'

function Get-DotEnvValue([string] $Name, [string] $Fallback = '') {
    if (-not (Test-Path -LiteralPath $composeEnv)) {
        return $Fallback
    }
    $line = Get-Content -LiteralPath $composeEnv |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=" } |
        Select-Object -First 1
    if (-not $line) {
        return $Fallback
    }
    return ($line -split '=', 2)[1]
}

$postgresUser = Get-DotEnvValue 'POSTGRES_USER' 'flashsale'
$shopperIds = [System.Collections.Generic.List[string]]::new()

function Invoke-AuthRequest([string] $Uri, [string] $Method, [hashtable] $Body, [string] $Trace) {
    $json = $Body | ConvertTo-Json -Compress
    for ($attempt = 1; $attempt -le 6; $attempt++) {
        $response = Invoke-WebRequest -Uri $Uri -Method $Method -ContentType 'application/json' `
            -Headers @{ 'X-Trace-Id' = $Trace } -Body $json -SkipHttpErrorCheck
        if ($response.StatusCode -in @(200, 201)) {
            return ($response.Content | ConvertFrom-Json)
        }
        if ($response.StatusCode -eq 429 -and $attempt -lt 6) {
            Start-Sleep -Milliseconds ([int](200 * [Math]::Pow(2, $attempt - 1)))
            continue
        }
        throw "Authentication request failed: $Method $Uri returned HTTP $($response.StatusCode)."
    }
    throw "Authentication request exhausted retries: $Method $Uri."
}

function New-ShopperTokens([int] $Count, [string] $Stage) {
    $tokens = [System.Collections.Generic.List[string]]::new()
    for ($index = 1; $index -le $Count; $index++) {
        $suffix = "$runTag-$Stage-$('{0:D5}' -f $index)"
        $email = "$suffix@example.test"
        $trace = "$suffix-register"
        $registration = Invoke-AuthRequest "$AuthBaseUrl/api/v1/auth/register" 'Post' `
            @{ email = $email; username = $suffix; password = $password } $trace
        $shopperIds.Add([string]$registration.data.userId)
        $login = Invoke-AuthRequest "$AuthBaseUrl/api/v1/auth/login" 'Post' `
            @{ login = $email; password = $password; deviceName = 'k6-ladder' } "$suffix-login"
        $tokens.Add([string]$login.data.accessToken)
        if (($index % 25) -eq 0 -or $index -eq $Count) {
            Write-Host "  token setup [$Stage] $index/$Count"
        }
        if ($AuthPauseMilliseconds -gt 0) {
            Start-Sleep -Milliseconds $AuthPauseMilliseconds
        }
    }
    return @($tokens)
}

function Remove-TestUsers {
    # Use the unique username prefix instead of an IN list: a 1,000-user ladder
    # would otherwise exceed Windows' docker command-line length limit.
    & docker compose --env-file $composeEnv -f $composeFile exec -T postgres `
        psql -X -q -v ON_ERROR_STOP=1 -U $postgresUser -d auth_db `
        -Atc "DELETE FROM users WHERE username LIKE '$runTag-%';" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Could not remove all ladder users automatically; clean the k6-ladder-* users from auth_db before rerunning.'
    }
}

function Invoke-K6Stage([string] $Stage, [int] $Count, [string[]] $Tokens) {
    $tokenFile = Join-Path $outputPath "$runTag-$Stage-tokens.json"
    $summaryFile = Join-Path $outputPath "$runTag-$Stage-summary.json"
    $logFile = Join-Path $outputPath "$runTag-$Stage.log"
    $Tokens | ConvertTo-Json -Compress | Set-Content -LiteralPath $tokenFile -Encoding UTF8
    $env:FLASHSALE_BASE_URL = $baseUrl
    $env:FLASHSALE_ROUTE = $Route
    $env:FLASHSALE_RUN_LABEL = $Stage
    $env:FLASHSALE_CAMPAIGN_ID = [string]$CampaignId
    $env:FLASHSALE_VARIANT_ID = [string]$VariantId
    $env:FLASHSALE_EXPECTED_ALLOCATION = [string]$ExpectedAllocation
    $env:FLASHSALE_VUS = [string]$Count
    $env:FLASHSALE_ITERATIONS_PER_VU = '1'
    $env:FLASHSALE_SHOPPER_TOKENS_FILE = $tokenFile
    $env:FLASHSALE_K6_SUMMARY_FILE = $summaryFile
    Push-Location $loadTestDirectory
    try {
        $k6Output = & k6 run --quiet reservation.js 2>&1
        $k6ExitCode = $LASTEXITCODE
        $k6Output | Set-Content -LiteralPath $logFile -Encoding UTF8
    } finally {
        Pop-Location
    }
    if ($k6ExitCode -ne 0) {
        $tail = (Get-Content -LiteralPath $logFile | Select-Object -Last 30) -join "`n"
        throw "k6 stage '$Stage' failed with exit code $k6ExitCode.`n$tail"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryFile | ConvertFrom-Json
    function Get-MetricCount([string] $Name) {
        $metric = $summary.metrics.PSObject.Properties[$Name]
        if ($null -eq $metric) { return 0 }
        $count = $metric.Value.values.PSObject.Properties['count']
        if ($null -eq $count) { return 0 }
        return [int]$count.Value
    }
    $winnerValues = $summary.metrics.reservation_winner_http_duration.values
    $replayValues = $summary.metrics.reservation_replay_http_duration.values
    $httpValues = $summary.metrics.http_reqs.values
    [pscustomobject]@{
        stage = $Stage
        route = $Route
        requests = $Count
        winners = Get-MetricCount 'reservation_successful_winners'
        replays = Get-MetricCount 'reservation_replays'
        soldOut = Get-MetricCount 'reservation_sold_out'
        pending = Get-MetricCount 'reservation_acceptance_pending'
        unexpectedErrors = Get-MetricCount 'reservation_unexpected_errors'
        winnerP95Ms = [double]$winnerValues.'p(95)'
        winnerP99Ms = [double]$winnerValues.'p(99)'
        replayP95Ms = [double]$replayValues.'p(95)'
        replayP99Ms = [double]$replayValues.'p(99)'
        totalP95Ms = [double]$summary.metrics.reservation_http_duration.values.'p(95)'
        httpRequests = [int]$httpValues.count
        summaryFile = $summaryFile
    }
}

$results = [System.Collections.Generic.List[object]]::new()
try {
    Write-Host "Ladder route=$Route baseUrl=$baseUrl campaign=$CampaignId variant=$VariantId"
    Write-Host "Allocation required=$totalRequests fixtureExpected=$ExpectedAllocation"

    if ($WarmupRequests -gt 0) {
        Write-Host "Warm-up: $WarmupRequests valid reservations (excluded from reported ladder stages)"
        $warmupTokens = New-ShopperTokens $WarmupRequests 'warmup'
        $results.Add((Invoke-K6Stage 'warmup' $WarmupRequests $warmupTokens))
    }

    foreach ($level in $Levels) {
        Write-Host "Measurement stage: $level new reservations"
        $stageName = 'stage-' + $level
        $stageTokens = New-ShopperTokens $level $stageName
        $result = Invoke-K6Stage $stageName $level $stageTokens
        $results.Add($result)
        $result | Format-List
    }

    $resultFile = Join-Path $outputPath "$runTag-results.json"
    [pscustomobject]@{
        generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
        route = $Route
        baseUrl = $baseUrl
        campaignId = [string]$CampaignId
        variantId = [string]$VariantId
        warmupRequests = $WarmupRequests
        levels = @($Levels)
        expectedAllocation = $ExpectedAllocation
        stages = @($results)
    } | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resultFile -Encoding UTF8
    Write-Host "Ladder results: $resultFile"
} finally {
    Remove-TestUsers
    Remove-Item -Path (Join-Path $outputPath "$runTag-*-tokens.json") -Force -ErrorAction SilentlyContinue
    Remove-Item Env:FLASHSALE_BASE_URL, Env:FLASHSALE_ROUTE, Env:FLASHSALE_RUN_LABEL, `
        Env:FLASHSALE_CAMPAIGN_ID, Env:FLASHSALE_VARIANT_ID, Env:FLASHSALE_EXPECTED_ALLOCATION, `
        Env:FLASHSALE_VUS, Env:FLASHSALE_ITERATIONS_PER_VU, Env:FLASHSALE_SHOPPER_TOKENS_FILE, `
        Env:FLASHSALE_K6_SUMMARY_FILE -ErrorAction SilentlyContinue
}
