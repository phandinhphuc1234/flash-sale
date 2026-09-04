# Data Model: Regular Purchase Checkout

**Feature**: 049 Regular Purchase Checkout
**Date**: 2026-09-03

Each table below belongs only to the named service database. IDs referencing another service are
opaque values, never cross-database foreign keys.

## Shared value concepts

### PurchaseSource

```text
FLASH_SALE | BUY_NOW | CART
```

- `FLASH_SALE` preserves all existing behavior.
- `BUY_NOW` is one regular variant submitted directly.
- `CART` is an immutable snapshot of one mutable Cart at submission time.

### StockParticipantType

```text
FLASH_SALE_RESERVATION | REGULAR_STOCK_HOLD
```

This determines which command/result branch the Order Saga uses. It is not inferred from nullable
foreign IDs.

### Money

- Decimal precision/scale remains `NUMERIC(19,4)`.
- Currency is one uppercase ISO-style three-letter code.
- Every line amount equals `unit_price * quantity`; Order total equals the exact sum of lines.
- All items in one Order must use one currency.

## Cart Service (`cart_db`)

### Cart changes

Existing table: `carts`

| Field | Type | Rules |
|---|---|---|
| `version` | BIGINT | New, non-negative, starts at 0 and increments for every durable item add/update/remove/clear or conditional reconciliation effect. |

Existing IDs, owner uniqueness, and timestamps remain unchanged.

### CartItem changes

Existing table: `cart_items`

| Field | Type | Rules |
|---|---|---|
| `version` | BIGINT | New, positive monotonic row revision. A newly inserted item starts at 1; every quantity replacement increments it. Re-adding a previously removed variant receives a revision greater than its earlier incarnation. |

To guarantee monotonic revision across delete/re-add, the Cart aggregate allocates item revisions
from its incremented Cart version. `item.version` therefore uses the Cart mutation version, not a
counter that disappears with the row.

### CheckoutSnapshot

This is an application/domain value returned by an internal Cart query, not a separate Cart-owned
durable table.

| Field | Type | Rules |
|---|---|---|
| `cartId` | UUID | Internal Cart identity. |
| `ownerId` | UUID | Must equal the authenticated shopper passed by trusted Order. |
| `cartVersion` | BIGINT | Exact observed Cart version. |
| `capturedAt` | Instant | UTC query time. |
| `items` | list | Non-empty, sorted by variant ID for canonicalization. |

Each snapshot item contains `variantId`, positive `quantity`, and positive `itemVersion`. It contains
no authoritative price or stock value.

### CartReconciliationInbox

New table: `cart_reconciliation_inbox`

| Field | Type | Rules |
|---|---|---|
| `command_id` | UUID | Primary key; stable Kafka event ID. |
| `order_id` | UUID | One confirmed Order; unique for the command semantic. |
| `cart_id` | UUID | Must match the owned Cart. |
| `owner_id` | UUID | Must match Cart owner. |
| `payload_fingerprint` | CHAR(64) | Lowercase canonical SHA-256. |
| `source_topic` | VARCHAR(200) | Expected Cart checkout command topic. |
| `source_partition` | INTEGER | Non-negative. |
| `source_offset` | BIGINT | Non-negative; unique with topic/partition. |
| `removed_item_count` | INTEGER | Non-negative; audit evidence without item identities. |
| `processed_at` | TIMESTAMPTZ | UTC. |

Same command ID/fingerprint is replay. Same ID/different fingerprint is conflict and must not mutate
the Cart. Inbox insertion and conditional item deletes occur in one transaction.

### Conditional reconciliation transition

For each snapshot item:

```text
delete where cart_id = expectedCartId
        and variant_id = expectedVariantId
        and quantity = expectedQuantity
        and version = expectedItemVersion
```

Missing or nonmatching rows are safe no-ops. If at least one row is deleted, Cart `version` advances
once and `updated_at` changes. No unpaid path writes this inbox or Cart.

## Inventory Service (`inventory_db`)

### InventoryItem compatibility

Existing table: `inventory_items`

No existing quantity column changes meaning. Availability for regular holds is evaluated while the
row is locked:

```text
available = on_hand_quantity
          - campaign_allocated_quantity
          - sum(quantity for unexpired regular hold items whose hold status = HELD)
```

An index on active hold items by `inventory_item_id` supports the sum. The implementation may keep
a denormalized `regular_held_quantity` on `inventory_items` only if migration and invariant tests
prove it equals active hold totals; the baseline plan does not require that optimization.

### RegularStockHold

