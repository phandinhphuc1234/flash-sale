# Feature Specification: GitOps Stateful Product Pilot

**Feature Branch**: `codex/gitops-phase9-stateful-pilot`
**Created**: 2026-08-21
**Status**: Approved for implementation
**Input**: User request: "Phase 9 — chuẩn hóa Product pilot và storage thành desired state GitOps."

## Problem and Scope

### Problem Statement

Phase 8 proved that one Product Service can run on EKS, but the storage add-on, PostgreSQL
StatefulSet, and Product deployment were created manually. That leaves the cluster state different
from Git and makes the pilot difficult to reproduce or review.

### In Scope

- Manage the existing EBS CSI add-on and its least-scoped AWS managed policy attachment through the
  existing Terraform root module.
- Adopt the already-created EBS CSI IAM role and add-on without replacement or destruction.
- Add a small `dev-pilot` Kustomize overlay containing only Product Service and its single training
  PostgreSQL instance.
- Keep credentials external to Git through name-only Kubernetes Secret references.
- Provide a reusable Phase 9 PowerShell validation/apply script and record operational evidence.

### Out of Scope

- Argo CD installation or Application resources.
- Redis, Kafka, Schema Registry, ingress, public load balancers, DNS, TLS, or the other seven
  application services.
- Production PostgreSQL high availability, backups, replicas, autoscaling, or managed RDS migration.
- Secret values, `.env` files, Kubernetes Secret manifests, or secret-manager installation.
- Changes to Java services, APIs, Kafka contracts, or database migrations.

### Non-goals

- This is a reproducible internship/development pilot, not a production database architecture.
- The pilot does not make the full `dev` overlay safe to apply; the pilot overlay remains the only
  live application overlay in this phase.

## User Scenarios & Testing

### User Story 1 - Reproducible EBS storage ownership (Priority: P1)

As an infrastructure operator, I want Terraform to describe the EBS CSI integration already used
by the cluster, so that storage prerequisites can be reviewed and recreated without hidden manual IAM
or add-on state.

**Why this priority**: Persistent workloads cannot be safely reconciled while their storage driver
is unmanaged.
**Independent Test**: Import the existing role/add-on, run Terraform plan, and observe zero destroy
or replacement actions.

#### Acceptance Scenarios

1. **Given** the existing EBS CSI role and add-on, **when** their Terraform identities are imported,
   **then** a refreshed plan manages them without proposing replacement or deletion.
2. **Given** the AWS policy attachment, **when** the plan is reviewed, **then** it uses the AWS
   managed `AmazonEBSCSIDriverPolicyV2` ARN and no broad custom policy is introduced.

### User Story 2 - Reproducible Product pilot (Priority: P1)

As a developer, I want one small Kustomize overlay for Product Service and PostgreSQL, so that I can
deploy the tested pilot from Git without accidentally starting all eight services.

**Why this priority**: It converts the successful manual smoke test into a reviewable desired state.
**Independent Test**: Render and client-side dry-run the pilot overlay, then apply it only after the
  externally managed secrets exist.

#### Acceptance Scenarios

1. **Given** the pilot overlay, **when** it is rendered, **then** it contains only one PostgreSQL
   StatefulSet, one PostgreSQL headless Service, one Product Deployment, one Product ClusterIP
   Service, one Namespace, and one non-secret ConfigMap.
2. **Given** an EBS-backed PostgreSQL volume, **when** PostgreSQL starts, **then** it initializes
   under a child directory and does not fail because of the filesystem `lost+found` directory.
3. **Given** a Product image tag, **when** the overlay is applied, **then** the deployment uses that
   immutable ECR tag and reaches Ready only after the database readiness check passes.

### User Story 3 - Safe operator workflow (Priority: P2)

As the project operator, I want a script that defaults to validation and requires an explicit apply
  switch, so that repeating Phase 9 does not silently mutate AWS or Kubernetes.

**Why this priority**: The manual workflow must be repeatable without making accidental changes.
**Independent Test**: Run the script without `-Apply` and verify that it performs no live mutation;
  run with `-Apply` only after the required external secrets and image are confirmed.

#### Acceptance Scenarios

1. **Given** no `-Apply`, **when** the script runs, **then** it renders and dry-runs only.
2. **Given** `-Apply` and an explicit image, **when** the pilot is applied, **then** the script waits
   for the PostgreSQL PVC/StatefulSet and Product Deployment and prints their final status.
3. **Given** a missing external Secret, **when** validation runs, **then** the script reports the
   missing name and never prints or creates secret values.

### Edge Cases

- A Terraform plan that proposes replacement or destruction is a hard stop; no apply is allowed.
- An existing manually-created role or add-on must be imported before Terraform apply.
- A bound PVC must never be deleted by the Phase 9 script.
- The full `infra/k8s/overlays/dev` remains source-only and is not applied by this feature.
- An Alpine PostgreSQL locale warning is non-blocking when the server becomes Ready; init failure is
  blocking.

