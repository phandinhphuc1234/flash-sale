# Research: Order-Owned Purchase Saga Completion

## Baseline findings

The repository already contains the durable first half of the flow:

- Flash Sale publishes `PurchaseAcceptedV1` after PostgreSQL acceptance.
- Order consumes that event and atomically stores one `PENDING_PAYMENT` Order, one line, one
  accepted-purchase inbox row, and one `OrderCreatedV1` outbox row.
- Payment already consumes `PaymentRequestedV1`, owns Stripe Checkout/webhooks/recovery, and
  publishes `PaymentSucceededV1` or `PaymentFailedV1` through its outbox.
- Order does not yet create Purchase Saga state, publish `PaymentRequestedV1`, or consume Payment
  results.
- Flash Sale reservation state is currently only `RESERVED -> EXPIRED`; confirm/release command
  consumers and result events do not exist.
- Existing Phase 24 cloud smoke therefore reaches Order `PENDING_PAYMENT` but has no real
  Order-owned Payment command to continue the workflow.

No new global coordinator is required. ADR 0018 already assigns orchestration to Order and provider
truth to Payment. Feature 044 activates the deferred reservation and terminal-Order part of that
accepted decision.

## Decision 1 — Keep orchestration inside Order Service

**Decision**: Add a `purchasesaga` business feature inside `order-service`; do not create a Saga
platform or new microservice.

**Rationale**:

- ADR 0018 already assigns durable workflow ownership to Order.
- The Saga needs atomic access only to Order-owned state, inbox, and outbox in `order_db`.
- A global coordinator would add another database, deployment, protocol, and failure boundary.
- Package-by-feature allows Saga policy to remain explicit without leaking orchestration into the
  Payment or Flash Sale participants.

**Alternatives considered**:

- Choreography driven by `OrderCreatedV1`: rejected because a fact is not authorization to prepare
  payment and no participant would own deadline/compensation decisions.
- New Saga coordinator service: rejected by ADR 0018 and unnecessary for one Order-owned workflow.
- Payment mutating Order or reservation state: rejected because it violates service/database
  ownership.

## Decision 2 — Reuse existing Payment contracts unchanged

**Decision**: Order produces the existing `PaymentRequestedV1`; Order consumes the existing
`PaymentSucceededV1` and `PaymentFailedV1`. No Payment schema or Payment production dependency is
changed.

**Rationale**:

- Feature 021 already implemented and verified these contracts.
- The command contains the exact immutable fields Order owns: Order/user identity, amount,
  currency, and payment deadline.
- The result events provide Payment aggregate version, provider truth, amount, currency, and
  Order correlation needed for monotonic handling.
- Avoiding a schema change reduces the affected production modules to Order and Flash Sale unless
  code audit later discovers a compatibility defect.

**Alternatives considered**:

- Reinterpret `OrderCreatedV1` as a payment command: rejected by ADR 0018.
- Add reservation identifiers to Payment result v1: rejected because Order can resolve them from
  its own Saga by `orderId`; changing an accepted contract is unnecessary.

## Decision 3 — Add one reservation command topic and extend two event topics

**Decision**:

- Create `flashsale.purchase.commands.v1`, keyed by `orderId`.
- Add `ConfirmPurchaseReservationV1` and `ReleasePurchaseReservationV1` to that topic.
- Add `PurchaseReservationConfirmedV1` and `PurchaseReservationReleasedV1` to the existing
  `flashsale.purchase.events.v1`, keyed by `orderId` after Order creation.
- Add `OrderConfirmedV1`, `OrderCancelledV1`, `OrderExpiredV1`, and
  `OrderPaymentReviewRequiredV1` to the existing
  `flashsale.order.events.v1`, keyed by `orderId`.
- Keep the existing pre-Order `PurchaseAcceptedV1` key as `purchaseRequestId`.

Every message is a separate Avro SpecificRecord using `TopicRecordNameStrategy`; adding records to
an existing topic does not mutate the accepted record schemas. Subjects remain
BACKWARD_TRANSITIVE and explicitly registered.

**Rationale**:

- These names, directions, and key transition already appear in the repository Kafka catalog.
- One `orderId` key preserves ordering for all messages after the Order exists.
- Separate command/result records retain semantic intent and permit strict adapter validation.

**Alternatives considered**:

- One generic reservation command with string action: rejected because it weakens schema and
  dispatch validation.
- Key reservation messages by `reservationId`: rejected because the approved Saga catalog uses
  `orderId` for post-Order ordering and retains reservation identity in data.
- Create separate topics for confirm and release: rejected because one keyed family already gives
  per-Order ordering with less operational surface.

## Decision 4 — Add consumer-specific DLTs, not business failure events

**Decision**: Provision these operational DLTs alongside the new/extended consumers:

- `flashsale.order.payment-result.dlt.v1`
- `flashsale.flash-sale.purchase-command.dlt.v1`
- `flashsale.order.purchase-reservation-result.dlt.v1`

Malformed, unsupported, or identity-conflicting records are non-retryable. Transient processing
failures receive bounded listener retries before DLT. Required outbox messages retain stable IDs and
durable capped-backoff retry without being discarded.

**Rationale**: DLTs isolate poison records without inventing business outcomes. A temporary Kafka,
Schema Registry, or PostgreSQL failure is not a Payment or reservation failure.

**Alternatives considered**:

- Publish domain failure on any exception: rejected because it converts infrastructure incidents
  into false business facts.
- Drop malformed records after logging: rejected because it removes operator evidence and replay.

## Decision 5 — Use a dedicated Purchase Saga aggregate and inbox

**Decision**: Add `purchase_sagas` and `purchase_saga_inbox` in `order_db`.

- The Saga identity/correlation ID is the upstream `purchaseRequestId`, which is already stable and
  is the `PurchaseAcceptedV1.correlationId`.
- `orderId` and `reservationId` are unique Saga identities.
- The Saga tracks state, payment deadline, latest Payment aggregate version, Payment facts, desired
  terminal Order status, current command identity, step timing, and manual-review evidence.
- The new inbox owns Payment and reservation-result receipts. The existing
  `order_consumer_inbox` continues to own only `PurchaseAcceptedV1`, avoiding a destructive rewrite
  of its accepted constraints and semantics.

**Rationale**: Workflow state is not an Order-line concern. A dedicated aggregate makes legal
transitions and replay rules testable while keeping Order's public status small.

**Alternatives considered**:

- Put all Saga columns on `orders`: rejected because technical workflow progress, participant
  versions, and manual-review evidence are not public Order state.
- Generalize the existing accepted-purchase inbox: rejected because it has specific invariants and
  a local FK shape already verified by Feature 020.

## Decision 6 — Generalize the existing Order outbox

**Decision**: Continue using `order_outbox_events`, but relax its Feature 020 check constraints and
replace the specialized publisher boundary with event-type dispatch for:

- `OrderCreated`
- `PaymentRequested`
- `ConfirmPurchaseReservation`
- `ReleasePurchaseReservation`
- `OrderConfirmed`
- `OrderCancelled`
- `OrderExpired`
- `OrderPaymentReviewRequired`

Each business transition and required outbox row commit in one Order-local transaction. The outbox
retains immutable JSON snapshots internally; only the Kafka adapter maps those snapshots to Avro.

**Rationale**: A second Order outbox would duplicate leasing, retry, observability, and recovery.
One service-owned outbox already provides the correct reliability boundary.

**Alternatives considered**:

- One outbox table per message family: rejected as needless operational duplication.
- Publish directly from the Kafka consumer: rejected because it creates a database/Kafka dual-write
  gap.

## Decision 7 — Model reservation finalization in PostgreSQL and reconcile Redis

**Decision**:

- Extend reservation state to `RESERVED`, `CONFIRMED`, `RELEASED`, and existing `EXPIRED`.
- PostgreSQL row locking decides the durable transition and stores the command inbox plus outcome
  outbox atomically.
- Every accepted reservation command owns one stable `result_event_id`. Replaying the same command
  reuses that result, while a different command may publish the reservation's current durable state
  even when the reservation aggregate version did not change.
- Reservation outcome outbox uniqueness is command-scoped with one partial unique `causation_id`
  across the confirmed/released outcome family, not reservation-version-scoped. Existing
  `PurchaseAccepted` uniqueness remains unchanged.
- Confirmation is allowed only while the reservation is `RESERVED` and before `expiresAt`.
- Release of a still-reserved reservation produces `RELEASED`; an already expired reservation is a
  stable non-confirmable outcome represented in `PurchaseReservationReleasedV1` with
  `reservationStatus=EXPIRED`.
- Add idempotent Redis Lua finalization:
  - confirm marks the Redis reservation `CONFIRMED` and removes its expiry index without restoring
    quota;
  - release marks it `RELEASED`, removes its expiry index, and restores stock/user quota once;
  - expiry retains its existing idempotent quota release.
- A nullable durable Redis-reconciliation marker on the reservation lets a bounded worker retry a
  crash or Redis outage after the PostgreSQL transition.
- Fix expiry selection/transition so a confirmed or released reservation can never restore quota.

**Rationale**: PostgreSQL remains durable truth while Redis stays the atomic hot-path projection.
The reconciliation marker closes the post-commit Redis failure window without a distributed
transaction.

**Alternatives considered**:

- Redis first, then PostgreSQL: rejected because a crash could mutate sellable quota without a
  durable business transition.
- Put Redis inside the database transaction: rejected because it holds locks across a remote call
  and still cannot atomically commit both systems.
- Ignore Redis after finalization: rejected because stale expiry/quota state can oversell or strand
  inventory.