New table: `regular_stock_holds`

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key; allocated by Order and stable across retry. |
| `purchase_request_id` | UUID | Unique idempotent request identity. |
| `order_id` | UUID | Unique opaque Order reference and Kafka key after acceptance. |
| `shopper_id` | UUID | Opaque owner reference for conflict validation; never exposed/logged. |
| `request_fingerprint` | CHAR(64) | Canonical variant/quantity fingerprint. |
| `status` | VARCHAR(20) | `HELD`, `CONFIRMED`, `RELEASED`, or `EXPIRED`. |
| `expires_at` | TIMESTAMPTZ | Exactly accepted/request hold time plus five minutes. |
| `confirmed_at` | TIMESTAMPTZ nullable | Present only for `CONFIRMED`. |
| `released_at` | TIMESTAMPTZ nullable | Present only for `RELEASED`. |
| `expired_at` | TIMESTAMPTZ nullable | Present only for `EXPIRED`. |
| `version` | BIGINT | Non-negative optimistic aggregate version. |
| `created_at`, `updated_at` | TIMESTAMPTZ | UTC. |

Invariants:

- `HELD` has no terminal timestamp.
- Each terminal status has only its matching terminal timestamp.
- `expires_at > created_at` and duration is exactly five minutes for Feature 049.
- Terminal states never transition to another terminal state automatically.
- Late success after `RELEASED`/`EXPIRED` cannot recreate stock; it yields non-confirmable outcome
  evidence for Order's recovery/manual-review decision.

### RegularStockHoldItem

New table: `regular_stock_hold_items`

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | Primary key. |
| `hold_id` | UUID | Local FK to `regular_stock_holds`, delete restricted. |
| `inventory_item_id` | UUID | Local FK to `inventory_items`. |
| `variant_id` | UUID | Snapshot identity; unique within hold. |
| `quantity` | BIGINT | Positive held quantity. |
| `sku_snapshot` | VARCHAR(100) | Inventory-owned operational snapshot. |
| `created_at` | TIMESTAMPTZ | UTC. |

The aggregate cannot be partially created. Requested duplicate variants are canonicalized into one
line before locking and validation.

### Stock movement additions

Existing table: `stock_movements`

Add or permit these movement types/reference semantics:

- `REGULAR_HOLD_CONFIRMED`: `on_hand_delta = -quantity`, reference is hold/order.
- `REGULAR_HOLD_RELEASED`: no physical on-hand change is required. Because the current movement
  constraint requires a quantity delta, release/expiry audit belongs to the hold/outbox unless the
  schema is additively generalized with an explicit reservation delta. Do not fabricate a stock
  delta merely to satisfy the old table.

One confirmation writes one movement per hold item with stable derived request ID. Replay writes no
additional movement.

### RegularHoldCommandInbox

New table: `regular_hold_command_inbox`

| Field | Type | Rules |
|---|---|---|
| `command_id` | UUID | Primary key. |
| `command_type` | VARCHAR(100) | Confirm or release. |
| `order_id`, `hold_id`, `purchase_request_id` | UUID | Must match durable hold identity. |
| `aggregate_version` | BIGINT | Positive Order Saga command version. |
| `payload_fingerprint` | CHAR(64) | Canonical SHA-256. |
| `result_event_id` | UUID | Stable associated outbox result. |
| `source_topic`, `source_partition`, `source_offset` | position | Source position is unique. |
| `processed_at` | TIMESTAMPTZ | UTC. |

### Inventory outbox generalization

Existing table: `outbox_events`

Add fields required by versioned event publication if absent: aggregate version, event version,
event key, correlation/causation IDs, trace context, attempt/lease scheduling, and timestamps. Accept
regular hold confirmed/released/expired event types. Each state transition, command inbox receipt,
stock movement, and result outbox row commits in one Inventory transaction.

## Order Service (`order_db`)

### RegularPurchaseRequest

New table: `regular_purchase_requests`

| Field | Type | Rules |
|---|---|---|
| `id` | UUID | `purchaseRequestId`, primary key. |
| `shopper_id` | UUID | Authenticated subject. |
| `idempotency_key` | VARCHAR(128) | Opaque caller value; unique with shopper. |
| `request_fingerprint` | CHAR(64) | Canonical immutable payload digest. |
| `source` | VARCHAR(16) | `BUY_NOW` or `CART`. |
| `state` | VARCHAR(32) | See state machine below. |
| `proposed_order_id` | UUID | Allocated once. |
| `proposed_hold_id` | UUID | Allocated once. |
| `cart_id`, `cart_version` | nullable | Required only for Cart source. |
| `snapshot_payload` | JSONB | Canonical requested items/revisions/expected prices; contains no credentials. |
| `hold_expires_at` | TIMESTAMPTZ nullable | Stored after Inventory acceptance. |
| `order_id` | UUID nullable | Local FK after acceptance. |
| `rejection_code` | VARCHAR(64) nullable | Stable business rejection; no raw downstream body. |
| `rejection_payload` | JSONB nullable | Shopper-safe current price/availability details only. |
| `response_payload` | JSONB nullable | Stable accepted response used for exact replay. |
| `traceparent`, `tracestate` | nullable | Bounded W3C context. |
| `created_at`, `updated_at` | TIMESTAMPTZ | UTC. |

Unique constraint: `(shopper_id, idempotency_key)`. Fingerprint constraint is lowercase SHA-256.
Sensitive credentials/tokens are never persisted.

State machine:

