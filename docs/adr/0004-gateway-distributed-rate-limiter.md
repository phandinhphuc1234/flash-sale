# ADR 0004: Gateway Distributed Rate Limiter

**Status**: Accepted

**Date**: 2026-07-22

**Accepted**: 2026-07-23 by Gateway/Platform owner (user), after Feature 013 artifact approval.

**Feature**: [013 Gateway Redis Rate Limiter](../../specs/013-gateway-redis-rate-limiter/spec.md)

## Context

`api-gateway` is the only public ingress and already routes public Product Catalog reads. Those reads
can burst before a flash sale, but the Gateway currently has no shared quota decision. Feature 012
verified a stable `RATE_LIMIT_EXCEEDED` body without activating a limiter or deciding quota, identity,
Redis state, failure mode, or headers.

A correct limiter must coordinate multiple Gateway replicas, preserve the existing downstream/error
ownership boundary, avoid exposing raw client identity, and have a deliberate outcome when Redis is
slow or unavailable. The project currently has one root-owned Redis 7.4 local runtime and does not
have Micrometer Tracing/OpenTelemetry runtime.

The decision is architectural because it adds distributed shared state and a new public-ingress
control/failure path. ADR 0003's lean Gateway package structure remains governing.

## Decision

### Scope and policy

- Protect only `GET` requests matched to the existing `product-catalog` route.
- Use one local/demo policy: capacity 60, refill 30 tokens per second, request cost 1.
- Keep quota acquisition disabled by default and activate it explicitly in test/local Docker. The
  route-scoped catalog correlation filter is deliberately always active.
- Treat this preset as learning/local protection, not a production throughput SLO.

### Coordination algorithm

- Add Spring Data Reactive Redis and execute one O(1) Lua script for each protected acquisition.
- Reuse the one Redis runtime in root Compose; do not add a second instance/topology.
- Use Redis `TIME` and integer credit units for exact millisecond refill progress. Java validates and
  passes precomputed capacity credit, cost credit, full-refill duration, refill rate, and TTL so Lua
  performs no division; Java derives retry delay from returned remaining credit with exact
  `Math.ceilDiv`.
- Accept only canonical unsigned decimal numeric arguments/state/results (`0` or `[1-9][0-9]*`) up
  to `2^53 - 1`; reject signs, fractions, exponents, leading zeroes and non-finite/out-of-range data
  before mutation, bound every numeric script argument including `refillTokens`, and persist/return
  accepted numbers with non-exponent decimal formatting.
- Keep one expiring hash per opaque caller bucket with `credit` and `last_refill_ms`.
- Missing state starts full. Allowed decisions persist and refresh TTL. Rejected decisions do not
  write or refresh TTL.
- Compute TTL as twice the full-refill duration clamped to 60 seconds–24 hours; the MVP uses 60
  seconds. Enabled configuration rejects a derived full-refill duration above 24 hours so expiry
  cannot reset a bucket before the largest one-request retry horizon.
- Return fixed bounded `ERROR/INVALID_STATE` or `ERROR/INVALID_ARGUMENT` tuples for recognized state
  or defensive argument failures; never classify by parsing exception messages. Treat wrong-type,
  incomplete/non-numeric, future-timestamp, persistent-key, or invalid-result state as typed failure
  and never reset silently. If invalid state has `PTTL = -1`, attach only the normal TTL in the same
  atomic script, preserve type/fields, return `INVALID_STATE`, and never refresh later still-invalid
  failures. Because no marker is added, an otherwise-valid hash may resume normal evaluation on the
  next acquisition. Invalid state that already has a finite TTL keeps that decreasing TTL even when
  it exceeds the policy TTL; the feature does not shorten it.
- Before a rejection, prove the current finite TTL covers the exact deficit/refill horizon without
  division; a shorter TTL returns `INVALID_STATE` and fail-opens without mutation rather than
  advertising a retry delay later than reset-by-expiry.
- Let Spring Data perform `EVALSHA` then `EVAL` cache recovery; add no blind application retry.

### Identity and key protection

- Use only normalized direct socket address bytes and pin `server.forward-headers-strategy=none` so
  `Forwarded` and `X-Forwarded-For` cannot rewrite the direct peer.
