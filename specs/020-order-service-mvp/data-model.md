# Data Model: Order Service Core MVP

**Feature**: [Order Service Core MVP](./spec.md)
**Date**: 2026-08-15
**Owner**: `order-service` / `order_db`

PostgreSQL is the sole durable source of truth for Feature 020. This model does not reference,
query, or constrain a table owned by another service. UUID references to purchase, reservation,
Campaign, Variant, and shopper are external business identities, not cross-database foreign keys.

## 1. Domain Concepts

### Order

- **Kind**: Aggregate Root
- **Identity**: `orderId` UUID
- **Business aliases**: unique human-readable `orderNumber`
- **Upstream identities**: unique `purchaseRequestId` and unique `reservationId`
- **Owner**: `userId` from the accepted event/JWT subject used for queries
- **State in Feature 020**: `PENDING_PAYMENT` only
- **Responsibilities**:
  - preserve the immutable accepted-purchase snapshot;
  - own one Order line;
  - calculate exact subtotal/total;
  - prevent mutation through duplicate or contradictory event delivery.
- **Not the same as**: Flash Sale reservation, purchase request, or Payment attempt

### Order Line

- **Kind**: Entity contained by Order
- **Identity**: `orderLineId` UUID
- **Meaning**: exact Variant/quantity/unit-price snapshot accepted upstream
- **Cardinality**: exactly one line per Order in Feature 020
- **Amount**: `lineAmount = unitPrice * quantity` with exact scale-4 decimal semantics

### Consumer Inbox Receipt

- **Kind**: durable integration-processing identity
- **Identity**: upstream `eventId`
- **Meaning**: proves that one physical event identity was evaluated and records the canonical
  business fingerprint used to distinguish duplicate from contradiction
- **Not domain state**: it is persistence/integration evidence and does not enter the Order domain

### Order-created Publication

- **Kind**: durable outbound integration intent
- **Identity**: stable outbound `eventId`
- **Aggregate identity**: `orderId`, version `1` for initial creation
- **Meaning**: immutable snapshot needed to publish `OrderCreatedV1`
- **Lifecycle**: `PENDING -> IN_PROGRESS -> PUBLISHED`, with failed attempts returning to a due
  retry state; a lease expiry recovers an abandoned `IN_PROGRESS` claim

## 2. Order State Model

```text
PurchaseAcceptedV1
      |
      v
PENDING_PAYMENT
```

Feature 020 has no Order transition after creation. `CONFIRMED`, `CANCELLED`, `EXPIRED`, and any
intermediate payment/reservation-confirmation states require a later approved Saga feature and a
forward migration of the status constraint.

The upstream `reservationExpiresAt` is immutable information. Reaching it does not cause a state
transition in this feature and does not imply that payment is still allowed.

## 3. Exact Value Rules

| Value | Representation | Rule |
|-------|----------------|------|
| UUID identities | PostgreSQL `UUID` / Java `UUID` | Required and immutable |
| Quantity | PostgreSQL `BIGINT` / Java `long` | Positive; multiplication must remain within supported exact amount range |
| Unit/line/subtotal/total amount | PostgreSQL `NUMERIC(19,4)` / Java `BigDecimal` | Positive, exact scale 4, no binary floating point and no rounding during creation |
| Currency | `CHAR(3)` | Exactly three uppercase ASCII letters |
| Instants | `TIMESTAMPTZ` / Java `Instant` | UTC semantics; reservation expiry strictly after accepted time |
| Fingerprint | lowercase hexadecimal SHA-256 (`CHAR(64)`) | Canonical content only; excludes infrastructure position and trace headers |
| Order status | `VARCHAR(32)` | `PENDING_PAYMENT` only in Feature 020 |
| Outbox status | `VARCHAR(16)` | `PENDING`, `IN_PROGRESS`, or `PUBLISHED` |

Canonical monetary values retain scale 4 in persistence and Avro decimal representation. An input
that cannot be represented exactly in `NUMERIC(19,4)` is non-retryable and creates no Order.

## 4. PostgreSQL Schema

### `orders`

| Column | Type | Null | Rule |
|--------|------|------|------|
| `id` | UUID | No | Primary key |
| `order_number` | VARCHAR(64) | No | Unique public/display identifier |
| `purchase_request_id` | UUID | No | Unique accepted-purchase identity |
| `reservation_id` | UUID | No | Unique reservation identity |
| `campaign_id` | UUID | No | Immutable upstream reference |
| `user_id` | UUID | No | Owner identity |
| `status` | VARCHAR(32) | No | Check: `PENDING_PAYMENT` |
| `currency` | CHAR(3) | No | Check: `^[A-Z]{3}$` |
| `subtotal_amount` | NUMERIC(19,4) | No | Positive |
| `total_amount` | NUMERIC(19,4) | No | Positive and equal to subtotal in this feature |
| `accepted_at` | TIMESTAMPTZ | No | Upstream durable acceptance time |
| `reservation_expires_at` | TIMESTAMPTZ | No | Strictly after `accepted_at` |
| `row_version` | BIGINT | No | JPA optimistic version, starts at 0 |
| `created_at` | TIMESTAMPTZ | No | Order commit timestamp source supplied by application clock |
| `updated_at` | TIMESTAMPTZ | No | Equal to created time until a future transition |

