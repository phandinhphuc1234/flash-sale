# Feature Specification: Product Pilot Image Promotion

**Feature Branch**: `codex/gitops-phase11-image-promotion`

**Created**: 2026-08-21

**Status**: Approved

**Input**: User request: "Phase 11 — connect CI image delivery with the Argo CD Product pilot and save a reusable script."

## Problem and Scope

### Problem Statement

Phase 10 established Argo CD reconciliation for the Product pilot, but publishing a new Product
Service image and changing the Git desired image are still manual. The delivery path needs to be
repeatable, reviewable, and safe for the protected `develop` branch.

### In Scope

- Verify the Product Service reactor before publishing an image.
- Publish an immutable Product Service image to the existing ECR repository after a successful
  change is merged to `develop`.
- Create a reviewable Git change that updates the Product pilot image reference.
- Let the existing Argo CD Application reconcile the image only after that Git change is merged.
- Provide a local PowerShell helper with validation-by-default behavior and explicit remote mutation
  switches.
- Document the existing GitHub OIDC role, ECR repository, branch protection, and operator-managed
  secrets required by the flow.

### Out of Scope

- Building or deploying the other services.
- Creating or changing AWS IAM roles, EKS, ECR repositories, databases, or Kubernetes Secrets.
- Automatically merging the image promotion pull request.
- Production environments, progressive delivery, rollback automation, or image garbage collection.
- Changing Argo CD pruning, ingress, or HA topology.

## User Scenarios & Testing

### User Story 1 - Publish a verified immutable image (Priority: P1)

As the project owner, I want a merged Product Service change to produce an immutable ECR image only
after verification succeeds, so that Argo CD never deploys an untested image.

**Why this priority**: An image that cannot be reproduced and traced to a verified commit is not a
safe deployment input.

**Independent Test**: Merge a Product Service change into `develop` and verify that the delivery
workflow completes Maven verification, assumes the existing AWS OIDC role, and publishes one image
tag tied to the commit SHA.

**Acceptance Scenarios**:

1. **Given** a Product Service change is merged to `develop`, **when** verification succeeds,
   **then** one immutable ECR image tagged with that commit is published.
2. **Given** Product Service verification fails, **when** the delivery workflow runs, **then** no
   ECR image is published and the workflow fails visibly.
3. **Given** a documentation-only change, **when** it reaches `develop`, **then** the Product
   delivery workflow does not run automatically.

### User Story 2 - Promote the image through reviewed Git state (Priority: P1)

As the project owner, I want the image reference to be updated through a pull request instead of a
direct push to `develop`, so that branch protection and Argo CD keep a reviewable audit trail.

**Why this priority**: The Git commit, not an imperative cluster command, must remain the deployment
intent.

**Independent Test**: After a successful image publish, verify that an automation branch and pull
request update only the Product pilot image tag; after the PR merges, Argo CD reports `Synced` and
`Healthy` for `dev-pilot`.

**Acceptance Scenarios**:

1. **Given** a new immutable image exists, **when** the workflow prepares promotion, **then** it
   opens or updates a pull request targeting `develop` and does not push directly to `develop`.
2. **Given** the promotion pull request is merged, **when** Argo CD reconciles, **then** the
   `dev-pilot` Application uses the new image and remains `Synced` and `Healthy`.
3. **Given** the promotion pull request is rejected, **when** no desired-state commit is merged,
   **then** Argo CD keeps the currently deployed image.

### User Story 3 - Reproduce the flow locally (Priority: P2)

As an operator, I want a local helper that validates by default and requires explicit switches for
ECR publishing or overlay edits, so that I can rehearse Phase 11 without accidentally changing AWS
or Git.

**Why this priority**: Local rehearsal makes the CI behavior understandable and provides a recovery
path when the hosted workflow is unavailable.

**Independent Test**: Run the helper without mutation switches and verify that it builds or validates
the Product image without pushing to ECR or writing the overlay; run explicit switches only after
reviewing the displayed image and diff.

**Acceptance Scenarios**:

1. **Given** no mutation switch, **when** the helper runs, **then** it prints the derived image and
   performs no ECR push or overlay write.
2. **Given** `-Push`, **when** AWS credentials and Docker are valid, **then** the exact immutable
   image is pushed to the existing Product ECR repository.
3. **Given** `-UpdateOverlay`, **when** the image is valid, **then** only the Product pilot tag is
   changed and the overlay passes client-side Kustomize rendering.

### Edge Cases

- The OIDC role is missing or lacks ECR push permission; the workflow fails before publishing.
- The repository is private; Argo CD continues using its existing operator-managed repository Secret.
- The immutable tag already exists; the workflow must not overwrite it.
- The image promotion PR has no diff because the same tag is already desired; no empty PR is created.
- The Argo Application cannot reach GitHub or the ECR image is unavailable; the failed health/sync
  state remains visible and no secret is printed.

