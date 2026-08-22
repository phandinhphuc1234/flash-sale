# Feature Specification: Eight-Service GitOps Image Delivery

**Feature Branch**: `codex/gitops-phase21-eight-service-delivery`

**Created**: 2026-08-22

**Status**: Implementing

**Input**: User request to finish canonical GitOps Phase 21 for all eight deployed services.

## Problem and Scope

### Problem Statement

The repository has selective Maven CI and a hosted Product-only image promotion workflow. The cloud
Argo CD Application manages eight deployed application services, but seven of them still require
manual image publication and desired-state edits. This leaves the delivery path inconsistent and
prevents a service change from following one reviewable source-to-cloud route.

### In Scope

- Build and verify the eight deployed services when their source or shared build inputs change:
  `api-gateway`, `authentication-service`, `product-service`, `campaign-service`,
  `flash-sale-service`, `inventory-service`, `order-service`, and `payment-service`.
- Map the `flash-sale-service` deployment name to its source module `services/flashsale-service`.
- Authenticate to the existing AWS ECR account with GitHub OIDC and the existing
  `github-ci-role`; no long-lived AWS key is introduced.
- Publish immutable, commit-derived images to the existing eight ECR repositories.
- Open one reviewable pull request that changes only the selected image tags in
  `infra/k8s/overlays/cloud/kustomization.yaml`.
- Keep `develop` protected and let the existing `flash-sale-cloud` Argo CD Application reconcile
  only after the promotion pull request is merged.
- Provide manual workflow dispatch for all services or one selected service.

### Out of Scope

- `cart-service` and `notification-service`, which are source modules but are not part of the
  deployed eight-service cloud topology.
- Direct `kubectl apply`, Argo CD mutation, or Kubernetes Secret creation by GitHub Actions.
- Automatic pull-request merge, public Gateway exposure, Stripe enablement, observability stack,
  rollback automation, or changes to service/application code.
- Creating or changing AWS IAM roles, ECR repositories, EKS, databases, Kafka, or Redis.

## Baseline References

- `docs/deployment/gitops-roadmap-status.md` — canonical roadmap and current Phase 21 gap.
- `docs/adr/0009-github-actions-ecr-argo-promotion.md` — historical Product-only promotion decision.
- `specs/026-gitops-image-promotion/` — completed Product pilot implementation and evidence.
- `infra/k8s/argocd/application-cloud.yaml` — cloud Argo CD source of truth.
- `infra/k8s/overlays/cloud/kustomization.yaml` — eight image references updated by promotion.

## User Scenarios & Testing

### User Story 1 - Publish verified service images (Priority: P1)

As the project owner, I want a changed deployed service to be compiled, tested, containerized, and
published to its ECR repository so that every cloud image is traceable to a verified commit.

**Why this priority**: A promotion cannot be safe if the artifact was not built and tested from the
same source revision.

**Independent Test**: Push a change under one deployed service and verify that only that service is
verified and an immutable image is published after the Maven step succeeds.

**Acceptance Scenarios**:

1. **Given** a change under `services/order-service`, **when** the workflow runs on `develop`,
   **then** Order verification and image publication complete without building unrelated service
   images.
2. **Given** a shared `pom.xml`, `libs/`, `contracts/`, or Maven workflow change, **when** the
   workflow runs, **then** all eight deployed services are selected.
3. **Given** any selected service fails verification, **when** the workflow runs, **then** no
   promotion pull request is opened.

### User Story 2 - Promote all selected images through reviewed Git state (Priority: P1)

As the project owner, I want selected image references updated in one pull request so that Git
remains the deployment intent and Argo CD never deploys an unreviewed tag.

**Why this priority**: The protected branch and Argo CD ownership require a reviewable desired-state
transition rather than an imperative cluster update.

**Independent Test**: Run the workflow for one service and for a shared change, then inspect the
automation branch and pull request diff before merging it.

**Acceptance Scenarios**:

1. **Given** successful image publication for one or more services, **when** promotion runs,
   **then** exactly one PR targets `develop` and changes only the matching `newTag` entries in the
   cloud Kustomize overlay.
2. **Given** the promotion PR is merged, **when** Argo CD reconciles, **then**
   `flash-sale-cloud` becomes `Synced` and `Healthy` at the merged revision.
3. **Given** the promotion PR is closed without merging, **when** Argo reconciles, **then** the
   previously deployed image tags remain in use.

### User Story 3 - Operate and rehearse the delivery path safely (Priority: P2)

As an operator, I want a documented manual dispatch and validation path so that I can re-run one
service or all services without editing credentials or Kubernetes state by hand.

**Why this priority**: Manual recovery and learning are needed when a hosted run is unavailable,
while remote mutation must remain explicit and reviewable.

**Independent Test**: Dispatch the workflow with one service and with `all`, then verify the selected
set, image tag, PR metadata, and absence of Secret values in logs.

**Acceptance Scenarios**:

1. **Given** a manual dispatch selecting one service, **when** the workflow runs, **then** only that
   service is built and promoted.
2. **Given** no selected service on manual dispatch, **when** the workflow runs, **then** all eight
   services are selected.
3. **Given** any workflow failure before promotion, **when** the run ends, **then** no Kubernetes
   resource and no Kubernetes Secret is changed.

### Edge Cases

- The source module and deployment image use different names for Flash Sale; the canonical mapping
  must remain explicit and tested.
- A promotion branch has no desired-state diff because the tag is already present; no empty PR is
  created.
- The existing ECR tag already exists; the push must fail rather than overwrite an immutable tag.
- Two runs overlap on `develop`; concurrency must serialize promotion runs instead of allowing
  competing desired-state branches to silently replace one another.
