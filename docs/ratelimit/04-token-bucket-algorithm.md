# 04 - Distributed Token Bucket Algorithm

**Document status**: Approved living design for Feature 013  
**Canonical sources**: [data model](../../specs/013-gateway-redis-rate-limiter/data-model.md),
[ADR 0004](../adr/0004-gateway-distributed-rate-limiter.md)

## 1. Goal

The token bucket allows a small burst while limiting average request rate. For Feature 013, every
read, refill, decision, mutation, and expiry operation for one bucket happens inside one atomic
Redis Lua execution.

## 2. Approved Credit Model

The implementation stores integer credit, not floating-point tokens:

```text
one token          = refillPeriodMs credit units
capacityCredit     = capacity * refillPeriodMs
requestCostCredit  = requestCost * refillPeriodMs
credit per ms      = refillTokens
```

For the MVP policy:

```text
capacity           = 60
refillTokens       = 30
refillPeriodMs     = 1000
requestCost        = 1
capacityCredit     = 60000
requestCostCredit  = 1000
```

Redis `TIME` is the coordinator clock. Gateway pod clocks are not used for quota arithmetic.

## 3. Refill and Decision

```text
elapsedMs = nowMs - lastRefillMs
refillCredit = elapsedMs * refillTokens
workingCredit = min(capacityCredit, storedCredit + refillCredit)
```

If `workingCredit >= requestCostCredit`, the request is allowed:

```text
remainingCredit = workingCredit - requestCostCredit
retryAfterMs = 0
```

If `workingCredit < requestCostCredit`, the request is rejected only after Lua proves the current
finite TTL covers the exact retry horizon:

```text
deficit = requestCostCredit - workingCredit
retryAfterMs = ceil(deficit / refillTokens)   # computed in Java with exact ceilDiv
```

Lua does not divide. Java validates the returned credit and computes the public retry delay.

## 4. Invariants

- `0 <= remainingCredit <= capacityCredit`.
- Request cost is positive and no greater than capacity.
- Refill tokens and refill period are positive.
- No same-bucket concurrency path can overspend the bucket.
- Rejected requests do not make balance negative.
- The script is `O(1)` and touches only `KEYS[1]`.
- Every numeric argument and state/result value is an exact integer no greater than `2^53 - 1`.

## 5. Missing and Invalid State

- Missing state starts full.
- A future timestamp is invalid state, not a clamped value.
- Partial, non-canonical, non-numeric, wrong-type, no-expiry, or malformed state returns a typed
  failure instead of silently resetting quota.
- A wrong-type or hash key with `PTTL = -1` receives only `PEXPIRE(stateTtlMs)`, keeps its type and
  fields unchanged, returns `INVALID_STATE`, and the current request fail-opens.
- Later still-invalid acquisitions do not refresh that expiry.
- An otherwise-valid hash whose only defect was missing TTL may resume normal evaluation later.

## 6. Why Not Fixed Window

Fixed windows allow sharp bursts at window boundaries. Token bucket better represents the approved
burst capacity plus refill rate and maps cleanly to `Retry-After` for the next one-unit request.
