# Data Model: Gateway Redis Rate Limiter

**Feature**: `013-gateway-redis-rate-limiter`  
**Status**: Supporting design input — governed by `plan.md` status  
**Owner**: `api-gateway`

> The state model includes the owner-approved expiry-only quarantine for a wrong-type or hash key
> with `PTTL = -1`; it never repairs or resets invalid balance data.

There is no JPA entity, PostgreSQL table, Liquibase change set, business Aggregate, event, inbox, or
outbox in this feature. The model below is expiring technical coordination state. Product,
inventory, order, and payment truth remains downstream-owned.

## 1. Runtime policy model

### `RateLimitPolicy`

| Field | Type | MVP value | Validation/meaning |
|-------|------|-----------|--------------------|
| `id` | bounded string | `public-catalog-read` | Stable low-cardinality policy identifier |
| `stateVersion` | bounded string | `p1` | Bumped for a material quota, TTL, cost, or identity-policy change |
| `routeId` | bounded string | `product-catalog` | Must match the existing Gateway route ID |
| `methods` | non-empty set | `GET` | Only exact HTTP methods are protected |
| `identityStrategy` | enum | `CLIENT_IP` | Only implemented strategy in Feature 013 |
| `capacity` | positive integer | `60` | Maximum token capacity |
| `refillTokens` | positive integer | `30` | Tokens credited each refill period |
| `refillPeriod` | positive duration | `1s` | Must be an exact positive number of milliseconds |
| `requestCost` | positive integer | `1` | Must be no greater than capacity |
| `failureMode` | enum | `ALLOW_WITH_METRIC` | Only implemented mode in Feature 013 |
| `enabled` | boolean | `true` under global flag | A disabled policy does not match traffic |

Global configuration additionally owns `enabled=false`, `environment=local`, `keyPrefix=rl`, the
fixed key-schema marker `k1`, `commandTimeout=50ms`, and the Base64 HMAC secret.

### Startup invariants

- When global enablement is false, no secret or Redis connection is required to preserve routing.
- When enabled, exactly one effective policy may match a `(routeId, HTTP method)` selector.
- `environment` is 1–32 ASCII characters and matches
  `[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?`; the MVP policy ID and route ID are exactly
  `public-catalog-read` and `product-catalog`; `stateVersion` matches `p[1-9][0-9]{0,8}`.
- Capacity, refill, period, and cost are positive; cost does not exceed capacity.
- Every numeric script argument—`capacityCredit`, `refillTokens`, `requestCostCredit`,
  `fullRefillMs`, and `stateTtlMs`—fits both Java `long` and Redis Lua's exact-integer range
  (`2^53 - 1`). Scaled products use checked arithmetic; overflow or any out-of-range argument fails
  startup.
- Derived `fullRefillMs` is no greater than `86_400_000`; this keeps the earliest one-request retry
  horizon within the maximum bucket TTL instead of allowing expiry to grant quota sooner.
- The HMAC setting is valid standard Base64 and decodes to at least 32 bytes.
- Only `CLIENT_IP` and `ALLOW_WITH_METRIC` are accepted in this feature.

## 2. Identity and key model

### Source identity

The only source is `ServerHttpRequest.getRemoteAddress().getAddress()`.

- Resolved IPv4 becomes four address bytes.
- Resolved IPv6 becomes sixteen address bytes.
- IPv4-mapped IPv6 is normalized to the equivalent four IPv4 bytes.
- Scope IDs and textual formatting do not participate in identity.
- `Forwarded` and `X-Forwarded-For` are ignored.
- Missing/unresolved/unsupported address data returns `identity unavailable`; it never becomes a
  shared string such as `unknown`.

Raw address bytes are temporary input to key derivation and are not retained in an object field,
cache, response, log, metric, trace, or baggage after HMAC computation.

### Canonical HMAC input

```text
u32be(length("gateway-rate-limit-key-k1")) + UTF-8("gateway-rate-limit-key-k1")
u32be(length(environment))                 + UTF-8(environment)
u32be(length(policyStateVersion))          + UTF-8(policyStateVersion)
u32be(length(policyId))                    + UTF-8(policyId)
u32be(length("CLIENT_IP"))                 + UTF-8("CLIENT_IP")
u32be(length(addressBytes))                 + addressBytes
```

`u32be` is a four-byte unsigned big-endian length. The HMAC algorithm is `HmacSHA256`. Output is the
complete 32-byte digest encoded as 64 lowercase hexadecimal characters.

### Physical Redis key

```text
rl:k1:<64-lowercase-hex-digest>
```

Only the opaque digest may appear in the Redis key. It must not appear in HTTP, log, metric, trace,
or baggage data. Environment, policy ID, state version, and raw IP are protected inside the HMAC and
do not appear in plaintext in the key.

`k1` is a code-owned key-layout version. `p1` is the deployment-owned policy-state version. They are
not interchangeable.

## 3. Redis hash state

| Field | Redis representation | Meaning |
|-------|----------------------|---------|
| `credit` | canonical unsigned decimal integer | Available quota credit at `last_refill_ms` |
| `last_refill_ms` | canonical unsigned decimal integer | Redis coordinator timestamp of the last allowed persistence |

