# Feature Specification: Argo CD Dev Pilot Bootstrap

**Feature Branch**: `codex/gitops-phase10-argocd`
**Created**: 2026-08-21
**Status**: Approved for implementation
**Input**: User request: "Phase 10 — kết nối Argo CD với Product pilot GitOps."

## Problem and Scope

### Problem Statement

The Product pilot desired state now exists in Git and runs on EKS, but an operator still has to run
`kubectl apply` manually. The project needs a small, reviewable Argo CD bootstrap so Git becomes the
source of reconciliation for the pilot.

### In Scope

- Install one non-HA Argo CD control plane in the `argocd` namespace using a pinned official
  manifest version.
- Keep Argo CD server internal as `ClusterIP` and document local port-forward access.
- Create one Argo CD `Application` pointing at the public repository's `develop` branch and
  `infra/k8s/overlays/dev-pilot` path.
- Enable automated sync and self-heal while disabling pruning to protect the training PVC.
- Add a validation-by-default PowerShell bootstrap script with explicit `-Apply`.

### Out of Scope

- HA/multi-tenant Argo CD, SSO, RBAC customization, notifications, ApplicationSets, or image updater.
- Public LoadBalancer, Ingress, DNS, TLS, or exposing the Argo CD UI to the Internet.
- Private GitHub repository credentials, secret manager integration, or Secret value management.
- CI image promotion, branch protection, production environments, or the other seven services.
- Changes to Product Java code, PostgreSQL schema, or EBS storage.

### Non-goals

- This phase bootstraps a safe development reconciliation loop; it is not a production Argo CD
  hardening exercise.
- Argo CD does not own credentials. Existing external Secrets remain operator-managed.

## User Scenarios & Testing

### User Story 1 - Internal Argo CD control plane (Priority: P1)

As an infrastructure operator, I want a pinned Argo CD installation inside EKS, so that the
reconciliation controller is reproducible and not publicly exposed.

**Why this priority**: The controller must exist and be reachable safely before an Application can
reconcile workloads.
**Independent Test**: Apply the pinned manifest and observe all Argo CD workloads Ready with only
internal Services.

#### Acceptance Scenarios

1. **Given** the EKS cluster, **when** the bootstrap runs, **then** the `argocd` namespace and Argo
   CD CRDs/workloads become Ready.
2. **Given** the Argo CD Services, **when** they are inspected, **then** `argocd-server` remains
   `ClusterIP` and no LoadBalancer or Ingress is created.
3. **Given** the local operator, **when** port-forward is used, **then** the UI/API is reachable
   without opening an AWS public endpoint.

### User Story 2 - Pilot reconciliation (Priority: P1)

As a developer, I want Argo CD to track the Product pilot overlay from Git, so that changes merged
to `develop` reconcile the pilot without applying unrelated services.

**Why this priority**: This is the first observable GitOps loop for the project.
**Independent Test**: Create the Application, wait for `Synced/Healthy`, and verify it manages only
the six pilot resources.

#### Acceptance Scenarios

1. **Given** the public repository and `develop` branch, **when** the Application is created, **then**
   its source path is exactly `infra/k8s/overlays/dev-pilot`.
2. **Given** the pilot Application, **when** Argo CD syncs, **then** Product and PostgreSQL remain
   Ready and unrelated services are not created.
3. **Given** a harmless desired-state change merged to `develop`, **when** self-heal runs, **then**
   the live pilot converges without pruning the PVC.

### User Story 3 - Safe repeatable bootstrap (Priority: P2)

As the project operator, I want a script that validates by default and applies only with an explicit
switch, so that Argo CD installation cannot happen accidentally.

**Why this priority**: The bootstrap creates cluster-scoped resources and needs an explicit gate.
**Independent Test**: Run without `-Apply` and observe no mutation; run with `-Apply` and observe
readiness plus Application status.

#### Acceptance Scenarios

1. **Given** no `-Apply`, **when** the script runs, **then** it performs client-side validation and
   prints the pinned URL without applying the remote manifest.
2. **Given** `-Apply`, **when** the script runs, **then** it applies only the pinned Argo CD install
   and the pilot Application, waits for readiness, and never prints the admin password.

### Edge Cases

