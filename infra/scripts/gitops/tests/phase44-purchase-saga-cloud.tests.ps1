<#
.SYNOPSIS
  Static cloud contract tests for Feature 044 topic names and runtime flags.

.DESCRIPTION
  Reads tracked manifests/scripts and renders Kustomize locally. It does not contact EKS, Kafka,
  Schema Registry, a database, Redis, Stripe, or any Secret.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$orderConfig = Join-Path $repoRoot "infra\k8s\overlays\cloud\config\order-service-runtime-config.yaml"
$flashSaleConfig = Join-Path $repoRoot "infra\k8s\overlays\cloud\config\flash-sale-service-runtime-config.yaml"
$phase20 = Join-Path $repoRoot "infra\scripts\gitops\phase20-kafka-contracts.ps1"
$schemaScript = Join-Path $repoRoot "infra\docker\schema-registry\register-purchase-saga-schemas.ps1"
$overlay = Join-Path $repoRoot "infra\k8s\overlays\cloud"

foreach ($path in @($orderConfig, $flashSaleConfig, $phase20, $schemaScript, $overlay)) {
  if (-not (Test-Path -LiteralPath $path)) { throw "Required Feature 044 cloud asset is missing: $path" }
}

$order = Get-Content -LiteralPath $orderConfig -Raw
$flashSale = Get-Content -LiteralPath $flashSaleConfig -Raw
$phase20Content = Get-Content -LiteralPath $phase20 -Raw

foreach ($marker in @(
  'ORDER_PAYMENT_COMMANDS_TOPIC: "flashsale.payment.commands.v1"',
  'ORDER_PAYMENT_EVENTS_TOPIC: "flashsale.payment.events.v1"',
  'ORDER_PAYMENT_EVENTS_DLT_TOPIC: "flashsale.order.payment-result.dlt.v1"',
  'ORDER_PURCHASE_COMMANDS_TOPIC: "flashsale.purchase.commands.v1"',
  'ORDER_PURCHASE_RESERVATION_RESULTS_DLT_TOPIC: "flashsale.order.purchase-reservation-result.dlt.v1"',
  'ORDER_PAYMENT_EVENTS_CONSUMER_ENABLED: "true"',
  'ORDER_PURCHASE_RESERVATION_RESULTS_CONSUMER_ENABLED: "true"'
)) {
  if ($order.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) { throw "Order cloud marker is missing: $marker" }
}

foreach ($marker in @(
  'FLASHSALE_PURCHASE_COMMANDS_TOPIC: "flashsale.purchase.commands.v1"',
  'FLASHSALE_RESERVATION_COMMANDS_DLT_TOPIC: "flashsale.flash-sale.purchase-command.dlt.v1"',
  'FLASHSALE_RESERVATION_COMMANDS_ENABLED: "true"',
  'FLASHSALE_RUNTIME_RECONCILIATION_ENABLED: "true"',
  'FLASHSALE_RUNTIME_RECONCILIATION_INTERVAL_MS: "2000"'
)) {
  if ($flashSale.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) { throw "Flash Sale cloud marker is missing: $marker" }
}

foreach ($marker in @(
  '"flashsale.purchase.commands.v1"',
  '"flashsale.order.payment-result.dlt.v1"',
  '"flashsale.flash-sale.purchase-command.dlt.v1"',
  '"flashsale.order.purchase-reservation-result.dlt.v1"',
  '"register-purchase-saga-schemas.ps1"',
  'topics=$($TopicContracts.Count)',
  'subjects=$($SubjectContracts.Count)'
)) {
  if ($phase20Content.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) { throw "Phase 20 marker is missing: $marker" }
}
if ($phase20Content -match 'flashsale\.order\.payment-events\.dlt\.v1') {
  throw "Deprecated Payment DLT name remains in Phase 20."
}

$rendered = & kubectl kustomize $overlay 2>&1
if ($LASTEXITCODE -ne 0) { throw "Cloud Kustomize render failed: $($rendered -join [Environment]::NewLine)" }
$renderedText = $rendered -join [Environment]::NewLine
foreach ($name in @(
  "ORDER_PAYMENT_EVENTS_CONSUMER_ENABLED",
  "ORDER_PURCHASE_RESERVATION_RESULTS_CONSUMER_ENABLED",
  "FLASHSALE_RESERVATION_COMMANDS_ENABLED",
  "FLASHSALE_RUNTIME_RECONCILIATION_ENABLED"
)) {
  if ($renderedText.IndexOf($name, [StringComparison]::Ordinal) -lt 0) { throw "Rendered cloud manifest omitted $name" }
}

Write-Output "Feature 044 cloud config contract tests: PASS"
Write-Output "No EKS, Kafka, Registry, database, Redis, Stripe, or Secret state was changed."
