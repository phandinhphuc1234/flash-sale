# Feature Specification: Inventory Service

**Feature Branch**: `016-inventory-service`

**Created**: 2026-07-27

**Status**: Approved for core PostgreSQL/HTTP phase; Kafka integration deferred

**Input**: `C:\Users\MSi\Downloads\inventory-service-spec.md`

## Summary

`inventory-service` is the durable owner of physical stock for Product variants and campaign-level
stock allocations. PostgreSQL is the source of truth. Flash-sale customer contention, per-user
limits, Redis runtime stock, reservations, orders, and payments remain outside this service and are
owned by `flashsale-service` or the corresponding bounded context.

## Scope

In scope:

- initialize one inventory item for a Product variant;
- increase and decrease physical stock;
- calculate available quantity as `on_hand_quantity - campaign_allocated_quantity`;
- allocate stock to one campaign/variant pair atomically;
- query an allocation;
- release an active campaign allocation;
- reconcile sold and returned quantities;
- expose immutable stock-movement history;
- persist successful state changes, movements, and required integration events transactionally;
- make every state-changing command idempotent by `requestId`.

Out of scope:

- campaign definition, schedule, lifecycle, or price;
- customer purchase attempts, reservations, checkout, or per-user limits;
- Redis runtime stock or Redis Lua scripts;
- orders, payments, customer profiles, or cross-service database access.

## User Scenarios & Testing

### User Story 1 - Initialize variant inventory (Priority: P1)

As an inventory operator, I want every Product variant to have one durable inventory item so stock
commands have an authoritative record.

**Independent Test**: Process a new-variant message twice and verify exactly one zero-balance item.

**Acceptance Scenarios**:

1. Given no item exists, when a valid variant-initialization command is processed, then one item is
   created with zero on-hand and allocated quantities.
2. Given an item already exists, when the same initialization is processed, then no duplicate is
   created and the operation is idempotent.

### User Story 2 - Adjust physical stock (Priority: P1)

As an administrator or warehouse simulator, I want to increase or decrease physical stock with an
auditable reason.

**Independent Test**: Apply valid increase/decrease commands and verify balances, movement, and
   duplicate-request behavior against PostgreSQL.

**Acceptance Scenarios**:

1. A positive increase changes only on-hand quantity and creates one movement.
2. A decrease that would fall below allocated quantity is rejected with no persisted mutation.
3. Retrying the same `requestId` returns the original result without double-applying stock.

### User Story 3 - Read inventory state and movements (Priority: P1)

As an operator or downstream service, I want current balances and immutable movement history.

**Independent Test**: Query an existing and missing variant and verify the standard response/error
   envelopes and derived available quantity.

**Acceptance Scenarios**:

1. `availableQuantity` equals on-hand minus campaign-allocated quantity and is not stored as a
   database column.
2. Movement history is ordered deterministically and cannot be edited through the API.
3. Missing inventory returns the project-standard not-found error.

### User Story 4 - Allocate campaign stock atomically (Priority: P1)

As Campaign Service, I want to allocate campaign stock without overselling the durable pool.

**Independent Test**: Run two concurrent allocations against 100 available units and verify only
   one allocation of 80 succeeds.

**Acceptance Scenarios**:

1. A valid allocation creates one active campaign/variant allocation, movement, and outbox event in
   one transaction.
2. Insufficient stock or an existing campaign/variant allocation produces no partial mutation.
3. Retrying the same request returns the existing allocation without increasing allocation twice.

### User Story 5 - Release unused campaign stock (Priority: P2)

As Campaign Service, I want to release an active allocation before settlement.

**Independent Test**: Release an active allocation and verify allocated quantity decreases, on-hand
   remains unchanged, and the allocation becomes terminal `RELEASED`.

### User Story 6 - Reconcile a completed campaign (Priority: P1)

As Campaign or Flash Sale Service, I want to settle sold and unused units exactly once.

**Independent Test**: Reconcile 80 sold and 20 returned from an allocation of 100, then verify
   on-hand decreases by 80, allocated decreases by 100, and a terminal `RECONCILED` state exists.

### User Story 7 - Initialize downstream runtime stock (Priority: P2)

As Flash Sale Service, I want a durable `CampaignStockAllocated` event so Redis runtime stock can be
initialized without calling Inventory Service for every customer purchase.

**Independent Test**: Verify an allocation commits its outbox event atomically and that a duplicate
   delivery can be consumed idempotently by the downstream contract test.

## Functional Requirements

