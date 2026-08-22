# Feature Specification: Cloud Environment and Configuration Guard

**Feature Branch**: `codex/gitops-phase22-cloud-guard`

**Created**: 2026-08-22

**Status**: Verified

**Input**: User request to continue the local-plus-cloud GitOps roadmap after Phase 21 release verification.

## Problem and Scope

Phase 21 proves that the currently selected images are running. The repository still needs a small,
repeatable gate that proves the cloud environment is owned by the expected Argo Application and that
configuration boundaries have not drifted before later deployment or rollback work.

### In Scope

- Read-only verification of the expected EKS context and cloud Kustomize overlay.
- Read-only verification of Argo source, target revision, destination, sync health, self-heal, and
  non-pruning policy.
- Verification that application Deployments reference their service-specific ConfigMap and Secret
  boundaries, and that JWT files are mounted only by Authentication Service.
- Verification that platform workloads do not expose a public `LoadBalancer`/`NodePort` service and
  that Kafka keeps explicit topic provisioning and private log-directory settings.
- Validation of the seven Payment runtime flags and existence of the approved Secret boundaries
  without displaying Secret values.

### Out of Scope

- Creating, updating, deleting, or reconciling Kubernetes resources.
- Reading or printing Kubernetes Secret values, local `.env` values, or JWT contents.
- Public ingress, DNS, TLS, production infrastructure, autoscaling, or high-availability changes.
- Enabling Stripe or Payment business processing.

## User Scenarios & Testing

### User Story 1 - Prove cloud ownership and configuration boundaries (Priority: P1)

As the project operator, I want one read-only command to detect cloud configuration drift before
deployment work, so that Argo, Kubernetes, and repository ownership remain predictable.

**Why this priority**: A wrong Argo source or leaked/misplaced secret boundary can invalidate every
subsequent rollout and is more important than adding another deployment action.

**Independent Test**: Run the Phase 22 verifier against `flash-sale-dev`; it exits zero only when the
expected Argo metadata, rendered overlay, workload references, private service shape, and Payment
flags all pass.

**Acceptance Scenarios**:

1. **Given** the expected EKS context and `flash-sale-cloud` Application, **When** the verifier runs,
   **Then** it reports `Synced`, `Healthy`, target `develop`, repository path
   `infra/k8s/overlays/cloud`, self-heal enabled, and pruning disabled.
2. **Given** the cloud overlay and live workloads, **When** the verifier runs, **Then** it reports a
   successful client-side render, all eight application ConfigMap/Secret pairings, the JWT mount,
   platform Secret references, private service types, Kafka safety settings, and seven disabled
   Payment flags without printing Secret values.

### Edge Cases

- A missing ConfigMap, Secret boundary, deployment, or Argo field fails with the named resource.
- A public `LoadBalancer` or `NodePort` service fails the guard because this project has no public
  cloud ingress yet.
- A ConfigMap contains a sensitive value key that belongs in a Secret; the guard fails before any
  mutation is possible.
- The command is run under Windows PowerShell 5.1; it fails with a clear PowerShell 7 requirement.

## Requirements

### Functional Requirements

- **FR-001**: The verifier MUST be read-only and MUST NOT expose Secret values, `.env` values, or JWT
  file contents.
- **FR-002**: The verifier MUST require the configured EKS cluster context and a client-side-valid
  `infra/k8s/overlays/cloud` render.
- **FR-003**: The verifier MUST require Argo Application `flash-sale-cloud` to use the approved
  repository, `develop`, cloud overlay path, `flash-sale` destination, self-healing, and no pruning.
- **FR-004**: The verifier MUST require each of the eight application Deployments to reference the
  shared runtime ConfigMap, its service ConfigMap, and its service Secret boundary.
- **FR-005**: The verifier MUST require the Authentication Service JWT Secret volume and the platform
  StatefulSets' password values to come from Secret references rather than literals.
- **FR-006**: The verifier MUST reject public `LoadBalancer`/`NodePort` Services, enabled Payment
  runtime flags, Kafka auto topic creation, or a Kafka log directory at the PVC root.
- **FR-007**: The verifier MUST document command, scope, result, and safety boundaries in validation
  evidence.

### Non-Functional Requirements

- **NFR-SEC-001**: Diagnostics MUST contain resource names and counts only; they MUST not contain
  Secret data, `.env` values, or private key material.
- **NFR-OPS-001**: The command MUST use bounded child-process execution and fail closed on command
  errors or timeouts.

## Assumptions

- The cloud EKS cluster remains the only deployed release-verification environment.
- Argo CD remains the desired-state owner for `flash-sale-cloud`.
- Phase 15 has already created the required Secret boundaries; this phase only checks their names.
- Payment and Stripe remain intentionally disabled.

## Constitutional Constraints

- **Service ownership**: No service database or application source changes; workload ownership stays
  with each service and shared assets remain under root `infra/`.
- **External ingress**: No ingress or public endpoint is introduced; api-gateway remains internal.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: No PostgreSQL or Redis state is changed.
- **Messaging reliability**: No consumer, outbox, or topic behavior is changed; Kafka settings are
  only verified.
- **Root infrastructure ownership**: The new verifier and evidence live under `infra/scripts/gitops`
  and `specs/`; no service-local platform asset is added.
- **Observability**: Existing health and Gateway smoke checks remain the runtime evidence; no service
  metrics implementation changes.
- **Verification**: PowerShell parsing, Kustomize dry-run, live read-only guard, and `git diff --check`
  apply. Maven and load tests are omitted because no Java or business behavior changes.
- **Architecture decisions**: No ADR is required because this is a read-only operator guard and does
  not change service boundaries, deployment ownership, or communication.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A clean cloud environment passes one command with exit code 0 and reports all guard
  sections as PASS.
- **SC-002**: A deliberate Argo source, public-service, or Secret-boundary drift causes a non-zero
  result before any Kubernetes mutation is attempted.
- **SC-003**: A successful run prints zero Secret values, `.env` values, and private key contents.
- **SC-004**: The guard completes within 600 seconds and leaves Kubernetes, Argo, ECR, PostgreSQL,
  Redis, and Kafka state unchanged.

## Approval and History

- 2026-08-22 — Draft created from the approved local-plus-cloud GitOps roadmap.
- 2026-08-22 — Approved for implementation by proceeding with Phase 22 after Phase 21 PASS.
- 2026-08-22 — Verified by static checks and live read-only EKS guard.
