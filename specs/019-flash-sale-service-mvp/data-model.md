# Feature 019 Data Model

**Status**: Draft design for plan approval  
**Owner**: `flashsale-service`  
**Durable source of truth**: service-owned PostgreSQL database `flashsale_db`

## Model Boundaries

- Redis owns the disposable, high-speed Campaign projection, quota counters, replay cache, expiry
  index, and recoverable handoff.
- PostgreSQL owns durable purchase requests, reservations, idempotency results, terminal outcomes,
  and event publication intent.
- Kafka owns integration facts after publication; it is not queried as an application database.
- Campaign, Product, Inventory, Order, and Authentication databases are never read by this service.

## Domain State

### CampaignProjection

| Field | Type | Rule |
|---|---|---|
| `campaignId` | UUID | Projection identity. |
| `aggregateVersion` | long | Only a strictly newer version may mutate the projection. |
| `state` | enum | `SCHEDULED`, `ACTIVE`, `ENDED`, `RECOVERY_REQUIRED`. |
| `startsAt`, `endsAt` | instant | Admission requires `startsAt <= now < endsAt`. |
| `item` | Campaign item | Contains the one approved Campaign item snapshot required by Lua. |
| `updatedAt` | instant | Operational visibility, not business ordering. |

### CampaignItemProjection

| Field | Type | Rule |
|---|---|---|
| `variantId` | UUID | Must match the reservation command. |
| `inventoryAllocationId` | UUID | Snapshot provenance; not used to mutate Inventory. |
| `skuSnapshot` | string | Display/audit snapshot; no Product lookup on hot path. |
| `saleUnitPrice` | decimal(19,4) | Non-negative exact monetary value. |
| `currency` | CHAR(3) | Uppercase ISO-style code from Campaign contract. |
| `allocatedQuantity` | long | Positive initial quota. |
| `perUserLimit` | long | Positive Campaign-owned limit. |
| `remainingQuantity` | long | Redis-only derived/mutable counter, never below zero. |

### PurchaseRequest

Represents the stable logical request and terminal decision. It is created with a stable ID before
the Redis Lua call so every retry and recovery path refers to the same identity.

```text
PENDING_HANDOFF -> ACCEPTED
PENDING_HANDOFF -> EXPIRED
ACCEPTED        -> ACCEPTED     (idempotent replay)
EXPIRED         -> EXPIRED      (terminal; cannot become ACCEPTED)
```

`PENDING_HANDOFF` is a Redis concept and does not need a PostgreSQL row. The first PostgreSQL writer
inserts one terminal `ACCEPTED` or `EXPIRED` row.

### Reservation

```text
RESERVED -> EXPIRED
```

Feature 019 does not implement confirmation, release by Order, or payment states. `expiresAt` is
exactly five minutes after the Redis winner is established. A reservation that is durably persisted
after its eligibility instant must be recorded as `EXPIRED`, not resurrected as `RESERVED`.

### IdempotencyRecord

Identity: `(userId, campaignId, idempotencyKeyHash)`.

- Same canonical request hash replays the original IDs/outcome.
- Different canonical request hash is a conflict.
- `retainedUntil = campaignEndsAt + 24 hours`.
- Cleanup permits the same raw key to become a new command only after the record is deleted.
- Cleanup does not delete `PurchaseRequest`, `Reservation`, or audit/outbox history.

## PostgreSQL Schema

Names are explicit to avoid collision with other service databases and to keep Liquibase/JPA mapping
reviewable.

### `purchase_requests`

| Column | PostgreSQL type | Null | Constraint / meaning |
|---|---|---:|---|
| `id` | UUID | no | Primary key; stable purchase request ID. |
| `reservation_id` | UUID | no | Unique stable reservation ID. |
| `campaign_id` | UUID | no | Campaign reference only; no foreign key outside service. |
| `variant_id` | UUID | no | Variant snapshot reference only. |
| `user_id` | UUID | no | JWT `sub` parsed as approved UUID identity. |
| `quantity` | BIGINT | no | Check `quantity > 0`. |
| `request_hash` | CHAR(64) | no | SHA-256 canonical request hash. |
| `outcome` | VARCHAR(16) | no | Check in `ACCEPTED`, `EXPIRED`. |
| `expires_at` | TIMESTAMPTZ | no | Five-minute eligibility boundary. |
| `accepted_at` | TIMESTAMPTZ | yes | Required only for `ACCEPTED`. |
| `created_at` | TIMESTAMPTZ | no | Insert timestamp. |
| `updated_at` | TIMESTAMPTZ | no | Last transition timestamp. |

Indexes:

- unique `reservation_id`;
- `(user_id, created_at DESC)` for owner-scoped operational lookup;
- `(campaign_id, outcome, expires_at)` for expiration/recovery scanning.

### `flash_sale_reservations`