Constraints:

```text
PK orders(id)
UNIQUE orders(order_number)
UNIQUE orders(purchase_request_id)
UNIQUE orders(reservation_id)
CHECK status = 'PENDING_PAYMENT'
CHECK subtotal_amount > 0
CHECK total_amount > 0
CHECK total_amount = subtotal_amount
CHECK reservation_expires_at > accepted_at
```

Indexes:

```text
(user_id, created_at DESC, id DESC)
```

The owner list query uses that index. Detail lookup includes both `id` and `user_id` so foreign and
missing identities share one database/result path.

### `order_lines`

| Column | Type | Null | Rule |
|--------|------|------|------|
| `id` | UUID | No | Primary key |
| `order_id` | UUID | No | Foreign key to `orders(id)` with delete restricted |
| `variant_id` | UUID | No | Immutable upstream reference |
| `quantity` | BIGINT | No | Positive |
| `unit_price` | NUMERIC(19,4) | No | Positive |
| `line_amount` | NUMERIC(19,4) | No | Positive and exact product of quantity/unit price |
| `created_at` | TIMESTAMPTZ | No | Same logical creation time as Order |

Constraints:

```text
PK order_lines(id)
FK order_lines(order_id) -> orders(id)
UNIQUE order_lines(order_id, variant_id)
CHECK quantity > 0
CHECK unit_price > 0
CHECK line_amount > 0
```

The application/domain enforces exactly one line and exact multiplication. Persistence tests prove
the database constraints and mapper preserve those values.

### `order_consumer_inbox`

| Column | Type | Null | Rule |
|--------|------|------|------|
| `event_id` | UUID | No | Primary key |
| `event_type` | VARCHAR(100) | No | `PurchaseAccepted` |
| `event_version` | INTEGER | No | `1` |
| `producer` | VARCHAR(100) | No | `flashsale-service` |
| `aggregate_id` | UUID | No | Must equal `purchase_request_id` |
| `aggregate_version` | BIGINT | No | Supported positive version |
| `purchase_request_id` | UUID | No | Diagnostic/business identity |
| `reservation_id` | UUID | No | Diagnostic/business identity |
| `payload_fingerprint` | CHAR(64) | No | Canonical SHA-256 |
| `source_topic` | VARCHAR(200) | No | Expected inbound topic |
| `source_partition` | INTEGER | No | Non-negative diagnostic position |
| `source_offset` | BIGINT | No | Non-negative diagnostic position |
| `order_id` | UUID | No | Foreign key to established Order |
| `processed_at` | TIMESTAMPTZ | No | Local processing instant |

Constraints/indexes:

```text
PK order_consumer_inbox(event_id)
FK order_consumer_inbox(order_id) -> orders(id)
UNIQUE order_consumer_inbox(source_topic, source_partition, source_offset)
INDEX order_consumer_inbox(purchase_request_id)
INDEX order_consumer_inbox(reservation_id)
```

Different event IDs for one equivalent purchase are semantic duplicates but do not require another
inbox row after the established Order is detected. Conflict/DLT evidence is operational and must
not mutate the canonical inbox/Order outcome.

### `order_outbox_events`

| Column | Type | Null | Rule |
|--------|------|------|------|
| `event_id` | UUID | No | Primary key and stable publication identity |
| `aggregate_type` | VARCHAR(64) | No | `ORDER` |
| `aggregate_id` | UUID | No | `orderId` |
| `aggregate_version` | BIGINT | No | `1` for Order creation |
| `event_type` | VARCHAR(100) | No | `OrderCreated` |
| `event_version` | INTEGER | No | `1` |
| `event_key` | VARCHAR(64) | No | UUID text equal to `orderId` |
| `correlation_id` | UUID | No | Preserved inbound correlation ID |
| `causation_id` | UUID | No | Inbound `PurchaseAccepted` event ID |
| `payload` | JSONB | No | Immutable exact publication snapshot |
| `traceparent` | VARCHAR(256) | Yes | Valid inbound W3C context when supplied |
| `tracestate` | VARCHAR(512) | Yes | Optional inbound vendor state |
| `status` | VARCHAR(16) | No | `PENDING`, `IN_PROGRESS`, `PUBLISHED` |
| `attempt_count` | INTEGER | No | Starts at 0, never negative |
| `next_attempt_at` | TIMESTAMPTZ | No | Due time |
| `claimed_by` | VARCHAR(200) | Yes | Worker identity while leased |
| `claim_until` | TIMESTAMPTZ | Yes | Lease deadline |
| `published_at` | TIMESTAMPTZ | Yes | Broker acknowledgement time |
| `last_error` | VARCHAR(1000) | Yes | Sanitized bounded diagnostic only |
| `occurred_at` | TIMESTAMPTZ | No | Order creation fact time |
| `created_at` | TIMESTAMPTZ | No | Row creation time |
| `updated_at` | TIMESTAMPTZ | No | Last relay state change |

