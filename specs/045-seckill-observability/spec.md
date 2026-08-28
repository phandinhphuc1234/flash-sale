# Feature Specification: Seckill Observability Baseline

**Feature Branch**: `codex/phase25-seckill-observability`
**Created**: 2026-08-28
**Status**: Approved
**Approval Basis**: User approved a compact Phase 25 implementation focused on the seckill flow and explicitly deferred Slack and other advanced observability components.

## User Scenarios & Testing

### User Story 1 - Observe the seckill path (Priority: P1)

As an operator or internship reviewer, I can open one private Grafana dashboard and quickly see whether Flash Sale, Order, and Payment are healthy and whether the purchase flow is progressing.

**Why this priority**: The project needs a simple, demonstrable operational view of the core seckill business path.

**Independent Test**: Port-forward Grafana, open the Seckill Overview dashboard, and confirm the service health and Saga panels return data from Prometheus.

**Acceptance Scenarios**:

1. **Given** all eight application services are running, **When** Prometheus completes two scrape intervals, **Then** every application target is shown as healthy.
2. **Given** seckill traffic has been executed, **When** the dashboard is opened, **Then** Flash Sale, Order Saga, Payment, outbox, dead-letter, and reconciliation signals are visible in one place.

---

### User Story 2 - Detect a stalled or failed purchase flow (Priority: P2)

As an operator, I can inspect Prometheus rules for stalled Saga steps, dead-letter growth, reconciliation backlog, and outbox lag without adding an external notification service.

**Why this priority**: These are the most important failure modes in the durable seckill flow and are already represented by service metrics.

**Independent Test**: Load the Prometheus rules page or query its API and confirm all approved Flash Sale, Order, and Payment rules load without errors.

**Acceptance Scenarios**:

1. **Given** the monitoring stack is running, **When** Prometheus evaluates the checked-in rules, **Then** all rule groups are loaded and report a valid state.
2. **Given** a relevant metric crosses a rule threshold, **When** the rule evaluation interval passes, **Then** Prometheus exposes the rule as pending or firing even though external notifications are disabled.

---

### User Story 3 - Install and verify monitoring safely (Priority: P3)

As an operator, I can validate and deploy the monitoring stack through a bounded GitOps workflow without exposing Grafana or Prometheus publicly and without committing credentials.

**Why this priority**: The observability demo must remain reproducible and must not weaken the existing cloud security boundary.

**Independent Test**: Run the Phase 25 script first in validation-only mode and then with explicit apply; confirm Argo CD is healthy, both Deployments are available, and both Services are private ClusterIP resources.

**Acceptance Scenarios**:

1. **Given** no apply flag is supplied, **When** the script runs, **Then** it validates manifests and exits without changing Kubernetes state.
2. **Given** the Grafana Secret does not exist, **When** apply mode starts, **Then** the script asks for the password securely, creates the Secret without printing its value, and deploys through Argo CD.
3. **Given** monitoring is deployed, **When** its resources are inspected, **Then** neither Prometheus nor Grafana has a LoadBalancer or NodePort service.

## Edge Cases

