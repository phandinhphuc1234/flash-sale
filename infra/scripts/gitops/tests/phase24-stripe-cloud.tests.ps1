<#
.SYNOPSIS
  Static safety checks for the Phase 24 Stripe cloud smoke wrapper.

.DESCRIPTION
  No EKS, Stripe, HTTP, database, broker, or Secret mutation is performed. These checks protect
  the operator boundary and ensure the cloud continuation remains attached to the canonical Phase
  22 fixture flow.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$wrapper = Join-Path $repoRoot "infra\scripts\gitops\phase24-stripe-cloud.ps1"
$runner = Join-Path $repoRoot "infra\scripts\gitops\phase22-internal-e2e.ps1"
foreach ($path in @($wrapper, $runner)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required script not found: $path" }
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors) | Out-Null
  if ($errors.Count -gt 0) { throw "PowerShell parser failed: $path" }
}

$wrapperContent = Get-Content -LiteralPath $wrapper -Raw
$runnerContent = Get-Content -LiteralPath $runner -Raw
foreach ($marker in @(
  "-AllowPaymentEnabled",
  "-RunStripeCloudSmoke",
  "-StripeGatewayBaseUri",
  "-Run"
)) {
  if ($wrapperContent.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Wrapper marker is missing: $marker"
  }
}
foreach ($marker in @(
  "Get-StripeRuntimeSecrets",
  "STRIPE_WEBHOOK_SECRET",
  "Post-SignedStripeWebhook",
  "Get-KafkaTopicEndOffset",
  "Payment aggregate",
  "Webhook replay: PASS",
  "Kafka Payment event: PASS",
  "confirmed Order convergence",
  "confirmed reservation convergence",
  "Reservation finalization: PASS",
  "status=CONFIRMED",
  "Checkout URL",
  'Start-Process -FilePath $checkoutUrl'
)) {
  if ($runnerContent.IndexOf($marker, [StringComparison]::OrdinalIgnoreCase) -lt 0) {
    throw "Stripe cloud smoke marker is missing: $marker"
  }
}

foreach ($pattern in @(
  '(?i)Write-(?:Output|Host).*checkoutUrl',
  '(?i)Write-(?:Output|Host).*STRIPE_SECRET_KEY',
  '(?i)Write-(?:Output|Host).*STRIPE_WEBHOOK_SECRET',
  '(?i)Write-(?:Output|Host).*Stripe-Signature',
  '(?i)\b(?:psql|mysql|redis-cli|kafka-console-(?:producer|consumer))\b'
)) {
  if ($runnerContent -match $pattern) { throw "Forbidden sensitive/data-plane output or command: $pattern" }
}

Write-Output "Phase 24 Stripe cloud static checks: PASS"
Write-Output "No EKS, Stripe, HTTP, database, broker, or Secret mutation was performed."