## Decision 8 — Apply owner-approved deadline and terminal policies

**Decision**:

- `paymentDeadline = reservationExpiresAt - 30 seconds`.
- If that deadline is not in the future at Order acceptance time, Order rejects the message as a
  non-retryable contract/business conflict; Flash Sale expiry remains the stock recovery path.
- `PAYMENT_DEADLINE_EXPIRED -> EXPIRED`.
- `CHECKOUT_ATTEMPT_LIMIT_REACHED -> CANCELLED`.
- `PROVIDER_TERMINAL_FAILURE -> CANCELLED`.
- Order remains `PENDING_PAYMENT` while the Saga is in `MANUAL_REVIEW`; no new public status is
  added.

A higher Payment aggregate version supersedes a lower-version failure. While release is still in
flight, success causes an idempotent confirm command; a correlated released/expired current-state
result moves the still-pending Order to manual review. If Order is already `CANCELLED`/`EXPIRED` and
Saga is `COMPENSATED`, the higher-version success directly and atomically corrects Order to
`PENDING_PAYMENT`, records `MANUAL_REVIEW`, and writes one `OrderPaymentReviewRequiredV1` outbox
fact. That already-terminal branch does not silently retry stock allocation. Automatic refund is
forbidden.

**Rationale**: This is the explicit A/A/A/A owner decision and the ADR success-dominant rule.

**Alternatives considered**: Equal deadline, all-cancelled/all-expired mapping, a new
`PAYMENT_REVIEW` Order status, or automatic refund were rejected by the owner or ADR.

## Decision 9 — Enforce monotonic participant versions and stable fingerprints

**Decision**:

- Same event ID plus same canonical fingerprint is a replay no-op.
- Same event ID plus different fingerprint is a non-retryable conflict.
- A participant aggregate version below the recorded version is stale and ignored after durable
  receipt.
- Same version with contradictory content is a conflict.
- A higher Payment version is processed; verified success is allowed to supersede failure.
- Reservation result versions may only move forward and must match Order/Saga/purchase/reservation
  identities.
- A result that reports an unchanged current reservation version is still actionable when its
  `causationId` equals the Saga's `activeCommandId`; this is how a confirm-after-release command gets
  a correlated released outcome and reaches manual review. Same-version contradictory state remains
  a conflict.

**Rationale**: Kafka ordering is per partition, not across topics or redelivery windows. Durable
version/fingerprint rules provide deterministic convergence.

## Decision 10 — One local runner with scenario selectors

**Decision**: Add `infra/docker/smoke/feature-044-purchase-saga.ps1` with bounded scenarios:

- `Contracts`
- `Start`
- `Paid`
- `Failed`
- `Replay`
- `LateSuccess`
- `All`

The script composes existing Feature 019 fixture behavior with the real Order/Payment/Flash Sale
interfaces. It may invoke narrowly named Maven fixture tests to publish schema-valid Kafka inputs,
but it must not mutate another service's database. `All` runs module/contract verification and the
complete deterministic local flow. An optional Stripe test/CLI scenario may consume ignored local
secrets without printing them; the deterministic Saga gate does not depend on a human browser.

**Rationale**: One reviewed runner avoids seven copies of topology, timeout, redaction, and cleanup
logic while still letting the maintainer validate one implementation group at a time.

**Alternatives considered**:

- Seven independent scripts: rejected because shared orchestration would drift.
- SQL fixture insertion: rejected because it bypasses domain and service boundaries.
- Deploy each group to EKS: rejected by the requested local-first, one-release workflow.

## Decision 11 — Single cloud image promotion after local completion

**Decision**:

1. Keep all development and local evidence on the feature branch.
2. Provision the new cloud topic/schemas before application activation.
3. Merge one reviewed implementation PR only after the aggregate local gate and full reactor pass.
4. Run the existing selective delivery for the affected services with immutable ECR tags.
5. Merge one image-promotion PR, let Argo reconcile, then run Phase 21 verification and the extended
   Phase 24 Stripe cloud smoke.

The new runtime values are harmless to old images and become effective as the promoted images roll
out. Kafka retains commands/results during transient rollout ordering. Rollback disables Order
command production first, then restores the prior immutable image set; durable inbox/outbox/Saga
rows are preserved.

**Rationale**: This gives one application image release after all local slices are proven while
retaining the existing GitOps review and rollback boundaries.

## Dependency and ADR result

No new production dependency is required. Existing Spring Data JPA, Spring Kafka, Liquibase, Avro,
Micrometer, Redis, Testcontainers, and PowerShell infrastructure are sufficient.

No new ADR is required because ADR 0018 already approves Order ownership, explicit Payment commands,
Payment result facts, outbox/inbox reliability, success-dominant late success, forward recovery,
manual review, and no automatic refund. This feature completes its explicit required follow-up.