## Requirements

### Functional Requirements

- **FR-001**: The delivery workflow MUST run automatically only for Product Service or required
  shared build changes merged into `develop`, and MUST support an explicit manual dispatch.
- **FR-002**: The workflow MUST run the repository Maven wrapper for the Product Service with reactor
  dependencies before any remote image mutation.
- **FR-003**: The workflow MUST authenticate to AWS using GitHub OIDC and the existing
  `github-ci-role`; long-lived AWS keys MUST NOT be stored in GitHub secrets or files.
- **FR-004**: The workflow MUST publish a Product Service image to the existing immutable ECR
  repository using a commit-derived tag and MUST fail if the tag cannot be published.
- **FR-005**: The workflow MUST update only the Product pilot desired image and MUST create a pull
  request targeting `develop`; it MUST NOT push directly to the protected branch or auto-merge.
- **FR-005a**: The workflow MUST dispatch the existing CI workflow for the automation branch when
  GitHub's token event-suppression would otherwise leave the protected `Maven Verify` check pending.
- **FR-006**: The Argo CD Application MUST remain the deployment reconciler; the workflow MUST NOT
  call `kubectl apply`, modify Argo CD resources, or write Kubernetes Secret values.
- **FR-007**: The local helper MUST validate by default and require explicit `-Push` and
  `-UpdateOverlay` switches for remote ECR or local desired-state mutations.
- **FR-008**: The helper and workflow MUST never print GitHub, AWS, registry, database, or Argo
  credential values.
- **FR-009**: The image reference MUST remain reproducible from the commit SHA and the GitOps PR
  MUST include the source commit and image tag in its description.
- **FR-010**: Documentation MUST describe required repository permissions, OIDC trust assumptions,
  ECR repository identity, protected-branch behavior, and validation commands.

### Non-Functional Requirements

- **NFR-SEC-001**: No long-lived cloud credential may be introduced for this feature.
- **NFR-REL-001**: A failed verification or image push MUST stop promotion and leave the current
  Argo CD deployment unchanged.
- **NFR-AUD-001**: A successful promotion MUST be traceable from source commit to image tag, Git PR,
  and Argo CD revision.

### Key Entities

- **Image Release**: The Product Service ECR image identified by source commit SHA, immutable tag,
  repository, and registry digest.
- **Promotion Pull Request**: A branch and review record containing the single desired-state image
  tag change and source image metadata.
- **Delivery Workflow Run**: The verification, authentication, build, publish, and PR outcome for a
  source revision.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Every successful Product promotion has one immutable image tag that can be mapped to
  the exact Git commit that produced it.
- **SC-002**: A failing Product verification produces zero new ECR images and zero desired-state PRs.
- **SC-003**: A successful desired-state merge causes the existing `dev-pilot` Application to become
  `Synced` and `Healthy` without manual `kubectl apply`.
- **SC-004**: The local helper's default mode performs zero remote ECR writes and zero overlay writes.
- **SC-005**: No credential value appears in workflow logs, generated manifests, or committed files.

## Assumptions

- The EKS cluster, ECR repository `flash-sale/product-service`, and Argo CD `dev-pilot` Application
  already exist from Phases 5–10.
- The AWS role `arn:aws:iam::090814040069:role/github-ci-role` trusts this repository's GitHub OIDC
  subject and has only the ECR permissions needed for the pilot.
- The GitHub Actions repository policy permits the workflow's declared `contents` and pull-request
  permissions.
- The existing Argo CD repository Secret is provisioned separately because the GitHub repository is
  private.

## Constitutional Constraints

- **Service ownership**: Product Service remains independently buildable; no service database or
  business code changes are introduced.
- **External ingress**: No API or ingress change.
- **API/event contracts**: No HTTP or Kafka contract change.
- **Durable and hot-path data**: No PostgreSQL or Redis change.
- **Messaging reliability**: No Kafka consumer or outbox change.
- **Root infrastructure ownership**: Workflow, GitOps overlay, and helper remain under `.github/`
  and root `infra/`; no platform asset is added to a service module.
- **Observability**: Existing build, Argo Application, Pod readiness, and ECR audit evidence are
  sufficient; no service metrics change is required.
- **Verification**: Product module verification, workflow shell validation, Docker build/push
  rehearsal, Kustomize render, and Argo sync evidence are required. No new HTTP, Kafka, or load test
  applies because runtime business behavior is unchanged.
- **Architecture decisions**: ADR 0009 records the delivery and ownership boundary.

## Approval and History

- 2026-08-21 — Draft created from the Phase 11 request.
- 2026-08-21 — Approved for implementation by project owner request.