- If direct identity is unavailable, skip Redis, forward once, and emit a distinct safe signal.
- Build a versioned, length-prefixed HMAC input containing environment, policy-state version, policy
  ID, identity type, and normalized address bytes.
- Use HMAC-SHA-256 with a dedicated standard-Base64 configuration secret that decodes to at least 32
  bytes. Enabled invalid configuration fails startup.
- Encode the full digest as 64 lowercase hexadecimal characters in `rl:k1:<digest>`. Raw identity and
  the digest never appear in HTTP, logs, metrics, traces, or baggage; only the digest may identify the
  Redis key.
- Create and initialize a fresh `javax.crypto.Mac` for each key derivation; never share a mutable
  `Mac` across concurrent WebFlux requests.

### HTTP and failure behavior

- Only a successful insufficient-quota decision returns the Feature 012 three-field HTTP 429 body.
- Add `Retry-After` delay-seconds, rounded up with minimum 1, and `Cache-Control: no-store` only to
  that Gateway-owned 429.
- Do not emit `RateLimit*` or `X-RateLimit-*` accounting headers.
- Preserve downstream responses, including downstream 429, byte/header ownership unchanged.
- Apply a 50 ms timeout around the Redis acquisition and do not retry ambiguous outcomes.
- Use `ALLOW_WITH_METRIC`: only `TIMEOUT`, `CONNECTION`, `INVALID_STATE`, `INVALID_RESULT`, `SCRIPT`,
  and `SERIALIZATION` forward downstream once without quota/retry headers. Do not add a limiter 503
  contract or unknown/catch-all failure category.
- Scope typed recovery to the Redis acquisition before the one terminal chain/write branch.
  Unexpected Gateway bugs, writer failures, and downstream errors remain owned by the existing error
  boundary.
- Classify only exact Reactor/Spring Data Redis/Lettuce timeout, connection, serialization and script
  types plus bounded Lua/result outcomes; unwrap `RedisSystemException` only for an explicitly
  recognized immediate Lettuce cause, and map Spring `QueryTimeoutException` only when its immediate
  cause is `RedisCommandTimeoutException`. Do not map generic data/Redis base exceptions, parse
  messages, walk arbitrary cause chains, or catch all failures.

### Architecture and operations

- Keep the lean packages from ADR 0003: `configuration`, `filter/global`, `ratelimit`,
  `ratelimit/redis`, `error`, and `observability`. Do not create business-service
  `domain/application/adapter` layers for this technical edge capability.
- The existing `GatewayHttpErrorWriter` remains the sole Gateway error renderer and gains a typed
  rate-limit operation; no second response DTO/writer is introduced. A successful expected 429 does
  not invoke the generic per-error WARN observation, while serialization fallback and every other
  Gateway error preserve central error observation behavior.
- Add an always-active route-aware catalog correlation filter at
  `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2`, immediately before the optional limiter at
  order `- 1`, so normalized/generated `X-Trace-Id` reaches Product on allowed/fail-open traffic and
  the same exchange value appears in an owned 429. Strict admin trace validation is unchanged.
- Add bounded Micrometer metrics and Observation hooks, including declarative local p50/p95/p99 for
  the acquisition timer. Emit no limiter-specific per-request INFO/WARN log; safe lifecycle logs and
  bounded metrics are the operator surface. Do not add an OpenTelemetry SDK, tracing bridge/exporter,
  Collector, Tempo, dashboard, or alert threshold in this feature.
- Set `management.health.redis.enabled=false` so Redis is not part of Gateway aggregate health,
  liveness, or readiness under the fail-open policy. Override Gateway's inherited root Compose
  `depends_on` with `{}` so backing-service startup health cannot block the stateless edge process.

## Alternatives considered

### Spring Cloud Gateway built-in RedisRateLimiter

Rejected for this feature because its public configuration does not own all approved semantics:
rejection TTL non-refresh, typed corrupt-state handling, exact fail-open branches, central writer
integration, and the required header/downstream ownership rules.

### Per-replica in-memory limiter

Rejected because every replica would grant its own full capacity and a caller could overspend simply
through load balancing or scaling.

