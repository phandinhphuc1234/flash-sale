# Research: Seckill Observability Baseline

## Decision 1 — Use a small manifest-based stack

**Decision**: Deploy plain Prometheus and Grafana Deployments with Kustomize.

**Rationale**: The cluster is an internship/demo environment with one replica per service. The
Prometheus Operator, Helm, CRDs, kube-state-metrics, and exporters add concepts and permissions that
do not improve the first seckill dashboard.

**Alternatives considered**: `kube-prometheus-stack`, managed monitoring, and an OpenTelemetry
collector were deferred because each expands operations, permissions, or cost beyond the approved
metrics-only scope.

## Decision 2 — Pin supported container versions

**Decision**: Use `prom/prometheus:v3.14.0` and `grafana/grafana:13.1.3`.

**Rationale**: Both are current stable upstream releases at planning time. Pinned image tags make
GitOps reconciliation repeatable and avoid `latest` drift.

**Sources**: [Prometheus releases](https://github.com/prometheus/prometheus/releases) and
[Grafana releases](https://github.com/grafana/grafana/releases).

## Decision 3 — Static service targets

**Decision**: Configure exactly eight targets using `<service>.flash-sale.svc.cluster.local:8080`.

**Rationale**: Kubernetes DNS is the repository-approved discovery mechanism. Static targets are
easy to explain, need no cluster-wide RBAC, and match the current single-replica model.

## Decision 4 — Internal access only

**Decision**: Both Services remain ClusterIP; use port-forward for the UI.

**Rationale**: Monitoring contains operational information and does not need public DNS,
certificates, or a paid LoadBalancer for the internship demo.

## Decision 5 — Ephemeral storage

**Decision**: Use `emptyDir`, Prometheus `--storage.tsdb.retention.time=3d`, and
`--storage.tsdb.retention.size=2GB`.

**Rationale**: Durable metrics are not required by this demo. Git remains the durable source for
configuration and dashboards. Pod rescheduling may erase recent history and UI-local state.

## Decision 6 — Reuse existing metrics and rules

**Decision**: Use service health, HTTP metrics, and the existing bounded Flash Sale, Order Saga, and
Payment outbox/recovery metrics. No Java instrumentation is added.

**Rationale**: These signals directly represent the durable seckill flow and already avoid business
identifiers in labels.

## Decision 7 — Rules without external notification

**Decision**: Load rules into Prometheus but do not deploy Alertmanager or Slack integration.

**Rationale**: The Prometheus rules UI and Grafana panels are enough to demonstrate detection.
Notification routing can become a later feature when an on-call channel is actually required.
