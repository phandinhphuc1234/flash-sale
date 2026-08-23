# Feature Specification: Internal Authenticated Cloud End-to-End Smoke

**Feature Branch**: `codex/gitops-phase22-internal-e2e`

**Created**: 2026-08-22

**Status**: Approved for planning

**Input**: User request to complete canonical GitOps roadmap Phase 22 after the eight-service
delivery and release verification gates are green.

## Problem and Scope

The cloud environment currently proves only Gateway readiness, public catalog access, and anonymous
admin rejection. That does not prove the business path from authentication through catalog,
inventory, campaign activation, flash-sale admission, and Order consumption. This feature adds a
repeatable, operator-run smoke that enters through the internal Gateway port-forward and verifies
the real authenticated journey without reading another service's database or mutating Kubernetes
objects.

In scope:

- a read-only preflight for the expected EKS context, Argo health, cloud Gateway Deployment, and
  required routes;
- an operator-supplied admin login used only in memory to obtain a JWT with the existing
  `ROLE_ADMIN` authorities;
- an automatically registered disposable shopper account and a shopper JWT;
- Product draft creation, server-owned Variant composition, publication, and Inventory stock adjustment through the
  documented Gateway APIs, after an approved Inventory initialization path is available;
- Campaign creation, item replacement, scheduling, and activation through the documented Gateway
  APIs;
- one authenticated Flash Sale reservation through Gateway with trace and idempotency headers;
- polling the owner reservation endpoint and the owner Order list until the durable
  `PurchaseAcceptedV1` consumer result is visible;
- sanitized evidence containing IDs, HTTP statuses, state transitions, elapsed time, and cleanup
  outcome, but never passwords, JWTs, cookies, or Secret values.

Out of scope:

- direct `kubectl exec`, SQL, Redis, Kafka, Schema Registry, or service-local database access;
- creating or elevating an admin account by modifying `auth_db`;
- public LoadBalancer/Ingress/DNS/TLS exposure;
- Stripe or Payment enablement;
- changing service Java code, HTTP contracts, Kafka contracts, or Kubernetes Secret values;
- load testing, rollback testing, or observability dashboards (later roadmap phases).

## Approved Decisions

1. **Admin credential source**: use an already provisioned `ROLE_ADMIN` account. The runner accepts
   the login as a parameter and reads the password with `Read-Host -AsSecureString` at runtime. The
   credential is never stored in `.env`, Kubernetes, Git, or logs. If login or authorities fail, the
   runner stops without attempting account elevation.

2. **Business fixture and Inventory initialization**: add an explicitly approved, Inventory-owned,
   one-off fixture Job/CLI that calls the Inventory application use case for the disposable Product
   Variant before the Gateway smoke. Product owns Variant identity: the composition request omits
   `Variant.id` for a new Variant, then the runner reads the server-assigned id from Product detail
   before initializing Inventory. The smoke never executes SQL or touches another service database.
   Product/Campaign fixtures use unique identifiers and remain inactive/archived after the run because
   Campaign has no delete endpoint; IDs are reported for manual cleanup.

3. **Order assertion**: poll `GET /api/v1/orders?page=0&size=100` as the shopper to discover a new
   owned Order, then query `GET /api/v1/orders/{orderId}` and require its `purchaseRequestId`,
   `reservationId`, `campaignId`, and `variantId` to match the reservation. The documented initial
   `PENDING_PAYMENT` state is accepted.

## User Scenarios & Testing

### User Story 1 - Provision a safe cloud smoke context (Priority: P1)

As an operator, I want the smoke to verify that it is connected to the intended EKS cluster and
healthy Argo-managed cloud overlay before creating any business fixture.

**Why this priority**: A wrong Kubernetes context or stale release could make a passing smoke
misleading or mutate the wrong environment.

**Independent Test**: Run validation mode and verify the expected context, Argo `Synced/Healthy`,
Gateway readiness, and anonymous admin `401/403` without a port-forward mutation beyond the
temporary process.

**Acceptance Scenarios**:

1. **Given** the current context is not `flash-sale-dev`, **when** the smoke starts, **then** it
   fails before registration or admin calls.
2. **Given** Argo is not `Synced/Healthy` or the cloud Kustomize dry-run fails, **when** the smoke
   starts, **then** it fails without creating a fixture.
3. **Given** healthy prerequisites, **when** anonymous admin access is probed, **then** the response
   is `401` or `403` and the script opens only a loopback Gateway port-forward.

### User Story 2 - Exercise the authenticated catalog-to-campaign path (Priority: P1)

