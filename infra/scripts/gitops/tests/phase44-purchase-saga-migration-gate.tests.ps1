<#
.SYNOPSIS
  Static safety contract for the Feature 044 cloud migration release gate.

.DESCRIPTION
  This script reads tracked release-gate assets only. It does not contact EKS, ECR, a database,
  Kafka, Redis, Schema Registry, Stripe, or Kubernetes Secrets.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$gate = Join-Path $repoRoot "infra\scripts\gitops\phase44-purchase-saga-migration-gate.ps1"
$overlay = Join-Path $repoRoot "infra\k8s\overlays\cloud-migrations\feature-044"
$orderJob = Join-Path $overlay "order-purchase-saga-migration-job.yaml"
$flashSaleJob = Join-Path $overlay "flash-sale-reservation-finalization-migration-job.yaml"
$deliveryWorkflow = Join-Path $repoRoot ".github\workflows\service-delivery.yml"

foreach ($path in @($gate, $overlay, $orderJob, $flashSaleJob, $deliveryWorkflow)) {
  if (-not (Test-Path -LiteralPath $path)) {
    throw "Required Feature 044 migration release-gate asset is missing: $path"
  }
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($gate, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
  throw "PowerShell parser failed for the Feature 044 migration release gate."
}

$gateContent = Get-Content -LiteralPath $gate -Raw
foreach ($marker in @(
  "ReleaseSha",
  "release-",
  "ls-remote",
  "flash-sale/order-service",
  "flash-sale/flash-sale-service",
  "migrate-order-purchase-saga",
  "migrate-flash-sale-reservation-finalization",
  "--dry-run=client",
  "-Apply",
  "Secret values were not read or printed"
)) {
  if ($gateContent.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Migration release-gate marker is missing: $marker"
  }
}

foreach ($forbidden in @(
  "migrate-authentication-service",
  "migrate-product-service",
  "migrate-campaign-service",
  "migrate-inventory-service",
  "migrate-payment-service",
  "kubectl delete",
  "--force"
)) {
  if ($gateContent.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
    throw "Feature 044 migration release gate must not contain: $forbidden"
  }
}

$rendered = & kubectl kustomize $overlay 2>&1
if ($LASTEXITCODE -ne 0) {
  throw "Feature 044 migration overlay render failed: $($rendered -join [Environment]::NewLine)"
}
$renderedText = $rendered -join [Environment]::NewLine
foreach ($marker in @(
  "name: migrate-order-purchase-saga",
  "name: migrate-flash-sale-reservation-finalization",
  "image: order-service",
  "image: flash-sale-service",
  "app.kubernetes.io/part-of: flash-sale"
)) {
  if ($renderedText.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Feature 044 migration overlay marker is missing: $marker"
  }
}
if (@($rendered | Where-Object { $_ -eq "kind: Job" }).Count -ne 2) {
  throw "Feature 044 migration overlay must render exactly two Jobs."
}

$workflowContent = Get-Content -LiteralPath $deliveryWorkflow -Raw
foreach ($marker in @(
  "requires_feature_044_migration_gate",
  "phase44-purchase-saga-migration-gate.ps1",
  "Feature 044 migration release gate",
  "--body-file"
)) {
  if ($workflowContent.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Service delivery workflow is missing the Feature 044 migration-gate handoff: $marker"
  }
}

Write-Output "Feature 044 migration release-gate static tests: PASS"
Write-Output "No EKS, ECR, database, Kafka, Redis, Schema Registry, Stripe, or Secret state was changed."
