# Feature Specification: Order Service Core MVP

**Feature Branch**: `020-order-service-mvp`
**Created**: 2026-08-15
**Status**: Verified
**Input**: Align the supplied Order Service development draft with the repository's approved Flash
Sale contracts, service boundaries, HTTP conventions, and Spec Kit workflow.
**Linked Business Requirements**: Convert each durably accepted Flash Sale purchase into one
durable, owner-queryable Order and one reliable Order-created fact.
**Business Owner**: Project owner
**Required Reviewers**: Project owner; architecture reviewer; security reviewer

> Order creation, money snapshots, asynchronous delivery, and owner authorization make this a
> correctness- and data-sensitive feature. Production implementation must not begin until this
> specification, its contracts, plan, and tasks are approved.

## Problem and Scope

### Problem Statement

Feature 019 durably accepts a winning purchase and publishes `PurchaseAccepted.v1`, but no service
currently turns that fact into an Order. A shopper therefore has a reservation identity but no
durable Order they can query, and downstream workflows have no Order-owned creation fact.

The Order Service must close that gap without re-running Flash Sale admission, recomputing price,
reading another service's database, or treating duplicate Kafka delivery as another purchase.

### Goal

Create exactly one logical Order for each accepted purchase, preserve the accepted commercial
snapshot, expose owner-only Order queries, and reliably publish an Order-created fact for future
downstream consumers.

### In Scope

- Consume the existing approved `PurchaseAccepted.v1` Avro record from
  `flashsale.purchase.events.v1`, keyed by `purchaseRequestId`.
- Create one durable Order and one Order line from the accepted purchase snapshot.
- Start the Order in `PENDING_PAYMENT` without claiming that payment has started or succeeded.
- Make duplicate and concurrent delivery idempotent by event identity and accepted-purchase
  business identity.
- Detect contradictory replays without mutating the established Order.
- Commit the Order, inbound-processing identity, and required Order-created publication intent as
  one durable outcome.
- Publish a new versioned `OrderCreated.v1` fact on `flashsale.order.events.v1`, keyed by `orderId`.
- Expose owner-only Order detail and bounded Order-list queries through API Gateway.
- Reuse the repository-wide public JWT trust contract, shared success/error envelopes, and trace
  header conventions.
- Provide retry/recovery behavior, health/readiness, metrics, structured logs, and applicable
  contract, integration, failure, and concurrency evidence.

### Out of Scope

- `PaymentRequested.v1`, payment-provider interaction, payment attempts, or payment result
  consumption.
- Reservation confirmation/release commands and their result events.
- Order transitions to `CONFIRMED`, `CANCELLED`, `EXPIRED`, or any other terminal state.
- A reservation-expired Kafka consumer. Feature 019 intentionally publishes no
  `FlashSaleReservationExpired.v1` event.
- Choosing a payment deadline, retry allowance, late-payment policy, refund policy, or
  compensation owner.
- Synchronous calls to Product, Campaign, Inventory, Flash Sale, or Payment during Order creation.
- Redis, distributed locks, cross-service database access, shipping, fulfillment, tax, voucher,
  invoice, refund, return, or administrative Order editing.

### Non-goals

- This feature does not implement the complete purchase/payment Saga.
- `OrderCreated.v1` is a committed fact, not a hidden command instructing Payment to charge a user.
- This feature does not claim end-to-end exactly-once delivery.
- This feature does not introduce a client-facing Order creation or mutation endpoint.

## Baseline References

