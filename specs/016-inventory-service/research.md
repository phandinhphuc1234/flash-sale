# Inventory Service Planning Research

## Decision: PostgreSQL is the durable source of truth

**Rationale**: Physical stock, campaign allocations, movement audit, and outbox rows require durable
transactions and queryable history. Redis runtime stock belongs to `flashsale-service` and must not be
introduced into this service.

**Alternatives considered**: Redis-only inventory was rejected because it weakens durability/auditability;
PostgreSQL per customer purchase was rejected because it would make Inventory Service a hot-path bottleneck.

## Decision: Lock competing stock commands in PostgreSQL

**Rationale**: Allocation, release, reconciliation, and stock adjustments mutate shared balances. A
pessimistic row lock (`FOR UPDATE`) plus database checks gives a clear no-oversell guarantee. The
aggregate still carries a version for ordinary optimistic concurrency.

**Alternatives considered**: Optimistic retries alone were rejected for the Phase 1 allocation guarantee;
distributed Redis locks were rejected because Redis is not the durable inventory owner.

## Decision: Use a transactional outbox

**Rationale**: Inventory state, immutable movement, and integration-event intent must commit atomically.
Kafka publication is at-least-once and consumers must be idempotent.

**Alternatives considered**: Publishing after commit without an outbox can lose events; publishing before
the database transaction can emit events for rolled-back state.

## Decision: Keep customer contention outside Inventory Service

**Rationale**: `flashsale-service` consumes allocated quantity into Redis Lua runtime stock and handles
customer reservation, purchase contention, expiry, and per-user limits. Inventory is called for durable
lifecycle operations only.

## Approved non-Kafka decisions

### Authorization

Human inventory operations use `INVENTORY_ADMIN` through Gateway, with a duplicate check inside
`inventory-service`. Internal lifecycle operations use a service JWT with `SCOPE_INVENTORY_WRITE` and
are not public routes. This keeps the existing `CATALOG_ADMIN` authority scoped to Product catalog work.

### SKU snapshot

Capture the Product SKU at initialization as a read-only snapshot. Product Service remains authoritative;
future Product update events may refresh the snapshot after the deferred Kafka contract is approved.

### Release ownership

Campaign Service owns release because it owns campaign cancellation and schedule lifecycle. Flash Sale
Service owns reconciliation because it knows sold and returned runtime quantities.

### Movement pagination

Use `common-web` `PageResponse<T>`/`PageMeta`, newest-first with `createdAt DESC, id DESC`, default
`page=0,size=20`, and maximum `size=100`.

## Unresolved Kafka decisions (must be answered before Kafka implementation)

The source specification intentionally leaves these behavior decisions open. They cannot be inferred safely:

1. Variant initialization: `ProductVariantCreated` Kafka event, internal HTTP, or both.
2. Rejected allocation event: synchronous rejection only, separate durable rejection transaction, or a
   command record that captures rejection.
3. Kafka topics, keys, headers, schema version, and producer/consumer rollout order.
4. Outbox polling/claim, retry/backoff, batch size, and permanent-failure policy.
These decisions are recorded in [spec.md](spec.md). Core non-Kafka implementation may proceed, but
initialization adapters and event publication tasks remain blocked until the four Kafka decisions are
approved.
