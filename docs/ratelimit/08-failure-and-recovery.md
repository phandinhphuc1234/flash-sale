# 08 - Failure, Retry, and Recovery Semantics

**Document status**: Approved living design for Feature 013  
**Canonical sources**: [spec](../../specs/013-gateway-redis-rate-limiter/spec.md),
[plan](../../specs/013-gateway-redis-rate-limiter/plan.md), [ADR 0004](../adr/0004-gateway-distributed-rate-limiter.md)

## 1. Failure Ownership Matrix

| Situation | Meaning | Approved outcome |
|-----------|---------|------------------|
| Bucket lacks credit | Quota was successfully evaluated and exhausted | Gateway-owned 429, downstream zero calls. |
| Missing/unusable direct IP | No safe bucket identity | Skip Redis, downstream once, identity-unavailable signal. |
| Redis timeout/connection/state/result/script/serialization failure | Quota cannot be trusted | Fail open, downstream once, bounded operator signal, no quota headers. |
| Invalid enabled configuration | Deployment/configuration bug | Fail startup before serving traffic. |
| Downstream returns 429 | Service-owned response | Pass through unchanged. |
| Downstream unavailable | Routing/downstream failure | Existing Gateway failure handling. |
| Unexpected limiter bug | Programming error outside approved coordinator failure allow-list | Existing Gateway error boundary. |
| Response already committed | Another owner started the response | Do not rewrite. |

## 2. Quota Exhaustion

Only a successful atomic `REJECTED` decision may select `RATE_LIMIT_EXCEEDED`. Redis failure,
identity failure, serialization failure, downstream 429, and unexpected code errors must never be
misrepresented as client quota exhaustion.

## 3. Fail-Open Allow-List

The MVP failure mode is `ALLOW_WITH_METRIC`. It applies only to controlled coordinator failures:

- `TIMEOUT`;
- `CONNECTION`;
- `INVALID_STATE`;
- `INVALID_RESULT`;
- `SCRIPT`;
- `SERIALIZATION`.

There is no limiter 503 contract and no catch-all `UNKNOWN` category in Feature 013.

## 4. Timeout and Retry

Redis acquisition timeout is exactly `50ms`. Timeout and connection reset are ambiguous: Redis may
have already consumed quota even though the Gateway did not receive the result.

Therefore:

- do not blindly retry an acquisition;
- do not compensate bucket state;
- do not call downstream more than once;
- distinguish timeout from quota rejection in telemetry.

Script cache miss is different: Spring Data may recover through its normal `EVALSHA` to `EVAL`
fallback. That is framework script-cache recovery, not an application retry after an ambiguous
acquisition.

## 5. Invalid Redis State

Invalid state returns `INVALID_STATE` and follows fail-open. The script must not silently repair,
reset, delete, or clamp invalid balance data.

Special approved `PTTL = -1` rule:

- if a wrong-type or hash key has no expiry, apply only `PEXPIRE(stateTtlMs)`;
- leave type and fields untouched;
- return `INVALID_STATE`;
- current request fail-opens;
- later still-invalid acquisitions do not refresh the expiry;
- otherwise-valid hash content may resume normal evaluation later because no quarantine marker is
  stored.

An invalid key with an existing finite TTL keeps that decreasing TTL, even if it is longer than the
normal policy TTL.

## 6. State Loss and Rotation

Missing state starts full. Redis restart, eviction, flush, secret rotation, or policy-state version
change can reset edge quota protection, but cannot lose durable Product/order/payment data.

Material policy changes use a new `state-version`; old keys expire naturally. Do not scan or delete
shared Redis as a routine rollout/rollback step.

## 7. Operational Recovery

Immediate recovery path:

1. Set `GATEWAY_RATE_LIMIT_ENABLED=false`.
2. Restart/redeploy Gateway.
3. Verify catalog routing and existing Gateway error behavior.
4. Inspect bounded metrics/Observation and safe lifecycle logs.
5. Let Redis keys expire naturally.

Do not flush the shared Redis or delete volumes just to roll back the limiter.