| Baseline | Current authority | Effect on Feature 020 |
|----------|-------------------|-----------------------|
| [Feature 019 specification](../019-flash-sale-service-mvp/spec.md) | Approved durable acceptance, five-minute reservation expiry, and no expiry event | Consume only the durable purchase-accepted fact; do not invent an expiry input |
| [PurchaseAccepted Kafka contract](../019-flash-sale-service-mvp/contracts/purchase-accepted-kafka.md) | Approved topic, key, Avro identity, fields, ordering, and at-least-once semantics | Adopt without changing the producer contract |
| [PurchaseAccepted Avro schema](../../contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc) | Implemented protocol source of truth | Order's inbound adapter must remain compatible with this exact v1 record |
| [Kafka topic and message catalog](../../docs/kafka/04-topic-message-catalog.md) | Repository target catalog; candidate messages require feature approval | Approve only `OrderCreated.v1` in the Order topic; leave payment and reservation commands candidate |
| [Flash Sale end-to-end flow](../../docs/architecture/flash-sale-end-to-end-flow.md) | Target Order-owned Saga direction, not approval of payment policy | Preserve future command/event boundaries without implementing them here |
| [HTTP response standardization](../018-http-response-standardization/spec.md) | Approved public success/error and pagination shape | Order queries use the shared envelopes from their first version |
| `services/order-service` scaffold | Existing empty Clean/Hexagonal service module | Add behavior without changing service ownership or deployment boundaries |

## Requirement Delta

### ADDED

- Order becomes the consumer owner for the approved `PurchaseAccepted.v1` fact.
- `OrderCreated.v1` becomes the first approved record on `flashsale.order.events.v1`.
- Authenticated shoppers gain owner-only Order detail and list queries.

### UNCHANGED

- Feature 019 continues to own reservation admission, five-minute expiry, quota release, and the
  `PurchaseAccepted.v1` producer contract.
- Payment and reservation-finalization messages in the repository catalog remain candidates.

### REJECTED DRAFT ASSUMPTIONS

- `flash-sale.reservation.v1` is not the inbound topic; the implemented topic is
  `flashsale.purchase.events.v1`.
- `FlashSaleReservationAccepted.v1` is not the implemented record; the record is
  `PurchaseAccepted.v1` / `PurchaseAcceptedV1`.
- `FlashSaleReservationExpired.v1` does not exist in Feature 019 and is not introduced here.
- `order.lifecycle.v1` and `payment.lifecycle.v1` do not match the repository catalog; the reserved
  topic families are `flashsale.order.events.v1`, `flashsale.payment.commands.v1`, and
  `flashsale.payment.events.v1`.
- Payment must not treat `OrderCreated.v1` as a charge command. A future approved Order Saga feature
  may issue `PaymentRequested.v1` explicitly.

## User Scenarios & Testing

### User Story 1 - Create one durable Order from an accepted purchase (Priority: P1)

As a winning shopper, I want my accepted purchase to become one durable Order, so that duplicate
delivery or a service restart cannot lose or multiply what I bought.

**Why this priority**: This is the feature's core business value and correctness boundary.

**Independent Test**: Deliver a valid `PurchaseAccepted.v1` to an isolated Order Service and verify
one Order, one line, one inbound-processing identity, and one stable Order-created publication
identity commit together.

**Use Case ID**: `UC-ORD-001`

#### Use Case UC-ORD-001: Record an accepted purchase as an Order

**Trigger**: Order Service receives a supported `PurchaseAccepted.v1` record.

**Preconditions**:

- The record satisfies the approved Feature 019 contract.
- No contradictory Order has already claimed its purchase or reservation identity.

**Main Flow**:

1. Validate the event envelope and accepted-purchase snapshot.
2. Determine whether the event or its accepted-purchase identity has already been processed.
3. Create a new Order in `PENDING_PAYMENT` with one Order line and exact accepted values.
4. Record the inbound event identity and the stable `OrderCreated.v1` publication identity.
5. Commit the complete local result atomically.

**Alternative Flows**:

- An identical delivery returns the already-established semantic result without another Order,
  line, or publication identity.
- A different event ID describing the same equivalent accepted purchase reuses the established
  Order and records no second logical creation fact.

**Exceptions**:

- Invalid or unsupported records create no business state and enter the approved non-retryable
  failure path.
- A reused event, purchase-request, or reservation identity with contradictory business content
  creates no mutation and produces an operator-visible conflict.
- A transient storage failure creates no partial Order result and leaves the record eligible for
  safe redelivery.

