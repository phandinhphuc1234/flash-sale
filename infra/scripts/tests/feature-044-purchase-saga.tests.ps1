<#
.SYNOPSIS
  Static safety and operator-contract tests for the Feature 044 local runner.

.DESCRIPTION
  Parses the runner without starting Docker, Maven, Kafka, PostgreSQL, Redis, or any application.
  If PSScriptAnalyzer is installed, error-severity findings are also enforced. The remaining checks
  protect scenario dispatch, bounded native processes, sanitized diagnostics, and temp cleanup.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$runner = Join-Path $repoRoot "infra\docker\smoke\feature-044-purchase-saga.ps1"
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) { throw "Runner not found: $runner" }

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runner, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
  throw ("PowerShell parser failed: " + (($errors | ForEach-Object Message) -join "; "))
}

if (Get-Command Invoke-ScriptAnalyzer -ErrorAction SilentlyContinue) {
  $analysisErrors = @(Invoke-ScriptAnalyzer -Path $runner -Severity Error)
  if ($analysisErrors.Count -gt 0) {
    throw ("PSScriptAnalyzer errors: " + (($analysisErrors | ForEach-Object Message) -join "; "))
  }
}

$content = Get-Content -LiteralPath $runner -Raw
foreach ($marker in @(
  "[ValidateSet('Contracts', 'Start', 'Paid', 'Failed', 'Replay', 'LateSuccess', 'All')]",
  "[ValidateRange(60, 3600)]",
  "Get-RemainingMilliseconds",
  "WaitForExit((Get-RemainingMilliseconds))",
  "Get-BoundedDiagnostic",
  "Invoke-ContractValidation",
  "Remove-Item -LiteralPath `$temporaryScript -Force",
  "finally",
  "FEATURE_044_CONTRACTS=PASS",
  "FEATURE_044_START=PASS",
  "FEATURE_044_PAID=PASS",
  "FEATURE_044_FAILED=PASS",
  "FEATURE_044_REPLAY=PASS",
  "FEATURE_044_LATE_SUCCESS=PASS",
  "FEATURE_044_MODULES=PASS",
  "FEATURE_044_MONOREPO=PASS",
  "FEATURE_044_LOCAL_GATE=PASS"
)) {
  if ($content.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Feature 044 runner marker is missing: $marker"
  }
}

foreach ($pattern in @(
  '(?i)Write-(?:Output|Host).*\b(?:password|secret|authorization|jwt|checkoutUrl|stripe-signature)\b',
  '(?i)Get-Content\s+.*\\\.env(?:\s|$)',
  '(?i)\b(?:psql|mysql|redis-cli|kafka-console-producer)\b',
  '(?i)kubectl\s+(?:apply|delete|patch|replace)\b',
  '(?i)docker\s+(?:push|login)\b'
)) {
  if ($content -match $pattern) { throw "Forbidden runner behavior found: $pattern" }
}

Write-Output "Feature 044 local runner static tests: PASS"
Write-Output "No Docker, Maven, Kafka, database, Kubernetes, Registry, or Secret state was changed."
