#Requires -Version 7.0
<#
.SYNOPSIS
  Starts the local Flash Sale Compose stack and runs the bounded full-stack k6 readiness smoke.
.DESCRIPTION
  This is deliberately separate from authenticated seckill tests. It verifies that the local
  backing services and all ten Spring services are alive, then asks k6 to repeat readiness checks.
  It never runs docker compose down, deletes volumes, reads secrets, or calls EKS/Stripe.
#>
[CmdletBinding()]
param(
  [switch]$Run,
  [switch]$Build,
  [ValidateRange(1, 200)][int]$Rate = 1,
  [ValidateRange(5, 300)][int]$DurationSeconds = 20,
  [ValidateRange(30, 900)][int]$StartupTimeoutSeconds = 300,
  [string]$GatewayBaseUrl = 'http://127.0.0.1:18080'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$composeArgs = @('-f', (Join-Path $repoRoot 'infra/docker/compose.yml'), '-f', (Join-Path $repoRoot 'infra/docker/compose.dev.yml'))
$dockerComposeArgs = @('compose') + $composeArgs
$k6 = (Get-Command k6 -CommandType Application -ErrorAction Stop).Source

if (-not $GatewayBaseUrl.StartsWith('http://127.0.0.1:', [StringComparison]::OrdinalIgnoreCase) -and
    -not $GatewayBaseUrl.StartsWith('http://localhost:', [StringComparison]::OrdinalIgnoreCase)) {
  throw 'This smoke is local-only; GatewayBaseUrl must point to loopback.'
}
if ($Rate -gt 50 -and $DurationSeconds -gt 10) {
  throw 'Rates above 50 iterations/s are limited to 10 seconds per smoke stage.'
}

$targets = @(
  @{ name = 'api-gateway'; port = 18080 },
  @{ name = 'authentication-service'; port = 18081 },
  @{ name = 'product-service'; port = 18082 },
  @{ name = 'campaign-service'; port = 18083 },
  @{ name = 'flashsale-service'; port = 18084 },
  @{ name = 'order-service'; port = 18085 },
  @{ name = 'payment-service'; port = 18086 },
  @{ name = 'notification-service'; port = 18087 },
  @{ name = 'inventory-service'; port = 18088 },
  @{ name = 'cart-service'; port = 18089 }
)
$targetJson = $targets | ForEach-Object { @{ name = $_.name; url = "http://127.0.0.1:$($_.port)" } } | ConvertTo-Json -Compress

function Invoke-Native([string]$File, [string[]]$Arguments) {
  $start = [Diagnostics.ProcessStartInfo]::new()
  $start.FileName = $File
  $start.WorkingDirectory = $repoRoot
  $start.UseShellExecute = $false
  $start.RedirectStandardOutput = $true
  $start.RedirectStandardError = $true
  foreach ($argument in $Arguments) { [void]$start.ArgumentList.Add($argument) }
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
  if (-not $process.Start()) { throw "Could not start $File." }
  $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
  $process.WaitForExit()
  $out = $stdout.GetAwaiter().GetResult(); $err = $stderr.GetAwaiter().GetResult()
  if ($process.ExitCode -ne 0) { throw "$File failed (exit=$($process.ExitCode)): $($err.Trim())" }
  if ($out) { Write-Host $out.Trim() }
}

Write-Host "FULL_STACK_READINESS_PLAN: services=$($targets.Count) rate=$Rate iterations/s duration=${DurationSeconds}s"
if (-not $Run) {
  Write-Host 'VALIDATION_ONLY=PASS. Add -Run to start Compose and invoke k6.'
  return
}

# Start backing services first. No build is performed unless the operator explicitly asks for -Build.
Invoke-Native 'docker' ($dockerComposeArgs + @('up', '-d', 'postgres', 'redis', 'kafka', 'schema-registry'))
if ($Build) { Invoke-Native 'docker' ($dockerComposeArgs + @('--profile', 'apps', 'build')) }
Invoke-Native 'docker' ($dockerComposeArgs + @('--profile', 'apps', 'up', '-d', '--no-deps', 'api-gateway', 'authentication-service', 'product-service', 'campaign-service', 'flashsale-service', 'order-service', 'payment-service', 'notification-service', 'inventory-service', 'cart-service'))

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($StartupTimeoutSeconds)
do {
  $states = @()
  foreach ($target in $targets) {
    try {
      $json = Invoke-RestMethod -TimeoutSec 3 -ErrorAction Stop "http://127.0.0.1:$($target.port)/actuator/health/readiness"
      $states += [pscustomobject]@{ service = $target.name; status = 200; health = $json.status }
    } catch { $states += [pscustomobject]@{ service = $target.name; status = 0; health = 'DOWN' } }
  }
  $ready = @($states | Where-Object { $_.status -eq 200 -and $_.health -eq 'UP' }).Count
  Write-Host "Startup readiness: $ready/$($targets.Count)"
  if ($ready -eq $targets.Count) { break }
  if ([DateTimeOffset]::UtcNow -ge $deadline) {
    $states | Format-Table | Out-String | Write-Host
    throw 'Timed out waiting for all local services to report readiness.'
  }
  Start-Sleep -Seconds 5
} while ($true)

$runId = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$resultDir = Join-Path $repoRoot "load-tests/full-stack-smoke/results/$runId"
New-Item -ItemType Directory -Path $resultDir -Force | Out-Null
$summaryFile = Join-Path $resultDir 'report.json'
$env:FULL_STACK_RUN = '1'
$env:FULL_STACK_TARGETS = $targetJson
$env:FULL_STACK_RATE = "$Rate"
$env:FULL_STACK_DURATION_SECONDS = "$DurationSeconds"
$env:FULL_STACK_SUMMARY_FILE = $summaryFile.Replace('\', '/')
$profile = Join-Path $repoRoot 'load-tests/full-stack-smoke/full-stack-readiness.js'
try {
  Invoke-Native $k6 @('run', '--quiet', $profile)
} finally {
  Remove-Item Env:FULL_STACK_RUN,Env:FULL_STACK_TARGETS,Env:FULL_STACK_RATE,Env:FULL_STACK_DURATION_SECONDS,Env:FULL_STACK_SUMMARY_FILE -ErrorAction SilentlyContinue
}
Write-Host "Report: $summaryFile"
Write-Host 'FULL_STACK_READINESS_SMOKE=PASS'
