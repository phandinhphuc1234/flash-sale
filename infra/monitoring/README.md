# Seckill Observability Baseline

This directory is the Git-owned desired state for the internal Phase 25 monitoring stack.

```text
9 Spring Boot Services --/actuator/prometheus--> Prometheus --> Grafana
                                                      |
                                                      +--> seckill/Saga rules
```

The dashboard focuses on Flash Sale reservation, Redis reconciliation, Order Purchase Saga,
Payment, outbox lag, and DLT growth. Prometheus and Grafana are single-replica `ClusterIP` services.
They are not exposed through the public Gateway or an AWS LoadBalancer.

## Deliberately deferred

- Slack, Alertmanager, email, and PagerDuty
- Loki, Tempo, and OpenTelemetry collectors
- node, Kubernetes, Kafka, Redis, and PostgreSQL exporters
- persistent volumes, HA, SSO, and production retention

Prometheus keeps at most three days/2 GiB in `emptyDir`; Grafana state is also disposable. The
datasource, rules, and dashboard are restored from Git after restart.

## Validate and deploy

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase25-seckill-observability.ps1
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase25-seckill-observability.ps1 -Apply
```

The first command is validation-only. The second must be run from merged `develop`; it creates the
Grafana administrator Secret through a secure prompt when absent and applies the isolated Argo CD
Application.

## Open Grafana

```powershell
kubectl -n monitoring port-forward service/grafana 3000:3000
```

Open `http://127.0.0.1:3000`. Local dashboard edits are intentionally not durable; change the JSON
in Git instead.

## Troubleshooting

```powershell
kubectl -n argocd get application flash-sale-observability
kubectl -n monitoring get pods,svc
kubectl -n monitoring logs deployment/prometheus --tail=200
kubectl -n monitoring logs deployment/grafana --tail=200
```

If a Prometheus target is down, first check its application Deployment and metrics endpoint from
inside the cluster. Monitoring is not part of application readiness, so do not restart healthy
application services only because the dashboard is unavailable.

## Rollback boundary

Revert the monitoring commit or remove only the `flash-sale-observability` Application. Never delete
`flash-sale-cloud` as part of monitoring rollback. No business database, Kafka topic, Redis stock,
or application Secret belongs to this subtree.
