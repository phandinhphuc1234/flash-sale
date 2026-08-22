# Feature Specification: Full-Stack Argo CD Ownership

**Feature Branch**: codex/gitops-phase19-full-stack-argocd
**Created**: 2026-08-22
**Status**: Implementing
**Input**: User request to continue with Phase 19 after the cloud Gateway smoke passed.
**Linked Business Requirement**: Technical enabler for a reviewable cloud GitOps source of truth.
**Business Owner**: Repository owner
**Required Reviewers**: Repository owner

## Problem and Scope

### Problem Statement

All eight cloud services are healthy, but the complete environment was applied manually. Argo CD
still manages only the historical Product pilot and can revert Product resources away from the
canonical cloud configuration. The cloud environment therefore does not yet have one unambiguous,
self-healing Git source of truth.

### In Scope

- Establish one Argo CD application as owner of the canonical cloud environment.
- Preserve the currently approved immutable Product image during the ownership transition.
- Stop the historical pilot from self-healing before the cloud owner reconciles overlapping
  resources.
- Remove only the obsolete Argo CD Application object after the cloud application is healthy.
- Provide a validation-by-default and explicit-apply operator workflow with rollback.
- Record reconciliation, workload health, image, and ownership evidence.

### Out of Scope

- Public LoadBalancer, Ingress, DNS, TLS, WAF, or a production environment.
- Enabling Payment acceptance, Stripe, consumers, recovery workers, or new credentials.
- Rerunning database migrations or deleting pilot PostgreSQL resources/PVCs.
- Changing service APIs, events, schemas, or business behavior.
- Enabling automatic pruning of cluster resources.

## User Scenarios & Testing

### User Story 1 - Reconcile the full cloud environment from Git (Priority: P1)

As an operator, I want one cloud application to own the complete environment so that Git changes
are reconciled consistently instead of being overwritten by a historical pilot.

**Why this priority**: Two desired-state owners for Product can continuously undo each other.

**Independent Test**: Reconcile the cloud owner and verify it is Synced and Healthy while all eight
application Deployments remain available.

**Acceptance Scenarios**:

1. **Given** the cloud workloads are healthy, **when** cloud ownership is activated, **then** one
   application reports Synced and Healthy for the canonical cloud environment.
2. **Given** the Product pilot uses an approved immutable image, **when** ownership changes,
   **then** Product continues using that same immutable image rather than a mutable or older tag.
3. **Given** the cloud application is healthy, **when** the transition completes, **then** the
   historical pilot Application object no longer self-heals overlapping resources.

### User Story 2 - Recover safely from a failed ownership transition (Priority: P2)

As an operator, I want the transition to stop and restore pilot reconciliation when the cloud
application cannot become healthy, so that a failed cutover does not leave two active controllers.

**Why this priority**: Desired-state ownership is an architectural boundary and requires a
deterministic recovery path.

**Independent Test**: Validate that apply is explicit, the historical Application has no cascade
finalizer, and the recovery sequence can restore the committed pilot manifest without deleting
application workloads.

**Acceptance Scenarios**:

1. **Given** the historical Application has a cascading resource finalizer, **when** validation
   runs, **then** the transition stops before changing live state.
2. **Given** the cloud application fails to become Synced and Healthy, **when** recovery runs,
   **then** the cloud Application object is removed and the historical pilot manifest is restored.
3. **Given** validation-only mode, **when** the operator runs it, **then** no Kubernetes or Argo CD
   resource changes.

### Edge Cases

- The historical Application is already absent because a previous transition completed.
- The cloud Application already exists and the operation is rerun.
- Argo CD can read the private repository but reconciliation is temporarily slow.
- Workloads are healthy while an Application reports OutOfSync.
- Pilot-only StatefulSet/PVC resources remain orphaned and must not be pruned in this phase.

## Requirements

### Functional Requirements

- **FR-001**: Exactly one active self-healing Application MUST own the canonical cloud overlay after
  a successful transition.
- **FR-002**: Validation MUST prove the cloud overlay and Argo CD Application manifest are renderable
  before live state changes.
- **FR-003**: The historical pilot MUST stop automatic reconciliation before the cloud owner begins
  managing overlapping Product resources.
- **FR-004**: The historical Application object MUST be removed only after the cloud Application is
  Synced and Healthy.
- **FR-005**: The transition MUST refuse to remove an Application that has a cascading resource
  deletion finalizer.
- **FR-006**: A failed transition MUST restore the committed historical pilot Application and MUST
  not delete application workloads, persistent volumes, Secrets, or migration evidence.
- **FR-007**: The cloud owner MUST keep automatic pruning disabled.
- **FR-008**: The approved Product image identity MUST be preserved in the canonical cloud desired
  state.
- **FR-009**: The workflow MUST not read or print Secret values.

### Key Entities

- **Desired-state owner**: The single controller responsible for reconciling the canonical cloud
  environment from Git.
- **Historical pilot**: The previous Product-only desired-state owner retained in Git as rollback
  material but inactive after a successful transition.
- **Ownership transition**: A guarded state change from pilot reconciliation to full-cloud
  reconciliation.

## Success Criteria

### Measurable Outcomes

- **SC-001**: The cloud desired-state owner reaches Synced and Healthy within 10 minutes.
- **SC-002**: All eight application Deployments remain available after the transition.
- **SC-003**: Exactly one active Application owns the cloud environment after success.
- **SC-004**: Product runs the same approved immutable image immediately before and after cutover.
- **SC-005**: Zero Secret values, workload deletions, PVC deletions, or migration reruns occur.

## Assumptions

- Phase 17 is merged into develop and the canonical cloud overlay is already present there.
- Argo CD already has read access to the private repository.
- The existing historical Application has no resource-deletion finalizer.
- Pilot-only database resources remain for later explicit cleanup.

## Constitutional Constraints

- **Service ownership**: No service code, database ownership, schema, or migration changes.
- **External ingress**: Gateway remains internal; no public exposure is introduced.
- **API/event contracts**: No HTTP or Kafka contract change.
- **Durable and hot-path data**: No PostgreSQL, Redis, stock, or payment behavior changes.
- **Messaging reliability**: No Kafka consumer, outbox, retry, or ordering change.
- **Root infrastructure ownership**: All shared deployment assets remain under root infra/.
- **Observability**: Existing Argo health and Kubernetes rollout/readiness signals provide evidence.
- **Verification**: Kustomize dry-runs, Argo status, Deployment availability, image identity, and
  recovery guard apply. Maven, contract, and load tests are omitted because no application code or
  interface changes.
- **Architecture decisions**: ADR 0022 records the full-cloud Argo ownership transition.

## Approval and History

- 2026-08-22 — Approved through the repository owner's request to implement Phase 19.