**Postconditions**:

- Success: exactly one durable Order represents the accepted purchase, and one stable Order-created
  publication intent exists.
- Failure: no partial Order is visible and no contradictory existing Order is changed.

**Acceptance Scenarios**:

1. **UC-ORD-001/AC-01** — **Given** a valid new `PurchaseAccepted.v1`, **When** it is consumed,
   **Then** one `PENDING_PAYMENT` Order, one Order line, one inbound-processing identity, and one
   `OrderCreated.v1` publication identity are committed atomically.
2. **UC-ORD-001/AC-02** — **Given** an event was committed, **When** the same physical record is
   delivered repeatedly or after a commit-before-acknowledgement crash, **Then** no additional
   logical Order or Order-created fact is produced.
3. **UC-ORD-001/AC-03** — **Given** an Order already represents a purchase request and reservation,
   **When** a different event ID carries equivalent business content, **Then** the existing Order
   remains the only logical result.
4. **UC-ORD-001/AC-04** — **Given** an established identity, **When** another record reuses that
   identity with a different shopper, Campaign, Variant, quantity, price, currency, or expiry,
   **Then** no Order mutation occurs and the conflict is observable.
5. **UC-ORD-001/AC-05** — **Given** durable storage is unavailable, **When** a new record is handled,
   **Then** no partial business result is acknowledged and later redelivery can safely complete it.

---

### User Story 2 - Publish the committed Order fact reliably (Priority: P1)

As a downstream workflow owner, I want a stable Order-created fact after the Order commits, so that
future workflows can react without querying Order's database or mistaking a retry for a new Order.

**Why this priority**: The event is the supported cross-service boundary for committed Order
creation and must not be lost in the database-to-broker gap.

**Independent Test**: Commit an Order while the broker or Schema Registry is unavailable, restore
the dependency, and verify the original `OrderCreated.v1` identity, key, data, and trace context are
published without another Order.

**Use Case ID**: `UC-ORD-002`

**Acceptance Scenarios**:

1. **UC-ORD-002/AC-01** — **Given** an Order commits while Kafka is unavailable, **When** Kafka
   recovers, **Then** the original Order-created fact is eventually published with the same event
   identity and content.
2. **UC-ORD-002/AC-02** — **Given** publication succeeds but publication acknowledgement is
   ambiguous, **When** the publication is retried, **Then** the retry preserves the same event ID,
   `orderId` key, payload, aggregate version, correlation, and trace linkage.
3. **UC-ORD-002/AC-03** — **Given** Kafka remains unavailable, **When** an owner queries an already
   committed Order, **Then** the query remains available and does not depend on publication success.

---

### User Story 3 - Query only my Orders (Priority: P2)

As an authenticated shopper, I want to retrieve my Order and browse my Order history, so that I can
see the durable result without learning whether another shopper's Order exists.

**Why this priority**: Queryability turns the asynchronous Order result into visible shopper value
while preserving object authorization.

**Independent Test**: Create Orders for two users, retrieve and list them through Gateway with valid
tokens, and verify unknown and foreign-owned IDs produce the same safe not-found outcome.

**Use Case IDs**: `UC-ORD-003`, `UC-ORD-004`

**Acceptance Scenarios**:

1. **UC-ORD-003/AC-01** — **Given** a shopper owns an Order, **When** they request that Order through
   Gateway, **Then** they receive its immutable purchase snapshot, current status, reservation
   expiry, and timestamps in the standard success envelope.
2. **UC-ORD-003/AC-02** — **Given** an Order is unknown or belongs to another shopper, **When** the
   shopper requests it, **Then** both cases return the same `404 ORDER_NOT_FOUND` response without
   disclosing ownership or existence.
3. **UC-ORD-004/AC-01** — **Given** a shopper owns multiple Orders, **When** they request a bounded
   page, **Then** only their Orders are returned newest first with deterministic tie-breaking and
   standard page metadata.