For a policy with `periodMs`:

```text
one token          = periodMs credit units
capacityCredit     = capacity * periodMs
requestCostCredit  = requestCost * periodMs
credit per ms      = refillTokens
```

The MVP therefore stores credit in `[0, 60000]`; one request costs `1000`; each elapsed millisecond
credits `30`, up to capacity.

Canonical unsigned decimal means exactly `0` or `[1-9][0-9]*`. Signs, fractions, exponent notation,
leading zeroes, NaN/infinity, and values above `2^53 - 1` are invalid. The script writes accepted
numeric state with `string.format("%.0f", value)` and returns the same non-exponent representation.

### TTL

```text
fullRefillMs = ceil(capacity * refillPeriodMs / refillTokens)
stateTtlMs   = clamp(fullRefillMs * 2, 60_000, 86_400_000)
```

The MVP TTL is 60,000 ms. It is an inactivity cleanup bound, not durable retention.

- Missing/new bucket plus allowed result: write state and `PEXPIRE stateTtlMs`.
- Existing allowed result: write state and refresh `PEXPIRE stateTtlMs`.
- Rejected result: write nothing and do not refresh expiry.
- Invalid wrong-type or hash state with `PTTL = -1`: apply only `PEXPIRE stateTtlMs`, leave the Redis
  type and fields untouched, return `INVALID_STATE`, and fail open the request.
- Existing state with `PTTL >= 0`: expiring state; `0` means expiry is imminent, not that the key is
  persistent. If its content is invalid, return `INVALID_STATE` without shortening or refreshing
  that expiry, even when a pre-existing finite TTL exceeds the normal policy TTL.
- A structurally valid state that would be rejected is usable only when its finite `PTTL` covers the
  exact deficit/refill retry horizon. If not, return `INVALID_STATE` without mutation; never emit a
  retry delay that extends beyond reset-by-expiry.
- Expiry-only normalization stores no quarantine marker. Therefore, an unchanged hash whose only
  defect was `PTTL = -1` may pass normal validation on the next acquisition; normal allowed/rejected
  semantics resume at that point. Still-invalid content keeps failing open without expiry refresh.

## 4. Atomic script contract

### Input

```text
KEYS[1] = physical bucket key

ARGV[1] = capacityCredit
ARGV[2] = refillTokens
ARGV[3] = requestCostCredit
ARGV[4] = fullRefillMs
ARGV[5] = stateTtlMs
```

Java derives all scaled values with exact overflow checks before invocation. All arguments are also
defensively validated by Lua as canonical unsigned decimal integers no greater than `2^53 - 1`.
The script performs no division and accesses no key not listed in `KEYS`.

### Output

The script returns a fixed three-element list of strings:

```text
["ALLOWED",  "<remainingCredit>", "0"]
["REJECTED", "<remainingCredit>", "0"]
["ERROR",    "INVALID_STATE",          "0"]
["ERROR",    "INVALID_ARGUMENT",       "0"]
```

For `ALLOWED` and `REJECTED`, Java validates `remainingCredit` as a canonical exact integer in
`[0, capacityCredit]` and requires the reserved third field to be `0`. A rejected result additionally
requires `remainingCredit < requestCostCredit`; Java then calculates
`retryAfterMs = Math.ceilDiv(requestCostCredit - remainingCredit, refillTokens)`. The result must be
in `[1, Math.ceilDiv(requestCostCredit, refillTokens)]` before it reaches HTTP rendering. This keeps
division out of Lua's floating-number runtime and preserves exact `long` rounding near the accepted
configuration bound. `ERROR/INVALID_STATE` is the stable, bounded
machine-readable signal for a wrong Redis type, incomplete/non-numeric/out-of-range state, a future
timestamp, a persistent key, or a finite TTL shorter than a would-be rejection's retry horizon.
`ERROR/INVALID_ARGUMENT` is the defensive signal for impossible
script arguments and maps to `SCRIPT`. Neither error tuple contains stored values or identity data.
Any unknown status/reason, wrong arity, non-numeric numeric field, negative remaining, or
out-of-bound credit is an `INVALID_RESULT`; Java never parses exception text to classify a state
failure.

### Script transition

1. Read Redis `TIME` and convert to `nowMs`.
2. Read the key type and current `PTTL`; read `credit` and `last_refill_ms` only for a hash.
3. If type is `none`, `PTTL = -2`, and both fields are absent, initialize working credit to capacity
   at `nowMs`.
4. If the key has another type, only part of state exists, fields are non-numeric/out of bounds,
   timestamp is in the future, or `PTTL = -1`, return the fixed `ERROR/INVALID_STATE` tuple without
   parsing or exposing Redis values. All invalid-state cases avoid balance repair/reset/delete. When and only
   when current `PTTL = -1`, first apply `PEXPIRE stateTtlMs`; an invalid key with `PTTL >= 0`
   receives no write or expiry refresh, so expiry-only normalization is attached at most once while
   content remains invalid. Because no marker is stored, an otherwise-valid hash can proceed through
   normal evaluation on a later call.
