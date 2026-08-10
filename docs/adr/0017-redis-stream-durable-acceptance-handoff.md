# ADR 0017: Redis Stream Durable Acceptance Handoff

**Status**: Accepted — approved with Feature 019 plan on 2026-08-10  
**Date**: 2026-08-10  
**Owners**: Project owner; architecture reviewer  
**Scope**: `flashsale-service` reservation admission and recovery

## Context

Feature 019 approves Redis Lua as the atomic Flash Sale quota admission mechanism and PostgreSQL as
the durable source of truth. These systems cannot participate in one local transaction.

The unsafe sequence is:

```text
Redis Lua reserves quota
-> Java attempts PostgreSQL persistence
```

If the process dies after Redis commits but before PostgreSQL commits, the system has a winner whose
durable purchase/reservation and required `PurchaseAccepted.v1` outbox intent may be lost. Sending
Kafka directly after Lua has the same dual-write gap and does not create the required durable
reservation first.

The approved specification requires the winner to remain recoverable during a PostgreSQL outage,
for 202 to be returned only after durable commit, and for expiry to prevent a late accepted event.

## Decision

1. The admission Lua script atomically performs quota/user/idempotency mutation and appends an
   immutable persistence command to Redis Stream.
2. Reuse the approved single Redis instance. Use one Stream, `fs:{hot}:handoff`, and one consumer
   group, `flashsale-durable-acceptance-v1`.
3. The HTTP request thread attempts immediate persistence for low latency; the Stream consumer calls
   the same idempotent application use case for recovery.
4. Use stable purchase-request, reservation, and event IDs generated before Lua. PostgreSQL unique
   constraints and terminal outcome rules make request-thread/worker duplicates converge.
5. A Stream record is acknowledged and deleted only after PostgreSQL commits either `ACCEPTED` or
   terminal `EXPIRED`. Use an atomic Redis script for `XACK` plus `XDEL`.
6. Use `XAUTOCLAIM` to recover pending entries after a consumer/process failure. Do not trim Stream
   entries that may still be pending.
7. PostgreSQL arbitrates the race between durable acceptance and five-minute expiry. Once expiry is
   terminal, a late handoff cannot create an accepted reservation/outbox event.
8. A Redis winner with unavailable PostgreSQL returns 503 acceptance-pending and a retry hint, not
   202. The same scoped idempotency key reuses the stable winner.
9. Durable acceptance inserts the purchase, reservation, idempotency result, and PostgreSQL outbox
   row in one transaction. The existing transactional-outbox pattern then publishes Avro to Kafka.
10. Redis Stream is an internal handoff contract. It is not a public integration event and is not
    consumed by another service.

## Boundary Rules

- Redis/Lua/Stream representations stay in reservation outbound/inbound adapters.
- Domain and application code see owned commands, results, and ports, not Spring Data Redis types.
- The Stream payload contains the complete immutable acceptance snapshot required for persistence;
  recovery never calls another service.
- JWTs, secrets, raw authorization headers, and raw idempotency keys are forbidden in Stream fields.
- Redis Pub/Sub, Kafka Connect, Debezium, and direct Kafka publication do not replace this handoff in
  Feature 019.

## Consequences

### Positive

- Closes the process-crash window between Redis admission and PostgreSQL persistence.
- Preserves the fast, atomic Redis hot path without making PostgreSQL the contention point.
- Supports safe replay and future multiple service instances through consumer groups and pending
  reclamation.
- Keeps public semantics honest: accepted means durable, pending means recoverable but not committed.
- Reuses existing Redis infrastructure instead of introducing another deployed component.

### Costs and Risks

- There are two recovery mechanisms: Redis Stream for pre-database handoff and PostgreSQL outbox for
  post-database Kafka publication. Their purposes and metrics must remain distinct.
- Stream pending-list growth and memory pressure become correctness-relevant operational signals.
- Full Redis data loss before PostgreSQL persistence can still lose a pending winner. AOF, backups,
  fail-closed behavior, and documented rebuild/replay procedures are required; zero-loss Redis
  disaster recovery is not claimed by the single-node MVP.
- A global `{hot}` hash tag concentrates keys. It is appropriate only for the approved one-Redis MVP;
  Redis Cluster sharding requires a later ADR and redesigned per-Campaign Stream topology.
- PostgreSQL outages can delay physical quota restoration after eligibility expires because the
  durable terminal outcome must be written first.

## Alternatives Rejected

- **Direct PostgreSQL call after Lua without Stream**: loses winners when the process crashes.
- **Direct Kafka send after Lua**: loses or duplicates events across the crash window and still lacks
  the required durable reservation transaction.
- **Redis Pub/Sub**: no durable pending/replay mechanism.
- **PostgreSQL-only pessimistic locking**: correct but turns the database row into the burst
  contention point and abandons the approved Redis hot path.
- **Distributed transaction / 2PC**: Redis and PostgreSQL do not offer a suitable shared coordinator;
  operational cost is disproportionate.
- **Kafka Connect/Debezium**: useful for PostgreSQL change capture, but does not atomically bridge the
  preceding Redis winner and adds a separate connector platform.
- **Compensate immediately on any database error**: can restore quota while an ambiguous database
  commit actually succeeded, causing oversell.

## Required Follow-up

- This ADR was accepted together with Feature 019 `plan.md`; `tasks.md` may now be generated.
- Implement and test the Redis Lua + XADD atomic boundary, Stream consumer group, `XAUTOCLAIM`,
  stable identities, PostgreSQL acceptance/expiry arbitration, and ACK/delete lifecycle.
- Expose pending count/age, recovery outcomes, PostgreSQL persistence outcomes, and Redis memory
  pressure using low-cardinality telemetry.
- Revisit this ADR before Redis Cluster, multi-region operation, another handoff consumer group, or a
  different durability bridge is introduced.
