<#
.SYNOPSIS
  Static safety checks for the Phase 26 adaptive seckill capacity runner.

.DESCRIPTION
  These checks are deliberately non-live. They parse PowerShell and inspect the k6 contract so a
  review cannot accidentally turn validation into a load run or add a direct database/Kubernetes
  mutation to the capacity probe.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$runnerPath = Join-Path $repoRoot 'infra\scripts\gitops\phase26-seckill-capacity.ps1'
$profilePath = Join-Path $repoRoot 'load-tests\flashsale-service\adaptive-arrival-rate.js'
$specDirectory = Join-Path $repoRoot 'specs\046-seckill-capacity-gate'

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw "Phase 26 static test failed: $Message" }
}

foreach ($path in @($runnerPath, $profilePath,
    (Join-Path $specDirectory 'spec.md'),
    (Join-Path $specDirectory 'plan.md'),
    (Join-Path $specDirectory 'tasks.md'),
    (Join-Path $specDirectory 'contracts\capacity-runner.md'))) {
  Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "missing required file $path"
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runnerPath, [ref]$tokens, [ref]$errors) | Out-Null
Assert-True ($errors.Count -eq 0) ("PowerShell parser failed: " + (($errors | ForEach-Object Message) -join '; '))

$runner = Get-Content -Raw -LiteralPath $runnerPath
$profile = Get-Content -Raw -LiteralPath $profilePath

foreach ($marker in @(
    '[switch]$Run',
    'constant-arrival-rate',
    'Get-StageRates',
    'Get-StageTokenBudget',
    'Test-K6CompletedWithSummary',
    'ConsecutiveBreaches',
    'firstBreachRate',
    'lastGoodRate',
    'Invoke-BoundedK6',
    'ProcessStartInfo',
    'ArgumentList.Add',
    'Kill($true)',
    'Write-SanitizedReport',
    'Validation-only mode',
    'git check-ignore',
    'databaseQueried = $false',
    'kubernetesMutated = $false'
  )) {
  Assert-True ($runner.IndexOf($marker, [StringComparison]::Ordinal) -ge 0) "runner marker is missing: $marker"
}

foreach ($marker in @(
    "executor: 'constant-arrival-rate'",
    "gracefulStop: '30s'",
    "adaptive_unexpected_errors",
    "dropped_iterations: ['count==0']",
    "adaptive_expected_outcome_rate",
    "adaptive_successful_winners",
    'FLASHSALE_TOKEN_OFFSET',
    'exec.scenario.iterationInTest',
    'replay preserves reservation identity',
    'FLASH_SALE_SOLD_OUT',
    'FLASH_SALE_ACCEPTANCE_PENDING',
    'handleSummary'
  )) {
  Assert-True ($profile.IndexOf($marker, [StringComparison]::Ordinal) -ge 0) "k6 marker is missing: $marker"
}

Assert-True ($profile.IndexOf('adaptive_dropped_iterations', [StringComparison]::Ordinal) -lt 0) `
  'k6 profile must use the built-in dropped_iterations metric rather than an undefined custom metric'
Assert-True ($profile.IndexOf("gracefulStop: '0s'", [StringComparison]::Ordinal) -lt 0) `
  'k6 profile must not abort an in-flight winner before its idempotency replay'
Assert-True ($runner.IndexOf('$metricValueProperty = $values.PSObject.Properties[$Property]',
    [StringComparison]::Ordinal) -ge 0) `
  'metric parser must not collide with its case-insensitive typed Property parameter'
Assert-True ($runner.IndexOf("if (`$outcome -eq 'stopped_on_danger')", [StringComparison]::Ordinal) -ge 0) `
  'correctness danger must produce the non-zero exit required by SC-002'

foreach ($pattern in @(
    '(?i)kubectl\s+(?:apply|delete|patch|replace|rollout)',
    '(?i)\b(?:psql|mysql|redis-cli|kafka-console-(?:producer|consumer))\b',
    '(?i)docker\s+(?:login|push)',
    '(?i)(?:password|secret|token)\s*=\s*["''][^"'']+["'']',
    '(?i)(?:sk_(?:test|live)_|whsec_)'
  )) {
  Assert-True ($runner -notmatch $pattern) "forbidden mutation or credential-like literal found in runner: $pattern"
}

foreach ($pattern in @(
    '(?i)console\.log\s*\(\s*(?:token|authorization)',
    '(?i)Bearer\s+["''][^"'']+["'']'
  )) {
  Assert-True ($profile -notmatch $pattern) "credential output pattern found in k6 profile: $pattern"
}

Write-Output 'PHASE_26_STATIC=PASS'
Write-Output 'Adaptive stages, hard cap, breach stop, token isolation, and child cleanup are present.'
Write-Output 'No live k6, Kubernetes, database, Kafka, Redis, ECR, or Secret operation was performed.'
