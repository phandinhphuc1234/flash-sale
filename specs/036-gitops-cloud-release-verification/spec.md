# Feature Specification: Cloud Release Artifact Verification

**Feature Branch**: `codex/gitops-phase21-cloud-release-verification`

**Created**: 2026-08-22

**Status**: Verified

**Input**: User requested Phase 21 from the CI/CD roadmap, adapted to the repository's local-plus-cloud deployment model.

**Linked Business Requirements**: Technical release-confidence gate for the single cloud environment used as staging.

**Business Owner**: Repository owner

**Required Reviewers**: Repository owner and GitOps owner

## Problem and Scope

### Problem Statement

The cloud environment is healthy, but health alone does not prove that the artifact selected by the
GitOps overlay is the artifact actually running in every service Pod. A release verification gate is
needed before later rollback and performance phases.

### In Scope

- Verify the canonical `flash-sale-cloud` Argo Application is Synced and Healthy.
- Verify all eight application Deployments are available and their running image digests match the
  image manifests referenced by the cloud desired state in ECR.
- Run the existing internal Gateway smoke checks for readiness, public catalog access, and protected
  admin access.
- Confirm all seven Payment/Stripe runtime flags remain disabled.
- Produce bounded operator output without reading or printing Kubernetes Secret values.

### Out of Scope

- Building or pushing images.
- Changing Kustomize image tags, Kubernetes resources, Argo Applications, Secrets, or feature flags.
- Creating a production environment, public ingress, DNS, or TLS resources.
- Enabling Payment, Stripe, Kafka consumers, or synthetic business transactions.
- Performance/load testing; that belongs to Phase 26.

## Baseline References

- `specs/034-gitops-full-stack-argocd/`
- `specs/035-gitops-cloud-kafka-contracts/`
- `infra/scripts/gitops/phase18-gateway-smoke.ps1`
- `infra/k8s/overlays/cloud/`

## User Scenarios & Testing

### User Story 1 - Verify the cloud release artifact (Priority: P1)

As a release operator, I want to verify that the cloud workloads run the exact ECR artifacts selected
by GitOps, so that a healthy deployment is also a trustworthy release candidate.

**Why this priority**: This is the release gate required before rollback rehearsal and performance testing.

**Independent Test**: Run the Phase 21 validation command against `flash-sale-dev` and observe eight
available Deployments, matching ECR/runtime digests, a Synced/Healthy Argo Application, and passing
Gateway smoke checks.

**Acceptance Scenarios**:

1. **Given** `flash-sale-cloud` tracks `develop`, **when** verification runs, **then** its sync and
   health status are `Synced` and `Healthy` and its live revision is reported.
2. **Given** the cloud overlay selects eight service images, **when** verification runs, **then** each
   Deployment image tag resolves to an ECR manifest and every selected Pod reports the same digest.
3. **Given** the Gateway is internal, **when** the smoke check runs, **then** readiness returns 200,
   public catalog access returns 200, and anonymous admin access returns 401.
4. **Given** Payment is not enabled in this phase, **when** verification runs, **then** all seven
   Payment/Stripe flags are reported disabled.

### Edge Cases

- Argo is Synced but not Healthy; verification fails without changing resources.
- A Deployment is available but a Pod image digest differs from ECR; verification reports the service.
- An image tag is missing or resolves to no ECR manifest; verification fails before smoke completion.
- A Gateway local forwarding port is occupied; the existing smoke helper reports the conflict and cleans
  up only its own temporary process.
- AWS, Kubernetes, or Argo access is unavailable; bounded diagnostics identify the failed dependency.

## Requirements

### Functional Requirements

- **FR-001**: The verifier MUST inspect the `flash-sale-cloud` Argo Application and report its sync,
  health, target revision, and live revision.
- **FR-002**: The verifier MUST inspect exactly these eight Deployments: `api-gateway`,
  `authentication-service`, `product-service`, `campaign-service`, `flash-sale-service`,
  `inventory-service`, `order-service`, and `payment-service`.
