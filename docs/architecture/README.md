# Architecture Guide

The architecture is easiest to understand in three layers: system ownership, internal dependency
direction, and cross-service reliability.

## Recommended order

1. [`service-clean-hex-structure.md`](service-clean-hex-structure.md) — package and dependency rules.
2. [`service-communication-protocols.md`](service-communication-protocols.md) — HTTP versus Kafka.
3. [`flash-sale-end-to-end-flow.md`](flash-sale-end-to-end-flow.md) — seckill flow.
4. [`outbox-flow.md`](outbox-flow.md) — atomic state plus event publication.
5. [`saga-messaging-reliability.md`](saga-messaging-reliability.md) — Order/Payment/reservation
   coordination and recovery.
6. [`schema-registry.md`](schema-registry.md) — Avro and compatibility model.

Use [`ddd-clean-hexagonal-quick-reference.md`](ddd-clean-hexagonal-quick-reference.md) as a code
review checklist. It explains tactical placement; it does not override an approved feature spec.

## System invariants

- Gateway is the only external business entry point.
- Service databases are private ownership boundaries.
- PostgreSQL is durable truth; Redis Lua controls the flash-sale contention window.
- Immediate business decisions use documented HTTP; durable commands/facts use Kafka.
- Durable state plus required publication uses an outbox.
- Consumers are idempotent and own their retry/DLT behavior.
- Order orchestrates purchase Sagas; Payment and Inventory never update Order tables.
- A timeout is ambiguous until the owning service's idempotency/recovery boundary resolves it.

For the current implemented/deferred boundary, read
[`../current-system-implementation.md`](../current-system-implementation.md).
