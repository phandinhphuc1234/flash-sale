# Data Model: Seckill Observability Baseline

This feature owns configuration state only. It adds no relational tables, Kafka records, or domain
aggregates.

## ScrapeTarget

| Field | Type | Rules |
|---|---|---|
| job | string | One of the eight approved application service names |
| address | Kubernetes DNS + port | Gateway uses `:443`; the other seven Services use `:8080` |
| metricsPath | string | Always `/actuator/prometheus` |
| interval | duration | 15 seconds |
| health | runtime enum | `up`, `down`, or `unknown`; observed by Prometheus |

## DashboardDefinition

| Field | Type | Rules |
|---|---|---|
| uid | string | Stable `seckill-overview` |
| title | string | `Flash Sale - Seckill Overview` |
| datasource | string | Provisioned Prometheus UID |
| panels | list | Health, request, Flash Sale, Order Saga, Payment, and rule state |
| editable | boolean | False; Git owns the dashboard |

## RuleGroup

| Field | Type | Rules |
|---|---|---|
| name | string | `purchase-saga` or `payment-service` |
| rules | list | Existing bounded rule definitions |
| evaluationInterval | duration | Global 15s or explicit 30s |
| delivery | enum | `ui_only`; no Alertmanager receiver |

## MonitoringApplication

| Field | Type | Rules |
|---|---|---|
| name | string | `flash-sale-observability` |
| sourceRevision | string | `develop` |
| sourcePath | string | `infra/monitoring` |
| destinationNamespace | string | `monitoring` |
| selfHeal | boolean | True |
| prune | boolean | False |

## GrafanaAdminSecret

| Field | Type | Rules |
|---|---|---|
| name | string | `grafana-admin` |
| namespace | string | `monitoring` |
| adminUser | secret string | Defaults to `admin`; never printed |
| adminPassword | secret string | Entered securely; never committed or printed |

## Lifecycle

```text
Git config -> Argo Application -> Kustomize resources -> Prometheus scrapes services
                                                   -> Grafana reads Prometheus
                                                   -> Operator uses port-forward
```

Monitoring state is independent from Order, Payment, reservation, stock, and Kafka state. Removing
it requires no business-data migration.
