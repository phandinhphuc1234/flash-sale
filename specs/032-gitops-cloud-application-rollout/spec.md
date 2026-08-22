# Feature Specification: Cloud Application Rollout

**Feature Branch**: `codex/gitops-phase17-cloud-application-rollout`

**Created**: 2026-08-22

**Status**: Approved

**Input**: User request to continue the GitOps deployment after cloud secrets, platform, and
database migrations are complete.

## Problem and Scope

Phase 16 has completed the seven service-owned PostgreSQL migrations, but the eight application
Deployments still need a repeatable cloud rollout gate. The rollout must prove that immutable ECR
images, service ConfigMaps, service Secrets, platform dependencies, and health probes are wired
correctly before the environment is considered usable.

### In scope

- Apply the existing cloud application overlay after platform, secrets, and migrations are ready.
- Wait for all eight application Deployments, including the stateless API Gateway.
- Keep Payment acceptance and Stripe disabled in this bootstrap phase while exposing public health
  probes; authenticated Payment APIs remain unavailable until their enablement phase.
- Provide an operator-controlled validation/apply script that never reads or prints Secret values.
- Record rollout evidence and preserve service ownership and root `infra/` ownership.

### Out of scope

- Public ingress, DNS, TLS, autoscaling, backups, or production environment creation.
- Enabling Stripe, Payment acceptance, Kafka consumers, or recovery workers.
- API, Kafka, database schema, or service-boundary changes.
- Automatic deployment from CI or Argo CD; desired-state promotion remains a later phase.

## User Stories & Testing

### User Story 1 - Roll out the cloud application (Priority: P1)

As an operator, I want one guarded command to apply the eight application Deployments and verify
their health, so that I can tell whether the cloud environment is usable without manually guessing
which service is missing.

**Independent Test**: Run the script in validation-only mode, then with `-Apply`, and observe eight
Deployments reach `availableReplicas=1` without Secret values being displayed.

**Acceptance Scenarios**:

1. **Given** the platform, namespace, ConfigMaps, service Secrets, and seven completed migration
   Jobs exist, **when** validation runs, **then** the cloud overlay renders and client dry-run passes.
2. **Given** validation prerequisites are present, **when** apply runs, **then** all eight
   Deployments become available within the configured timeout.
3. **Given** a Deployment fails readiness, **when** the rollout waits, **then** the command stops
   and prints only resource status/log pointers, preserving the failed Pod for diagnosis.

### User Story 2 - Keep disabled Payment safe (Priority: P1)

As an operator, I want disabled Payment to remain non-functional for business APIs while Kubernetes
can still probe it, so that a bootstrap rollout does not accidentally enable payments or leave a
healthy process marked unavailable.

**Independent Test**: With `PAYMENT_ACCEPTANCE_ENABLED=false`, call the Actuator liveness and
readiness paths without credentials and verify a non-401 health response; call a Payment business
path and verify it is not publicly authorized.

**Acceptance Scenarios**:

1. **Given** Payment acceptance is disabled, **when** Kubernetes calls `/actuator/health/liveness`
   or `/actuator/health/readiness`, **then** the request is not challenged for a bearer token.
2. **Given** Payment acceptance is disabled, **when** a caller invokes a Payment business API,
   **then** the business API is not exposed as an unauthenticated endpoint.

## Edge Cases

- A missing service Secret or ConfigMap must fail before any Deployment is changed.
- An image pull or readiness failure must stop the script and leave the Pod available for inspection.
- A second apply is safe and converges existing resources; it must not recreate databases or rerun
  migration Jobs.
- A disabled Payment feature must not cause Actuator probes to return `401`.

## Requirements

- **FR-001**: The rollout MUST validate the cloud Kustomize overlay with client-side dry-run before
  applying it.
- **FR-002**: The rollout MUST require the namespace, four platform resources, eight service
  ConfigMaps, and eight service Secret boundaries by name, without reading Secret data.
- **FR-003**: The rollout MUST wait for exactly eight application Deployments: API Gateway,
  Authentication, Product, Campaign, Flash Sale, Inventory, Order, and Payment.
- **FR-004**: The rollout MUST stop on a failed Deployment rollout and preserve diagnostics.
- **FR-005**: Payment health probes MUST remain unauthenticated when acceptance is disabled, while
  Payment business APIs MUST remain protected/denied until enablement is explicitly configured.
- **FR-006**: The rollout MUST not enable Stripe, Payment consumers, recovery, or public ingress.

## Success Criteria

- **SC-001**: A prepared cloud environment reaches 8/8 available application Deployments in one
  guarded run.
- **SC-002**: A missing prerequisite is reported before live resources are changed.
- **SC-003**: Payment liveness and readiness return a non-authentication response while acceptance is
  disabled, and no unauthenticated business Payment request succeeds.
- **SC-004**: The rollout script and evidence contain no Secret values or JWT contents.

## Assumptions

- Phase 14 platform, Phase 15 Secret provisioning, and Phase 16 migrations are complete.
- ECR contains the `initial` image for each of the eight application services.
- `flash-sale` is the target namespace and `kubectl` already points to the intended EKS cluster.
- Argo CD remains the later desired-state reconciler; this phase uses an explicit operator command.

## Constitutional Constraints

- **Service ownership**: No service database or migration ownership changes; Payment security code
  remains inside Payment Service.
- **External ingress**: No ingress or gateway route is added; existing API Gateway remains the edge.
- **API/event contracts**: None changed.
- **Durable and hot-path data**: No database or Redis behavior changed.
- **Messaging reliability**: Kafka consumers and outbox/recovery workers remain disabled in bootstrap.
- **Root infrastructure ownership**: Rollout script and manifests remain under `infra/`; no service
  runtime configuration is moved.
- **Observability**: Existing Actuator, readiness, Prometheus, and trace-ID configuration is used;
  health endpoints are explicitly reachable by probes.
- **Verification**: Payment module tests, full build as required, Kustomize dry-run, script
  validation, and live Deployment rollout evidence apply; no load test is needed for bootstrap.
- **Architecture decisions**: No service-boundary or communication change; no new ADR required.