```text
RECEIVED
  -> SNAPSHOT_VALIDATED       (Cart source only)
  -> PRODUCT_VALIDATED
  -> HOLD_ACQUIRED
  -> ACCEPTED

RECEIVED/SNAPSHOT_VALIDATED/PRODUCT_VALIDATED
  -> REJECTED                 (validation, Cart, price, sellability, stock)

non-terminal transient dependency failure
  -> same state               (safe retry resumes)
```

`HOLD_ACQUIRED` may not become business `REJECTED`; Order must either finish the idempotent accepted
commit or let/release the hold through an explicit safe recovery path. `ACCEPTED` has non-null
Order/response; `REJECTED` has a stable rejection code.

### Order changes

Existing table: `orders`

| Field | Type | Rules |
|---|---|---|
| `purchase_source` | VARCHAR(16) | New, `FLASH_SALE`, `BUY_NOW`, or `CART`; existing rows backfilled `FLASH_SALE`. |
| `stock_participant_type` | VARCHAR(32) | New, explicit participant enum. |
| `stock_reference_id` | UUID | New, generic Flash Sale reservation or regular hold ID. |
| `cart_id`, `cart_version` | UUID/BIGINT nullable | Present only for `CART`; opaque Cart snapshot reference. |

Existing `purchase_request_id`, user, amount, status, dates, and optimistic version remain.
`campaign_id` and legacy `reservation_id` remain populated for Flash Sale and become nullable only
for regular rows. Cross-field checks enforce:

- `FLASH_SALE` requires campaign/reservation and `FLASH_SALE_RESERVATION`.
- `BUY_NOW` requires no Cart/campaign and uses `REGULAR_STOCK_HOLD`.
- `CART` requires Cart ID/version, no campaign, and uses `REGULAR_STOCK_HOLD`.

### OrderLine changes

Existing `order_lines` already supports multiple rows per Order. The Java domain/persistence mapper
is generalized from one line to a non-empty immutable list. Add optional Product snapshot fields
(`product_id`, `sku_snapshot`, `display_name_snapshot`) only if the public Order contract requires
them; price/currency/variant/quantity remain mandatory. No Product FK is introduced.

### PurchaseSaga changes

Existing table: `purchase_sagas`

| Field | Type | Rules |
|---|---|---|
| `stock_participant_type` | VARCHAR(32) | Backfilled `FLASH_SALE_RESERVATION`; determines command/result branch. |
| `stock_reference_id` | UUID | Generic participant identity, backfilled from `reservation_id`. |

Legacy `reservation_id` stays for existing Flash Sale compatibility and is nullable for regular
rows. State names may be generalized from `CONFIRMING_RESERVATION`/`RELEASING_RESERVATION` only
through additive accepted values; existing values are not reinterpreted or removed. The preferred
new names are `CONFIRMING_STOCK` and `RELEASING_STOCK` for new regular rows while the domain maps
both families explicitly.

### PurchaseSagaInbox changes

Extend accepted event types/producers for regular hold confirmed/released/expired facts. Identity
validation uses participant type plus `stock_reference_id`; the same source-position, event-ID,
fingerprint, and monotonic aggregate-version rules remain.

### Order outbox changes

Extend accepted event types with:

- `OrderCreatedV2`, `OrderConfirmedV2`, `OrderCancelledV2`, `OrderExpiredV2` for regular Orders;
- `ConfirmRegularStockHold`, `ReleaseRegularStockHold` commands;
- `ReconcilePurchasedCartSnapshot` command.

PaymentRequested remains V1. Event key rules:

- all Order/Payment/regular-hold messages use `orderId`;
- Cart reconciliation uses `cartId` to preserve one Cart's cleanup ordering.

The existing outbox row already stores a general string key, so this is a constraint/dispatcher
extension, not a second outbox.

## Product and Payment data

Product has no schema change. Its quote is a current read model. Payment has no schema change because
the existing Payment command already carries the immutable Order amount/currency/deadline and the
existing provider idempotency model remains authoritative.

## Cross-service lifecycle invariants

1. An `ACCEPTED` regular request has exactly one Order, one Saga, one regular hold, and one semantic
   PaymentRequested command.
2. A `REJECTED` request has no Order, Payment command, or nonterminal hold.
3. Order total is derived only from Product quotes explicitly matched to shopper-confirmed prices.
4. Inventory never confirms more quantity than durable regular availability.
5. Order does not become `CONFIRMED` until Inventory emits confirmed hold evidence.
6. Unpaid Order does not cause Cart reconciliation.
7. Confirmed Cart cleanup cannot delete an item revision created after checkout capture.
8. Flash Sale rows/events retain their existing identifiers and semantics.

## Migration and retention

- Cart, Inventory, and Order add forward-only Liquibase changesets.
- Migrations are idempotently tracked by Liquibase and run in service-specific Kubernetes Jobs,
  sequentially before the new images are promoted.
- No migration reads or writes another service database.
- Rollback preserves expanded schema and all regular request/hold/inbox/outbox history.
- Retention/deletion of completed requests, holds, and inbox rows is outside Feature 049; no cleanup
  job may be added without a separately approved retention rule.
