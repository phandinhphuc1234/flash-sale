# Feature Specification: Order-Owned Purchase Saga Completion

**Feature Branch**: `codex/order-purchase-saga`
**Created**: 2026-08-24
**Status**: Approved for implementation — owner decisions A/A/A/A and plan approved 2026-08-24
**Input**: Complete the missing Order → Payment → Flash Sale → Order workflow, validate each
coherent slice locally, and deploy the affected services to the cloud only once after all local
gates pass. Cart Service and Notification Service are deferred.

## Problem and scope

The current cloud flow durably accepts a Flash Sale purchase and creates an Order in
`PENDING_PAYMENT`, but it does not complete the approved Order-owned Purchase Saga. Order does not
publish the Payment command, does not consume Payment outcomes, and Flash Sale does not yet expose
the confirm/release reservation command path needed to finish the transaction. Consequently the
Phase 24 Stripe runner can create the upstream Order but cannot prove Checkout, webhook, reservation
finalization, or the final Order state.

This feature closes that gap without adding Cart or Notification work. Delivery is local-first:
each coherent Saga slice receives a repeatable bounded test gate, then the affected services are
promoted to the cloud together once the complete local flow is green.

### In scope

- Create durable Purchase Saga state when Order accepts a `PurchaseAccepted.v1` event.
- Publish `PaymentRequested.v1` from Order through the existing versioned Payment command topic.
- Consume Payment success/failure results idempotently in Order.
- Add versioned confirm/release reservation commands owned by Flash Sale and publish corresponding
  reservation outcome events.
- Finalize Order only after the required reservation outcome has been observed.
- Handle duplicate delivery, out-of-order delivery, deadlines, late success, forward recovery, and
  manual-review escalation according to approved rules.
- Publish an explicit Order review-required correction fact when verified late Payment success
  reopens an already terminal unpaid Order for manual review.
- Provide bounded local runners for contracts, happy path, terminal failure, replay/idempotency,
  and late/out-of-order outcomes, plus one aggregate local gate.
- Extend the Phase 24 cloud runner only after the local aggregate gate is green.
- Promote only the affected service images in one reviewed GitOps release after local validation.

### Out of scope

- Cart Service and Notification Service implementation or their future topics.
- Product, Inventory, Campaign-end, notification, refund, dispute, payout, or shipping workflows.
- Automatic refunds or a second payment provider.
- Stripe live mode, delayed payment methods, or raw card handling.
- Replacing Kafka, Schema Registry, the outbox/inbox pattern, PostgreSQL durable truth, or Order Saga
  ownership.
- Direct database access between services or SQL-based cross-service test fixtures.

## User scenarios and testing

### User Story 1 — Order durably requests payment (Priority: P1)

As a shopper whose reservation was accepted, I need the system to create exactly one durable Order
workflow and request payment so that I can proceed to Checkout without duplicate charges.

**Independent Test**: Deliver the same accepted-purchase event repeatedly and verify one semantic
Order, one active Saga, and one stable `PaymentRequested.v1` publication.

**Acceptance Scenarios**:

1. **Given** a new valid accepted-purchase event, **when** Order commits it, **then** the Order,
   Purchase Saga, consumer inbox record, and Payment command outbox intent are durable together.
2. **Given** the same accepted-purchase event is redelivered, **when** Order consumes it again,
   **then** no second Order, Saga, or semantic Payment request is created.

### User Story 2 — Successful payment confirms the purchase (Priority: P1)

As a shopper who successfully paid, I need the reserved stock and Order to become confirmed exactly
once so that a verified payment cannot remain indefinitely in `PENDING_PAYMENT`.

**Independent Test**: Inject a valid `PaymentSucceeded.v1`, process the reservation confirmation,
and verify one confirmed reservation, one confirmed Order, and one terminal Order event.

**Acceptance Scenarios**:

1. **Given** an unpaid active Saga and a valid Payment success, **when** Order processes the result,
   **then** it requests reservation confirmation and does not finalize the Order before Flash Sale
   acknowledges that confirmation.
2. **Given** Flash Sale confirms the reservation, **when** Order consumes the outcome, **then** the
   Order reaches `CONFIRMED` once and emits one stable terminal Order event.

### User Story 3 — Terminal payment failure releases stock (Priority: P1)

As a shopper whose payment cannot complete, I need the reservation released and the Order closed in
the correct unpaid terminal state so that stock is not stranded.

**Independent Test**: Inject each approved terminal Payment failure and verify one release command,
one released reservation, and the approved terminal Order state.

**Acceptance Scenarios**:

1. **Given** a terminal Payment failure, **when** Order processes it, **then** it requests reservation
   release and waits for the Flash Sale outcome before closing the Order.
2. **Given** Flash Sale reports the reservation released, **when** Order processes the outcome,
   **then** the Order reaches the approved unpaid terminal state once.

### User Story 4 — Replays and late outcomes remain safe (Priority: P1)

As an operator, I need duplicate, reordered, and late provider outcomes to converge safely so that
at-least-once delivery cannot create contradictory stock, Order, or Payment facts.

