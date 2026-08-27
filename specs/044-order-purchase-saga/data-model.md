# Data Model: Order-Owned Purchase Saga Completion

## Ownership overview

```text
order_db
  orders 1 ── 1 purchase_sagas
  purchase_sagas 1 ── * purchase_saga_inbox
  orders / purchase_sagas ── * order_outbox_events

flashsale_db
  purchase_requests 1 ── 1 flash_sale_reservations
  flash_sale_reservations 1 ── * reservation_command_inbox
  flash_sale_reservations ── * flash_sale_outbox_events

payment_db
  unchanged by Feature 044
```

There are no cross-service foreign keys. UUIDs shared in messages are correlation values only.

## Order aggregate changes

Existing fields remain. The allowed `status` values become:

| Status | Meaning |
|---|---|
| `PENDING_PAYMENT` | Order exists but the Saga has not completed a confirmed purchase. Also retained while paid work is in manual review. |
| `CONFIRMED` | Payment succeeded and Flash Sale acknowledged reservation confirmation. |
| `CANCELLED` | Payment ended with attempt-limit or provider-terminal failure and reservation release was acknowledged. |
| `EXPIRED` | Payment deadline expired and reservation release/expiry was acknowledged. |

### Order transitions

```text
PENDING_PAYMENT -> CONFIRMED
PENDING_PAYMENT -> CANCELLED
PENDING_PAYMENT -> EXPIRED

CANCELLED/EXPIRED -> PENDING_PAYMENT
  only for a higher-version verified late Payment success that cannot yet be confirmed;
  PurchaseSaga must become MANUAL_REVIEW and OrderPaymentReviewRequiredV1 must be durable
  in the same transaction
```

No general-purpose reopen operation exists. `CONFIRMED` never transitions to an unpaid state.

## PurchaseSaga aggregate

Table: `purchase_sagas`

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key; equal to `purchaseRequestId` and the stable Saga/correlation identity. |
| `order_id` | UUID | Unique, non-null FK to local `orders`. |
| `purchase_request_id` | UUID | Unique, non-null; equal to `id`. |
| `reservation_id` | UUID | Unique, non-null. |
| `status` | VARCHAR(40) | One approved Saga status below. |
| `payment_deadline` | TIMESTAMPTZ | Exactly `reservationExpiresAt - 30 seconds`; future at creation. |
| `payment_id` | UUID nullable | Set by the first accepted Payment result; later results must agree. |
| `last_payment_version` | BIGINT nullable | Strict monotonic guard for Payment aggregate results. |
| `payment_succeeded_at` | TIMESTAMPTZ nullable | Present after verified success. |
| `payment_failure_reason` | VARCHAR(64) nullable | One accepted terminal reason while no higher success supersedes it. |
| `desired_order_status` | VARCHAR(32) nullable | `CANCELLED` or `EXPIRED` while release is pending. |
| `active_command_id` | UUID nullable | Stable outbox command identity for the current Saga step. |
| `step_started_at` | TIMESTAMPTZ | Used for recovery age and bounded operator evidence. |
| `manual_review_reason` | VARCHAR(100) nullable | Stable low-cardinality reason; no provider body or secret. |
| `version` | BIGINT | Optimistic version; non-negative. |
| `created_at` | TIMESTAMPTZ | UTC. |
| `updated_at` | TIMESTAMPTZ | UTC, not before `created_at`. |

### PurchaseSaga statuses

| Status | Meaning | Allowed next statuses |
|---|---|---|
| `PAYMENT_PENDING` | Payment command is durable; waiting for a terminal/current Payment fact. | `CONFIRMING_RESERVATION`, `RELEASING_RESERVATION` |
| `CONFIRMING_RESERVATION` | Paid success known; confirm command is durable. | `COMPLETED`, `MANUAL_REVIEW` |
| `RELEASING_RESERVATION` | Terminal unpaid result known; release command is durable. | `COMPENSATED`, `CONFIRMING_RESERVATION` for higher-version success |
| `COMPLETED` | Reservation confirmed and Order confirmed. | none |
| `COMPENSATED` | Reservation released/expired and Order cancelled/expired. | `MANUAL_REVIEW` only for a higher-version verified late success |
| `MANUAL_REVIEW` | Verified payment exists but safe automatic reservation confirmation is unavailable. | operator-owned future workflow; no automatic transition in this feature |

### Invariants

- `payment_deadline = orders.reservation_expires_at - interval '30 seconds'`.
- `payment_deadline > created_at` at initial creation.
- `COMPLETED` requires Order `CONFIRMED`, `payment_id`, and `payment_succeeded_at`.
- `COMPENSATED` requires Order `CANCELLED` or `EXPIRED` and `desired_order_status` matching it.
- `MANUAL_REVIEW` requires verified Payment success and Order `PENDING_PAYMENT`.
- Payment identity never changes after first observation.
- A lower Payment aggregate version cannot change Saga or Order state.

## PurchaseSagaInbox

Table: `purchase_saga_inbox`

| Field | Type | Rules |
|---|---|---|
| `event_id` | UUID | Primary key, stable broker-message identity. |
| `event_type` | VARCHAR(100) | Payment success/failure or reservation confirmed/released. |
| `event_version` | INTEGER | `1` for Feature 044. |
| `producer` | VARCHAR(100) | `payment-service` or `flashsale-service` as required by type. |
| `aggregate_id` | UUID | Participant aggregate identity. |
| `aggregate_version` | BIGINT | Positive monotonic participant version. |
| `order_id` | UUID | Non-null local FK to `orders`. |
| `payload_fingerprint` | CHAR(64) | Canonical lowercase SHA-256. |
| `source_topic` | VARCHAR(200) | Expected topic for the event family. |
| `source_partition` | INTEGER | Non-negative. |
| `source_offset` | BIGINT | Non-negative. |
| `processed_at` | TIMESTAMPTZ | UTC durable receipt time. |