- AWS OIDC or ECR permission is unavailable; the workflow fails before image publication or Git
  desired-state mutation.
- Product-only historical `dev-pilot` files remain for rollback evidence but are not updated by the
  eight-service cloud workflow.

## Requirements

### Functional Requirements

- **FR-001**: The hosted delivery workflow MUST recognize exactly the eight deployed service targets
  and their source-module-to-image mappings.
- **FR-002**: The workflow MUST select only services affected by service-local changes and MUST
  select all eight for shared Maven, contract, Docker, or workflow inputs.
- **FR-003**: Each selected service MUST run the Maven wrapper with its reactor dependencies before
  any ECR push.
- **FR-004**: The workflow MUST assume AWS through GitHub OIDC and the existing `github-ci-role`;
  long-lived AWS credentials MUST NOT be stored in GitHub or the repository.
- **FR-005**: Each successful selected service MUST publish one immutable ECR image tagged from the
  full source commit SHA and MUST NOT overwrite an existing tag.
- **FR-006**: A promotion MUST update only the selected image `newTag` entries in
  `infra/k8s/overlays/cloud/kustomization.yaml` and MUST create one PR targeting `develop`.
- **FR-007**: The workflow MUST NOT call `kubectl`, mutate Argo CD, create Kubernetes Secrets, push
  directly to `develop`, or auto-merge a pull request.
- **FR-008**: The workflow MUST support `workflow_dispatch` with an explicit one-service or `all`
  selection and MUST document the manual operation.
- **FR-009**: A failed verification or image publication MUST prevent promotion PR creation and
  leave the current Argo CD desired state unchanged.
- **FR-010**: The workflow and PR body MUST identify source commit, selected services, image tags,
  and cloud overlay path without printing credential values.
- **FR-011**: The former Product-only hosted trigger MUST NOT run concurrently for the same Product
  change; one generalized workflow owns cloud image promotion.

### Non-Functional Requirements

- **NFR-SEC-001**: AWS authentication MUST use short-lived GitHub OIDC credentials.
- **NFR-REL-001**: A successful run MUST be serialized per `develop` and produce at most one
  promotion PR for its source revision.
- **NFR-AUD-001**: Source commit → ECR image tag/digest → Git promotion PR → Argo revision MUST be
  traceable.
- **NFR-OPS-001**: Workflow logs MUST report service names and outcomes but MUST withhold AWS,
  GitHub, registry, database, JWT, and Stripe Secret values.

## Key Entities

- **Delivery Target**: Canonical service name, source module directory, Dockerfile, ECR repository,
  and cloud Kustomize image key.
- **Image Release**: Immutable ECR image identified by service, source commit SHA, tag, and registry
  digest.
- **Promotion Pull Request**: Reviewable Git change containing only selected cloud image tags and
  source/artifact metadata.
- **Delivery Run**: One workflow execution with selected targets, verification outcomes, publication
  outcomes, and PR outcome.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A service-local change selects and verifies exactly its affected deployed service(s),
  while a shared build/contract change selects all eight.
- **SC-002**: Every successful selected target has an immutable ECR tag derived from the exact source
  commit and a corresponding promotion PR.
- **SC-003**: A successful promotion merge changes no cloud desired-state image entries outside the
  selected service set.
- **SC-004**: After a merged promotion, `flash-sale-cloud` reaches `Synced` and `Healthy` and all
  eight application Deployments report Available.
- **SC-005**: A failed verification or ECR publication produces zero promotion PRs and zero direct
  Kubernetes mutations.
- **SC-006**: Static workflow validation, all affected Maven verification, cloud Kustomize dry-run,
  and the Phase 21 release verification pass before the feature is marked complete.

## Assumptions

- The existing ECR repositories, EKS cluster, Argo CD `flash-sale-cloud` Application, and
  `github-ci-role` already exist and are managed outside this feature.
- The repository Actions policy permits `contents: write`, `pull-requests: write`, and `id-token:
  write` for the workflow.
- `infra/k8s/overlays/cloud/kustomization.yaml` remains the sole image desired-state file for the
  cloud Application.
- The canonical Phase 21 scope is the eight services deployed in the cloud overlay; source-only
  `cart-service` and `notification-service` remain outside cloud delivery.

## Constitutional Constraints

- **Service ownership**: No Java source, database, migration, JPA, or service boundary changes.
- **External ingress**: No Gateway or public endpoint change.
- **API/event contracts**: No HTTP or Kafka contract change; shared contract changes only trigger
  verification of all eight services.
- **Durable and hot-path data**: No PostgreSQL, Redis, or stateful workload change.
- **Messaging reliability**: No consumer, outbox, retry, or schema behavior change.
- **Root infrastructure ownership**: Workflow and promotion metadata remain under `.github/`,
  `infra/`, `docs/`, and `specs/`; no service-local orchestration is added.
- **Observability**: Existing workflow, ECR, PR, Argo, Deployment, and Phase 21 checks provide
  delivery evidence; no metrics registry or application instrumentation changes.
- **Verification**: Workflow static checks, Maven reactor verification, Docker/ECR path checks,
  Kustomize dry-run, and live Phase 21 release verification apply. A separate business E2E is
  canonical roadmap 22 and remains out of scope.
- **Architecture decisions**: ADR 0024 records the transition from Product-only promotion to the
  eight-service cloud promotion workflow.

## Approval and History

- 2026-08-22 — Draft created from the canonical Phase 21 gap and approved for implementation by the
  project owner request.
- 2026-08-22 — Workflow, ADR, contracts, and local validation implemented; hosted run/PR evidence
  remains pending.
