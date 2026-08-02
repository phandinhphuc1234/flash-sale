# 10 - Testing, Concurrency, and Validation

**Document status**: Approved living design for Feature 013  
**Canonical source**: [tasks](../../specs/013-gateway-redis-rate-limiter/tasks.md) and
[quickstart](../../specs/013-gateway-redis-rate-limiter/quickstart.md)

## 1. Testing Principle

Tests prove approved behavior. They do not invent quota, TTL, identity, failure, or security policy.
Evidence is recorded during implementation in
`specs/013-gateway-redis-rate-limiter/validation.md`.

## 2. Required Test Layers

| Layer | Scope |
|-------|-------|
| Unit/config | Policy binding, selector uniqueness, numeric bounds, timeout, default disabled behavior. |
| Unit/security | Direct IP normalization, HMAC determinism, secret validation, exposure checks. |
| Real Redis integration | Lua math, TTL, state corruption, script-cache recovery, concurrency. |
| Gateway contract | Exact owned 429, headers, zero downstream, pass-through downstream 429. |
| Failure injection | Timeout, connection, invalid state/result, identity unavailable, no broad catch. |
| Observability/health | Bounded tags, no per-request log amplification, Redis not readiness dependency. |
| Measurement-only load | k6 catalog burst plus Prometheus and Redis INFO capture. |
| Maven gates | Gateway module verify and full reactor verify. |

## 3. Deterministic Concurrency Gate

Approved fixture:

```text
capacity: 20
effective refill during fixture: none
independent limiter clients: 3
concurrent same-bucket acquisitions: 100

expected:
allowed  = exactly 20
rejected = exactly 80
remaining never negative
```

The test uses one Redis 7.4 Testcontainer to simulate shared state across Gateway replicas. It must
not connect to or flush developer Compose Redis.

## 4. Gateway Contract Assertions

- Owned 429 uses the shared `ApiErrorResponse` body; `X-Trace-Id` is a response header and JSON has no `traceId`.
- `Retry-After` is positive integer delay seconds.
- `Cache-Control: no-store` is present only on owned 429.
- No `RateLimit*` or `X-RateLimit-*` accounting headers are emitted.
- Rejected request calls downstream zero times.
- Allowed, identity-unavailable, and fail-open paths call downstream exactly once.
- Downstream-owned 429 status/body/sentinel headers pass through unchanged.
- Serialization fallback 500 has no leftover quota headers.

## 5. Failure Assertions

- Exact 50 ms timeout is proven with Reactor virtual time.
- Ambiguous Redis outcomes are not retried.
- Only the approved finite coordinator failure categories fail open.
- Near-miss wrappers and unexpected programming errors propagate to existing Gateway handling.
- Missing direct identity skips Redis instead of sharing a fallback bucket.
- Redis-down aggregate health/liveness/readiness remain independent of Redis.

## 6. Load Measurement

The k6 scenario targets exactly:

```text
GET /api/v1/catalog/products?page=0&size=20
```

It accepts only HTTP 200 and Gateway-owned 429 and requires both in the burst phase. The run records
HTTP throughput, HTTP p50/p95/p99, limiter acquisition p50/p95/p99 from Prometheus, rejection
accuracy, Redis INFO stats/memory, tool versions, and setup/transport errors.

No production throughput threshold is approved in Feature 013.

## 7. Validation Commands

Focused and full commands are listed in
[quickstart](../../specs/013-gateway-redis-rate-limiter/quickstart.md). Required final gates are:

```powershell
.\mvnw.cmd -pl services/api-gateway -am verify
.\mvnw.cmd clean verify
```

Kubernetes dry-run is not required for Feature 013 because it changes no Kubernetes overlay.