| Column | PostgreSQL type | Null | Constraint / meaning |
|---|---|---:|---|
| `id` | UUID | no | Primary key; equals the stable Lua reservation ID. |
| `purchase_request_id` | UUID | no | Unique FK to `purchase_requests(id)`. |
| `campaign_id` | UUID | no | Snapshot reference. |
| `variant_id` | UUID | no | Snapshot reference. |
| `user_id` | UUID | no | Reservation owner. |
| `inventory_allocation_id` | UUID | no | Campaign allocation snapshot; never used to update Inventory DB. |
| `sku_snapshot` | VARCHAR(120) | no | Immutable accepted snapshot. |
| `unit_price` | NUMERIC(19,4) | no | Check `unit_price >= 0`. |
| `currency` | CHAR(3) | no | Check uppercase three letters. |
| `quantity` | BIGINT | no | Check `quantity > 0`. |
| `status` | VARCHAR(16) | no | Check in `RESERVED`, `EXPIRED`. |
| `expires_at` | TIMESTAMPTZ | no | Five-minute boundary. |
| `version` | BIGINT | no | JPA optimistic version; non-negative. |
| `created_at` | TIMESTAMPTZ | no | Insert timestamp. |
| `updated_at` | TIMESTAMPTZ | no | Last transition timestamp. |

Indexes:

- unique `purchase_request_id`;
- `(user_id, id)` supports owner lookup without exposing foreign rows;
- partial `(status, expires_at)` where status is `RESERVED` supports expiry batches.

### `purchase_idempotency_records`

| Column | PostgreSQL type | Null | Constraint / meaning |
|---|---|---:|---|
| `user_id` | UUID | no | Composite identity. |
| `campaign_id` | UUID | no | Composite identity. |
| `idempotency_key_hash` | CHAR(64) | no | Composite identity; raw key is never stored. |
| `request_hash` | CHAR(64) | no | Canonical request comparison. |
| `purchase_request_id` | UUID | no | Unique FK to purchase request. |
| `reservation_id` | UUID | no | Stable replay result. |
| `retained_until` | TIMESTAMPTZ | no | Campaign end plus 24 hours. |
| `created_at` | TIMESTAMPTZ | no | Insert timestamp. |

Primary key: `(user_id, campaign_id, idempotency_key_hash)`.

Indexes:

- unique `purchase_request_id`;
- `(retained_until)` for bounded cleanup.

### `flash_sale_outbox_events`

| Column | PostgreSQL type | Null | Constraint / meaning |
|---|---|---:|---|
| `event_id` | UUID | no | Primary key and stable Kafka event ID. |
| `aggregate_type` | VARCHAR(64) | no | `PURCHASE_REQUEST`. |
| `aggregate_id` | UUID | no | Purchase request ID and Kafka message key. |
| `aggregate_version` | BIGINT | no | `1` for initial acceptance. |
| `event_type` | VARCHAR(100) | no | `PurchaseAccepted`. |
| `event_version` | INTEGER | no | `1`. |
| `payload` | JSONB | no | Internal stable publication snapshot; mapped to Avro by adapter. |
| `status` | VARCHAR(16) | no | `PENDING`, `PROCESSING`, `PUBLISHED`. |
| `attempt_count` | INTEGER | no | Non-negative. |
| `next_attempt_at` | TIMESTAMPTZ | no | Retry scheduling. |
| `claimed_by` | VARCHAR(200) | yes | Worker identity while leased. |
| `claim_until` | TIMESTAMPTZ | yes | 30-second claim lease. |
| `published_at` | TIMESTAMPTZ | yes | Set after Kafka acknowledgement. |
| `last_error` | VARCHAR(1000) | yes | Sanitized diagnostic, no secrets/payload dump. |
| `created_at` | TIMESTAMPTZ | no | Creation timestamp. |
| `updated_at` | TIMESTAMPTZ | no | Last attempt/status timestamp. |

Constraints/indexes:

- unique `(aggregate_id, event_type, event_version)` prevents a second accepted fact;
- check valid status and non-negative counts;
- partial `(next_attempt_at, created_at)` for `PENDING` rows;
- `(status, claim_until)` reclaims expired `PROCESSING` leases.

## Transaction Boundaries

### Durable acceptance transaction

One local PostgreSQL transaction:

1. Insert or load `purchase_requests` by stable ID.
2. If durable outcome is `EXPIRED`, return terminal expiry without inserting acceptance data.
3. Insert/replay the composite idempotency record and compare `request_hash`.
4. Insert/replay the `flash_sale_reservations` row.
5. Insert/replay the one `flash_sale_outbox_events` row.
6. Commit all four outcomes together.

Unique-constraint conflicts are translated into an idempotent reload and comparison, not a generic
500. A hash mismatch becomes an idempotency conflict.

### Expiry transaction

