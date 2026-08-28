<#
.SYNOPSIS
  Static contract for the Gateway smoke port selection across bootstrap and HTTPS-only releases.

.DESCRIPTION
  Reads the tracked Phase 18 script only. It does not contact Kubernetes or any external system.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$phase18 = Join-Path $repoRoot "infra\scripts\gitops\phase18-gateway-smoke.ps1"
if (-not (Test-Path -LiteralPath $phase18 -PathType Leaf)) {
  throw "Phase 18 Gateway smoke script is missing: $phase18"
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($phase18, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) { throw "PowerShell parser failed for the Phase 18 Gateway smoke script." }

$content = Get-Content -LiteralPath $phase18 -Raw
foreach ($marker in @(
  "Resolve-GatewayServicePort",
  'foreach ($candidate in @(8080, 443))',
  'exposes neither the bootstrap port 8080 nor the Phase 24 port 443',
  'Gateway port-forward target: service port $GatewayServicePort (localhost only)',
  '("{0}:{1}" -f $LocalPort, $GatewayServicePort)',
  'Stop-Process -Id $PortForward.Id -Force'
)) {
  if ($content.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Phase 18 port-selection marker is missing: $marker"
  }
}

Write-Output "Phase 18 Gateway service-port selection static tests: PASS"
Write-Output "No Kubernetes, network, or Secret state was read or changed."