**Independent Test**: Replay every command/result, deliver success after a prior unpaid terminal
signal, and verify monotonic state, forward recovery, and bounded manual-review escalation.

**Acceptance Scenarios**:

1. **Given** a previously processed command or outcome, **when** it is delivered again, **then** the
   system returns the prior semantic result without another state transition or business event.
2. **Given** verified Payment success arrives after an unpaid terminal path started, **when** the
   system can still confirm the reservation, **then** paid success wins and the Saga converges to
   confirmed.
3. **Given** verified Payment success cannot be reconciled automatically, **when** forward recovery
   is exhausted, **then** the Saga enters a visible manual-review state without an automatic refund.

### User Story 5 — Local-first release evidence (Priority: P1)

As the project maintainer, I need repeatable local scripts for each Saga slice and one aggregate gate
so that cloud deployment happens once, only after behavior and contracts are proven locally.

**Independent Test**: Run the aggregate local command from a clean documented environment and verify
all stage results, bounded timeouts, sanitized output, module tests, and required Kafka schemas.

**Acceptance Scenarios**:

1. **Given** local PostgreSQL, Redis, Kafka, and Schema Registry are healthy, **when** a stage runner
   is invoked, **then** it validates only its named slice and reports bounded actionable evidence.
2. **Given** all stage runners pass, **when** the aggregate local gate runs, **then** it verifies the
   affected Maven modules and complete Saga without printing secrets, JWTs, Checkout URLs, or raw
   provider payloads.
3. **Given** the aggregate local gate is green, **when** the reviewed delivery workflow runs, **then**
   only the affected images are promoted in one cloud release before the Phase 24 Stripe smoke.

## Approved owner decisions

### Decision SAGA-DEADLINE-001 — Payment deadline versus reservation expiry

**Decision A selected**: `paymentDeadline` is exactly 30 seconds earlier than
`reservationExpiresAt`. Order MUST reject an accepted-purchase input that cannot produce a future
payment window after applying this safety margin. The margin leaves bounded time for the Payment
outcome and reservation-confirmation round trip before Redis/PostgreSQL reservation expiry.

### Decision SAGA-TERMINAL-001 — Mapping Payment failures to Order terminal states

**Decision A selected**:

- `PAYMENT_DEADLINE_EXPIRED` maps to Order `EXPIRED` after reservation release succeeds.
- `CHECKOUT_ATTEMPT_LIMIT_REACHED` maps to Order `CANCELLED` after reservation release succeeds.
- `PROVIDER_TERMINAL_FAILURE` maps to Order `CANCELLED` after reservation release succeeds.

### Decision SAGA-REVIEW-001 — Public Order state during paid manual review

**Decision A selected**: If verified Payment success cannot automatically confirm the reservation,
Order remains publicly `PENDING_PAYMENT` while the Purchase Saga enters durable `MANUAL_REVIEW`.
Operator evidence MUST distinguish this state from an ordinary unpaid Order. No new public Order
status is introduced by this feature.

### Decision SAGA-LATE-CORRECTION-001 — Late success after an unpaid terminal Order

**Decision A selected**: If a higher-version verified Payment success arrives after Order already
became `CANCELLED` or `EXPIRED`, Order is atomically corrected to `PENDING_PAYMENT`, the Purchase
Saga enters `MANUAL_REVIEW`, and Order publishes `OrderPaymentReviewRequiredV1`. The correction fact
allows future consumers to supersede the earlier unpaid terminal fact without introducing a new
public Order status. Automatic refund remains forbidden.

## Functional requirements

- **FR-001**: Order MUST own and durably persist the Purchase Saga; no other service may mutate
  Order's workflow state.
- **FR-002**: For a newly accepted purchase, Order MUST atomically persist its aggregate, Saga,
  consumer deduplication record, and required outbox intent.
- **FR-003**: Order MUST publish `PaymentRequested.v1` on
  `flashsale.payment.commands.v1`, keyed by stable `orderId`, using the accepted Payment contract.
- **FR-004**: The same accepted purchase MUST always resolve to the same Order, Saga, correlation
  identifiers, and semantic Payment request.
- **FR-005**: Order MUST consume `PaymentSucceeded.v1` and `PaymentFailed.v1` idempotently from
  `flashsale.payment.events.v1` and reject contradictory regressions.
- **FR-006**: Verified paid success MUST be success-dominant. Order MUST attempt forward recovery
  before escalating a paid-but-unconfirmed workflow to manual review.
- **FR-007**: Order MUST NOT automatically refund a late success; refund remains a separate future
  feature.
- **FR-008**: Reservation confirm/release commands MUST use a documented versioned contract on the
  approved Flash Sale command topic and a stable key that preserves per-purchase ordering.
- **FR-009**: Flash Sale MUST consume reservation commands idempotently and atomically persist the
  reservation transition, inbox record, and required outcome outbox intent.