5. Calculate replenished credit with integer arithmetic. Saturate to capacity before any addition
   that would exceed `capacityCredit`, so no intermediate leaves the exact-integer range.
6. If working credit covers request cost, subtract cost, persist both fields, refresh expiry, and
   return `ALLOWED`.
7. Otherwise let `deficit = requestCostCredit - workingCredit`. Prove expiry safety without division:
   `PTTL >= fullRefillMs` is immediately safe; otherwise `PTTL * refillTokens` is below
   `capacityCredit` and is compared with `deficit`. If it is smaller, return `INVALID_STATE` with no
   mutation. If safe, perform no write/expiry refresh and return `REJECTED` with exact remaining
   credit; Java derives the earliest sufficient delay with exact `long` ceiling division.

The script is O(1) and one invocation is the complete same-bucket atomic boundary.

## 5. State machine

| Current condition | Event | Next condition | Public/filter outcome |
|-------------------|-------|----------------|-----------------------|
| Key missing | Valid acquisition | State created with remaining credit and 60s TTL | Allowed, downstream once |
| Valid state, enough credit | Valid acquisition | Credit reduced; timestamp/TTL refreshed | Allowed, downstream once |
| Valid state, insufficient credit, TTL covers retry horizon | Valid acquisition | State and TTL unchanged | Gateway-owned 429, downstream zero times |
| Structurally valid state, insufficient credit, TTL shorter than retry horizon | Acquisition | State and expiry unchanged | Typed failure; fail open downstream once |
| Valid state | Inactivity reaches TTL | Key removed by Redis | No response; next request starts full |
| Invalid state/future timestamp with TTL | Acquisition | No mutation or expiry refresh | Typed failure; fail open downstream once |
| Wrong-type or hash invalid state with `PTTL = -1` | Acquisition | Attach only 60s quarantine expiry; preserve type/fields | Typed failure; fail open downstream once |
| Still-invalid state with `PTTL >= 0` | Acquisition | State and decreasing finite expiry unchanged, even if longer than policy TTL | Typed failure; fail open downstream once |
| Otherwise-valid hash after expiry-only normalization | Acquisition | Resume normal transition; allowed result may refresh normal TTL | Normal allowed/rejected outcome |
| Redis unavailable/timeout/invalid result | Acquisition | Unknown or unchanged | Typed failure; fail open downstream once; no retry |
| Identity unavailable | Protected request | Redis not called | Distinct bypass; downstream once |
| Limiter disabled/unmatched policy | Any request | Redis not called | Existing Gateway behavior |

## 6. Failure classification

The Redis boundary exposes only controlled categories:

- `TIMEOUT`;
- `CONNECTION`;
- `INVALID_STATE`;
- `INVALID_RESULT`;
- `SCRIPT`;
- `SERIALIZATION`.

Only recognized failures produced while invoking or decoding the Redis acquisition map to this
allow-list. There is no unknown/catch-all category. Downstream errors, writer errors,
`NullPointerException`, `IllegalStateException`, and other unexpected Gateway programming errors are
outside the coordinator boundary and continue to existing Feature 011 error handling.

Exact adapter predicates are: outer `java.util.concurrent.TimeoutException`, direct
`io.lettuce.core.RedisCommandTimeoutException`, or
`org.springframework.dao.QueryTimeoutException` whose immediate cause is that Lettuce timeout →
`TIMEOUT`;
`org.springframework.data.redis.RedisConnectionFailureException` or direct
`io.lettuce.core.RedisConnectionException` → `CONNECTION`;
`org.springframework.data.redis.serializer.SerializationException` → `SERIALIZATION`; direct
`io.lettuce.core.RedisCommandExecutionException` → `SCRIPT`. Lua and strict-decoder outcomes own the
remaining categories. `org.springframework.data.redis.RedisSystemException` is inspected only for an
immediate cause equal to an explicitly listed Lettuce type. Generic base exceptions, message
parsing, arbitrary cause walking, and near-miss wrappers do not enter the allow-list.

## 7. Lifecycle, cleanup, and recovery

- Expiry, eviction, Redis restart, or flush can reset protection state but cannot change business
  truth.
- A detected persistent invalid key receives one bounded expiry-only quarantine. Later invalid
  acquisitions do not refresh it; normal expiry removes still-invalid state and a later request
  starts full. An otherwise-valid unchanged hash may resume normal quota evaluation before expiry.
- A pre-existing invalid key with a finite TTL retains that decreasing TTL even if it is longer than
  the normal policy TTL. The feature does not shorten it; disablement or state-version rotation is
  the operational escape hatch when waiting is unacceptable.
- A finite TTL shorter than a would-be rejection's exact deficit/refill horizon is treated as invalid
  without mutation, so expiry reset never undercuts a published retry delay.
- Material policy changes bump `policyStateVersion`; secret rotation changes the digest. Both create
  fresh keys, and old keys expire without scanning.
- The feature performs no keyspace scan, cross-version read, migration, reconciliation, or backfill.
- All active replicas must share environment, secret, key schema, and policy-state version. A later
  production rolling-rotation design requires a separate approved feature.
