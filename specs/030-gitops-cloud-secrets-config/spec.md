# Feature Specification: Cloud Secrets and Service Configuration

**Feature Branch**: `030-gitops-cloud-secrets-config`

**Created**: 2026-08-21

**Status**: Approved

**Input**: User description: "After the cloud stateful foundation, inventory the whole repository so no required secret or ConfigMap is missing, tell me exactly what must be entered manually, and prepare the cloud services without committing secret values."

## Problem and Scope

### Problem Statement

The cloud overlay currently gives every application Deployment one shared ConfigMap and one shared
Secret name. That is not enough for a full deployment: each service owns a different PostgreSQL
database, some services need Redis/Kafka endpoints, Authentication needs JWT key files, and Payment
has provider credentials that must not be exposed to unrelated Pods. The repository also has many
optional tuning properties with safe application defaults; copying all of them into Git would make
the configuration noisy and obscure the values that truly block startup.

### In Scope

- Inventory all runtime environment placeholders used by the eight cloud application services,
  platform manifests, Compose, and `.env.example`.
- Classify each value as required manual secret, non-secret Cloud ConfigMap value, local-only value,
  or deferred optional value.
- Add service-specific Cloud ConfigMaps with the required non-secret endpoints and feature flags.
- Replace the shared cloud `envFrom` Secret with least-privilege Secret names per service.
- Add an operator-only PowerShell preflight/provisioning script that reads local values without
  printing them and can create Kubernetes Secrets only when explicitly requested.
- Mount the Authentication JWT key Secret at the file locations expected by the service.
- Keep Stripe values documented but disabled/deferred until the Payment enablement phase.

### Out of Scope

- Committing any Secret manifest containing a value.
- Printing, uploading, or transmitting `.env`, JWT PEM, OAuth, or Stripe values.
- Changing Java business behavior, HTTP/Kafka contracts, schemas, migrations, or service boundaries.
- Enabling Stripe, public ingress, or a production HTTPS cookie policy.
- Creating configuration for `cart-service` or `notification-service` in the cloud overlay; those
  services are not part of the approved eight-service cloud topology.

### Non-goals

- This phase does not run a live application rollout.
- This phase does not run Liquibase migrations; cloud ConfigMaps keep migration flags disabled and a
  later migration phase will run service-owned migrations in order.
- This phase does not replace the local `.env`; it consumes it only through an operator-controlled
  script.

## Baseline References

- `infra/docker/.env.example` and ignored `infra/docker/.env`.
- `infra/docker/compose.yml` service environment blocks.
- `services/*/src/main/resources/application.yml` placeholders.
- `infra/k8s/base/*/deployment.yaml` shared ConfigMap/Secret references.
- `infra/k8s/overlays/cloud/platform/*` PostgreSQL/Redis Secret references.
- `infra/ENVIRONMENTS.md` environment and secret boundary documentation.

## User Scenarios & Testing

### User Story 1 - Know exactly what must be entered manually (Priority: P1)

As an operator, I want a complete secret inventory with a manual/deferred classification, so that I
can prepare the cloud rollout without guessing or pasting credentials into Git.

**Why this priority**: A missing JWT, OAuth, database, Redis, or HMAC value prevents startup and is
harder to diagnose after a partial rollout.

**Independent Test**: Run the preflight against the ignored local `.env`; it reports missing key
names and JWT file paths only, never values.

**Acceptance Scenarios**:

1. **Given** the local `.env` contains all Phase 15 values, **when** preflight runs, **then** it
   reports that required manual inputs are present without printing any value.
2. **Given** a required key is missing, **when** preflight runs, **then** it exits non-zero and names
   only the missing key and the owning Kubernetes Secret.
3. **Given** Stripe values are absent, **when** preflight runs without `-EnableStripe`, **then** it
   succeeds and reports Stripe as deferred.

### User Story 2 - Give each service complete non-secret runtime configuration (Priority: P1)