Constraints:

- primary key on `event_id`;
- unique `(source_topic, source_partition, source_offset)`;
- same ID/same fingerprint is replay; same ID/different fingerprint is conflict;
- indexes on `(order_id, processed_at)` and `(aggregate_id, aggregate_version)`.

The existing `order_consumer_inbox` remains specific to `PurchaseAcceptedV1`.

## OrderOutboxEvent changes

Existing table: `order_outbox_events`

The row shape and leasing fields remain. Check constraints are widened:

- `aggregate_type`: `ORDER` or `PURCHASE_SAGA`;
- `aggregate_version`: positive rather than exactly one;
- `event_type`: the eight approved Order-owned messages (`OrderCreated`, `PaymentRequested`,
  confirm/release commands, three terminal facts, and `OrderPaymentReviewRequired`);
- `event_key`: always `orderId` for all new post-Order messages;
- status remains `PENDING`, `IN_PROGRESS`, or `PUBLISHED`.

Logical uniqueness remains `(aggregate_id, aggregate_version, event_type)`. The `event_id`, payload,
causation/correlation values, trace context, and occurred time are immutable across publication
retry.

## FlashSale Reservation changes

Existing table: `flash_sale_reservations`

New/changed fields:

| Field | Type | Rules |
|---|---|---|
| `status` | VARCHAR(16) | `RESERVED`, `CONFIRMED`, `RELEASED`, or `EXPIRED`. |
| `redis_reconciled_at` | TIMESTAMPTZ nullable | Set only after the idempotent Redis finalization projection agrees with durable state. |
| `finalized_at` | TIMESTAMPTZ nullable | Present for terminal/confirmed durable state. |

### Reservation transitions

```text
RESERVED -> CONFIRMED   only before expiresAt
RESERVED -> RELEASED    terminal Payment failure before natural expiry
RESERVED -> EXPIRED     expiry at/after expiresAt
```

All terminal states are idempotent. `CONFIRMED`, `RELEASED`, and `EXPIRED` never transition back to
`RESERVED`. A confirm request for `RELEASED`/`EXPIRED` is non-confirmable and drives the Order Saga
toward manual review through the released result/recovery evidence; it never reallocates stock.

### Redis projection rules

- `CONFIRMED`: mark reservation confirmed and remove expiry-index membership; do not increment
  stock or decrement user quantity.
- `RELEASED`: restore stock and user quota exactly once, mark released, remove expiry membership.
- `EXPIRED`: preserve the existing exact-once quota release behavior.
- `redis_reconciled_at` is written after Lua reports applied or already-applied.
- A recovery worker repeatedly selects final rows with a null marker. This is eventual projection
  repair, not a second business decision.

## ReservationCommandInbox

Table: `reservation_command_inbox`

| Field | Type | Rules |
|---|---|---|
| `command_id` | UUID | Primary key; Avro `eventId`. |
| `command_type` | VARCHAR(100) | Confirm or release. |
| `command_version` | INTEGER | `1`. |
| `producer` | VARCHAR(100) | `order-service`. |
| `saga_id` | UUID | Must equal command correlation ID/purchase request ID. |
| `order_id` | UUID | Kafka key and post-Order ordering identity. |
| `purchase_request_id` | UUID | Must match the durable reservation. |
| `reservation_id` | UUID | Must match the durable reservation. |
| `payload_fingerprint` | CHAR(64) | Canonical lowercase SHA-256. |
| `result_event_id` | UUID nullable | Stable associated outcome outbox identity; non-null after a command is processed. |
| `source_topic` | VARCHAR(200) | `flashsale.purchase.commands.v1`. |
| `source_partition` | INTEGER | Non-negative. |
| `source_offset` | BIGINT | Non-negative. |
| `processed_at` | TIMESTAMPTZ | UTC. |

Constraints include unique source position and unique `(reservation_id, command_type)` for one
semantic finalization action. The same command ID and fingerprint reuses `result_event_id`; a
different command receives its own result identity. Contradictory identity reuse is rejected.

## FlashSale outbox changes

Existing `flash_sale_outbox_events` gains accepted event types:

- `PurchaseAccepted`
- `PurchaseReservationConfirmed`
- `PurchaseReservationReleased`

The table also gains nullable `causation_id`. The existing
`(aggregate_id, event_type, event_version)` uniqueness remains for legacy `PurchaseAccepted` rows
whose `causation_id` is null. Reservation confirmed/released outcomes instead use a partial unique
`causation_id` across that outcome family. Therefore one command cannot produce contradictory
confirmed and released results, replaying one command returns one stable event, and a later distinct
command can receive a new outcome describing the reservation's unchanged current state without
colliding with the earlier outcome.

The internal payload carries `orderId` for post-Order result keying. The relay dispatches by event
type and maps only at the Kafka adapter. Existing lease/retry semantics remain.

Order accepts an unchanged reservation aggregate version only when the result `causationId` equals
the Saga's current `active_command_id` and the reported state matches durable progression. This
correlated current-state result is actionable; same-version contradictory content remains a
non-retryable conflict.

## Migration and rollback

Order and Flash Sale each add one forward-only Liquibase changeset. Migrations:

- create the new Saga/inbox tables and indexes;
- widen only the required existing status/event check constraints;
- add nullable finalization/reconciliation and Flash Sale outbox causation columns plus scoped
  uniqueness indexes;
- retain existing IDs, rows, and outbox history.

Rollback for application release disables Order command production first and restores prior images.
It does not drop tables, delete inbox/outbox rows, shrink status constraints, or rewrite durable
workflow history. Destructive database rollback is not part of the operational rollback.
