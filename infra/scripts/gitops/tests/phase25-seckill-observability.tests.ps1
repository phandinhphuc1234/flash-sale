<#
.SYNOPSIS
  Static contract checks for the compact Phase 25 Prometheus/Grafana stack.

.DESCRIPTION
  This test reads repository files and renders Kustomize locally. It never calls kubectl apply and
  never reads Kubernetes Secrets. Failures describe the desired-state contract that was violated.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$monitoringPath = Join-Path $repoRoot "infra\monitoring"
$argoPath = Join-Path $repoRoot "infra\k8s\argocd\observability"
$dashboardPath = Join-Path $monitoringPath "grafana\dashboards\seckill-overview.json"
$prometheusConfigPath = Join-Path $monitoringPath "prometheus\prometheus.yml"
$runnerPath = Join-Path $repoRoot "infra\scripts\gitops\phase25-seckill-observability.ps1"

function Assert-True([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw "Phase 25 static test failed: $Message" }
}

foreach ($path in @($dashboardPath, $prometheusConfigPath, $runnerPath,
    (Join-Path $argoPath "application-observability.yaml"))) {
  Assert-True (Test-Path -LiteralPath $path -PathType Leaf) "missing required file $path"
}

$rendered = (& kubectl kustomize $monitoringPath 2>&1) -join "`n"
Assert-True ($LASTEXITCODE -eq 0) "kubectl kustomize failed: $rendered"

Assert-True ($rendered -match 'image: prom/prometheus:v3\.14\.0') "Prometheus image is not pinned"
Assert-True ($rendered -match 'image: grafana/grafana:13\.1\.3') "Grafana image is not pinned"
Assert-True (($rendered | Select-String -Pattern 'type: ClusterIP' -AllMatches).Matches.Count -eq 2) "expected exactly two private ClusterIP Services"
Assert-True ($rendered -notmatch 'type: (LoadBalancer|NodePort)') "monitoring must not be public"
Assert-True ($rendered -notmatch '(?im)^kind: (StatefulSet|PersistentVolumeClaim|Ingress)$') "demo baseline must remain ephemeral and internal"
Assert-True ($rendered -notmatch '(?im)^\s*name: (alertmanager|loki|tempo|node-exporter|kube-state-metrics)\s*$') "a deferred component entered the rendered stack"
Assert-True ($rendered -match 'name: GF_SECURITY_ADMIN_PASSWORD') "Grafana password Secret reference is missing"
Assert-True ($rendered -notmatch '(?i)admin-password:\s+[^\s]') "a Grafana password value appears in desired state"

$prometheusConfig = Get-Content -LiteralPath $prometheusConfigPath -Raw
$expectedTargets = @{
  "api-gateway" = "api-gateway.flash-sale.svc.cluster.local:443"
  "authentication-service" = "authentication-service.flash-sale.svc.cluster.local:8080"
  "product-service" = "product-service.flash-sale.svc.cluster.local:8080"
  "campaign-service" = "campaign-service.flash-sale.svc.cluster.local:8080"
  "flash-sale-service" = "flash-sale-service.flash-sale.svc.cluster.local:8080"
  "inventory-service" = "inventory-service.flash-sale.svc.cluster.local:8080"
  "order-service" = "order-service.flash-sale.svc.cluster.local:8080"
  "payment-service" = "payment-service.flash-sale.svc.cluster.local:8080"
  "cart-service" = "cart-service.flash-sale.svc.cluster.local:8080"
}
foreach ($service in $expectedTargets.Keys) {
  Assert-True ($prometheusConfig -match [regex]::Escape($expectedTargets[$service])) "missing target $service"
}
Assert-True (($prometheusConfig | Select-String -Pattern 'job_name:' -AllMatches).Matches.Count -eq 9) "expected exactly nine scrape jobs"
Assert-True ($prometheusConfig -match 'scrape_interval:\s*15s') "scrape interval must be 15 seconds"
Assert-True ($prometheusConfig -match 'metrics_path:\s*/actuator/prometheus') "Actuator Prometheus path is missing"

try { $dashboard = Get-Content -LiteralPath $dashboardPath -Raw | ConvertFrom-Json } catch {
  throw "Phase 25 static test failed: dashboard JSON is invalid: $($_.Exception.Message)"
}
Assert-True ($dashboard.uid -eq "seckill-overview") "dashboard UID changed"
Assert-True ($dashboard.title -eq "Flash Sale - Seckill Overview") "dashboard title changed"
Assert-True ($dashboard.panels.Count -ge 8) "dashboard needs at least eight focused panels"
$dashboardText = Get-Content -LiteralPath $dashboardPath -Raw
foreach ($metric in @("order_saga_state", "flashsale_redis_reconciliation_backlog",
    "payment_outbox_lag_seconds", "order_saga_dlt_publication_total")) {
  Assert-True ($dashboardText.Contains($metric)) "dashboard does not query $metric"
}

$argoText = Get-Content -LiteralPath (Join-Path $argoPath "application-observability.yaml") -Raw
Assert-True ($argoText -match 'name:\s*flash-sale-observability') "Argo Application name is wrong"
Assert-True ($argoText -match 'targetRevision:\s*develop') "Argo must target develop"
Assert-True ($argoText -match 'path:\s*infra/monitoring') "Argo source ownership is wrong"
Assert-True ($argoText -match 'prune:\s*true') "Argo must prune stale hashed monitoring ConfigMaps"
$namespaceText = Get-Content -LiteralPath (Join-Path $monitoringPath "namespace.yaml") -Raw
Assert-True ($namespaceText -match 'argocd\.argoproj\.io/sync-options:\s*Prune=false') "monitoring Namespace must be protected from prune"

$runnerText = Get-Content -LiteralPath $runnerPath -Raw
Assert-True ($runnerText -match '\[switch\]\$Apply') "runner must require explicit Apply"
Assert-True ($runnerText -match 'Read-Host.+-AsSecureString') "runner must read the password securely"
Assert-True ($runnerText -match 'Validation-only mode') "runner must document no-mutation default"
Assert-True ($runnerText -notmatch 'jsonpath=\{range') "runner must parse Service JSON instead of shell-sensitive multiline JSONPath"
Assert-True ($runnerText -notmatch '(?i)whsec_|sk_(test|live)_|password\s*=\s*["''][^"'']+["'']') "runner contains a credential-like literal"

Write-Output "PHASE_25_STATIC=PASS"
Write-Output "Targets: 9 application Services; monitoring Services: 2 ClusterIP."
Write-Output "Deferred components absent; Secret values absent."