4. **UC-ORD-004/AC-02** — **Given** an invalid page or size, **When** the list is requested, **Then**
   the standard validation error is returned without querying another user's data.
5. **UC-ORD-003/AC-03** — **Given** a missing, invalid, expired, or wrong-audience token, **When** a
   protected Order query is attempted, **Then** the request is rejected before Order data is read.

### Edge Cases and Failure Outcomes

- The same event ID arrives with different serialized business content.
- Different event IDs reuse one `purchaseRequestId` or `reservationId` with contradictory content.
- Two service instances consume racing duplicates before either transaction is visible to the
  other.
- The database commit succeeds but Kafka offset acknowledgement does not.
- The Order commits while Kafka or Schema Registry is unavailable.
- A payload has an unsupported version, missing identity, non-positive quantity or price, invalid
  currency, invalid temporal ordering, or an amount outside the supported exact range.
- Order-number generation collides while the accepted-purchase identities remain unique.
- A requested page is past the final page; the response is an empty page with valid metadata.
- The upstream reservation expires after Order creation. Feature 020 records the supplied
  `expiresAt` but performs no terminal Order transition and must not imply that payment remains
  possible after that instant.

## Requirements

### Functional Requirements

- **FR-001**: Order Service MUST consume the existing Avro `PurchaseAccepted.v1` record from
  `flashsale.purchase.events.v1` using `purchaseRequestId` as the Kafka key.
- **FR-002**: The consumer MUST accept only supported event types and versions and MUST validate all
  required envelope and business fields before creating an Order.
- **FR-003**: One logical accepted purchase MUST establish one stable Order identity and one
  human-readable Order number.
- **FR-004**: Both `purchaseRequestId` and `reservationId` MUST identify at most one logical Order.
- **FR-005**: A new Order MUST start in `PENDING_PAYMENT`; Feature 020 MUST NOT infer a paid,
  confirmed, cancelled, or expired outcome.
- **FR-006**: The Order MUST preserve `purchaseRequestId`, `reservationId`, `campaignId`, `variantId`,
  `userId`, quantity, unit price, currency, accepted time, and reservation expiry from the approved
  inbound snapshot.
- **FR-007**: Each v1 accepted purchase MUST create exactly one Order line. Its line amount MUST be
  the exact unit price multiplied by the accepted quantity, and the Order subtotal and total MUST
  equal that line amount in this no-discount, no-tax, no-shipping slice.
- **FR-008**: Event receipt, Order creation, Order line creation, and the required Order-created
  publication identity MUST commit atomically as one local durable result.
- **FR-009**: Redelivery of the same event ID and content MUST be a semantic no-op.
- **FR-010**: A different event ID with the same equivalent accepted-purchase identities MUST NOT
  create another logical Order or Order-created fact.
- **FR-011**: Reuse of an event, purchase-request, or reservation identity with contradictory
  business content MUST NOT mutate the established Order and MUST produce an operator-visible,
  non-retryable conflict outcome.
- **FR-012**: A transient failure before local commit MUST leave the input eligible for safe retry;
  a crash after commit but before acknowledgement MUST replay without duplicate business effects.
- **FR-013**: A committed new Order MUST produce one stable `OrderCreated.v1` publication identity on
  `flashsale.order.events.v1`, keyed by `orderId`.
- **FR-014**: Publication retry MUST preserve event identity, version, Order identity, aggregate
  version, business snapshot, correlation/causation identity, occurrence time, and trace context.
- **FR-015**: `OrderCreated.v1` MUST remain a fact. Feature 020 MUST NOT use it as an implicit payment
  command and MUST NOT emit `PaymentRequested.v1`.
- **FR-016**: An authenticated shopper MUST be able to retrieve an Order they own through
  `GET /api/v1/orders/{orderId}`.
- **FR-017**: Unknown and foreign-owned Order IDs MUST return the same `404 ORDER_NOT_FOUND` public
  outcome.
