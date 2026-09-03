<#
.SYNOPSIS
  Secure convenience wrapper for the Phase 22 authenticated internal E2E.

.DESCRIPTION
  Prompts once for the existing ROLE_ADMIN password as a SecureString, invokes the
  canonical runner in the same PowerShell process, and disposes the password object
  in a finally block. The password is never written to a file, passed as plaintext,
  printed, or placed in shell history.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)][string]$AdminLogin,
  [ValidateRange(1024, 65535)]
  [int]$LocalPort = 28082,
  [ValidateRange(60, 1800)]
  [int]$TimeoutSeconds = 600,
  [ValidateRange(1, 100000)]
  [int]$InventoryQuantity = 1,
  [ValidateRange(1, 100)]
  [int]$ReservationQuantity = 1,
  [ValidateRange(5, 60)]
  [int]$CampaignDurationMinutes = 5,
  [switch]$FixtureOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($PSVersionTable.PSVersion.Major -lt 7) {
  throw "Phase 22 requires PowerShell 7 or newer."
}

$runner = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")) `
  "infra\scripts\gitops\phase22-internal-e2e.ps1"
if (-not (Test-Path -LiteralPath $runner)) {
  throw "Canonical Phase 22 runner not found: $runner"
}

$securePassword = Read-Host "Existing ROLE_ADMIN password (memory only)" -AsSecureString
try {
  & $runner `
    -Run `
    -AdminLogin $AdminLogin `
    -AdminPassword $securePassword `
    -LocalPort $LocalPort `
    -TimeoutSeconds $TimeoutSeconds `
    -InventoryQuantity $InventoryQuantity `
    -ReservationQuantity $ReservationQuantity `
    -CampaignDurationMinutes $CampaignDurationMinutes `
    -FixtureOnly:$FixtureOnly
} finally {
  if ($null -ne $securePassword) {
    $securePassword.Dispose()
    $securePassword = $null
  }
}
