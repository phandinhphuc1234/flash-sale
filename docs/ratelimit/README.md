# Gateway Rate Limiting Design Guide

**Status**: Approved living design guide for Feature 013  
**Owner**: `api-gateway`  
**Runtime state**: Approved for implementation; production code starts at T007  
**Last reviewed**: 2026-07-23  
**Target feature**: [013 Gateway Redis Rate Limiter](../../specs/013-gateway-redis-rate-limiter/spec.md)

This folder splits the Gateway rate-limiter design into small topic docs so HTTP contract, policy,
algorithm, Redis state, architecture, observability, runtime, and rollout decisions do not blur
together.

## Authority

If documents disagree, use this order:

```text
Constitution
  -> Accepted ADRs
    -> Approved feature spec/contracts
      -> Approved plan
        -> Approved tasks
          -> code/tests/evidence
```

These docs explain and synchronize the approved Feature 013 decisions. They do not override the
Spec Kit artifacts.

## Status Labels

| Label | Meaning |
|-------|---------|
| `Verified` | Implemented and backed by evidence. |
| `Approved` | Approved for implementation by the owning feature artifacts. |
| `Deferred` | Explicitly outside Feature 013. |

Feature 013 is approved, not verified. Runtime behavior becomes verified only after implementation
tasks and validation evidence pass.

## Table of Contents

1. [Scope and status](01-scope-and-status.md)
2. [HTTP response body and headers](02-http-contract.md)
3. [Policy and configuration](03-policy-and-configuration.md)
4. [Distributed token bucket algorithm](04-token-bucket-algorithm.md)
5. [Redis key, state, TTL, and Lua](05-redis-lua-state.md)
6. [Gateway architecture, package, and build](06-gateway-architecture-and-build.md)
7. [Identity, HMAC, and trusted proxy](07-identity-and-security.md)
8. [Failure, retry, and recovery semantics](08-failure-and-recovery.md)
9. [Metrics, logs, and distributed tracing](09-observability.md)
10. [Testing, concurrency, and validation](10-testing-and-validation.md)
11. [Local runtime and Kubernetes expansion](11-runtime-and-kubernetes.md)
12. [Delivery slices and decision register](12-delivery-slices-and-decisions.md)

## Approved MVP Rules

- Protect only `product-catalog` `GET`.
- Use capacity `60`, refill `30/1s`, request cost `1`.
- Use direct socket `CLIENT_IP` identity and ignore forwarding headers.
- HMAC identity with a dedicated Base64 secret of at least 32 decoded bytes.
- Store only ephemeral Redis coordination state in `rl:k1:<digest>` keys.
- Use one Redis Lua acquisition per protected request.
- Return Gateway-owned 429 only for proven quota exhaustion.
- Add only `Retry-After` and `Cache-Control: no-store` to Gateway-owned 429.
- Emit no `RateLimit*` or `X-RateLimit-*` accounting headers.
- Fail open for approved coordinator uncertainty; do not add limiter 503.
- Keep quota acquisition disabled by default.
- Use Micrometer metrics/Observation hooks without claiming full OpenTelemetry runtime.
- Keep shared runtime infrastructure under root `infra/`.

## Canonical Sources

- [Feature 013 spec](../../specs/013-gateway-redis-rate-limiter/spec.md)
- [Feature 013 plan](../../specs/013-gateway-redis-rate-limiter/plan.md)
- [Feature 013 tasks](../../specs/013-gateway-redis-rate-limiter/tasks.md)
- [Feature 013 HTTP contract](../../specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-http.md)
- [Feature 013 configuration contract](../../specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-configuration.md)
- [ADR 0003: Lean API Gateway Package Structure](../adr/0003-lean-api-gateway-package-structure.md)
- [ADR 0004: Gateway Distributed Rate Limiter](../adr/0004-gateway-distributed-rate-limiter.md)