- **FR-018**: An authenticated shopper MUST be able to list only their Orders through
  `GET /api/v1/orders`, with zero-based page default `0`, size default `20`, maximum size `100`, and
  deterministic ordering by creation time descending then Order ID descending.
- **FR-019**: Public success responses MUST use `ApiResponse<T>`; paginated data MUST use
  `PageResponse<T>`; errors MUST use the shared error envelope; trace identity MUST be returned in
  `X-Trace-Id` rather than duplicated in response bodies.
- **FR-020**: API Gateway and Order Service MUST independently validate the approved public JWT
  trust contract. The JWT subject is the shopper identity; client-supplied user identity is not
  trusted.
- **FR-021**: Order creation MUST NOT synchronously call Product, Campaign, Inventory, Flash Sale, or
  Payment and MUST NOT access another service's database.
- **FR-022**: Feature 020 MUST NOT add Redis, a distributed lock, or a public Order creation/mutation
  endpoint.
- **FR-023**: Feature 020 MUST NOT consume or invent `FlashSaleReservationExpired.v1`; the supplied
  reservation `expiresAt` is recorded without defining a terminal Order transition.
- **FR-024**: Invalid/unsupported/conflicting inputs MUST be isolated from transient infrastructure
  failures so poison records do not retry indefinitely while recoverable records remain retryable.
- **FR-025**: Committed Order queries MUST remain available during Kafka or Schema Registry outage.
- **FR-026**: Important request and event paths MUST preserve W3C trace context when available and
  MUST NOT log raw JWTs, secrets, or payment credentials.

### Non-Functional Requirements

- **NFR-COR-001**: Under at least 100 repeated and concurrent deliveries for one accepted-purchase
  identity, the observable result MUST remain one Order, one Order line, and one logical
  Order-created fact.
- **NFR-REL-001**: Required event publication MUST recover after process, Kafka, or Schema Registry
  interruption without changing the committed Order or generating a new event identity.
- **NFR-AVAIL-001**: Owner query APIs MUST continue serving committed data while event publication is
  unavailable.
- **NFR-PERF-001**: Under the documented nominal local profile, at least 95% of owner Order-detail
  queries MUST complete within 200 ms.
- **NFR-PERF-002**: Under the documented nominal local profile, at least 95% of valid inbound events
  MUST become committed Orders within one second of consumer receipt, excluding time spent waiting
  before delivery to Order Service.
- **NFR-SEC-001**: All tested invalid trust and foreign-owner cases MUST disclose no Order data,
  token material, or internal infrastructure detail.
- **NFR-OBS-001**: The service MUST expose liveness, readiness, and Prometheus-compatible metrics,
  and operators MUST be able to distinguish consumed, created, duplicate, conflict, failed,
  publication-pending, publication-success, and query outcomes without high-cardinality ID labels.

### Business Rules and Invariants

- **INV-001**: One `purchaseRequestId` and one `reservationId` each map to at most one logical Order.
- **INV-002**: An established Order's shopper, Campaign, Variant, quantity, price, currency,
  reservation identity, and reservation expiry are immutable.
- **INV-003**: Quantity and unit price are positive, currency is exactly three uppercase letters,
  and all monetary calculations are exact within the supported range.
- **INV-004**: The v1 Order has exactly one line because `PurchaseAccepted.v1` contains one Variant.
- **INV-005**: In this feature, subtotal equals total and both equal unit price multiplied by
  quantity.
- **INV-006**: `PENDING_PAYMENT` is the only Order status created or transitioned by Feature 020.
- **INV-007**: A duplicate or conflicting event cannot create a second logical state transition or
  a second logical `OrderCreated.v1` fact.
- **INV-008**: An Order is observable only to the shopper identity that owns it.

### Key Entities

- **Order**: The durable record of what one winning shopper accepted purchasing. Its business
  identity is stable, it references one accepted purchase and reservation, owns its status and
  immutable commercial snapshot, and is not the same as a Flash Sale reservation or Payment.
- **Order Line**: The immutable Variant, quantity, unit-price, and line-amount snapshot contained by
  an Order. Feature 020 creates exactly one line per Order.