Constraints/indexes:

```text
PK order_outbox_events(event_id)
UNIQUE order_outbox_events(aggregate_id, aggregate_version, event_type)
CHECK status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED')
CHECK attempt_count >= 0
INDEX order_outbox_events(status, next_attempt_at, created_at)
INDEX order_outbox_events(claim_until) WHERE status = 'IN_PROGRESS'
```

The JSONB snapshot is an adapter-owned persistence representation. The Kafka adapter maps it into
the generated `OrderCreatedV1`; domain/application do not consume JSON maps.

## 5. Atomic Creation Outcomes

The persistence capability returns one of these application results:

| Outcome | Condition | Mutation | Kafka acknowledgement |
|---------|-----------|----------|-----------------------|
| `CREATED` | No inbox/Order owns the identities | Insert Order, line, inbox, outbox | Yes after commit |
| `EVENT_REPLAYED` | Same event ID and fingerprint | None | Yes |
| `BUSINESS_REPLAYED` | Different event ID, same purchase/reservation and equivalent fingerprint | None | Yes |
| `CONFLICT` | Any owned identity has contradictory fingerprint/business values | None | No normal success; route non-retryable failure to DLT |
| `RETRYABLE_FAILURE` | PostgreSQL unavailable/deadlock/transient failure before commit | Full rollback | No; retry/redeliver |

Locks are acquired for canonical UUID identity keys in deterministic order inside the transaction.
The adapter then evaluates inbox first, followed by purchase and reservation Order lookups. The
database unique constraints are final safeguards, not the primary branching mechanism.

## 6. Canonical Fingerprint Fields

The fingerprint input is length-delimited canonical UTF-8 content in fixed field order:

```text
eventType
eventVersion
producer
aggregateType
aggregateId
aggregateVersion
purchaseRequestId
reservationId
campaignId
variantId
userId
quantity
unitPrice(scale=4, plain decimal)
currency(uppercase)
acceptedAt(UTC instant)
expiresAt(UTC instant)
```

`eventId` is the inbox identity and is intentionally not part of business equivalence. Kafka key,
topic/partition/offset, correlation/causation, `traceparent`, and `tracestate` also do not affect
business equivalence, but their contract consistency is validated separately before processing.

## 7. OrderCreatedV1 Snapshot Mapping

| Outbound value | Source |
|----------------|--------|
| `eventId` | outbox `event_id` |
| `eventType` / `eventVersion` | `OrderCreated` / `1` |
| `producer` | `order-service` |
| `aggregateType` | `ORDER` |
| `aggregateId` / Kafka key | Order `id` |
| `aggregateVersion` | `1` |
| `correlationId` | inbound correlation ID |
| `causationId` | inbound PurchaseAccepted `eventId` |
| `occurredAt` | Order `created_at` |
| data identities and amounts | immutable Order and line snapshot |
| `reservationExpiresAt` | upstream `expiresAt` |
| W3C headers | persisted safe inbound trace context |

## 8. Query Models

### Order Detail

Contains Order ID/number, purchase request/reservation/Campaign identities, status, currency,
subtotal/total, accepted time, reservation expiry, one line, created time, and updated time. It does
not expose `userId`, inbox, outbox, row version, Kafka offsets, internal errors, or database fields.

### Order Summary

Contains Order ID/number, status, currency, total, reservation expiry, and created time. Pages use
the shared page metadata and deterministic `(createdAt DESC, id DESC)` sorting.

## 9. Migration and Rollback

One immutable Liquibase changeset creates objects in this order:

```text
orders
  -> order_lines
  -> order_consumer_inbox
  -> order_outbox_events
  -> indexes
```

Development rollback removes them in reverse dependency order. Once real Order data exists, a
production rollback does not drop business tables; use a forward corrective migration and, if
needed, stop the consumer/publisher through configuration while preserving durable rows.

## 10. Retention

Feature 020 defines no deletion or archival schedule for Orders, lines, inbox, or outbox. No cleanup
job is created. A future retention feature must define audit/idempotency windows, legal/business
requirements, publication evidence, migration, and safe replay behavior before deleting data.
