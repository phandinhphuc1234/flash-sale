<#
.SYNOPSIS
  Static contract for Phase 21 verification before and after Stripe enablement.

.DESCRIPTION
  Reads the tracked Phase 21 script only. It does not contact Kubernetes, AWS, Stripe, a database,
  Kafka, Schema Registry, Redis, or any Secret.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$phase21 = Join-Path $repoRoot "infra\scripts\gitops\phase21-cloud-release-verify.ps1"
if (-not (Test-Path -LiteralPath $phase21 -PathType Leaf)) {
  throw "Phase 21 cloud release verifier is missing: $phase21"
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($phase21, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
  throw "PowerShell parser failed for the Phase 21 cloud release verifier."
}

$content = Get-Content -LiteralPath $phase21 -Raw
foreach ($marker in @(
  '[ValidateSet("disabled", "enabled")]',
  '[string]$PaymentRuntimeState = "disabled"',
  '$expectedValue = if ($PaymentRuntimeState -eq "enabled") { "true" } else { "false" }',
  'Phase 21 expected the reviewed ''$PaymentRuntimeState'' state',
  'Payment flags: $($ExpectedPaymentFlags.Count)/$($ExpectedPaymentFlags.Count) $PaymentRuntimeState',
  'No Kubernetes or ECR state was changed'
)) {
  if ($content.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Phase 21 runtime-state marker is missing: $marker"
  }
}

Write-Output "Phase 21 Payment runtime-state static tests: PASS"
Write-Output "No Kubernetes, AWS, Stripe, database, Kafka, Registry, Redis, or Secret state was changed."
