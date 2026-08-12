# 05 - Redis Key, State, TTL, and Lua

**Document status**: Approved living design for Feature 013  
**Canonical source**: [data model](../../specs/013-gateway-redis-rate-limiter/data-model.md)

## 1. One Redis Runtime

Feature 013 reuses the existing root Compose Redis 7.4 runtime. It must not add another Redis
container, volume, network, logical service topology, or service-local Compose file.

Rate-limit state is isolated by key namespace, not by a new Redis instance.

## 2. Ephemeral Coordination State

Bucket state is technical edge coordination data:

- no PostgreSQL table;
- no Liquibase migration;
- no Kafka event;
- no historical retention;
- no durable business truth;
- safe to lose from a Product/order/payment correctness perspective.

Redis loss can reset edge protection, but cannot mutate downstream business state.

## 3. Physical Key

```text
rl:k1:<64-lowercase-hex-digest>
```

The digest is created by the identity/HMAC boundary. Redis/Lua never receives raw IP, JWT, API key,
user ID, path variable, query string, or secret material.

`k1` is the code-owned key-layout version. `p1` is the policy-state version inside the HMAC input.
They solve different problems and must not be mixed.

## 4. Hash State

```text
credit          -> canonical unsigned decimal integer
last_refill_ms  -> canonical unsigned decimal integer
```

Canonical unsigned decimal means exactly `0` or `[1-9][0-9]*`. Signs, leading zeroes, fractions,
exponent notation, NaN/infinity, and values above `2^53 - 1` are invalid.

Lua writes accepted numeric state and returns numeric results with `string.format("%.0f", value)` so
scientific notation is never emitted.

## 5. TTL

```text
fullRefillMs = ceil(capacity * refillPeriodMs / refillTokens)
stateTtlMs   = clamp(fullRefillMs * 2, 60_000, 86_400_000)
```

For the MVP policy, `stateTtlMs = 60_000`.

| Decision | TTL behavior |
|----------|--------------|
| Missing/new bucket allowed | Write state and `PEXPIRE stateTtlMs`. |
| Existing allowed | Write state and refresh `PEXPIRE stateTtlMs`. |
| Rejected | Write nothing and do not refresh expiry. |
| Invalid state with finite TTL | Return `INVALID_STATE`; do not shorten or refresh expiry. |
| Wrong-type/hash state with `PTTL = -1` | Apply only `PEXPIRE stateTtlMs`, leave type/fields untouched, return `INVALID_STATE`. |

Before returning `REJECTED`, Lua must prove the current finite TTL covers the exact retry horizon.
If not, it returns `INVALID_STATE` without mutation so expiry cannot reset quota earlier than the
advertised `Retry-After`.

## 6. Lua Input Contract

```text
KEYS[1] = physical bucket key

ARGV[1] = capacityCredit
ARGV[2] = refillTokens
ARGV[3] = requestCostCredit
ARGV[4] = fullRefillMs
ARGV[5] = stateTtlMs
```

Java derives scaled values with overflow checks before invocation. Lua still defensively validates
all arguments before mutation.

## 7. Lua Output Contract

```text
["ALLOWED",  "<remainingCredit>", "0"]
["REJECTED", "<remainingCredit>", "0"]
["ERROR",    "INVALID_STATE",    "0"]
["ERROR",    "INVALID_ARGUMENT", "0"]
```

Unknown status/reason, wrong arity, non-canonical numeric field, negative remaining, or
out-of-bound credit is `INVALID_RESULT` at the Java adapter boundary. Java never parses exception
messages to decide state semantics.

## 8. Execution Rules

- Reactive Lettuce only.
- No `.block()`, `.join()`, synchronous Redis template, or local mutable counter.
- One Redis round trip for normal decision.
- One key per script invocation.
- No `KEYS`, `SCAN`, or aggregation on request path.
- Spring Data owns `EVALSHA` to `EVAL` script-cache recovery.
- No blind application retry after timeout or connection reset.

## 9. Test Redis Boundary

Integration tests may use a Redis Testcontainer for isolation. That container is test infrastructure
only and does not violate the one-Redis local runtime decision.