- **FR-001**: Maintain exactly one `inventory_items` row per logical `variant_id`.
- **FR-002**: Enforce non-negative on-hand and allocated quantities and `allocated <= on_hand`.
- **FR-003**: Derive available quantity as `on_hand - campaign_allocated`; do not persist it.
- **FR-004**: Require positive quantities and a stable `requestId` for every state-changing command.
- **FR-005**: Make duplicate requests idempotent and reject reuse of a request ID with conflicting data.
- **FR-006**: Support `INCREASE` and `DECREASE` physical-stock adjustments.
- **FR-007**: Reject a decrease that would make on-hand lower than allocated quantity.
- **FR-008**: Support one active allocation per `(campaign_id, variant_id)` in Phase 1.
- **FR-009**: Allocate concurrently without exceeding available stock.
- **FR-010**: Release an active allocation without changing physical on-hand quantity.
- **FR-011**: Reconcile only balanced settlements where `sold + returned = allocated`.
- **FR-012**: Record exactly one immutable movement for each successful mutation.
- **FR-013**: Commit inventory/allocation, movement, and required outbox rows atomically.
- **FR-014**: Keep `variant_id` and `campaign_id` as logical external identifiers; create no
  cross-service foreign keys.
- **FR-015**: Expose documented HTTP contracts and the project-standard response/error envelope.
- **FR-016**: Expose required versioned Kafka events through an idempotent outbox publisher.
- **FR-017**: Propagate correlation/trace IDs through HTTP, Kafka, logs, and publisher processing.
- **FR-018**: Expose liveness, readiness, and Prometheus endpoints using Spring Boot Actuator
  auto-configuration.

## Key Entities

- **InventoryItem**: Durable physical and allocated balance for one Product variant.
- **CampaignStockAllocation**: Campaign/variant allocation lifecycle and settlement quantities.
- **StockMovement**: Immutable audit record containing deltas and resulting balances.
- **OutboxEvent**: Transactionally persisted event awaiting Kafka publication.

## Invariants and lifecycle

```text
available = on_hand - campaign_allocated
on_hand >= 0
campaign_allocated >= 0
campaign_allocated <= on_hand
sold + returned = allocated  (reconciliation)
```

```text
ACTIVE -> RELEASED
ACTIVE -> RECONCILED
RELEASED and RECONCILED are terminal
```

## Approved Decisions

- **Authorization**: Human inventory operations use `INVENTORY_ADMIN` through Gateway at
  `/api/v1/admin/inventory/**`; `inventory-service` revalidates the authority. Internal allocation
  lifecycle endpoints use service-to-service JWT with `SCOPE_INVENTORY_WRITE` and are not public Gateway
  routes.
- **SKU snapshot**: `sku_snapshot` is captured at initialization and is non-authoritative/read-only.
  Product Service remains authoritative. A future versioned Product update event may refresh it; no
  synchronous Product lookup is required for inventory commands.
- **Release ownership**: Campaign Service owns release for campaign cancellation/pre-start. Flash Sale
  Service owns reconciliation because it knows sold and returned runtime quantities.
- **Movement pagination**: Movement history uses shared `common-web` `PageResponse<T>`/`PageMeta`, newest
  first with stable `createdAt DESC, id DESC` ordering. The HTTP contract uses `page=0`, `size=20`, and
  rejects sizes above 100.

## Human Decisions Required

| Priority | Decision | Why it blocks | Options |
|---|---|---|---|
| P0 | Variant initialization trigger | Determines inbound contract and idempotency boundary | `ProductVariantCreated` Kafka event, internal HTTP command, or both |
| P0 | Allocation rejection event | A rolled-back rejection cannot be in the same outbox transaction | Synchronous rejection only; separate rejection transaction; durable command record |
| P1 | Kafka event/topic/version policy | Required before producer and consumer implementation | Confirm repository convention and topic names |
| P1 | Outbox retry policy | Retry/ordering/failure behavior is operationally observable | Batch/claim, retry limit, backoff, and `FAILED` handling |

## Constitutional Constraints

- `inventory-service` owns its PostgreSQL schema, migrations, JPA entities, repositories, and tests.
- Public traffic enters through `api-gateway`; internal HTTP contracts are documented.
- Kafka contracts are versioned and consumers are idempotent.
- PostgreSQL is durable truth; Redis runtime stock is explicitly out of scope here.
- Durable mutation plus event creation uses a transactional outbox.
- Shared orchestration stays under `infra/`; service migrations stay under the service module.
- The service uses feature-local Clean/Hexagonal boundaries: features with domain invariants have
  `domain`, use cases have `application`, HTTP/messaging entry points have `adapter/in`, and
  persistence/broker/client integrations have `adapter/out`. Empty packages are not created. The
  structure is governed by [ADR 0011](../../docs/adr/0011-inventory-feature-hexagonal-boundaries.md).
- The dependency direction remains `adapter -> application -> domain`; configuration may wire both
  adapters and application services.
- Unit, PostgreSQL integration, HTTP contract, Kafka/outbox, concurrency, and observability tests
  apply according to the plan.