- **Inbound Event Receipt**: Evidence that one integration-event identity was evaluated, including
  enough content identity to distinguish an identical delivery from a contradictory reuse.
- **Order-created Publication**: The stable identity and immutable content required to announce a
  committed new Order reliably.

### Domain Model Delta

- Added Order state `PENDING_PAYMENT` only.
- Reserved future Order states `CONFIRMED`, `CANCELLED`, and `EXPIRED` remain outside this feature and
  are not valid transitions until the purchase/payment Saga specification approves them.
- `reservationExpiresAt` means the Flash Sale reservation deadline supplied upstream; it is not an
  Order payment deadline.
- Payment success is not equivalent to final Order confirmation because the future Saga must still
  confirm the Flash Sale reservation.

## Contract Direction and Compatibility

| Direction | Topic | Message | Key | Feature 020 status |
|-----------|-------|---------|-----|--------------------|
| Flash Sale -> Order | `flashsale.purchase.events.v1` | `PurchaseAccepted.v1` (`PurchaseAcceptedV1`) | `purchaseRequestId` | Existing approved input; unchanged |
| Order -> observers | `flashsale.order.events.v1` | `OrderCreated.v1` (`OrderCreatedV1`) | `orderId` | New contract to approve and add before implementation |
| Order -> Payment | `flashsale.payment.commands.v1` | `PaymentRequested.v1` | `orderId` | Candidate; explicitly deferred |
| Payment -> Order | `flashsale.payment.events.v1` | `PaymentSucceeded.v1`, `PaymentFailed.v1` | `orderId` | Candidate; explicitly deferred |
| Order -> Flash Sale | `flashsale.purchase.commands.v1` | `ConfirmPurchaseReservation.v1`, `ReleasePurchaseReservation.v1` | `orderId` | Candidate; explicitly deferred |
| Flash Sale -> Order | `flashsale.purchase.events.v1` | `PurchaseReservationConfirmed.v1`, `PurchaseReservationReleased.v1` | `orderId` | Candidate; explicitly deferred |

The future payment and reservation-finalization contracts require a separate approved feature that
defines payment deadline, retry, ambiguous provider result, compensation, late-payment, and final
Order-state behavior. They must not be implemented from the target catalog alone.

`OrderCreated.v1` must use the repository's Avro `SpecificRecord` envelope conventions and carry,
at minimum, stable event/order/purchase/reservation identities, aggregate version, correlation and
causation identities, occurrence time, shopper/Campaign/Variant references, current Order status,
exact quantity/amount/currency snapshot, reservation expiry, and trace context in Kafka headers.
The detailed schema and compatibility tests belong to this feature's contract artifact before code.

## Success Criteria

### Measurable Outcomes

- **SC-001**: One valid accepted purchase produces one queryable `PENDING_PAYMENT` Order and one
  logical Order-created fact.
- **SC-002**: One hundred repeated or concurrent equivalent deliveries produce exactly one Order,
  one line, and one logical Order-created publication; contradictory deliveries produce no
  mutation.
- **SC-003**: Every tested commit/acknowledgement and publication failure window recovers without a
  lost Order, duplicate Order, or changed event identity.
- **SC-004**: One hundred percent of owner, foreign-owner, unknown-ID, missing-token, invalid-token,
  and wrong-audience contract cases return the approved non-enumerating result.
- **SC-005**: At least 95% of Order-detail queries finish within 200 ms and at least 95% of delivered
  valid events commit within one second in the documented nominal local test profile.
- **SC-006**: The Order module and full Maven reactor pass their approved unit, integration,
  contract, failure, concurrency, and architecture checks with no required test failure.

## Dependencies and Compatibility

- **Upstream**: Feature 019 remains producer and owner of `PurchaseAccepted.v1`; Feature 020 does not
  change its schema, topic, key, subject, or behavior.
- **Public edge**: API Gateway requires a new `/api/v1/orders/**` route and must preserve the public
  JWT, response, and trace contracts.