- Existing `argocd` resources are upgraded in place; the script must be safe to rerun.
- A GitHub source or path error must surface as `Application` OutOfSync/ComparisonError, not trigger
  a fallback to another branch/path.
- Missing external application Secrets remain a workload readiness problem; Argo CD must not create
  secret values.
- The Argo CD admin secret is retrieved manually by the operator and never emitted by the script.

## Requirements

### Functional Requirements

- **FR-001**: The bootstrap MUST use a pinned official Argo CD non-HA manifest version and the
  `argocd` namespace.
- **FR-002**: Argo CD server MUST remain `ClusterIP`; no public LoadBalancer, Ingress, DNS, or TLS
  resource may be created by this feature.
- **FR-003**: The pilot Application MUST use repository
  `https://github.com/phandinhphuc1234/flash-sale.git`, revision `develop`, and path
  `infra/k8s/overlays/dev-pilot`.
- **FR-004**: The Application MUST target namespace `flash-sale` and enable automated sync with
  self-heal while disabling prune.
- **FR-005**: The Application MUST not include the full eight-service overlay or any unrelated
  service path.
- **FR-006**: The Phase 10 script MUST validate by default and require `-Apply` for cluster mutation.
- **FR-007**: The script MUST not print, create, or persist Secret values or the Argo CD admin
  password.
- **FR-008**: The bootstrap MUST provide readiness and Application status commands for operator
  verification.

### Non-Functional Requirements

- **NFR-SAFE-001**: A rerun MUST be idempotent and MUST NOT delete the Product PVC.
- **NFR-OBS-001**: Argo CD workload health and Application sync/health status MUST be observable via
  kubectl.
- **NFR-REPRO-001**: The manifest version, repository URL, revision, and path MUST be source-visible.

## Key Entities

- **Argo CD control plane**: Namespaced controller workloads and CRDs that reconcile Applications.
- **Pilot Application**: The Argo CD declarative link from Git revision/path to the `flash-sale`
  namespace.
- **Git source**: Repository URL, revision, and path that define the desired state.

## Success Criteria

- **SC-001**: All Argo CD core workloads report Ready after bootstrap.
- **SC-002**: `argocd-server` is `ClusterIP` and there are zero Argo CD LoadBalancer Services or
  Ingress resources.
- **SC-003**: The pilot Application reports `Synced` and `Healthy` and manages only the six pilot
  resources.
- **SC-004**: PostgreSQL remains `1/1 Running`, Product remains `1/1 Running`, and its PVC remains
  `Bound` after the first Argo sync.
- **SC-005**: A default script run causes no live mutation and emits no Secret value.

## Dependencies and Compatibility

- Phase 9 must be merged and the `dev-pilot` overlay must already be valid.
- The repository is public; no GitHub credentials are required for this pilot.
- External Secrets `product-postgres-credentials` and `flash-sale-secrets` remain outside Git.
- Argo CD version is pinned in the plan/script and must be upgraded deliberately later.

## Assumptions

- The development cluster has enough capacity for the non-HA Argo CD control plane.
- The operator accesses Argo CD through `kubectl port-forward`, not an AWS public endpoint.
- Automated sync with `prune: false` is an acceptable first GitOps safety posture.

## Constitutional Constraints

- **Service ownership**: Argo CD only reconciles existing Product/PostgreSQL manifests; no service
  boundary or database ownership changes.
- **External ingress**: Argo CD and pilot Services remain internal; public ingress remains a later
  api-gateway decision.
- **API/event contracts**: No HTTP/Kafka contract changes.
- **Durable and hot-path data**: PostgreSQL/PVC remains durable; no Redis change.
- **Messaging reliability**: No Kafka change.
- **Root infrastructure ownership**: Bootstrap script and Application resource remain under root
  `infra/`.
- **Observability**: Argo CD workload and Application health are explicitly checked.
- **Verification**: Manifest validation, readiness, Service type, Application sync/health, and pilot
  PVC/workload checks apply; Maven tests are not applicable.
- **Architecture decisions**: ADR 0008 records controller ownership, source-of-truth boundaries,
  and rollback.

## Approval and History

- 2026-08-21 — Draft created after Phase 9 merge.
- 2026-08-21 — Owner approved the bounded non-HA Argo CD pilot scope.