As an operator, I want one Cloud ConfigMap per application service, so that datasource, Redis, Kafka,
Schema Registry, service DNS, JWT, topic, and feature-flag configuration is explicit and does not
leak across service boundaries.

**Why this priority**: One shared ConfigMap cannot represent the eight different database URLs or the
Kubernetes DNS name `flash-sale-service` used by the Gateway.

**Independent Test**: Render the cloud overlay and inspect every application Deployment's
`envFrom`; each uses the common runtime ConfigMap, exactly one service ConfigMap, and its own Secret.

**Acceptance Scenarios**:

1. **Given** the cloud overlay is rendered, **when** a service Deployment is inspected, **then** its
   datasource and dependency endpoints point to internal Kubernetes DNS names and its Secret name is
   service-specific.
2. **Given** the Gateway is rendered, **when** its routes are inspected, **then** Flash Sale points
   to `http://flash-sale-service:8080`, not the local Compose name `flashsale-service`.
3. **Given** Payment is rendered, **when** feature flags are inspected, **then** Stripe remains
   disabled until the later enablement phase.

### User Story 3 - Provision Secrets safely when explicitly requested (Priority: P1)

As an operator, I want an explicit dry-run/validate mode and an opt-in apply mode, so that a script
cannot mutate the cluster or expose credential values accidentally.

**Why this priority**: Secret provisioning is the highest-risk operational step in this phase.

**Independent Test**: Run preflight without `-Apply`, then run a value-leak scan; no Kubernetes
Secret is changed and no value appears in output.

**Acceptance Scenarios**:

1. **Given** preflight is run without `-Apply`, **when** it completes, **then** it only validates
   local inputs and cluster context.
2. **Given** `-Apply` is supplied and all required inputs exist, **when** the script provisions,
   **then** it creates per-service Secrets and `auth-jwt` without printing values.
3. **Given** `-EnableStripe` is omitted, **when** the script provisions, **then** Stripe keys are
   not required and Payment remains disabled by ConfigMap.

### Edge Cases

- The `.env` file is missing or not ignored; preflight must stop before reading values.
- `AUTH_JWT_KEY_DIR` is missing or either PEM file is absent; preflight must name the missing path.
- The existing `flash-sale-secrets` from Product pilot lacks a key; preflight must not silently reuse
  it for cloud Pods.
- A service ConfigMap references a key not consumed by its `application.yml`; it should be removed
  or documented as an intentional compatibility setting, not copied indiscriminately.

## Requirements

### Functional Requirements

- **FR-001**: The repository MUST document every manual Phase 15 secret key and its owning Kubernetes
  Secret name.
- **FR-002**: Phase 15 manual inputs MUST include `POSTGRES_USER`, `POSTGRES_PASSWORD`,
  `REDIS_PASSWORD`, `RATE_LIMIT_KEY_HMAC_SECRET`, `AUTH_THROTTLE_HMAC_SECRET`,
  `CAMPAIGN_CLIENT_SECRET`, `FLASHSALE_CLIENT_SECRET`, and the two JWT PEM files.
- **FR-003**: Stripe keys MUST be classified as deferred unless `-EnableStripe` is explicitly used.
- **FR-004**: Cloud application Deployments MUST use service-specific ConfigMaps and Secret names;
  the shared `flash-sale-secrets` reference MUST NOT remain in the canonical cloud overlay.
- **FR-005**: The Authentication Deployment MUST mount `auth-jwt` as
  `/run/secrets/auth-jwt/jwt-public.pem` and `jwt-private.pem`.
- **FR-006**: Cloud ConfigMaps MUST set service-specific PostgreSQL URLs, internal Redis/Kafka/
  Schema Registry endpoints, internal HTTP URLs, JWT settings, topic settings, and safe feature flags.
- **FR-007**: Cloud ConfigMaps MUST keep Liquibase disabled in this phase; migration execution is
  deferred to a later service-owned migration phase.
