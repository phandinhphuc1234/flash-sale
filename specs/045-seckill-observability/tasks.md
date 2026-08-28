# Tasks: Seckill Observability Baseline

**Input**: Approved design artifacts under `specs/045-seckill-observability/`
**Delivery rule**: One branch and one PR. Live apply occurs only after merge to `develop` because the
Argo Application targets `develop`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes independent files.
- **[Story]**: Maps the task to an approved user story.
- Checked tasks require validation evidence; a checkbox alone is not completion evidence.

## Phase 1: Monitoring configuration — G1

**Purpose**: Create a private, resource-bounded Prometheus/Grafana desired state.

- [x] T001 [P] [US1] Add the `monitoring` Namespace, Prometheus Deployment/Service, pinned image, probes, resources, ephemeral retention, and private ClusterIP under `infra/monitoring/`
- [x] T002 [P] [US1] Add Prometheus configuration for exactly eight Kubernetes DNS targets at `/actuator/prometheus` with a 15-second interval
- [x] T003 [P] [US2] Wire the existing purchase-Saga and Payment rule files into the Prometheus rule loader without Alertmanager
- [x] T004 [P] [US1] Add the Grafana Deployment/Service, pinned image, probes, resources, ephemeral storage, private ClusterIP, and `grafana-admin` Secret references
- [x] T005 [P] [US1] Add the Git-provisioned Prometheus datasource, dashboard provider, and Seckill Overview dashboard focused on Flash Sale, Order Saga, Payment, outbox, DLT, and Redis reconciliation
- [x] T006 [US1] Add `infra/monitoring/kustomization.yaml`, generate deterministic ConfigMaps, render the complete stack, and validate it with client-side dry-run

**Checkpoint**: The monitoring subtree renders two private Deployments, two private Services, no
Secret values, and no unsupported observability component.

---

## Phase 2: GitOps and safe operations — G2

**Purpose**: Reconcile and verify monitoring without coupling it to application ownership.

- [x] T007 [P] [US3] Add the isolated `flash-sale-observability` Argo Application under `infra/k8s/argocd/observability/` with source `develop`, path `infra/monitoring`, self-heal enabled, and prune disabled
- [x] T008 [US3] Add `infra/scripts/gitops/phase25-seckill-observability.ps1` with PowerShell 7 validation-only default, context/merge/manifest gates, secure Grafana Secret provisioning, bounded Argo/rollout waits, target/rule/dashboard verification, and redacted output
- [x] T009 [P] [US3] Add `infra/scripts/gitops/tests/phase25-seckill-observability.tests.ps1` to reject public Services, floating images, forbidden components, missing targets/panels/rules, unbounded waits, or unsafe Secret handling
- [x] T010 [US3] Update `infra/monitoring/README.md` and the quickstart with architecture, access, troubleshooting, known ephemeral-storage trade-offs, and rollback boundaries

**Checkpoint**: Validation-only execution is green and cannot mutate live Kubernetes state.

---

## Phase 3: Validation and handoff — G3

**Purpose**: Prove the repository artifact is ready for one reviewed merge and a later live apply.

- [x] T011 Run the PowerShell static suite, parser checks, dashboard JSON parsing, Kustomize render, Kubernetes client-side dry-run, and `git diff --check`
- [x] T012 Run the Phase 25 script in validation-only mode and confirm it reports eight targets, private Services, pinned images, and no live mutation
- [x] T013 Run `./mvnw -pl services/flashsale-service,services/order-service,services/payment-service -am -DskipTests compile` only as a regression boundary confirming no Java source/dependency breakage
- [x] T014 Update `specs/045-seckill-observability/validation.md` with sanitized commands/results and check completed tasks
- [x] T015 Prepare the one-PR handoff with test summary, architecture flow, manual post-merge `-Apply` step, and commit message

**Checkpoint**: Source PR is ready. Live Argo/target/dashboard evidence remains explicitly pending
until the PR is merged to `develop`.

## Dependencies

```text
G1 configuration
      ↓
G2 GitOps + safe runner
      ↓
G3 static/source validation
      ↓
merge to develop
      ↓
manual -Apply + live evidence
```

## Deferred follow-up

Slack/Alertmanager, Loki, Tempo, exporters, persistent monitoring volumes, public access, SSO, HA,
and production retention remain outside Feature 045.