- **Authentication**: Existing issuer, JWKS, `flash-sale-api` audience, `at+jwt` token type, expiry,
  signature, and subject semantics are reused; no new login or token format is introduced.
- **Persistence**: Order Service owns its PostgreSQL database, schema, migrations, and retention.
  No foreign key crosses a service boundary.
- **Messaging**: The root Avro contract module receives the new Order schema before producer code;
  the plan must define compatibility, subject naming, topic provisioning, consumer group,
  acknowledgement, retry/DLT, rollout, and recovery details.
- **Future Payment compatibility**: Payment integration must consume an explicit command rather than
  reinterpret `OrderCreated.v1`; no payment behavior is promised by this feature.

## Assumptions

- Feature 019's approved `PurchaseAccepted.v1` implementation and local topic are available as the
  upstream baseline.
- Each v1 accepted purchase contains exactly one Variant, so Feature 020 creates one Order line.
- The accepted price and currency are authoritative for Order creation; current Product or Campaign
  values are irrelevant to this snapshot.
- `expiresAt` is the upstream reservation expiry and is retained for visibility and future Saga
  decisions; it does not authorize this feature to choose a payment deadline or expire an Order.
- Status filtering is omitted from the initial list API because Feature 020 creates only
  `PENDING_PAYMENT`; a future lifecycle feature may add a compatible filter.
- The original file in `C:\Users\MSi\Downloads` is source material only; this repository feature
  specification becomes the canonical reviewed artifact.

## Constitutional Constraints

- **Service ownership**: Order Service owns Order state and its database. It does not read another
  service database or share JPA/domain models.
- **External ingress**: Public Order queries enter only through API Gateway; no public creation or
  mutation route exists.
- **API/event contracts**: The existing purchase contract is unchanged. The new Order event uses a
  versioned Avro contract in the root protocol module and must be documented/tested before producer
  implementation.
- **Durable and hot-path data**: PostgreSQL is Order's durable source of truth. Redis is not used by
  Order, and Feature 020 does not alter the Flash Sale Redis Lua hot path.
- **Messaging reliability**: Consumer processing is idempotent; durable Order creation and required
  publication are atomic locally; delivery remains at least once; duplicate, conflict, retry,
  ordering, DLT, replay, and recovery behavior must be explicit in the plan.
- **Root infrastructure ownership**: Shared Kafka/Schema Registry/Compose/Kubernetes/monitoring
  changes belong under root `infra/`; service source, configuration, and migrations remain in
  `services/order-service`.
- **Observability**: Order uses Spring Boot Actuator auto-configuration, a runtime Prometheus
  registry, declarative configuration, bounded metric labels, and trace propagation. Business code
  must not construct or depend on a Prometheus registry implementation.
- **Verification**: Domain/unit, PostgreSQL integration, Kafka/Schema Registry contract,
  redelivery/concurrency, failure recovery, web/security, architecture, Compose smoke, module, and
  full-reactor tests apply. A separate Flash Sale burst load test is not repeated because Order is
  downstream of the accepted-winner stream; nominal consumer-throughput and query-latency evidence
  remain required.
- **Architecture decisions**: No service boundary, ownership, discovery, ingress, or communication
  style changes are introduced beyond the repository's documented target. If planning departs from
  that direction, an accepted ADR is required before implementation.

## Approval and History

| Date | Change | Author | Reviewer | Status |
|------|--------|--------|----------|--------|
| 2026-08-15 | Imported and aligned the supplied Order draft with Feature 019, the implemented Avro contract, repository topic catalog, HTTP conventions, and scoped Order Core MVP | Codex | Pending project owner | Draft — ready for human review |
| 2026-08-15 | Project owner approved the scoped Order Core MVP and authorized preparation for implementation | Project owner | Project owner | Approved for planning |
| 2026-08-17 | G1-G8 implementation, failure recovery, query performance, event-to-commit, module, and full-reactor evidence reconciled | Codex | Project owner | Verified |