As an operator, I want the smoke to create and activate a disposable sale through the supported
HTTP boundaries so that Product, Inventory, and Campaign integration is proven without cross-service
database access.

**Why this priority**: Flash Sale admission is not meaningful unless its Product variant, stock, and
active Campaign projection were established through their owners.

**Independent Test**: With an existing admin account, create a unique Product and Campaign through
Gateway and verify each response, version/ETag transition, and inventory quantity.

**Acceptance Scenarios**:

1. **Given** a valid admin JWT, **when** the script creates a Product draft, submits a composition
   with a new Variant without an id, reads the server-assigned Variant id, initializes stock, and
   publishes, **then** the variant ID and active catalog state are captured without exposing the JWT.
2. **Given** the published variant, **when** the script creates, populates, schedules, and activates
   a Campaign, **then** the campaign response is active and the requested quantity is positive.
3. **Given** any admin call returns an authentication/authorization or validation error, **when** the
   script observes it, **then** it stops before the shopper reservation and reports only status and
   sanitized error code.

### User Story 3 - Verify the durable shopper-to-Order journey (Priority: P1)

As an operator, I want one shopper reservation to become an owned Order so that Redis admission,
Flash Sale PostgreSQL durability, outbox publication, Kafka consumption, and Order persistence are
verified together.

**Why this priority**: This is the canonical Phase 22 business outcome.

**Independent Test**: With a newly registered shopper and an active Campaign, submit one reservation
with a unique idempotency key, replay the same key, query ownership, and observe the matching Order.

**Acceptance Scenarios**:

1. **Given** an active Campaign and available stock, **when** the shopper submits a reservation with
   `Idempotency-Key` and `X-Trace-Id`, **then** Gateway returns the documented `202` acceptance and
   a Location/identity that can be polled.
2. **Given** the same shopper and idempotency key are retried with the same payload, **when** the
   request is replayed, **then** the returned purchase/reservation identities remain the same and
   stock is not consumed twice.
3. **Given** the accepted reservation, **when** the owner reservation and Order endpoints are
   polled within the configured bounded timeout, **then** the reservation is owned by the shopper and
   the Order identity references the same purchase request, reservation, Campaign, and Variant.
4. **Given** the campaign is missing, inactive, sold out, or the required Kafka consumer is not
   healthy, **when** the smoke reaches the affected step, **then** it fails with a bounded diagnostic
   and does not claim a successful Phase 22 result.

## Edge Cases

- Admin login returns `401` or an account has no `CATALOG_ADMIN`, `INVENTORY_ADMIN`, or
  `CAMPAIGN_ADMIN`: stop and request an approved existing admin account; never elevate through SQL.
- Shopper registration collides with an existing generated identifier: regenerate a unique suffix and
  retry only the registration step within a bounded limit.
- A repeated run finds prior fixtures: use a unique code/slug and do not mutate unrelated IDs.
- A Campaign is scheduled but not yet projected active: poll the documented readiness/activation
  observation until timeout; do not bypass the Campaign/Redis projection through SQL.
- Reservation returns `202` but Order does not appear before timeout: report the durable reservation
  response and the missing Order separately; do not label the journey successful.
- Any port-forward or HTTP child process exits unexpectedly: terminate it, remove temporary logs,
  and fail without printing credential material.
- Payment flags remain disabled by design; no Checkout or webhook assertion is made.

## Requirements

### Functional Requirements

- **FR-001**: The smoke MUST verify the expected EKS context, Argo `flash-sale-cloud` source/revision,
  Gateway readiness, and cloud overlay dry-run before any fixture mutation.
- **FR-002**: All business HTTP calls MUST enter through the API Gateway port-forward; direct service
  URLs and direct database/cache/broker commands MUST be rejected by the implementation.
- **FR-003**: The smoke MUST use an existing operator-supplied admin credential or a pre-approved
  fixture path; it MUST NOT create/elevate an admin by SQL or Kubernetes Secret mutation.
- **FR-004**: The smoke MUST register a unique shopper through `/api/v1/auth/register`, log in through
  `/api/v1/auth/login`, and keep access/refresh credentials only in process memory.
- **FR-005**: The smoke MUST create Product and Campaign state through the approved Gateway contracts
  with required trace, idempotency, and optimistic-version headers, and MUST invoke only the approved
  Inventory-owned fixture Job/CLI for Inventory initialization.
- **FR-006**: The smoke MUST submit exactly one reservation with a unique idempotency key and verify
  an identical replay returns the same durable identities.
