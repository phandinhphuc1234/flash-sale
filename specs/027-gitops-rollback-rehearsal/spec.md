# Feature Specification: Product Pilot GitOps Rollback Rehearsal

**Feature Branch**: `codex/gitops-phase12-rollback`

**Created**: 2026-08-21

**Status**: Approved

**Input**: User-approved Phase 12 scope: rehearse a reviewed GitOps rollback for the Product pilot.

## User Scenarios & Testing

### User Story 1 - Roll back a reviewed Product promotion (Priority: P1)

As the platform operator, I want to revert the Product pilot's desired image through Git so that a
bad promotion can be reversed without direct cluster mutation or deleting a registry artifact.

**Why this priority**: A forward-only delivery flow is incomplete without a reversible, auditable
operator path.

**Independent Test**: A rollback PR changes only the Product pilot image tag, passes required CI, and
is merged into protected `develop`.

**Acceptance Scenarios**:

1. **Given** `dev-pilot` is healthy and the previous immutable image exists, **when** the operator
   reverts the promotion merge commit, **then** the rollback branch contains only the expected
   Kustomize image-tag change.
2. **Given** the rollback PR is merged, **when** Argo CD reconciles `develop`, **then** the Product
   Deployment runs the previous immutable image and remains healthy.

### User Story 2 - Restore the forward state after the rehearsal (Priority: P2)

As the platform operator, I want to restore the previously promoted image after observing rollback
so that the development pilot is left in the intended state.

**Why this priority**: The rehearsal must not leave the shared development environment on an obsolete
image.

**Independent Test**: Reverting the rollback commit and merging its PR causes Argo CD to reconcile
back to the original promoted image.

**Acceptance Scenarios**:

1. **Given** the rollback is healthy, **when** the rollback-of-rollback PR is merged, **then** Argo
   CD deploys the original immutable image and the Product rollout completes successfully.

## Edge Cases

- The previous image tag is missing from ECR: stop before creating the rollback PR.
- The rollback PR changes files outside the Product pilot overlay: do not merge; close and recreate it.
- CI or Argo reconciliation fails: keep the current desired state and investigate; do not use direct
  `kubectl apply` as a bypass.
- A Kubernetes Secret or `.env` file appears in the diff: stop and remove it from the change.

## Requirements

### Functional Requirements

- **FR-001**: Rollback MUST be represented by a Git revert or equivalent reviewed commit targeting
  the protected `develop` branch.
- **FR-002**: The rollback MUST change only `infra/k8s/overlays/dev-pilot/kustomization.yaml` and
  only the Product image tag.
- **FR-003**: The rollback MUST use an existing immutable ECR image tag verified before promotion.
- **FR-004**: The rollback PR MUST pass the repository's required `Maven Verify` check before merge.
- **FR-005**: Argo CD MUST remain the only live reconciler; no direct `kubectl apply` is allowed.
- **FR-006**: The operator MUST verify Argo `Synced`/`Healthy`, Deployment rollout, and the running
  image after rollback and after restoration.
- **FR-007**: The rehearsal MUST NOT change `.env`, Kubernetes Secret values, application code, or
  delete ECR images.
- **FR-008**: The original promoted image MUST be restored after rollback evidence is captured.

### Key Entities

- **Promotion commit**: The Git commit that changed the Product pilot image to the newer immutable
  tag (`pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72`).
- **Rollback PR**: A reviewed PR reverting that desired-state change.
- **Image reference**: The ECR repository and immutable tag used by the Product Deployment.
- **Argo application revision**: The Git revision reconciled into the `dev-pilot` namespace.

## Success Criteria

### Measurable Outcomes

- **SC-001**: One rollback PR changes exactly one desired-state file and no secret or application
  source file.
- **SC-002**: The rollback PR receives a passing required check before it is merged.
- **SC-003**: Argo reports `Synced` and `Healthy`, and the Product Deployment reaches one available
  replica within 180 seconds after each merge.
- **SC-004**: The running image before rollback, after rollback, and after restoration is recorded
  by immutable tag and Git revision.
- **SC-005**: The development pilot is restored to the original promoted image before the feature is
  marked complete.

## Assumptions

- The current promoted image is `pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72`.
- The previous known-good image is `pilot-f5fa7cb` and has already been verified to exist in ECR.
- The rehearsal is limited to `dev-pilot`; production and other services are out of scope.
- Existing ADR 0009 remains the governing decision for Git-based rollback and ECR retention.

## Constitutional Constraints

- **Service ownership**: No service code or database ownership changes.
- **External ingress**: N/A; no route or gateway change.
- **API/event contracts**: N/A; no HTTP or Kafka contract changes.
- **Durable and hot-path data**: N/A; no PostgreSQL or Redis data change.
- **Messaging reliability**: N/A; no Kafka consumer or outbox change.
- **Root infrastructure ownership**: Desired state remains under root `infra/k8s/`; no service-local
  platform asset is introduced.
- **Observability**: Existing Argo health, Kubernetes readiness, rollout status, and image evidence
  are sufficient; no application metrics change.
- **Verification**: Git diff, Kustomize render, required CI, Argo status, and rollout checks apply;
  Maven source tests are not repeated for a desired-state-only rollback.
- **Architecture decisions**: ADR 0009 governs this rehearsal; no new service-boundary decision is
  introduced.