- A service target is temporarily unavailable during rollout: Prometheus records the target as down without making application readiness depend on monitoring.
- Grafana or Prometheus restarts: the stack recreates from Git; short-lived metric history or local Grafana state may be lost in this demo baseline.
- A Prometheus rule references a metric not yet emitted: the rule remains valid and inactive rather than blocking deployment.
- The Grafana Secret already exists: repeated apply reuses it and does not ask for or reveal the password.
- Argo CD is unavailable or cannot reach the private repository: the script stops with a bounded error and does not mutate application Deployments.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST deploy one Prometheus replica and one Grafana replica in a dedicated `monitoring` namespace.
- **FR-002**: Prometheus MUST scrape `/actuator/prometheus` for the eight application services through stable Kubernetes DNS names.
- **FR-003**: The default scrape interval MUST be 15 seconds for an internship/demo feedback loop.
- **FR-004**: Grafana MUST provision a Prometheus datasource and a Seckill Overview dashboard from version-controlled files.
- **FR-005**: The dashboard MUST prioritize Flash Sale, Order Saga, Payment, outbox lag, dead-letter growth, and reconciliation backlog signals.
- **FR-006**: Prometheus MUST load the existing approved Flash Sale, Order, and Payment alert rules.
- **FR-007**: The baseline MUST NOT deploy Slack integration, Alertmanager, Loki, Tempo, or infrastructure exporters.
- **FR-008**: Prometheus and Grafana Services MUST remain `ClusterIP`; operator access MUST use `kubectl port-forward`.
- **FR-009**: Grafana administrator credentials MUST be supplied through a manually provisioned Kubernetes Secret and MUST NOT be committed or printed.
- **FR-010**: Monitoring desired state MUST be reconciled by a dedicated Argo CD Application targeting `develop`.
- **FR-011**: The operator script MUST default to validation-only mode and require explicit `-Apply` for live mutations.
- **FR-012**: The operator script MUST use bounded waits and verify Argo health, Deployment availability, private service types, Prometheus targets, and dashboard provisioning.
- **FR-013**: Monitoring MUST NOT modify Java business code, public HTTP contracts, Kafka contracts, database schemas, payment flags, or application readiness semantics.
- **FR-014**: The demo baseline MAY use ephemeral Prometheus and Grafana storage, MUST limit Prometheus retention, and MUST document that restart can discard metric history and local UI state.
- **FR-015**: Monitoring resource requests and limits MUST fit the existing three-node internship EKS cluster without adding nodes.

### Key Entities

- **Scrape Target**: One application Service DNS name, metrics path, labels, and current Prometheus health.
- **Dashboard**: A Git-provisioned Grafana view composed of panels for the seckill purchase path.
- **Rule Group**: A version-controlled set of Prometheus recording or alert rules evaluated from service metrics.
- **Monitoring Application**: The Argo CD desired-state owner for the `infra/monitoring` subtree.
- **Grafana Admin Secret**: A manually supplied credential boundary referenced by Grafana and never stored in Git.

## Success Criteria

### Measurable Outcomes

- **SC-001**: All eight application scrape targets report `UP` within two 15-second scrape intervals when their Deployments are healthy.
- **SC-002**: The Seckill Overview dashboard is provisioned and reachable through port-forward within five minutes of a healthy Argo reconciliation.
- **SC-003**: All checked-in Prometheus rule groups load without syntax or evaluation configuration errors.
- **SC-004**: Static and live validation confirm zero public monitoring Services and zero committed Secret values.
- **SC-005**: Prometheus and Grafana each run as one available replica within the approved resource budget.
- **SC-006**: Stopping monitoring does not make any application Deployment unready and does not interrupt the seckill purchase path.

## Assumptions

- The eight services continue to expose Spring Boot Actuator Prometheus endpoints as required by the repository constitution.
- Existing service metrics and alert-rule files provide enough signal for the first seckill dashboard; adding new Java metrics is deferred.
- The cloud environment is an internship/demo environment rather than a production environment.

## Out of Scope

- Slack, email, PagerDuty, Alertmanager, or any external alert delivery.
- Loki log aggregation, Tempo tracing, OpenTelemetry collectors, and public observability endpoints.
- Node Exporter, kube-state-metrics, Redis/PostgreSQL/Kafka exporters, or a full Kubernetes platform dashboard.
- Highly available monitoring, persistent monitoring storage, backups, SSO, TLS termination for Grafana, or a production retention policy.
- Changes to seckill business behavior or service-owned persistence.

## Human Decisions Required

No unresolved P0/P1 decisions remain. The user explicitly approved the compact internal Prometheus/Grafana baseline and deferred advanced components.

## Approval History

- 2026-08-28: User approved planning and implementation in one branch, focused on seckill and suitable for an internship demonstration.