## Requirements

### Functional Requirements

- **FR-001**: Terraform MUST describe the EKS EBS CSI add-on and its service-account IAM role.
- **FR-002**: Terraform MUST attach only `arn:aws:iam::aws:policy/AmazonEBSCSIDriverPolicyV2` to
  the EBS CSI role.
- **FR-003**: The implementation MUST provide an import procedure for the existing EBS CSI role and
  add-on before the first Terraform apply.
- **FR-004**: The `dev-pilot` overlay MUST contain exactly six resources: one Namespace, one
  ConfigMap, one PostgreSQL StatefulSet, one PostgreSQL headless Service, one Product Deployment,
  and one Product ClusterIP Service.
- **FR-005**: PostgreSQL MUST use `/var/lib/postgresql/data/pgdata` as its data directory while the
  PVC remains mounted at `/var/lib/postgresql/data`.
- **FR-006**: The overlay MUST reference `product-postgres-credentials` and `flash-sale-secrets` by
  name only; no Secret resource or secret value may be committed.
- **FR-007**: Product Service MUST use an immutable ECR image tag supplied by the overlay or script.
- **FR-008**: The Phase 9 script MUST be read-only by default and MUST require `-Apply` for live
  Kubernetes mutation; it MUST never run `terraform apply` automatically.
- **FR-009**: The pilot MUST keep all services internal (`ClusterIP` or headless Service) and expose
  no public ingress.
- **FR-010**: The overlay MUST pass Kustomize rendering and client-side dry-run validation.

### Non-Functional Requirements

- **NFR-SAFE-001**: Validation output MUST not contain secret values, passwords, or token material.
- **NFR-REPRO-001**: Re-running the script with the same image and existing resources MUST be
  idempotent and MUST NOT delete the PVC.
- **NFR-EVID-001**: Terraform plan, Kustomize validation, rollout, PVC, and health evidence MUST be
  recorded in the feature task ledger or validation document.

### Key Entities

- **EBS CSI integration**: The cluster storage driver, IAM role, and managed add-on required to
  provision EBS-backed PVCs.
- **Product pilot workload**: One Product Service Deployment and its internal Service.
- **Product PostgreSQL instance**: One non-HA StatefulSet with one durable PVC owned by Product
  Service for this training environment.
- **External secret reference**: A name-only reference to credentials created outside Git.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A refreshed Terraform plan after imports reports zero destroy and zero replacement
  actions for the existing EBS role/add-on and preserves the three-node EKS baseline.
- **SC-002**: `kubectl kustomize infra/k8s/overlays/dev-pilot` renders exactly six resources.
- **SC-003**: `kubectl apply --dry-run=client -k infra/k8s/overlays/dev-pilot` exits with status 0
  and creates no live resource.
- **SC-004**: With external secrets present, PostgreSQL and Product Service each reach `1/1 Ready`
  and the PostgreSQL PVC reaches `Bound` without any PVC deletion.
- **SC-005**: A default script run performs no Terraform apply, Kubernetes apply, pod deletion, or
  secret-file write.

## Dependencies and Compatibility

- The existing EKS cluster `flash-sale-dev` and ECR repositories remain the target.
- The EBS CSI role and add-on currently exist from the Phase 8 manual pilot and must be adopted, not
  recreated.
- Product image `pilot-f5fa7cb` is the initial immutable pilot tag; later tags are explicit changes
  to the overlay.
- Product Service continues to use its existing PostgreSQL schema migrations.

## Assumptions

- The current `gp2` StorageClass remains available through the EBS CSI migration path for this
  training cluster; changing to `gp3` is a later capacity/cost decision.
- One PostgreSQL replica and an 8 GiB PVC are acceptable for the internship pilot.
- The operator creates both external Secrets before a live apply and keeps their values private.
- Argo CD will consume this overlay in a later phase after the desired state is reviewed.

## Constitutional Constraints

- **Service ownership**: Product Service remains the owner of its PostgreSQL schema; no cross-service
  database access is added.
- **External ingress**: Services remain internal; public traffic remains reserved for api-gateway in
  a later phase.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: PostgreSQL remains durable truth; Redis is not changed.
- **Messaging reliability**: No Kafka consumer or outbox behavior changes.
- **Root infrastructure ownership**: Terraform, Kustomize, and scripts remain under root `infra/`.
- **Observability**: Existing Product Actuator probes remain the readiness contract; PostgreSQL uses
  `pg_isready` probes.
- **Verification**: Terraform plan, Kustomize render/dry-run, rollout, PVC, and health checks apply;
  Maven tests are not applicable because no Java source changes occur.
- **Architecture decisions**: ADR 0007 records the single-replica EKS PostgreSQL pilot and its
  migration/rollback boundary.

## Approval and History

- 2026-08-21 — Draft created from the approved Phase 9 direction.
- 2026-08-21 — Owner approved the bounded stateful pilot scope for implementation planning.
