# Feature Specification: Local and Cloud Environment Foundation

**Feature Branch**: `028-gitops-local-cloud-foundation`

**Created**: 2026-08-21

**Status**: Verified

**Input**: User description: "The project has only local and cloud environments. Standardize the full GitOps deployment around those two environments and prepare the first phase for the full project rollout."

## Problem and Scope

### Problem Statement

The repository currently contains Docker Compose assets for local development and several Kubernetes
overlays created during the pilot rollout. The names `dev` and `dev-pilot` do not clearly express
which assets are local-only and which assets are intended for AWS EKS. This makes the future
full-stack rollout harder to operate and creates a risk that a temporary pilot configuration is
mistaken for the canonical cloud deployment.

### In Scope

- Establish `local` and `cloud` as the only supported environment names.
- Document the source of truth for each environment and the promotion boundary between them.
- Provide a renderable cloud Kubernetes overlay for the complete eight-service application topology.
- Keep the pilot overlay available only as historical rollback evidence.
- Define the configuration and secret inventory that later phases must satisfy.
- Provide a repeatable, value-safe validation command for the environment contract.

### Out of Scope

- Deploying PostgreSQL, Redis, Kafka, or Schema Registry to EKS.
- Creating, rotating, or storing secret values in Git.
- Changing service business behavior, HTTP contracts, Kafka contracts, or database schemas.
- Adding a production environment, high-availability replicas, public ingress, or a managed data service.

### Non-goals

- This feature does not make the entire application live in the cloud yet.
- This feature does not replace Docker Compose with Kubernetes for local development.
- This feature does not expose the API Gateway publicly.

## Baseline References

- `infra/docker/compose.yml` and `infra/docker/compose.dev.yml` define the local runtime.
- `infra/k8s/base/` defines the shared Kubernetes resources for the eight services.
- `infra/k8s/overlays/dev/` is the existing full-stack overlay with temporary `dev` naming.
- `infra/k8s/overlays/dev-pilot/` is the existing Product-only pilot and rollback target.
- `infra/k8s/argocd/application-dev-pilot.yaml` currently points Argo CD at the pilot overlay.

## User Scenarios & Testing

### User Story 1 - Identify the target environment unambiguously (Priority: P1)

As a developer or operator, I want every deployment instruction and infrastructure path to identify
`local` or `cloud`, so that I cannot accidentally treat a pilot or production-like name as the
canonical environment.

**Why this priority**: Every later deployment phase depends on an unambiguous environment boundary.

**Independent Test**: Review the environment contract and run its validator; it must identify local
as Docker Compose, cloud as EKS/Argo CD, and reject the unsupported `product` environment.

**Acceptance Scenarios**:

1. **Given** the repository is checked out, **when** an operator reads the environment contract,
   **then** the only supported environment names are `local` and `cloud`.
2. **Given** a request to deploy a `product` environment, **when** the validation script checks the
   request, **then** it fails with an actionable message explaining the supported alternatives.

### User Story 2 - Render the canonical cloud topology (Priority: P1)

As an operator, I want a single cloud Kustomize overlay that references the complete eight-service
base, so that later image promotion and Argo CD reconciliation have one canonical target.

**Why this priority**: Without a renderable target, secrets, stateful services, and GitOps promotion
cannot be added safely.

**Independent Test**: Run `kubectl kustomize infra/k8s/overlays/cloud` and the client-side dry-run;
the output must contain the flash-sale namespace and exactly the eight application Deployments and
their ClusterIP Services without requiring secret values.

**Acceptance Scenarios**:

1. **Given** the cloud overlay is checked out, **when** Kustomize renders it, **then** all eight
   application services are present and each image points to the ECR repository namespace.
2. **Given** no Kubernetes Secret values have been supplied, **when** the overlay is dry-run
   validated, **then** validation succeeds because secret material is intentionally deferred to a
   later phase.

### User Story 3 - Preserve safe configuration boundaries (Priority: P2)

As a maintainer, I want the environment documentation and validator to enumerate required secret
names without reading or writing their values, so that later cloud setup cannot accidentally commit
credentials.

**Why this priority**: The full-stack rollout includes JWT, OAuth, database, Redis, and Stripe
credentials; leaking them would invalidate the deployment process.

**Independent Test**: Run the validator against a clean checkout; it reports required secret keys,
confirms that `.env` files are ignored, and never prints secret values.

**Acceptance Scenarios**:

1. **Given** a clean checkout, **when** the validator runs, **then** it reports the required secret
   names and the next phase that will provision them.
