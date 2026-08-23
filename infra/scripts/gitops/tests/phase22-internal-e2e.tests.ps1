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
$wrapper = Join-Path $repoRoot "infra\scripts\gitops\phase22-internal-e2e-memory.ps1"
if (-not (Test-Path -LiteralPath $runner)) { throw "Runner not found: $runner" }
if (-not (Test-Path -LiteralPath $wrapper)) { throw "Secure wrapper not found: $wrapper" }

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runner, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
  throw ("PowerShell parser failed: " + (($errors | ForEach-Object Message) -join "; "))
}

$content = Get-Content -LiteralPath $runner -Raw
$wrapperContent = Get-Content -LiteralPath $wrapper -Raw
$requiredMarkers = @(
  'Read-Host "Existing ROLE_ADMIN password" -AsSecureString',
  '[Security.SecureString]$AdminPassword',
  'SkipHeaderValidation',
  'variants = @(@{ id = $null',
  '$CampaignPrice -ge $ProductBasePrice',
  'campaignPrice = $CampaignPrice',
  'Write-Host "Campaign fixture: PASS',
  'Product composition detail',
  'deployment/inventory-service',
  'INVENTORY_FIXTURE_ENABLED',
  'Inventory fixture status',
  'Inventory fixture Job failed',
  'Get-BoundedText $logs 5000',
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

foreach ($marker in @(
  'Read-Host "Existing ROLE_ADMIN password (memory only)" -AsSecureString',
  '-AdminPassword $securePassword',
  '$securePassword.Dispose()'
)) {
  if ($wrapperContent.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Secure wrapper marker is missing: $marker"
  }
}

if ($wrapperContent -match '(?i)(password\s*=\s*"|ConvertTo-SecureString\s+-String)') {
  throw "Secure wrapper contains a plaintext password assignment."
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