- **FR-007**: The smoke MUST verify the owner reservation and an Order discovered from the owner list
  and loaded through the owner detail endpoint, with the same purchase, reservation, Campaign, and
  Variant identities.
- **FR-008**: Every external call MUST have a bounded timeout and the whole run MUST have a bounded
  deadline; failure output MUST include status/error code but no token, password, cookie, or Secret.
- **FR-009**: Validation-only mode MUST never mutate Kubernetes, Product, Inventory, Campaign, or
  shopper state; run mode MAY mutate only the documented disposable business fixtures through Gateway.
- **FR-010**: The script MUST clean up the loopback port-forward and temporary files on success or
  failure and print a compact evidence summary suitable for `validation.md`.

### Key Entities

- **Admin session**: operator-owned JWT session used for catalog, inventory, and campaign commands;
  never persisted by the smoke.
- **Shopper session**: disposable registered account and in-memory JWT used for reservation and
  owner-scoped reads.
- **Product fixture**: unique Product draft, Variant ID, version, and publication state.
- **Campaign fixture**: unique Campaign ID, current version/ETag, active window, and Variant item.
- **Reservation evidence**: purchase request ID, reservation ID, campaign/variant IDs, replay result,
  owner query result, and trace ID.
- **Order evidence**: owner-visible Order ID and references to the reservation and purchase request.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Validation mode performs zero business-state or Kubernetes mutations.
- **SC-002**: A run with an approved admin account completes Product → Inventory → Campaign → Flash
  Sale → Order through Gateway with all required identity and status assertions.
- **SC-003**: A same-key replay returns the original reservation identities and does not create a
  second Order or consume a second unit of stock.
- **SC-004**: Every successful run emits sanitized evidence with the EKS context, Argo revision,
  fixture IDs, HTTP statuses, trace ID, and elapsed time while containing no credential values.
- **SC-005**: Any prerequisite, authentication, validation, timeout, or missing Order failure exits
  non-zero and clearly identifies the failed stage without claiming Phase 22 completion.

## Assumptions

- `flash-sale-dev` is the only cloud verification environment; there is no production environment.
- The existing Gateway, Authentication, Product, Inventory, Campaign, Flash Sale, and Order
  deployments are Argo-managed and healthy before the run.
- The operator can supply an already provisioned `ROLE_ADMIN` account.
- Inventory initialization is intentionally transport-deferred in the current service; the existing
  `InventoryLocalSmokeFixture` is a local opt-in test fixture and must not be copied into a cloud
  runner without the approved choice in Human Decision 2.
- The cloud topology's Payment flags remain disabled during this feature.
- The Campaign API currently has no delete endpoint, so cleanup policy must be chosen explicitly.

## Constitutional Constraints

- **Service ownership**: No database, JPA, repository, Redis, Kafka, or Schema Registry access is
  added to the smoke; each service remains the owner of its own state.
- **External ingress**: All calls use the internal API Gateway port-forward; no public endpoint is
  introduced.
- **API/event contracts**: Existing Auth, Product, Inventory, Campaign, Flash Sale, and Order HTTP
  contracts are consumed as documented; no route or Kafka contract changes are allowed.
- **Durable and hot-path data**: PostgreSQL remains durable truth and Flash Sale continues to use its
  existing Redis Lua path; the smoke does not bypass either owner.
- **Messaging reliability**: The smoke observes the existing outbox/Kafka/Order consumer result but
  does not change consumer, retry, DLT, or schema behavior.
- **Root infrastructure ownership**: The runner belongs under `infra/scripts/gitops/`; evidence and
  quickstart documentation belong under this feature and `docs/deployment/`.
- **Observability**: Trace IDs are supplied and recorded; no Prometheus registry or application
  instrumentation is added.
- **Verification**: PowerShell parser/static checks, cloud Kustomize dry-run, Phase 21 verification,
  and the live authenticated smoke are required; no load test is part of Phase 22.
- **Architecture decisions**: No ADR is required unless an approved admin-fixture provisioning path
  is selected, because the recommended design consumes existing HTTP contracts only.

## Approval and History

- 2026-08-22 — Draft created after review of Gateway routes, Auth role semantics, Product/Inventory/
  Campaign admin contracts, Flash Sale reservation behavior, and Order's `PurchaseAcceptedV1`
  consumer.
- 2026-08-22 — Project owner approved the existing-admin credential, Inventory-owned fixture Job/CLI,
  and identity-matching Order poll decisions; planning may proceed.
