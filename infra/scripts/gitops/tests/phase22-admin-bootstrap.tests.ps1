<#
.SYNOPSIS
  Static safety checks for the one-time Authentication admin bootstrap launcher.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$runner = Join-Path $repoRoot "infra\scripts\gitops\phase22-admin-bootstrap.ps1"
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) { throw "Launcher not found: $runner" }

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runner, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
  throw ("PowerShell parser failed: " + (($errors | ForEach-Object Message) -join "; "))
}

$content = Get-Content -LiteralPath $runner -Raw
foreach ($marker in @(
    'Read-Host "Admin bootstrap password" -AsSecureString',
    'AUTH_ADMIN_BOOTSTRAP_ENABLED',
    'SPRING_MAIN_WEB_APPLICATION_TYPE',
    'authentication-admin-bootstrap',
    'secretKeyRef',
    'delete secret',
    '--dry-run=client'
  )) {
  if ($content.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw "Required bootstrap safety marker is missing: $marker"
  }
}

foreach ($forbidden in @(
    '(?i)\b(?:psql|mysql|sqlcmd|Invoke-Sqlcmd)\b',
    '(?i)kubectl\s+exec',
    '(?i)AUTH_ADMIN_BOOTSTRAP_PASSWORD\s*=\s*[^\r\n]+\s*(?:#|$)'
  )) {
  if ($content -match $forbidden) { throw "Forbidden direct or hard-coded credential pattern found: $forbidden" }
}

Write-Output "Phase 22 admin bootstrap static checks: PASS"
Write-Output "No EKS, database, or Secret mutation was performed."
