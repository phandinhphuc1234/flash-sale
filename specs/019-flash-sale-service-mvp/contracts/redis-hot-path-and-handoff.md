# Redis Hot Path and Durable Handoff Contract

**Scope**: Internal `flashsale-service` adapter contract  
**Redis deployment for MVP**: One authenticated Redis 7.4 instance with AOF enabled

## Atomic Admission

One Lua invocation performs all of the following or none of them:

1. Load Campaign metadata and reject missing/recovery-required state.
2. Check state and `startsAt <= now < endsAt` using the supplied application clock instant.
3. Validate Variant snapshot and positive quantity.
4. Read remaining Variant quota and the user's already reserved quantity.
5. Replay or reject an existing scoped idempotency key before any mutation.
6. Reject sold-out or purchase-limit overflow.
7. Decrement remaining quota and increment the user's Variant quantity.
8. Store stable purchase/reservation/event IDs, immutable price/SKU/allocation snapshot, hashes,
   expiry, and retention time.
9. Add the reservation to `fs:{hot}:expirations`.
10. `XADD` the immutable persistence command to `fs:{hot}:handoff`.
11. Return a typed outcome and stable snapshot.

Every referenced key uses the same `{hot}` Redis hash tag. This is a deliberate single-instance MVP
choice, not a claim that one global Stream scales across Redis Cluster shards.

## Replay Rules

- Identity: `(userId, campaignId, idempotencyKeyHash)`.
- Same `requestHash`: return the original stable identifiers and snapshot without mutation/XADD.
- Different `requestHash`: return `IDEMPOTENCY_CONFLICT` without mutation.
- The raw key is absent from Redis keys/values and logs.
- Redis idempotency expiry is Campaign end plus 24 hours, never only five minutes.

## Stream Processing

```text
Lua winner -> XADD
  -> request thread tries durable persistence
  -> consumer group also recovers pending/new work
  -> one idempotent PostgreSQL outcome
  -> terminal outcome committed
  -> atomic XACK + XDEL
```

Configuration:

- group: `flashsale-durable-acceptance-v1`;
- consumer: `${HOSTNAME}:${applicationInstanceId}`;
- blocking poll: 1 second;
- batch: 100;
- `XAUTOCLAIM` after 30 seconds idle, batch 100;
- do not use `MAXLEN` trimming while an entry can be pending;
- create the group idempotently at service startup without deleting an existing group.

Request-thread persistence and Stream-worker persistence call the same application use case. A
unique conflict is reloaded and compared; it is not treated as a second success or generic failure.

## Quota Release on Expiry

The expiry Lua script runs only after PostgreSQL has committed a terminal expiry outcome. It:

1. loads the reservation hash;
2. checks/sets a one-way `quotaReleased` marker;
3. restores exactly the accepted quantity to Variant remaining quota;
4. decrements the user's reserved quantity without going below zero;
5. removes the reservation from the expiration ZSET;
6. keeps or expires the replay data until the Campaign end + 24 hour boundary.

Repeated calls are no-ops after the release marker. An eligibility timeout at five minutes is
enforced even if a PostgreSQL outage delays the physical counter restoration.

## Campaign Projection Updates

Scheduled/activated update scripts compare `aggregateVersion` atomically:

- incoming lower/equal version -> no-op success;
- newer scheduled snapshot -> replace complete item/quota projection;
- newer activation with existing scheduled projection -> activate;
- activation without valid scheduled projection -> set recovery marker, do not initialize quota.

The ordinary reservation script never invokes Campaign/Product/Inventory/Order/Payment/Auth.

## Failure Mapping

| Failure | Behavior |
|---|---|
| Redis unavailable before decision | Fail closed; public 503; no PostgreSQL acceptance. |
| Process dies after Lua | Stream pending/new entry is replayed by the consumer group. |
| PostgreSQL unavailable after winner | Keep Stream entry; public acceptance-pending 503; retry same key. |
| Consumer dies after reading | `XAUTOCLAIM` transfers pending entry after 30 seconds. |
| Process dies after DB commit before ACK | Replay observes same terminal result, then ACK/deletes. |
| Expiry and acceptance race | PostgreSQL terminal outcome decides; no `EXPIRED -> ACCEPTED`. |
| Redis state completely lost | Admission fails closed; operator/event/snapshot rebuild is required. |

## Memory and Security

- Production-like Redis must require authentication and AOF according to root infrastructure policy.
- Business keys must not be silently evicted; memory exhaustion must surface through readiness and
  alerts before admission becomes unsafe.
- Metrics/logs cannot tag or print user IDs, raw keys, JWTs, full Stream payloads, or secrets.
- Lua SHA/script loading may be cached, but adapters must recover from `NOSCRIPT` by reloading the
  approved script rather than falling back to non-atomic Java commands.
