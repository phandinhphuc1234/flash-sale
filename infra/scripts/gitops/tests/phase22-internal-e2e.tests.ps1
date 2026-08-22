<#
.SYNOPSIS
  Static safety checks for the Phase 22 internal E2E runner.

.DESCRIPTION
  These checks do not contact EKS or mutate business/Kubernetes state. They protect the
  operator boundary: the runner must remain PowerShell 7 parseable, use secure admin input,
  delegate stock initialization to the Inventory-owned Job, and avoid direct data-plane access.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$runner = Join-Path $repoRoot "infra\scripts\gitops\phase22-internal-e2e.ps1"
if (-not (Test-Path -LiteralPath $runner)) { throw "Runner not found: $runner" }

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runner, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
  throw ("PowerShell parser failed: " + (($errors | ForEach-Object Message) -join "; "))
}

$content = Get-Content -LiteralPath $runner -Raw
$requiredMarkers = @(
  'Read-Host "Existing ROLE_ADMIN password" -AsSecureString',
  'SkipHeaderValidation',
  'deployment/inventory-service',
  'INVENTORY_FIXTURE_ENABLED',
  'api/v1/orders?page=0&size=100',
  'purchaseRequestId',
  'reservationId',
  'campaignId',
  'variantId',
  'PENDING_PAYMENT',
  'Assert-AnonymousAdminRejected'
)
foreach ($marker in $requiredMarkers) {
  if ($content.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Required safety/flow marker is missing: $marker"
  }
}

$forbiddenPatterns = @(
  '(?i)kubectl\s+exec',
  '(?i)\b(?:psql|mysql|redis-cli|kafka-console-(?:producer|consumer))\b',
  '(?i)\b(?:Invoke-Sqlcmd|sqlcmd)\b',
  '(?i)kubectl\s+port-forward\s+.*(?:postgres|redis|kafka)'
)
foreach ($pattern in $forbiddenPatterns) {
  if ($content -match $pattern) { throw "Forbidden direct data-plane command found: $pattern" }
}

Write-Output "Phase 22 runner static checks: PASS"
Write-Output "No EKS, HTTP, database, broker, or Secret mutation was performed."
