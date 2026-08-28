# Validation Ledger: Seckill Observability Baseline

Do not record Grafana passwords, Kubernetes Secret values, authorization headers, provider payloads,
JWTs, Checkout URLs, or public infrastructure credentials here.

## Planning approval — 2026-08-28

| Check | Result | Evidence / boundary |
|---|---|---|
| Scope approval | PASS | User approved a compact Prometheus/Grafana Phase 25 focused on seckill and deferred Slack and advanced components. |
| Business behavior boundary | PASS | Spec and plan introduce no Java, database, Kafka, payment flag, or public API changes. |
| Secret boundary | PASS | Grafana credentials are manual Kubernetes Secret input and are excluded from Git/evidence. |

## G1 — Monitoring desired state — 2026-08-28

| Check | Result | Evidence / boundary |
|---|---|---|
| Static contract suite | PASS | `pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\tests\phase25-seckill-observability.tests.ps1` emitted `PHASE_25_STATIC=PASS`; 8 targets, 2 ClusterIP Services, no deferred component, and no Secret value. |
| Dashboard JSON | PASS | PowerShell `ConvertFrom-Json` parsed UID `seckill-overview`, title `Flash Sale - Seckill Overview`, and 8 focused panels. |
| Prometheus configuration and rules | PASS | `promtool check config /etc/prometheus/prometheus.yml` in `prom/prometheus:v3.14.0` found two rule files; config, 3 Payment rules, and 8 purchase-Saga rules all succeeded. |
| Grafana image | PASS | `docker manifest inspect grafana/grafana:13.1.3` exited 0; no floating tag is used. |
| Kubernetes render | PASS | `kubectl kustomize infra/monitoring` rendered Namespace, five hashed ConfigMaps, two ClusterIP Services, and two Deployments. |
| Kubernetes dry-run | PASS | Client-side dry-run passed for `infra/monitoring` and `infra/k8s/argocd/observability`. No live object was changed. |

## G2 — Safe operations and source handoff — 2026-08-28

| Check | Result | Evidence / boundary |
|---|---|---|
| Phase 25 validation-only runner | PASS | Runner repeated static and dry-run gates, confirmed EKS context `flash-sale-dev`, reported pinned/private resources, and explicitly reported no Kubernetes resource or Secret change. |
| Script parsers | PASS | PowerShell parser returned zero syntax errors for the runner and static test. |
| Seckill service regression boundary | PASS | `mvnw.cmd --batch-mode --no-transfer-progress -pl services/flashsale-service,services/order-service,services/payment-service -am -DskipTests compile`: 6 reactor modules, `BUILD SUCCESS`, 0 compilation failures. |
| Whitespace hygiene | PASS | `git diff --check` exited 0; only local LF/CRLF advisory warnings were emitted by Git status. |
| Live apply boundary | PENDING AFTER MERGE | Argo reconciliation, secure Secret creation, 8/8 live targets, loaded live rules, and Grafana health are intentionally deferred until this reviewed branch is merged to `develop`. |

No Java production source, database migration, Kafka topic/schema, Payment flag, ECR image, or
application Deployment was changed by Feature 045 source validation.