### Fixed/sliding window in Redis

Rejected because the approved user behavior is a refillable burst capacity. Token-bucket credit
matches the selected `capacity/refill/cost` model without boundary spikes from fixed windows.

### Redis transactions or several commands

Rejected because read/refill/consume/expiry would need contention retries or could interleave across
replicas. One small Lua script is the clearer atomic boundary.

### Fail closed with HTTP 503

Rejected for the public catalog MVP because the owner selected availability over enforcement during
coordinator failure. A future critical route can choose fail-closed only with a new exact contract
and plan.

### Raw IP keys or unkeyed hashes

Rejected because raw/stable identities would be visible or vulnerable to offline enumeration. HMAC
with a dedicated secret provides deterministic but opaque bucket separation.

### Multiple Redis instances

Rejected because prefix isolation and bounded TTL are sufficient for current ephemeral state. Extra
instances add topology and operations without changing the correctness model.

## Consequences

### Positive

- All Gateway replicas share one atomic caller balance.
- The public 429 contract remains stable and clients receive an exact retry hint.
- Redis uncertainty is distinguishable from client abuse and does not silently take catalog traffic
  offline.
- Raw caller identity is not stored or exported.
- Valid state is self-cleaning; persistent invalid state receives one expiry-only normalization and
  cannot become durable business truth through a limiter repair/reset/scan. Otherwise-valid hash
  content may resume normally because Option A stores no quarantine marker.
- The design can later plug into Micrometer/OpenTelemetry runtime without direct SDK coupling.

### Negative and accepted trade-offs

- During Redis failure, fail-open callers can overload Product service; metrics, the 50 ms bound, the
  feature flag, and downstream protections are required mitigations.
- Redis restart/eviction/expiry can reset a bucket to full.
- A pre-existing invalid key with a finite TTL longer than policy TTL is not shortened; operators
  must wait, disable limiting, or rotate policy-state version when that external corruption matters.
- Direct socket identity in Kubernetes will identify an ingress/proxy rather than the original client;
  production ingress activation is therefore deferred until an exact trusted-proxy strategy exists.
- Secret or policy-state rotation creates fresh buckets. Mixed replicas during an uncoordinated
  rollout can grant parallel capacity.
- The custom script and result mapper require real-Redis concurrency, TTL, corrupt-state, and
  cache-recovery tests plus controlled reactive test doubles for deterministic timeout and malformed
  result behavior.

## Migration and rollout impact

1. Add the Gateway dependencies, service-owned configuration, implementation, tests, and script.
2. Add the Feature 013 contract as the approved amendment and link it from Feature 012 before
   emitting the new headers.
3. Keep quota acquisition disabled while deploying and running Gateway regressions; verify the
   intentional additive catalog `X-Trace-Id` downstream request propagation separately.
4. Wire Gateway to the existing root Compose Redis, remove its inherited backing-service startup
   dependency, and provide a non-committed generated secret.
5. Enable only in local/test, run contract/failure/concurrency/load measurements, then review evidence.

No database migration, Kafka contract, Kubernetes manifest, service boundary, or durable data
migration is introduced.

## Rollback

Set `GATEWAY_RATE_LIMIT_ENABLED=false` and redeploy/restart Gateway. Requests then preserve existing
routing without Redis acquisition; the always-active catalog correlation filter continues the
approved additive downstream `X-Trace-Id` propagation until Feature 013 code itself is rolled back.
Do not flush Redis or delete volumes; valid keys and expiry-normalized invalid keys clean up
naturally. Roll back the dependency/code only after the flag has disabled enforcement and regression
tests pass.

## Future Kubernetes decision boundary

Before cluster activation, a separate approved feature must define:

- trusted ingress/proxy CIDRs or hop strategy for client identity;
- Kubernetes Secret/external secret distribution and coordinated rotation;
- Redis HA/Cluster topology, credentials/TLS, resource limits, and failure objectives;
- multi-replica activation/rollback without parallel state versions;
- monitoring assets/alerts under root `infra/monitoring/`;
- applicable Kustomize/Helm ownership and dry-run validation.

This ADR does not authorize those production decisions.