- **FR-003**: For every inspected Deployment, the verifier MUST compare the running Pod image digest
  with the ECR manifest digest resolved from the selected image tag.
- **FR-004**: The verifier MUST fail when a required Deployment is unavailable, an image reference is
  missing, or an ECR digest cannot be resolved.
- **FR-005**: The verifier MUST invoke the existing Gateway smoke behavior and preserve its 200/200/401
  acceptance outcomes.
- **FR-006**: The verifier MUST confirm these seven flags are `false`: `PAYMENT_ACCEPTANCE_ENABLED`,
  `PAYMENT_CHECKOUT_ENABLED`, `STRIPE_ENABLED`, `PAYMENT_CONSUMER_ENABLED`,
  `PAYMENT_OUTBOX_PUBLISHER_ENABLED`, `PAYMENT_RECOVERY_ENABLED`, and
  `PAYMENT_WEBHOOK_PROCESSING_ENABLED`.
- **FR-007**: Default execution MUST be read-only apart from a temporary localhost port-forward used by
  the existing smoke helper; it MUST NOT apply Kubernetes, Argo, ECR, or Secret changes.
- **FR-008**: The verifier MUST use bounded native-process execution and MUST NOT read or print Secret
  values.

### Key Entities

- **Release artifact observation**: The selected image reference, ECR digest, running Pod digest, and
  verification result for one service; it is ephemeral evidence, not business state.
- **Cloud release verification run**: A timestamped operator execution containing Argo, Deployment,
  digest, flag, and smoke outcomes.

## Success Criteria

### Measurable Outcomes

- **SC-001**: One validation run reports 8/8 available Deployments and 8/8 matching ECR/runtime digests.
- **SC-002**: Argo reports `Synced|Healthy` for `flash-sale-cloud` at the cloud revision under test.
- **SC-003**: Gateway smoke reports readiness 200, public catalog 200, and anonymous admin 401.
- **SC-004**: Payment runtime flags report 7/7 disabled, with zero Secret values printed.
- **SC-005**: A failed dependency produces a bounded diagnostic and no persistent Kubernetes or ECR mutation.

## Assumptions

- The AWS profile `flash-sale-terraform`, region `ap-southeast-2`, EKS cluster `flash-sale-dev`, and
  ECR repositories already exist.
- Argo remains the owner of `infra/k8s/overlays/cloud`.
- The cloud environment is the project's only deployed release-verification environment; no production
  approval gate is introduced here.
- ECR image tags may be existing immutable promotion tags; verification resolves their current digest
  and compares it with the running Pod image ID.

## Constitutional Constraints

- **Service ownership**: No service source, database, migration, JPA type, or domain model changes.
- **External ingress**: Gateway remains ClusterIP and smoke access remains localhost-only.
- **API/event contracts**: No API or Kafka contract changes.
- **Durable and hot-path data**: No PostgreSQL or Redis state changes.
- **Messaging reliability**: No consumer, outbox, retry, ordering, or schema behavior changes.
- **Root infrastructure ownership**: The operator script and evidence remain under root `infra/` and
  `specs/`; no service-local platform asset is added.
- **Observability**: Verification consumes existing health and smoke signals; no manual Prometheus
  registry or application instrumentation is introduced.
- **Verification**: PowerShell parser/static checks, cloud Kustomize dry-run, bounded operator checks,
  and live EKS smoke/digest evidence apply. Maven service tests are omitted because application code is
  unchanged.
- **Architecture decisions**: No new ADR is required because ownership, ingress, storage, and service
  boundaries do not change.

## Approval and History

- 2026-08-22 — Draft created from the Phase 21 CI/CD roadmap and adapted to local-plus-cloud deployment.
- 2026-08-22 — Approved for implementation by the repository owner request to proceed with Phase 21.
- 2026-08-22 — Verified on EKS: Argo Synced/Healthy, 8/8 ECR/Pod digests matched, Gateway smoke passed, and 7/7 Payment flags remained disabled.
