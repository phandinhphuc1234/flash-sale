# Feature Specification: Kubernetes Manifest Bootstrap

**Feature Branch**: `023-k8s-manifests-bootstrap`
**Created**: 2026-08-20
**Status**: Approved — source manifests and client-side validation only
**Input**: User description: "Create the Kubernetes manifests for the current EKS GitOps phase, then guide manual deployment step by step."

## Problem and Scope

The EKS cluster is reachable and has three Ready nodes, but the repository needs a small,
reviewable desired-state baseline before Argo CD is introduced. The baseline must represent the
eight approved Flash Sale services without storing credentials in Git or creating live workloads.

**In scope**:

- A Kustomize base and `dev` overlay under root `infra/k8s/`.
- Namespace, non-secret runtime configuration, internal Services, Deployments, image mappings,
  resources, and health probes for the eight approved services.
- Rendering and client-side Kubernetes validation.

**Out of scope**:

- `kubectl apply` without `--dry-run`, Argo CD, ingress/load balancers, Helm, replicas/autoscaling,
  database/Redis/Kafka deployment, and service business-code changes.
- Any real secret value, Kubernetes Secret manifest, or change to a local `.env` file.

## User Scenarios & Testing

### User Story 1 - Review the intended development topology (Priority: P1)

As the project owner, I can inspect one source-controlled Kustomize overlay and see each approved
service’s intended image, internal Service, Deployment, resources, and health probes before it is
ever applied to EKS.

**Why this priority**: A reviewable desired state is the prerequisite for safe GitOps adoption.

**Independent Test**: Render `infra/k8s/overlays/dev` locally and inspect the generated resources.

**Acceptance Scenarios**:

1. **Given** the `dev` overlay, **When** it is rendered, **Then** it contains exactly one namespace,
   one non-secret runtime ConfigMap, eight Deployments, and eight ClusterIP Services.
2. **Given** an application workload, **When** its manifest is inspected, **Then** it has startup,
   liveness, and readiness probes using the service’s Actuator health paths.

---

### User Story 2 - Validate manifests without changing the cluster (Priority: P1)

As the project owner, I can validate the complete overlay on my workstation before a future GitOps
controller consumes it.

**Why this priority**: It detects malformed manifests while preserving the existing EKS cluster.

**Independent Test**: Run the repository Kubernetes client-side dry-run command.

**Acceptance Scenarios**:

1. **Given** a valid `kubectl` installation, **When** client-side dry-run is run for the `dev`
   overlay, **Then** validation succeeds and no resources are created in EKS.

## Edge Cases

- A real application Pod would not yet be healthy because the project has not decided where its
  PostgreSQL, Redis, Kafka, and Schema Registry dependencies run or how they are reached from EKS.
  This feature therefore does not apply the overlay.
- A referenced `flash-sale-secrets` Secret is intentionally absent from Git. A future live deploy
  must create it by an approved secret-management procedure before Pods are created.
- `initial` is a placeholder ECR image tag for rendering only; a future delivery workflow must use
  an immutable image tag.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST keep shared Kubernetes manifests under `infra/k8s/` using a Kustomize
  base and a `dev` overlay.
- **FR-002**: The base MUST define the `flash-sale` Namespace and workloads for exactly these
  services: api-gateway, authentication-service, campaign-service, flash-sale-service,
  inventory-service, order-service, payment-service, and product-service.
- **FR-003**: Each workload MUST have a Deployment and a ClusterIP Service exposing port 8080.
- **FR-004**: Each workload MUST consume only non-secret runtime settings from a ConfigMap and may
  reference a manually managed `flash-sale-secrets` Secret; no Secret value or Secret manifest may
  be committed by this feature.
- **FR-005**: Each workload MUST provide startup, liveness, and readiness probes using
  `/actuator/health/liveness` or `/actuator/health/readiness` as appropriate.
- **FR-006**: The `dev` overlay MUST map every service placeholder image to its existing ECR
  repository with the source-only placeholder tag `initial`.
- **FR-007**: Every Service MUST remain `ClusterIP`; public exposure remains a later api-gateway
  ingress decision.
- **FR-008**: The changed overlay MUST pass `kubectl kustomize` and
  `kubectl apply --dry-run=client -k infra/k8s/overlays/dev`.

### Key Entities

- **Kubernetes workload manifest**: Desired state for one independently deployable service.
- **Runtime ConfigMap**: Shared, non-sensitive process settings for this bootstrap environment.
- **External Secret reference**: The name-only reference to credentials managed outside Git.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Rendering the `dev` overlay produces 18 resources: 1 Namespace, 1 ConfigMap,
  8 Deployments, and 8 Services.
- **SC-002**: Kubernetes client-side dry-run completes successfully without creating live Pods.
- **SC-003**: A repository scan of this feature’s new manifest files contains no Kubernetes `Secret`
  resource or secret value.

## Assumptions

- The existing EKS cluster and ECR repositories are the intended development targets.
- All eight current services bind their HTTP/Actuator endpoints on port 8080.
- A single namespace-scoped secret named `flash-sale-secrets` will be created outside Git before
  the first live application deployment; its keys and backing data topology need a separate
  approved decision.
- The source-controlled baseline does not attempt to make applications runnable until backing
  services and secret management are approved.

## Constitutional Constraints

- **Service ownership**: No service source, database schema, JPA entity, or service boundary changes.
- **External ingress**: All services remain internal ClusterIP; future external traffic must enter
  through api-gateway only.
- **API/event contracts**: N/A; no HTTP or Kafka contract changes.
- **Durable and hot-path data**: N/A; no PostgreSQL or Redis behavior changes.
- **Messaging reliability**: N/A; no Kafka producer or consumer changes.
- **Root infrastructure ownership**: Shared Kustomize assets are owned by root `infra/k8s/`; no ADR
  is required because this follows the existing constitutional location and does not alter the
  deployment architecture.
- **Observability**: Manifests consume the current Actuator liveness/readiness endpoints; no custom
  metrics registry or application observability code changes.
- **Verification**: Kubernetes render and client-side dry-run apply. Maven tests are not applicable
  because no Java source or dependency changes occur.
- **Architecture decisions**: N/A; this establishes source-only manifests and intentionally defers
  live data-plane/ingress decisions.