- **FR-010**: Flash Sale MUST emit versioned confirmed/released outcomes on
  `flashsale.purchase.events.v1`; repeated commands MUST return the same semantic outcome.
- **FR-011**: Order MUST wait for the required reservation outcome before publishing a terminal
  Order event on `flashsale.order.events.v1`.
- **FR-012**: All Saga consumers MUST tolerate at-least-once delivery and use durable inbox or
  equivalent stable deduplication evidence; required publications MUST use outbox delivery.
- **FR-013**: Important commands and events MUST retain stable event identity, correlation identity,
  causation identity, occurred-at time, schema version, and trace context.
- **FR-014**: All new or changed Kafka payloads MUST have versioned contract documentation and
  registered Schema Registry subjects before cloud enablement.
- **FR-015**: Local stage runners MUST be bounded, rerunnable, fail with actionable diagnostics,
  avoid direct writes into another service's database, and never print secrets or sensitive values.
- **FR-016**: The aggregate local gate MUST validate contracts, happy path, terminal failure,
  duplicate/replay, out-of-order/late success, affected module tests, and Schema Registry
  compatibility before cloud delivery is permitted.
- **FR-017**: Cloud rollout MUST occur once after local gates pass, use immutable ECR tags and the
  existing image-promotion PR/Argo CD flow, and preserve rollback to the prior image set.
- **FR-018**: The Phase 24 Stripe runner MUST reuse the real Order-owned Payment request and MUST NOT
  fabricate Payment database state, Kafka state, or cross-service SQL fixtures.
- **FR-019**: Cart Service and Notification Service MUST remain disabled/deferred and MUST NOT block
  this feature's local or cloud acceptance.
- **FR-020**: A higher-version verified Payment success that arrives after an unpaid terminal Order
  MUST atomically correct Order to `PENDING_PAYMENT`, persist Saga `MANUAL_REVIEW`, and publish one
  stable `OrderPaymentReviewRequiredV1` correction fact.

## Contract and state boundaries

- **Order-owned state**: Order, Order lines, Purchase Saga, event inbox, outbox, and terminal Order
  status.
- **Payment-owned state**: Payment, Checkout attempts, provider receipts, command inbox,
  idempotency evidence, recovery work, and Payment outbox.
- **Flash-Sale-owned state**: purchase request, reservation, reservation command inbox, and purchase
  outcome outbox.
- **Existing topics**:
  - `flashsale.purchase.events.v1`
  - `flashsale.payment.commands.v1`
  - `flashsale.payment.events.v1`
  - `flashsale.order.events.v1`
- **Approved command topic**: `flashsale.purchase.commands.v1` for reservation confirm/release
  commands. No Cart or Notification topic is introduced.
- **Order correction fact**: `OrderPaymentReviewRequiredV1` is additive on
  `flashsale.order.events.v1`; it changes no public Order status enum.

## Architecture and security constraints

- Services communicate only through approved HTTP or Kafka contracts and never access another
  service's database.
- Domain and application layers remain independent of Spring, Kafka, JPA, and Stripe types.
- Each service keeps package-by-feature Clean/Hexagonal boundaries with inbound adapters invoking
  use cases and outbound adapters implementing ports.
- PostgreSQL remains durable truth; Redis remains limited to approved Flash Sale hot-path work.
- API Gateway remains the only external ingress. Card data remains on Stripe-hosted Checkout.
- Consumer state transitions and required outbox writes share one local transaction.
- No new production dependency or service-boundary change is permitted without an approved plan or
  ADR update.

## Success criteria

- **SC-001**: Replaying the same accepted-purchase command and every downstream outcome produces one
  semantic Order, Payment request, reservation transition, and terminal Order event.
- **SC-002**: The local paid-success scenario reaches reservation `CONFIRMED` and Order `CONFIRMED`
  within the approved bounded timeout.
- **SC-003**: Each approved unpaid terminal reason reaches reservation `RELEASED` and its approved
  Order terminal state within the bounded timeout.
- **SC-004**: Late verified success either converges forward to confirmed or produces durable,
  observable manual-review evidence plus exactly one review-required correction fact when an unpaid
  terminal Order was already published; it never triggers an automatic refund.
- **SC-005**: Contract, module, integration, replay, and aggregate local gates all pass before any
  affected cloud image is promoted.
- **SC-006**: One reviewed cloud image-promotion release reconciles the affected services to
  `Synced/Healthy`, after which the Phase 24 Stripe runner proves Checkout, valid webhook receipt,
  duplicate webhook idempotency, Payment outcome publication, reservation finalization, and final
  Order state.
- **SC-007**: Cart and Notification remain explicitly deferred with no false completion claim.

## Dependencies

- `specs/019-flash-sale-reservation`
- `specs/020-order-service-mvp`
- `specs/021-payment-service-mvp`
- `specs/043-gitops-stripe-cloud-enablement`
- `docs/adr/0018-order-owned-purchase-saga-payment-contracts.md`
- Existing Kafka, Schema Registry, PostgreSQL, Redis, EKS, ECR, GitHub Actions, and Argo CD
  infrastructure.
