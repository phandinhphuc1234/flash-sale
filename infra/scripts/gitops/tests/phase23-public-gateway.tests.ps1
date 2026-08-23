<#
.SYNOPSIS
  Static safety checks for the Phase 23 public Gateway helper and cloud patch.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$runner = Join-Path $repoRoot "infra\scripts\gitops\phase23-public-gateway.ps1"
$patch = Join-Path $repoRoot "infra\k8s\overlays\cloud\patches\api-gateway-public-service.yaml"
foreach ($path in @($runner, $patch)) {
  if (-not (Test-Path -LiteralPath $path)) { throw "Required Phase 23 file is missing: $path" }
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($runner, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) { throw "PowerShell parser failed: " + (($errors | ForEach-Object Message) -join "; ") }

$content = Get-Content -LiteralPath $runner -Raw
$patchContent = Get-Content -LiteralPath $patch -Raw
foreach ($marker in @(
  "--dry-run=client",
  "--validate=false",
  "service.beta.kubernetes.io/aws-load-balancer-scheme",
  "internet-facing",
  "LoadBalancer",
  "Synced",
  "Healthy",
  "api/v1/catalog/products",
  "api/v1/admin/catalog/products",
  "401",
  "403"
)) {
  if ($content.IndexOf($marker, [StringComparison]::Ordinal) -lt 0 -and
      $patchContent.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Required Phase 23 safety marker is missing: $marker"
  }
}
if ($content -match '(?i)kubectl\s+(apply|delete|patch)\s+(?!.*--dry-run=client)') {
  throw "Public Gateway helper contains a mutating kubectl command."
}
if ($content -match '(?i)(Authorization:|Bearer\s+|Read-Host.*password|\.env|secretRef)') {
  throw "Public Gateway helper must not read or print credentials."
}

Write-Output "Phase 23 public Gateway static checks: PASS"
Write-Output "No EKS, HTTP endpoint, or Kubernetes resource was changed."