1. Lock/load the purchase request or insert an `EXPIRED` terminal tombstone using the same ID.
2. If `ACCEPTED`, lock/load the reservation and transition `RESERVED -> EXPIRED` once.
3. If already `EXPIRED`, return replay success.
4. Commit.
5. Only after commit, run the Redis quota-release Lua script.

No expiry event is inserted in Feature 019.

### Outbox claim transaction

1. Select due `PENDING` rows or expired `PROCESSING` leases ordered by creation using
   `FOR UPDATE SKIP LOCKED`, limit 100.
2. Mark them `PROCESSING`, assign worker, set `claim_until = now + 30 seconds`, increment attempts.
3. Commit quickly; publish outside the database transaction.
4. On Kafka acknowledgement, mark `PUBLISHED` in a short transaction.
5. On failure, return to `PENDING`, set exponential `next_attempt_at` capped at 60 seconds, and store
   a sanitized error.

## Redis Key Model

Feature 019 uses the global `{hot}` hash tag because local/VPS deployment has one Redis instance and
Redis Cluster support is out of scope. This deliberately keeps all keys available to one Lua script.

| Key | Type | Purpose |
|---|---|---|
| `fs:{hot}:campaign:{campaignId}:meta` | HASH | Version, state, start/end, recovery flag. |
| `fs:{hot}:campaign:{campaignId}:stock` | HASH | `variantId -> remainingQuantity`. |
| `fs:{hot}:campaign:{campaignId}:item:{variantId}` | HASH | Allocation, SKU, price, currency, per-user limit. |
| `fs:{hot}:campaign:{campaignId}:user:{userId}:qty` | HASH | `variantId -> reserved quantity`. |
| `fs:{hot}:campaign:{campaignId}:idem:{userId}:{keyHash}` | HASH | Request hash, stable IDs, outcome, retention boundary. |
| `fs:{hot}:campaign:{campaignId}:reservation:{reservationId}` | HASH | Winner snapshot and release marker. |
| `fs:{hot}:expirations` | ZSET | Score is `expiresAt` epoch milliseconds; member is reservation ID. |
| `fs:{hot}:handoff` | STREAM | Durable-acceptance work created atomically with the winner. |
| `fs:{hot}:projection-recovery` | ZSET | Campaign IDs requiring snapshot repair; score is next attempt. |

Business quota/projection keys must not use arbitrary LRU/LFU eviction. The deployment must use a
memory policy compatible with protected hot-path correctness and surface memory pressure before
admission becomes unsafe.

## Redis Lua Admission Inputs and Result

Inputs include `now`, Campaign ID, Variant ID, user ID, positive quantity, key hash, request hash,
stable purchase request/reservation/event IDs, accepted timestamp, five-minute expiry, and
idempotency retention timestamp.

The script returns a typed result code plus stable IDs and snapshot data:

- `ACCEPTED_NEW`
- `ACCEPTED_REPLAY`
- `IDEMPOTENCY_CONFLICT`
- `CAMPAIGN_UNKNOWN`
- `CAMPAIGN_RECOVERY_REQUIRED`
- `CAMPAIGN_NOT_ACTIVE`
- `CAMPAIGN_NOT_STARTED`
- `CAMPAIGN_ENDED`
- `VARIANT_NOT_ELIGIBLE`
- `SOLD_OUT`
- `PURCHASE_LIMIT_EXCEEDED`

No infrastructure exception text is returned to the public caller.

## Redis-to-PostgreSQL Stream Record

The Stream is an internal recoverable command, not a public Kafka contract. Fields are immutable
strings/integers sufficient to persist without another service call:

`purchaseRequestId`, `reservationId`, `eventId`, `campaignId`, `variantId`, `userId`,
`inventoryAllocationId`, `skuSnapshot`, `unitPrice`, `currency`, `quantity`, `requestHash`,
`idempotencyKeyHash`, `acceptedAt`, `expiresAt`, `retainedUntil`, `traceparent`, and optional
`tracestate`.

The raw JWT and raw idempotency key are forbidden.

## Validation Rules

- UUID fields must be valid and non-null where required.
- Quantity, initial allocation, and per-user limits are positive `long` values; arithmetic detects
  overflow before mutation.
- Remaining and user-reserved quantities never become negative.
- Price uses exact decimal scale four, never floating point.
- Currency is exactly three uppercase ASCII letters.
- `startsAt < endsAt`; `acceptedAt < expiresAt`; `expiresAt = acceptedAt + 5 minutes`.
- Request and key hashes are lowercase 64-character SHA-256 hex.
- All stored timestamps are UTC instants.

## Cleanup and Retention

- Delete eligible Redis/PostgreSQL idempotency records in bounded batches after `retainedUntil`.
- Delete Stream entries only after a terminal PostgreSQL outcome and successful `XACK`.
- Do not trim pending Stream entries.
- No automatic deletion policy for purchases, reservations, or outbox rows is approved in this
  feature; later retention work requires an explicit spec amendment.