2. **Given** an ignored `.env` file contains values, **when** the validator runs, **then** its output
   contains only key names and status, never the values.

### Edge Cases

- A developer runs the validator from outside the repository root; it must fail with a path hint.
- A legacy `dev-pilot` overlay is referenced; the documentation must identify it as pilot history,
  not as a third supported environment.
- A cloud image tag is still `initial`; the overlay remains renderable, while runtime readiness is
  deferred to the image-promotion phase.

## Requirements

### Functional Requirements

- **FR-001**: The repository MUST define exactly two supported environment labels: `local` and
  `cloud`.
- **FR-002**: The local environment MUST identify `infra/docker/` as its orchestration source of
  truth and MUST NOT require Kubernetes or Argo CD.
- **FR-003**: The cloud environment MUST identify AWS EKS with Argo CD as its reconciliation target.
- **FR-004**: The canonical cloud overlay MUST include the eight application services already owned
  by the repository: API Gateway, Authentication, Product, Campaign, Flash Sale, Inventory, Order,
  and Payment.
- **FR-005**: The cloud overlay MUST remain renderable without embedding secret values in Git.
- **FR-006**: The environment validator MUST reject unsupported environment labels, including
  `product`, and MUST return a non-zero exit code for invalid input.
- **FR-007**: The environment validator MUST list secret names and file locations needed by later
  phases without printing secret values.
- **FR-008**: The existing `dev-pilot` overlay MUST be documented as a historical Product-only pilot
  and MUST NOT be treated as the canonical full-stack environment.
- **FR-009**: No service boundary, database ownership, HTTP contract, Kafka contract, or business
  behavior MUST change in this phase.

### Non-Functional Requirements

- **NFR-SEC-001**: Validation output MUST never reveal the contents of `.env`, Kubernetes Secret
  manifests, JWT PEM files, or provider credentials.
- **NFR-OPS-001**: A new operator MUST be able to identify the local/cloud command path from the
  environment documentation without relying on chat history.
- **NFR-VAL-001**: The cloud overlay MUST pass client-side Kustomize validation on Windows PowerShell
  and in CI.

## Key Entities

- **Environment Contract**: The repository-level mapping between an environment label, its source of
  truth, deployment target, and allowed operations.
- **Cloud Overlay**: The canonical Kubernetes composition used to render the full application on EKS.
- **Secret Inventory**: Names and ownership of credentials required later, without their values.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A clean checkout renders `infra/k8s/overlays/cloud` successfully with both
  `kubectl kustomize` and `kubectl apply --dry-run=client -k`.
- **SC-002**: The rendered cloud output contains exactly eight application Deployments and eight
  application Services, all in the `flash-sale` namespace.
- **SC-003**: The validator rejects `product` and any unknown environment with a non-zero exit code.
- **SC-004**: The validator and documentation expose all required secret names while exposing zero
  secret values in their output.
- **SC-005**: No Java module, HTTP contract, Kafka contract, or database migration changes as part
  of this phase.

## Assumptions

- `local` continues to use the existing Docker Compose topology and ignored `infra/docker/.env`.
- `cloud` initially uses one replica per service and internal Kubernetes Services; public Gateway
  exposure is a later, explicit task.
- Existing ECR repositories and the current EKS cluster remain the cloud target.
- Secret provisioning will be performed by a later operator-only script or secret manager; this phase
  creates only the inventory and boundaries.
- `dev` and `dev-pilot` remain temporarily for compatibility and rollback history while `cloud`
  becomes the canonical full-stack overlay.

## Constitutional Constraints

- **Service ownership**: No service code, schema, JPA entity, or database ownership changes.
- **External ingress**: The Gateway remains the only public entry point; this phase does not expose it.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: No PostgreSQL or Redis behavior changes.
- **Messaging reliability**: No Kafka consumer, outbox, retry, or ordering behavior changes.
- **Root infrastructure ownership**: Environment documentation, Kustomize overlays, and validation
  scripts remain under root `infra/`.
- **Observability**: Existing probes and Actuator paths are reused; no manual registry is added.
- **Verification**: Kustomize render, client-side dry-run, validator tests, and Git diff checks apply.
- **Architecture decisions**: No new ADR is required because this phase introduces no service,
  discovery, ingress, persistence, or communication boundary.

## Approval and History

- 2026-08-21 — Draft created from the requested local/cloud-only deployment scope.
- 2026-08-21 — Approved for Phase 13 implementation by the user request to write the document and
  deploy the first phase.
- 2026-08-21 — Phase 13 implementation and validation evidence completed in `validation.md`.
