<#
.SYNOPSIS
  Static safety contract for the Feature 044 rollback compatibility rehearsal.

.DESCRIPTION
  Reads tracked files only. It does not contact EKS, ECR, PostgreSQL, Kafka, Redis, Schema
  Registry, Stripe, or Kubernetes Secrets.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$rehearsal = Join-Path $repoRoot "infra\scripts\gitops\phase44-purchase-saga-rollback-rehearsal.ps1"
$orderMigration = Join-Path $repoRoot "services\order-service\src\main\resources\db\changelog\changes\002-add-purchase-saga.sql"
$flashSaleMigration = Join-Path $repoRoot "services\flashsale-service\src\main\resources\db\changelog\changes\002-add-reservation-finalization.sql"

foreach ($path in @($rehearsal, $orderMigration, $flashSaleMigration)) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
    throw "Required Feature 044 rollback rehearsal asset is missing: $path"
  }
}

$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($rehearsal, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count -gt 0) {
  throw "PowerShell parser failed for the Feature 044 rollback compatibility rehearsal."
}

$content = Get-Content -LiteralPath $rehearsal -Raw
foreach ($marker in @(
  "PriorReleaseSha",
  "exactly one scalar account ID",
  'enum\s+',
  '(?m)^\s*',
  '-split "\r?\n"',
  '-split "\|", 2',
  "release-",
  "flash-sale/order-service",
  "flash-sale/flash-sale-service",
  "OrderStatus.java",
  "ReservationStatus.java",
  "BEGIN TRANSACTION READ ONLY",
  "order_db",
  "flashsale_db",
  "purchase_sagas",
  "reservation_command_inbox",
  "reviewed per-service releases after targeted hotfix promotion",
  "Incompatible persisted terminal state",
  "return to spec/plan approval",
  "No Kubernetes Deployment, ConfigMap, Git, database, Kafka, Schema Registry, Redis, or ECR state was changed"
)) {
  if ($content.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Rollback compatibility rehearsal marker is missing: $marker"
  }
}

foreach ($forbidden in @(
  "kubectl apply",
  "kubectl delete",
  "kubectl patch",
  "kubectl rollout restart",
  "git revert",
  "liquibase rollback",
  "DROP TABLE",
  "DELETE FROM",
  "UPDATE ",
  "INSERT INTO"
)) {
  if ($content.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
    throw "Rollback compatibility rehearsal must not contain: $forbidden"
  }
}

$orderMigrationContent = Get-Content -LiteralPath $orderMigration -Raw
$flashSaleMigrationContent = Get-Content -LiteralPath $flashSaleMigration -Raw
foreach ($marker in @("purchase_sagas", "purchase_saga_inbox", "'CONFIRMED'", "'CANCELLED'", "'EXPIRED'")) {
  if ($orderMigrationContent.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Order migration marker is missing: $marker"
  }
}
foreach ($marker in @("reservation_command_inbox", "redis_reconciled_at", "finalized_at", "causation_id", "'CONFIRMED'", "'RELEASED'", "'EXPIRED'")) {
  if ($flashSaleMigrationContent.IndexOf($marker, [StringComparison]::Ordinal) -lt 0) {
    throw "Flash Sale migration marker is missing: $marker"
  }
}

Write-Output "Feature 044 rollback compatibility rehearsal static tests: PASS"
Write-Output "No EKS, ECR, database, Kafka, Redis, Schema Registry, Stripe, or Secret state was changed."