- **FR-008**: Payment Cloud ConfigMap MUST keep `STRIPE_ENABLED`, `PAYMENT_ACCEPTANCE_ENABLED`, and
  `PAYMENT_CHECKOUT_ENABLED` false until the Payment enablement phase.
- **FR-009**: The provisioning script MUST default to validation-only, must require an explicit
  `-Apply` for mutation, and must never print Secret values.
- **FR-010**: The provisioning script MUST create separate Secrets for platform, Gateway,
  Authentication, Product, Campaign, Flash Sale, Inventory, Order, and Payment boundaries.
- **FR-011**: No HTTP/Kafka contract, service-owned migration, Java source, or business behavior MUST
  change.

### Non-Functional Requirements

- **NFR-SEC-001**: No Secret value or PEM body may be committed, logged, or included in rendered
  Kubernetes output.
- **NFR-SEC-002**: Payment provider keys MUST be visible only to `payment-service` when enabled.
- **NFR-OPS-001**: A new operator MUST be able to run a validation-only command and understand every
  missing manual input from the documentation.
- **NFR-VAL-001**: The cloud overlay MUST pass Kustomize render and client-side dry-run before any
  Secret apply.

## Key Entities

- **Secret Boundary**: A named Kubernetes Secret scoped to one platform or service owner.
- **Service Runtime Config**: A named ConfigMap containing only non-secret values consumed by one
  application Deployment.
- **Manual Input Inventory**: A key/path checklist sourced from `.env` and JWT files without values.

## Success Criteria

### Measurable Outcomes

- **SC-001**: The inventory lists every Phase 15 manual input and classifies Stripe as deferred.
- **SC-002**: The rendered cloud overlay contains eight service ConfigMaps and eight service-specific
  Secret references, plus the platform and JWT Secret references.
- **SC-003**: No application Deployment in the cloud overlay references `flash-sale-secrets`.
- **SC-004**: Validation-only preflight succeeds without mutating Kubernetes and prints zero secret
  values; missing-input mode returns non-zero.
- **SC-005**: Kustomize render and client-side dry-run pass with no Secret values present.
- **SC-006**: No Java module, contract, migration, or Terraform resource changes.

## Assumptions

- The ignored `infra/docker/.env` remains the operator's local input file.
- `AUTH_JWT_KEY_DIR` contains `jwt-public.pem` and `jwt-private.pem` for cloud Secret creation.
- Cloud is initially tested over port-forward, so `AUTH_COOKIE_SECURE=false` and localhost CORS are
  intentional temporary settings.
- Kafka and Schema Registry remain unauthenticated and internal under ADR 0019.
- The existing EKS namespace `flash-sale` and `flash-sale-secrets` may exist, but the cloud overlay
  will not consume the old global Secret.

## Human Decisions Required

None for Phase 15. Stripe enablement and public HTTPS origin are deferred decisions for later phases.

## Constitutional Constraints

- **Service ownership**: ConfigMaps/Secrets do not grant cross-service database access; each URL points
  to that service's own logical database.
- **External ingress**: Gateway remains the only public application entry point.
- **API/event contracts**: No contract changes.
- **Durable and hot-path data**: PostgreSQL/Redis endpoints preserve the Phase 14 ownership model.
- **Messaging reliability**: Kafka/topic configuration is explicit; no consumer behavior changes.
- **Root infrastructure ownership**: ConfigMaps, patches, scripts, and inventory remain under root
  `infra/`.
- **Observability**: Existing probes/configuration remain active.
- **Verification**: Inventory scan, script tests, Kustomize render, dry-run, and secret-value scan.
- **Architecture decisions**: ADR 0020 records the per-service Secret boundary.

## Approval and History

- 2026-08-21 — Draft created after repository-wide runtime configuration inventory.
- 2026-08-21 — Approved for implementation by the user's request to continue Phase 15.
