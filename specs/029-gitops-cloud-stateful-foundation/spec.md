# Feature Specification: Cloud Stateful Service Foundation

**Feature Branch**: `029-gitops-cloud-stateful-foundation`

**Created**: 2026-08-21

**Status**: Verified

**Input**: User description: "Continue the local/cloud-only GitOps rollout by adding the minimum single-node PostgreSQL, Redis, Kafka, and Schema Registry foundation to the cloud EKS environment."

## Problem and Scope

### Problem Statement

The canonical cloud overlay now contains the eight application services, but those services do not
yet have the internal durable database, hot-path cache, event broker, or schema registry endpoints
that they require. Deploying application Pods before these dependencies exist would produce noisy
readiness failures and make service-level debugging misleading.

### In Scope

- Add one cloud PostgreSQL StatefulSet with durable storage and isolated logical databases for the
  eight application services.
- Add one cloud Redis StatefulSet with append-only persistence for flash-sale hot-path coordination.
- Add one single-node Kafka KRaft StatefulSet with durable storage and internal DNS.
- Add one Schema Registry Deployment with an internal Service and readiness probe.
- Keep all four dependencies private to the `flash-sale` namespace.
- Make the resources renderable without committing credential values.
- Add a preflight script that verifies EBS CSI/storage prerequisites and reports missing Secrets
  without printing their values.

### Out of Scope

- High availability, replicas greater than one, managed RDS/ElastiCache/MSK, or cross-zone failover.
- Public exposure of PostgreSQL, Redis, Kafka, or Schema Registry.
- Application-specific Secret provisioning, database migrations, or service Deployments.
- Kafka UI, Prometheus, Grafana, or an external ingress.
- Changes to Java code, HTTP contracts, Kafka event schemas, or business behavior.

### Non-goals

- This is a learning/demo cloud foundation, not a production durability design.
- A single PostgreSQL instance does not transfer schema ownership to infrastructure; each service's
  migrations remain owned and executed by that service in a later phase.
- Redis is not a durable source of business truth; PostgreSQL remains authoritative.

## Baseline References

- `infra/docker/compose.yml` is the local reference topology for image names, ports, and bootstrap
  database names.
- `infra/k8s/overlays/cloud/` is the canonical full-stack cloud overlay from Feature 028.
- `infra/k8s/base/product-postgres/` demonstrates the existing EBS-backed StatefulSet pattern.
- `infra/terraform/ebs-csi.tf` and the live EBS CSI addon provide the cloud storage driver.
- `infra/docker/postgres/init/01-create-databases.sql` defines the local logical database baseline.
- `docs/adr/0007-eks-stateful-product-pilot.md` records the earlier Product-only stateful pilot.

## User Scenarios & Testing

### User Story 1 - Start the cloud platform dependencies (Priority: P1)

As an operator, I want PostgreSQL, Redis, Kafka, and Schema Registry manifests in the cloud overlay,
so that application services have stable internal dependencies before they are deployed.

**Why this priority**: The application cannot become ready without these platform endpoints.

**Independent Test**: Render and dry-run the cloud overlay; it must contain one StatefulSet each for
PostgreSQL, Redis, and Kafka, one Schema Registry Deployment, internal Services, and no public Service.

**Acceptance Scenarios**:

1. **Given** the cloud overlay is checked out, **when** Kustomize renders it, **then** the four
   platform components and their probes are present in namespace `flash-sale`.
2. **Given** no Secret values are committed, **when** client-side dry-run runs, **then** rendering
   succeeds and only Secret references are present.

### User Story 2 - Preserve data and service discovery (Priority: P1)

As a service operator, I want durable volumes and stable Kubernetes DNS names, so that a Pod restart
does not change the dependency endpoint or discard the platform's local state.

**Why this priority**: Kafka offsets, Schema Registry metadata, Redis AOF data, and application
database state must survive a container restart during the demo.

**Independent Test**: Inspect the rendered resources and verify PVC templates, internal Services,
storage class `gp2`, and the endpoint names `postgres`, `redis`, `kafka`, and `schema-registry`.

**Acceptance Scenarios**:

1. **Given** a cloud StatefulSet Pod is restarted, **when** Kubernetes reattaches its PVC, **then**
   the Pod keeps the same service DNS name and storage claim.
2. **Given** an application config points to a platform DNS name, **when** the corresponding Service
   exists, **then** traffic remains inside the cluster and does not need a public IP.

### User Story 3 - Keep credentials outside Git (Priority: P1)

As a maintainer, I want stateful manifests to reference operator-managed Secrets, so that cloud
credentials can be added locally without entering the repository or command output.

**Why this priority**: PostgreSQL and Redis require credentials, and leaking them would compromise the
cloud environment.

**Independent Test**: Search the changed manifests and preflight output; there must be no credential
values, while missing Secret names are reported as actionable prerequisites.

**Acceptance Scenarios**:

1. **Given** `flash-sale-secrets` is absent, **when** preflight runs without `-RequireSecrets`,
   **then** it reports Secret provisioning as deferred and exits successfully for manifest work.
2. **Given** `flash-sale-secrets` is required for a live apply, **when** preflight runs with
   `-RequireSecrets`, **then** it fails without printing any Secret data.

### Edge Cases

- The EBS CSI driver or `gp2` StorageClass is missing; preflight must fail before a live apply.
- A PVC is Pending; the operator must inspect CSI events rather than deleting application data.
- Kafka or Schema Registry is not ready; application rollout is deferred until their readiness
  probes pass.
