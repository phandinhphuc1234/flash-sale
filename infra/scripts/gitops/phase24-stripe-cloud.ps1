<#
.SYNOPSIS
  Run the operator-controlled Phase 24 Stripe cloud smoke.

.DESCRIPTION
  This wrapper reuses the canonical Phase 22 fixture flow, but allows the seven Payment flags to
  be enabled and continues through hosted Checkout, the HTTPS Stripe webhook, duplicate receipt
  acknowledgement, Kafka outbox publication, and the final Order query. It never prints secrets,
  JWTs, Checkout URLs, Stripe identifiers, signatures, or raw webhook bodies.

  Validation mode performs only the cloud/Argo/flag checks. -Run prompts for the existing
  ROLE_ADMIN password and opens the hosted Stripe Checkout page in the default browser. Complete
  the test payment manually with a Stripe test card, then press Enter in the terminal.
#>
[CmdletBinding()]
param(
  [switch]$Run,
  [Parameter(Mandatory)][string]$AdminLogin,
  [ValidateRange(1024, 65535)]
  [int]$LocalPort = 28083,
  [ValidateRange(120, 1800)]
  [int]$TimeoutSeconds = 900,
  [ValidateRange(1, 100)]
  [int]$InventoryQuantity = 1,
  [string]$StripeGatewayBaseUri = "https://api.flashsale123.tech"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$phase22 = Join-Path $repoRoot "infra\scripts\gitops\phase22-internal-e2e.ps1"
if (-not (Test-Path -LiteralPath $phase22 -PathType Leaf)) {
  throw "Phase 22 canonical smoke script was not found."
}

$arguments = @(
  "-NoLogo", "-NoProfile", "-File", $phase22,
  "-AllowPaymentEnabled", "-RunStripeCloudSmoke",
  "-AdminLogin", $AdminLogin,
  "-LocalPort", [string]$LocalPort,
  "-TimeoutSeconds", [string]$TimeoutSeconds,
  "-InventoryQuantity", [string]$InventoryQuantity,
  "-StripeGatewayBaseUri", $StripeGatewayBaseUri
)
if ($Run) { $arguments += "-Run" }

& pwsh @arguments
if ($LASTEXITCODE -ne 0) {
  throw "Phase 24 Stripe cloud smoke failed with exit code $LASTEXITCODE."
}
