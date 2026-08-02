# 01 - Scope and Status

**Document status**: Approved living design for Feature 013  
**Canonical source**: [Feature 013 spec](../../specs/013-gateway-redis-rate-limiter/spec.md),
[plan](../../specs/013-gateway-redis-rate-limiter/plan.md), [ADR 0004](../adr/0004-gateway-distributed-rate-limiter.md)

## 1. What Feature 013 Solves

Gateway rate limiting protects the public Product Catalog route from one caller exhausting
downstream capacity during bursts. It is edge protection only: it decides whether a catalog request
may be proxied, but it does not own Product data, stock, order, payment, authentication, or business
purchase limits.

## 2. Approved MVP Scope

- Protect only `GET` requests matched to the existing `product-catalog` Gateway route.
- Use one effective policy per protected request.
- Use the local/demo policy: capacity `60`, refill `30` quota units per `1s`, request cost `1`.
- Coordinate same-bucket decisions through one atomic Redis Lua acquisition.
- Resolve caller identity from the normalized direct socket IP only.
- HMAC the identity before it becomes a Redis key.
- Return a Gateway-owned 429 only when quota exhaustion is successfully proven.
- Fail open for approved identity/Redis coordinator uncertainty, with bounded operator signals.
- Keep quota acquisition disabled by default.
- Propagate one safe `X-Trace-Id` to downstream catalog requests even when limiting is disabled.

## 3. Verified Baseline Preserved

| Baseline | Required behavior |
|----------|-------------------|
| Spring Cloud Gateway WebFlux | Request path remains reactive and non-blocking. |
| Feature 012 429 body | `ApiErrorResponse` with `RATE_LIMIT_EXCEEDED` and `Retry-After`; trace is header-only. |
| Downstream response ownership | A downstream 429 or error is passed through unchanged. |
| One root Redis runtime | Reuse root `infra/docker` Redis 7.4; do not add another Redis topology. |
| Gateway package shape | Follow ADR 0003 lean edge packages; no fake business Clean/Hex layers. |
| Observability baseline | Use Actuator/Micrometer abstractions; do not construct Prometheus registries in Java. |

## 4. Explicitly Out of Scope

| Item | Status |
|------|--------|
| API-key, authenticated-user, or hierarchical buckets | Deferred to a later feature. |
| Trusted ingress/proxy parsing in Kubernetes | Deferred until exact proxy trust rules are approved. |
| Fail-closed limiter 503 | Not implemented in Feature 013. |
| Redis Cluster, HA, multi-region quota consistency | Deferred production architecture work. |
| Dynamic policy database/admin UI | Deferred because it needs authorization, audit, and rollout rules. |
| Circuit breaker around Redis | Deferred; Feature 013 uses exact 50 ms timeout plus fail-open. |
| Grafana dashboards, alerts, OTel Collector/Tempo runtime | Deferred to monitoring/tracing features. |
| Production quota/SLO claims | Deferred until load evidence and owner targets exist. |

## 5. Ownership

- `api-gateway` owns edge quota selection, Redis bucket coordination, and Gateway-owned 429 rendering.
- Product service owns catalog business behavior and all Product persistence.
- Redis bucket state is ephemeral coordination data, not durable business truth.
- Root `infra/` owns shared Compose/Kubernetes/monitoring assets.
- Gateway service owns its dependencies, `application.yml`, Lua resource, tests, and runtime policy.

## 6. Current Delivery State

Feature 013 artifacts are approved, ADR 0004 is accepted, and T004-T006 documentation
synchronization is complete. The next approved work is T007 production setup.