- A pre-existing Product-only PostgreSQL resource exists from `dev-pilot`; the new cloud overlay uses
  the canonical `postgres` name and does not manage the pilot resource.

## Requirements

### Functional Requirements

- **FR-001**: The cloud overlay MUST define exactly one PostgreSQL StatefulSet named `postgres` with
  one replica and a `gp2` PVC template.
- **FR-002**: PostgreSQL bootstrap MUST create isolated logical databases for `auth`, `product`,
  `campaign`, `flashsale`, `inventory`, `order`, and `payment`; schema migrations remain service-owned.
- **FR-003**: The cloud overlay MUST define exactly one Redis StatefulSet named `redis` with one
  replica, password Secret reference, append-only persistence, and an internal Service.
- **FR-004**: The cloud overlay MUST define exactly one Kafka KRaft StatefulSet named `kafka` with
  one broker/controller, one replica, internal advertised listener `kafka:9092`, and durable storage.
- **FR-005**: The cloud overlay MUST define exactly one Schema Registry Deployment named
  `schema-registry` with an internal Service at port `8081` and a readiness probe on `/subjects`.
- **FR-006**: All platform Services MUST be internal ClusterIP or headless Services and MUST NOT use
  `LoadBalancer` or `NodePort`.
- **FR-007**: PostgreSQL and Redis credential values MUST be obtained from the operator-managed
  Kubernetes Secret `flash-sale-secrets`; no value may be stored in Git.
- **FR-008**: The preflight script MUST verify the EBS CSI driver and `gp2` StorageClass, and MUST
  support a strict mode that checks `flash-sale-secrets` without reading its data.
- **FR-009**: The cloud overlay MUST remain client-side renderable before Secrets are provisioned.
- **FR-010**: No service boundary, API/event contract, business behavior, or service-owned migration
  MUST change in this feature.

### Non-Functional Requirements

- **NFR-SEC-001**: No password, token, private key, or provider credential may appear in Git,
  rendered output, or preflight output.
- **NFR-REL-001**: Each stateful workload MUST have startup/readiness/liveness checks appropriate to
  its protocol.
- **NFR-OPS-001**: Each dependency MUST be reachable by a stable in-cluster DNS name documented for
  the later service configuration phase.
- **NFR-VAL-001**: The cloud overlay MUST pass `kubectl apply --dry-run=client -k` on Windows
  PowerShell and CI.

## Key Entities

- **Cloud PostgreSQL platform**: One durable instance with separate logical databases owned by the
  application services.
- **Redis hot-path platform**: One append-only Redis instance used for atomic flash-sale operations,
  never as authoritative business storage.
- **Kafka event platform**: One KRaft broker/controller for versioned event transport in the demo
  environment.
- **Schema Registry platform**: One internal HTTP service storing Kafka schema metadata.
- **Platform Secret reference**: A name/key reference to operator-managed credentials, without the
  credential value.

## Success Criteria

### Measurable Outcomes

- **SC-001**: `kubectl kustomize infra/k8s/overlays/cloud` renders the four platform components and
  all eight application services with no error.
- **SC-002**: `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` passes without a live
  resource change.
- **SC-003**: The rendered platform contains exactly 3 StatefulSets, 1 Deployment, and 4 internal
  Services for PostgreSQL, Redis, Kafka, and Schema Registry.
- **SC-004**: All three stateful workloads declare PVC templates using `gp2` and one replica.
- **SC-005**: Preflight returns success for available EBS CSI/StorageClass prerequisites and returns
  a non-zero result for a missing required Secret in strict mode without printing values.
- **SC-006**: No changed file contains a provider key, webhook secret, password value, or PEM body.

## Assumptions

- The existing EKS cluster has the AWS EBS CSI addon and `gp2` StorageClass, as proven by the Product
  pilot.
- The three `m7i-flex.large` nodes are sufficient for a single-node platform foundation plus the
  application rollout when workloads are started in batches.
- Kafka and Schema Registry run without authentication inside the private cluster for this learning
  deployment; network exposure remains internal.
- `flash-sale-secrets` will be created in Phase 15 from operator-local values.
- The cloud overlay uses one replica per platform component; no HA claim is made.

## Human Decisions Required

None for this approved demo scope. Moving to managed services or HA would require a separate feature
and ADR.

## Constitutional Constraints

- **Service ownership**: Infrastructure creates logical database containers only; migrations and
  schemas remain owned by each service.
- **External ingress**: Platform services remain private; application traffic still enters through
  API Gateway later.
- **API/event contracts**: No contract changes; Kafka remains the existing versioned transport.
- **Durable and hot-path data**: PostgreSQL is durable truth; Redis is AOF-backed coordination only.
- **Messaging reliability**: No consumer/outbox behavior changes; broker replication is intentionally
  one for the demo and recorded as a limitation.
- **Root infrastructure ownership**: All manifests and scripts remain in root `infra/k8s` and
  `infra/scripts`; no service module owns platform resources.
- **Observability**: Platform probes are added; service Actuator behavior remains unchanged.
- **Verification**: Kustomize, dry-run, preflight, secret-safety scan, and live readiness checks after
  Phase 15 apply.
- **Architecture decisions**: See ADR 0019 for the single-node in-cluster decision.

## Approval and History

- 2026-08-21 — Draft created from the local/cloud-only full-stack rollout plan.
- 2026-08-21 — Approved for implementation by the user's request to continue to the next phase.
- 2026-08-21 — Phase 14 manifests, preflight, and client-side validation completed.
