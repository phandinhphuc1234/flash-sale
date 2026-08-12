# Feature Specification: Gateway Redis Rate Limiter

**Feature Branch**: `013-gateway-redis-rate-limiter`
**Created**: 2026-07-22
**Status**: Approved
**Owner**: Gateway/Platform owner (user)
**Reviewers**: Gateway technical owner, security owner, observability owner, client-contract reviewer
**Input**: User description: "Approve the documented MVP preset and create Feature 013, ADR, contract, plan, and tasks for review before implementation."

> This feature uses the Flash Sale risk profile because it introduces a quota at the only public
> ingress, concurrent shared state, caller identity processing, Redis failure behavior, and an HTTP
> compatibility delta. This approved specification records the accepted MVP preset and all resolved
> risk decisions; implementation must follow the approved plan and task order.

## Clarifications

### Session 2026-07-22

- Q: What happens when the direct client IP is absent or cannot be normalized? → A: Option A —
  bypass quota evaluation, forward downstream exactly once, emit a distinct identity-unavailable
  metric/Observation outcome, and emit no quota or retry header.
- Q: How do rejected requests and material policy changes affect bucket-state lifetime? → A:
  Option A — rejected requests do not refresh TTL; every material quota, TTL, or identity-policy
  change uses a new state version rather than reusing an old bucket.
- Q: What encoding and minimum secret rule govern opaque bucket identities? → A: Option A — emit a
  lowercase hexadecimal HMAC-SHA-256 digest; accept the dedicated secret as Base64 configuration
  that must decode to at least 32 random bytes; fail startup if the enabled limiter receives a
  missing, invalid Base64, or shorter secret.
- Q: How does a wrong-type or persistent (`PTTL = -1`) bucket recover? → A: P0 Option A —
  atomically attach only the normal `stateTtlMs` quarantine expiry when no expiry exists, leave the
  Redis type and fields untouched, return `INVALID_STATE` and fail open this request; later invalid
  acquisitions do not refresh the quarantine expiry. If the unchanged fields are otherwise valid,
  a later acquisition may resume normal quota evaluation and an allowed result may refresh normal TTL.

## Problem and Scope

**Business problem**: Public Product Catalog traffic can spike before a flash sale and overload the
downstream Product service. The Gateway already has a stable 429 envelope, but it does not yet make
a distributed quota decision, coordinate quota across replicas, or expose a retry hint.

**Goal**: Protect public catalog reads with one predictable, distributed MVP quota while preserving
downstream response ownership, safe caller identity handling, and an explicit availability outcome
when quota coordination cannot be evaluated.

**In scope**:

- Protect only `GET` requests matched by the existing `product-catalog` Gateway route.
- Apply one effective policy and one caller bucket to each protected request.
- Use the approved MVP quota: capacity 60, refill 30 quota units per second, request cost 1.
- Coordinate quota across Gateway replicas with one atomic shared-state decision.
- Group the local MVP by a safely resolved direct client IP without trusting forwarding headers.
- Return the verified Gateway 429 envelope plus approved retry/cache headers when quota is exhausted.
- Propagate one normalized/generated `X-Trace-Id` on protected public catalog requests even when the
  limiter is disabled; this is an intentional additive downstream request-header delta, not a
  client-visible response-header or tracing-runtime claim.
- Continue a request when quota coordination is unavailable, while making that failure visible to
  operators and never representing it as quota exhaustion.
- Keep the capability disabled by default and enable it explicitly in test/local Docker profiles.
- Prove exact quota, concurrency, compatibility, security, and failure behavior before activation.

**Out of scope**:

- Authentication, login, token issuance, JWT/JWKS, user-based or API-key-based buckets.
- Admin catalog writes or any route other than `product-catalog` `GET`.
- Multiple or hierarchical buckets, pre-authentication limits, per-product limits, dynamic policy
  administration, or a policy database.
- A fail-closed 503 limiter contract, retrying ambiguous limiter outcomes, or fallback business data.
- Production quota sizing, a throughput SLO, Redis high availability, Redis Cluster, or multi-region
  quota consistency.
- Installing Micrometer Tracing/OpenTelemetry runtime, Collector/Tempo assets, dashboards, or alert
  thresholds; this feature adds only safe metrics/Observation hooks and lifecycle/configuration logs.
- Changing Product service business validation, stock, order, payment, idempotency, or purchase limits.
- Adding a second Redis runtime or moving service-owned configuration into root infrastructure.

## Baseline References and Requirement Delta

| Reference | Governing content | Confirmed delta |
|-----------|-------------------|-----------------|
| [Feature 011](../011-gateway-error-handling/spec.md) and its [HTTP contract](../011-gateway-error-handling/contracts/gateway-error-http.md) | Gateway-owned error boundary, safe three-field envelope, downstream response pass-through, trace correlation | Preserve every existing Gateway error/downstream ownership rule; add only route-scoped catalog request propagation of the same normalized/generated correlation value |
| [Feature 012](../012-gateway-rate-limit-contract/spec.md) and its [429 contract](../012-gateway-rate-limit-contract/contracts/gateway-rate-limit-error-http.md) | Verified `RATE_LIMIT_EXCEEDED` status, message, body and deferred header/policy decisions | Activate the first limiter and amend only the deferred 429 header semantics |
| [ADR 0003](../../docs/adr/0003-lean-api-gateway-package-structure.md) | Lean responsibility-oriented Gateway packages | Add real rate-limit/filter/configuration/observability types without recreating business-service layers |
| [Rate-limit design guide](../../docs/ratelimit/README.md) | Draft design options, ownership boundaries and decision register | Promote only the explicitly accepted MVP choices in this specification |
| [Shared Compose](../../infra/docker/compose.yml) | One existing Redis 7.4 local runtime | Reuse that runtime; do not introduce another Redis service |

## User Scenarios & Testing

### User Story 1 - Protect Public Catalog Reads (Priority: P1)

As a shopper, I want normal catalog reads to remain available while abusive bursts are limited so
that I can browse products without one caller exhausting downstream capacity.

**Why this priority**: Public catalog reads are the first shopper-facing path and can receive a large
burst immediately before a sale.

**Independent Test**: Enable the single MVP policy against an isolated catalog downstream, issue
requests for one caller bucket, and verify allowed requests reach downstream once while the first
request without enough quota receives the exact Gateway-owned 429 and reaches downstream zero times.

**Use-case references**: UC-GW-RL-01 Enforce Catalog Quota

**Acceptance Scenarios**:

1. **Given** a full MVP bucket, **When** a matching catalog GET arrives, **Then** one quota unit is
   consumed and the request reaches downstream exactly once.
2. **Given** the bucket does not have one available quota unit, **When** a matching request arrives,
   **Then** the Gateway returns the verified `RATE_LIMIT_EXCEEDED` response with an accurate
   `Retry-After` and does not call downstream.
3. **Given** elapsed time has replenished quota without exceeding capacity, **When** another matching
   request arrives, **Then** it is evaluated against the replenished shared balance.
4. **Given** 100 concurrent acquisitions for one test bucket with capacity 20 and no effective
   refill during the fixture, **When** three independent limiter clients share the coordinator,
   **Then** exactly 20 are allowed, exactly 80 are rejected, and the balance never becomes negative.
5. **Given** an exhausted bucket, **When** further requests are rejected, **Then** those rejections
   do not extend the bucket expiry; an allowed decision refreshes the approved inactivity TTL.

---

### User Story 2 - Preserve HTTP and Caller Boundaries (Priority: P2)

As an API client and security owner, I want rate-limit responses and caller grouping to be stable and
safe so that clients can back off without learning another caller's identity or Gateway internals.

**Why this priority**: A limiter at the public edge must not introduce a second error shape, trust a
spoofed address, or expose stable caller identifiers.

**Independent Test**: Send allowed, rejected and downstream-owned 429 responses with direct and
spoofed forwarding inputs, then verify exact Gateway headers/body, downstream pass-through, bucket
separation, and absence of raw identity in responses, telemetry and shared-state keys.

**Use-case references**: UC-GW-RL-02 Return Safe Quota Outcome

**Acceptance Scenarios**:

1. **Given** a Gateway-owned quota rejection, **When** the response is rendered, **Then** it contains
   exactly the verified `{code,message,traceId}` body, `Content-Type: application/json`, an integer
   delta-seconds `Retry-After` of at least 1, and `Cache-Control: no-store`.
2. **Given** an allowed response or a Gateway-owned 429, **When** headers are inspected, **Then** no
   `RateLimit`, `RateLimit-Policy`, `RateLimit-Limit/Remaining/Reset`, or `X-RateLimit-*` accounting
   header is emitted.
3. **Given** a downstream service returns HTTP 429 normally, **When** the Gateway receives it, **Then**
   its status, body and sentinel headers pass through without Gateway quota headers or translation.
4. **Given** an untrusted caller supplies `Forwarded` or `X-Forwarded-For`, **When** identity is
   resolved for the local MVP, **Then** those values are ignored and only the normalized direct
   socket address may identify the bucket.
5. **Given** any limiter outcome, **When** response, logs, metrics and shared state are inspected,
   **Then** raw IP, JWT, API key, user ID and secret are absent; the full opaque bucket digest may
   appear only as the Redis key identity and MUST NOT appear in responses, logs, metrics, traces or
   baggage.
6. **Given** the limiter is enabled, **When** its dedicated HMAC secret is missing, is not valid
   Base64, or decodes to fewer than 32 bytes, **Then** Gateway startup fails before serving traffic;
   a valid secret produces only a lowercase 64-character hexadecimal bucket digest.
7. **Given** a protected public catalog request carries a valid caller `X-Trace-Id`, **When** it is
   forwarded or rejected by the Gateway, **Then** the normalized value is propagated downstream or
   reused in the Gateway-owned error body; a missing or invalid value receives one generated
   fallback, without changing the stricter admin-catalog validation contract.

---

### User Story 3 - Degrade Safely and Diagnose the Limiter (Priority: P3)

As an operator, I want catalog traffic to continue with bounded delay and clear signals when the
limiter cannot evaluate quota so that a Redis incident does not masquerade as client abuse or become
a hidden outage.

**Why this priority**: A shared coordination dependency introduces a new failure path at the only
public ingress.

**Independent Test**: Inject Redis connection failure, a 50 ms command timeout, malformed limiter
state and script-cache loss, then verify the approved allow-with-metric outcome, no blind retry, no
false 429/accounting header, framework script-cache recovery, and distinguishable operator signals.

**Use-case references**: UC-GW-RL-03 Operate Through Limiter Failure

**Acceptance Scenarios**:

1. **Given** Redis is unavailable, times out, or returns malformed limiter state, **When** quota
   cannot be evaluated, **Then** the request continues downstream exactly once, no quota header is
   fabricated, and an operator-visible `fail_open` outcome is recorded.
2. **Given** a Redis timeout or connection reset has an unknown execution outcome, **When** failure
   handling runs, **Then** the Gateway does not retry the acquisition blindly.
3. **Given** the Redis script cache is empty, **When** the next acquisition runs, **Then** normal
   framework script evaluation recovery occurs without changing the public outcome.
4. **Given** the Redis bucket key is missing after expiry, restart or flush, **When** the next valid
   acquisition runs, **Then** the bucket starts full and no durable business data is considered lost.
5. **Given** the capability is disabled, **When** matching catalog traffic arrives, **Then** no quota
   coordination is attempted and existing routing behavior is preserved.
6. **Given** the direct client IP is absent or cannot be normalized, **When** identity resolution
   fails, **Then** the request continues downstream exactly once, Redis is not called, a distinct
   identity-unavailable signal is recorded, and no quota or retry header is emitted.
7. **Given** a material quota, TTL, request-cost or identity-policy change, **When** the new policy is
   activated, **Then** it uses a new state version and old bucket keys expire naturally without a
   key-space scan or state reinterpretation.
8. **Given** an existing wrong-type or hash bucket has no expiry, **When** an acquisition detects the
   invalid state, **Then** the script attaches only the normal 60-second quarantine expiry, leaves
   its type and fields untouched, returns `INVALID_STATE`, and later acquisitions that still find
   invalid content do not refresh that expiry; an otherwise valid hash may resume normal evaluation.

### Edge Cases and Failure Outcomes

- Multiple Gateway replicas race for the last quota unit in the same bucket.
- Elapsed time is zero or very large; a future refill timestamp is invalid shared state and follows
  the typed fail-open path in FR-019 rather than being silently clamped.
- Requested cost, capacity, refill amount or refill period is invalid at startup.
- A caller attempts to spoof forwarding headers from an untrusted direct peer.
- The direct socket address is missing or cannot be normalized; the request follows the approved
  identity-unavailable fail-open behavior without sharing a fallback bucket.
- Redis state is missing, malformed, expired, evicted or lost during restart.
- Redis state has the wrong type or no expiry; it receives only the approved bounded quarantine
  expiry and is never repaired, reset, deleted, or refreshed by later invalid acquisitions. An
  unchanged hash whose only defect was missing expiry may become normally evaluable on the next call.
- Pre-existing invalid state with a finite expiry keeps that decreasing expiry even when it is longer
  than the normal policy TTL; Feature 013 neither shortens nor refreshes it, and state-version
  rotation is the bounded operational escape hatch when waiting is unacceptable.
- A structurally valid bucket with a positive finite TTL that is too short to cover a rejected
  request's computed refill deficit is invalid state; it fails open without mutation instead of
  advertising a `Retry-After` later than the bucket reset.
- The script has executed but the Gateway times out before receiving the result.
- Another owner has already committed the response.
- A downstream service deliberately returns 429.
- Policy configuration changes while old bucket state still exists; material changes use a new
  state version and old bucket keys are left to expire naturally.
- The limiter is enabled with a missing, malformed or insufficiently strong Base64 HMAC secret.
- The dedicated HMAC secret is rotated; the rotation intentionally creates a fresh bucket namespace
  and old opaque keys expire naturally.

## Requirements

### Functional Requirements

- **FR-001**: The Gateway MUST evaluate the MVP quota only for `GET` requests matched to the existing
  `product-catalog` route; all other routes and methods MUST preserve existing behavior.
- **FR-002**: Each protected request MUST resolve at most one effective policy and one caller bucket.
- **FR-003**: The MVP policy MUST have capacity 60 quota units, replenish 30 units per second, and
  charge one unit per matching request. These values define the local/demo MVP and MUST NOT be
  represented as a production SLO or final capacity recommendation.
- **FR-004**: Concurrent decisions for the same bucket MUST share one atomic balance across Gateway
  replicas; an allowed decision consumes exactly the request cost and a rejected decision never
  makes the balance negative.
- **FR-005**: A new or missing bucket MUST begin at full capacity; replenishment MUST use a shared
  coordinator time source, never exceed capacity, and preserve fractional progress consistently.
- **FR-006**: Only a successful quota evaluation with insufficient available units may select
  `RATE_LIMIT_EXCEEDED`; Redis, identity, configuration, serialization or downstream failures MUST
  NOT be represented as quota exhaustion.
- **FR-007**: A Gateway-owned quota rejection MUST preserve Feature 012's HTTP 429, exact message
  `Too many requests`, `{code,message,traceId}` body and existing trace-correlation semantics.
- **FR-008**: A Gateway-owned 429 MUST add `Retry-After` as an integer delta-seconds value rounded up
  to the earliest time the same one-unit request can be accepted, with a minimum value of 1.
- **FR-009**: A Gateway-owned 429 MUST add `Cache-Control: no-store`.
- **FR-010**: The Gateway MUST NOT emit `RateLimit`, `RateLimit-Policy`, older
  `RateLimit-Limit/Remaining/Reset`, or legacy `X-RateLimit-*` headers in this feature, including on
  successful responses.
- **FR-011**: A downstream-owned HTTP 429 MUST pass through without Gateway translation or added
  quota headers.
- **FR-012**: The local MVP identity strategy MUST use the normalized direct socket client IP and
  MUST ignore `Forwarded` and `X-Forwarded-For`; trusted-proxy/ingress resolution is deferred.
- **FR-013**: Raw caller identity MUST NOT be stored in shared state or exposed through response,
  metric, trace, baggage or normal log data. A deterministic keyed digest MUST separate environment,
  policy and identity type before producing a bounded opaque bucket identity.
- **FR-014**: If a direct socket client IP is absent or cannot be normalized, the Gateway MUST skip
  quota acquisition, MUST NOT create or use a shared fallback bucket, MUST forward downstream
  exactly once, MUST record a distinct identity-unavailable operator signal, and MUST omit quota and
  retry headers.
- **FR-015**: The quota-acquisition capability MUST be disabled by default and MUST require explicit enablement
  for tests and local Docker runtime. Enabled configuration with invalid policy values or missing
  required secret material MUST fail validation before serving traffic. The derived full-refill
  duration MUST NOT exceed 24 hours so a bucket cannot expire and reset before its advertised
  `Retry-After` horizon. Every numeric value passed to Lua, including `refillTokens`, MUST be an
  exact positive integer no greater than `2^53 - 1`.
- **FR-016**: A quota acquisition MUST have a 50 ms command timeout. Timeout, connection reset, or
  another ambiguous execution outcome MUST NOT be retried blindly.
- **FR-017**: If quota coordination is unavailable or invalid at runtime, the MVP MUST continue the
  request downstream exactly once, record a distinct operator-visible fail-open signal, and omit
  quota accounting and retry headers.
- **FR-018**: The MVP MUST NOT add a limiter-unavailable 503 code or fail-closed response.
- **FR-019**: Missing shared state MUST start full. Incomplete/non-numeric state, a refill timestamp
  later than the current coordinator time, a wrong Redis type, a persistent key with `PTTL = -1`, or
  an invalid decision result MUST become a typed limiter failure and follow FR-017 rather than being
  silently reset or clamped. When a wrong-type or hash key has `PTTL = -1`, the same atomic script
  MUST apply only `PEXPIRE(stateTtlMs)`, MUST leave its type and fields untouched, and MUST still
  return `INVALID_STATE` so the current request follows FR-017. Lua numeric arguments and stored
  numeric fields MUST use canonical unsigned decimal notation (`0` or `[1-9][0-9]*`) and represent
  exact integers no greater than `2^53 - 1`; signs, fractions, exponent notation, non-finite values,
  leading zeroes, and values outside that bound MUST be rejected before any balance mutation.
  Numeric state and result values MUST be written and returned in canonical non-exponent decimal
  form. On a would-be rejection, the script MUST prove the existing finite TTL is at least the exact
  deficit/refill retry horizon; otherwise it MUST return `INVALID_STATE` without mutation so expiry
  cannot accept a request earlier than the advertised `Retry-After`.
- **FR-020**: Bucket inactivity TTL MUST be calculated as
  `clamp(ceil(capacity × refillPeriod / refillTokens) × 2, 60 seconds, 24 hours)`; the MVP policy
  therefore uses 60 seconds. A new bucket and each allowed acquisition MUST set or refresh that TTL,
  while a rejected acquisition MUST NOT extend it. A material change to capacity, refill amount,
  refill period, request cost, TTL policy, or identity strategy MUST use a new policy-state version;
  old keys MUST expire naturally and MUST NOT be scanned, reinterpreted or clamped into the new state.
  An invalid key that already has `PTTL >= 0` MUST NOT have its expiry refreshed; therefore the
  expiry-only quarantine mutation in FR-019 occurs at most once for a continuously invalid key. If
  the unchanged hash fields pass all validation on a later acquisition, normal allowed/rejected TTL
  semantics apply from that acquisition onward, including the finite-TTL retry-horizon check before
  a rejection is authoritative.
- **FR-021**: Opaque bucket identities MUST use HMAC-SHA-256 and lowercase hexadecimal output. The
  HMAC input MUST separate environment, policy-state version, policy ID, identity type and normalized
  raw identity. The dedicated HMAC secret MUST be supplied as Base64 configuration and decode to at
  least 32 random bytes; it MUST NOT reuse a JWT signing key or another application secret. If the
  limiter is enabled and this secret is missing, invalid Base64 or shorter than 32 decoded bytes,
  Gateway startup MUST fail before serving traffic. Secret rotation MUST intentionally create fresh
  bucket identities while old keys expire naturally.
- **FR-022**: The Gateway MUST reuse the one Redis runtime already owned by root infrastructure and
  MUST NOT introduce a second Redis service, volume, network or service-local Compose topology.
- **FR-023**: Bucket state MUST be treated as ephemeral coordination data with bounded expiry, not as
  authoritative or historical business data. Valid state expires by normal inactivity TTL and a
  detected persistent invalid key becomes expiry-bounded by the FR-019 quarantine TTL. If unchanged
  hash fields later validate, the key re-enters normal TTL behavior; otherwise invalid acquisitions
  do not refresh it. A pre-existing invalid key with a finite TTL preserves only that decreasing TTL,
  even when it exceeds the policy TTL; Feature 013 does not claim to shorten externally-created
  corruption. Redis loss MUST NOT alter Product, stock, order or payment truth.
- **FR-024**: Limiter telemetry MUST use bounded policy, route, outcome and controlled error-type
  dimensions. Raw identity, trace ID, request path variables and bucket keys MUST NOT be metric tags.
- **FR-025**: This feature MUST provide safe structured lifecycle/configuration logs and Micrometer
  metric/Observation hooks but MUST NOT create limiter-specific per-request INFO/WARN log
  amplification or claim/install complete OpenTelemetry tracing runtime.
- **FR-026**: Existing liveness, readiness, Actuator/Prometheus exposure, route security and
  downstream error ownership MUST remain backward compatible. Correlation has one intentional
  additive delta: every protected public catalog request MUST carry exactly one safe `X-Trace-Id`
  downstream even when limiting is disabled. Reuse a normalized valid caller value or generate one
  fallback and cache it for the exchange. A Gateway-owned 429 MUST use the same value in its body;
  invalid public-catalog input generates a fallback and MUST NOT weaken the existing strict
  admin-catalog boundary. No new client-visible response header is introduced.

### Non-Functional Requirements

- **NFR-001**: The deterministic concurrency fixture of 100 same-bucket calls, capacity 20 and no
  effective refill MUST produce exactly 20 allowed and 80 rejected decisions across three limiter
  clients, with no negative balance.
- **NFR-002**: One hundred percent of Gateway-owned 429 contract tests MUST match the exact status,
  three-field body, content type and approved headers, and MUST call downstream zero times.
- **NFR-003**: One hundred percent of representative downstream 429 tests MUST preserve downstream
  status, body and sentinel headers without Gateway quota-header injection.
- **NFR-004**: Every injected identity-resolution, Redis connection, timeout and malformed-result
  case MUST be distinguishable from quota rejection and MUST demonstrate its approved fail-open
  outcome.
- **NFR-005**: Security tests MUST find zero raw client IPs, bearer tokens, API secrets, HMAC secrets,
  user identifiers or full bucket hashes in public responses, metric tags and captured normal logs.
- **NFR-006**: A measurement-only load exercise MUST report limiter decision latency, Gateway
  throughput, rejection accuracy and Redis resource/error signals; this feature sets no production
  throughput threshold until evidence and an environment-specific target are approved.

### Business Rules and Invariants

- **INV-001**: A protected request is forwarded downstream at most once.
- **INV-002**: A successful atomic quota decision is the only authority for consuming or rejecting
  one request's quota unit.
- **INV-003**: Quota state for one environment, policy and caller identity cannot consume or expose
  another bucket's balance.
- **INV-004**: Infrastructure uncertainty never becomes a false client-abuse assertion.
- **INV-005**: An HTTP response obtained normally from downstream remains downstream-owned.
- **INV-006**: Ephemeral bucket loss may reset edge protection but cannot lose or mutate durable
  business truth.

## Distributed-System Risk Decisions

| Risk area | Decision and required behavior | Requirement/scenario reference |
|-----------|--------------------------------|--------------------------------|
| Money/payment | N/A: the Gateway quota does not price, authorize, refund or reinterpret payment outcomes. | Out of scope, INV-005 |
| Inventory/oversell | N/A: this is edge traffic protection, not stock or purchase-limit enforcement. Product and flash-sale business truth remain downstream-owned. | FR-023, INV-006 |
| Concurrency | All replicas share one atomic bucket decision; the approved deterministic fixture proves no overspend. | FR-004, NFR-001, INV-002 |
| Idempotency/deduplication | N/A: each HTTP request consumes quota independently; the limiter creates no durable business command or replay result. Ambiguous infrastructure outcomes are not retried. | FR-016, INV-001 |
| Consistency/ordering | Same-bucket decisions are serialized atomically at the shared coordinator; there is no cross-bucket or global ordering promise. | FR-004, INV-003 |
| Retry/timeout/compensation | Command timeout is 50 ms; no blind retry; runtime coordinator failure follows allow-with-metric and no compensation. | FR-016–FR-019 |
| Security/authorization | Local MVP uses direct socket IP, ignores forwarded headers, and fail-opens with a distinct operator signal rather than sharing a bucket when identity is unavailable. Raw identity is protected by a dedicated HMAC-SHA-256 secret of at least 32 decoded random bytes and represented only as lowercase hexadecimal opaque state identity. | FR-012–FR-014, FR-021, NFR-004–NFR-005 |
| TTL/quota/retention | Quota is 60 capacity, 30/second refill and cost 1. TTL uses twice the full-refill duration clamped to 60 seconds–24 hours, yielding 60 seconds for the MVP; enabled configuration rejects a full-refill duration above 24 hours. Rejections do not refresh TTL and are authoritative only after the script proves finite TTL covers the exact retry horizon. A detected persistent invalid key receives one expiry-only normalization without balance repair and later still-invalid failures do not refresh it; an otherwise-valid hash may resume normally. Existing finite invalid TTL is preserved/decreases. Material policy changes use a new state version and old keys expire naturally. | FR-003, FR-008, FR-015, FR-019–FR-020, FR-023 |

## Dependencies and Compatibility

- **Upstream dependencies**: Public catalog callers and the existing `product-catalog` route; no new
  authentication dependency is introduced.
- **Downstream consumers**: Product service remains the catalog owner and receives only allowed or
  explicitly fail-open requests. API clients consume the additive `Retry-After` and
  `Cache-Control: no-store` 429 headers.
- **Compatibility promise**: Feature 012's status, code, message, body and trace rules remain stable.
  The header delta is additive. Existing allowed responses receive no new accounting headers.
  Downstream HTTP responses remain unchanged. Limiting is disabled by default until explicit
  rollout. Routing, quota, status/body and security behavior remain inactive/default-compatible;
  the route-scoped downstream `X-Trace-Id` request propagation is deliberately active immediately.

## Success Criteria

### Measurable Outcomes

- **SC-001**: The approved deterministic concurrency scenario allows exactly the available quota and
  rejects every excess acquisition without a negative balance or duplicate downstream forwarding.
- **SC-002**: Clients receive the exact approved 429 body and retry/cache headers in 100% of
  Gateway-owned rejection scenarios, while representative downstream 429 responses remain unchanged.
- **SC-003**: All injected coordinator failures continue catalog traffic under the approved MVP
  failure mode and produce operator evidence distinguishable from both allowed traffic and quota
  exhaustion.
- **SC-004**: Security verification finds no raw caller or secret material in shared-state keys,
  public responses, normal logs, metrics, traces or baggage.
- **SC-005**: Existing Gateway routing, security, error and observability regression suites pass when
  the limiter is disabled.

## Assumptions

- The accepted `60/30/1` policy is a learning/local-demo preset and will be revisited with measured
  evidence before any production capacity claim.
- A single local Redis runtime is sufficient for the current project stage; production HA topology
  is a separate architecture decision.
- Actual Micrometer Tracing/OpenTelemetry runtime remains a later feature; current `X-Trace-Id`
  correlation behavior remains in force.
- No Kubernetes manifest changes are needed for the initial local/test implementation; future
  cluster activation requires trusted-ingress and secret-distribution decisions.

## Human Decisions Required

| Priority | Question | Options/trade-off | Owner | Decision deadline | Resolution |
|----------|----------|-------------------|-------|-------------------|------------|
| RESOLVED | How does a wrong-type or persistent (`PTTL = -1`) bucket recover? | A: atomically apply only `PEXPIRE(stateTtlMs)`, leave type/fields untouched, return `INVALID_STATE`, and do not refresh on later invalid acquisitions; B: never mutate and require targeted operator cleanup/state-version rotation; C: delete/reset immediately, which grants a fresh bucket | Gateway/Platform owner | Resolved 2026-07-22 | P0 Option A — expiry-only normalization, no repair/reset/delete, current request fail-open; still-invalid calls do not refresh, while otherwise-valid unchanged state may resume normal evaluation |
| RESOLVED | Missing or invalid direct client IP outcome? | A: fail open with distinct signal and no fallback bucket; B: shared fallback bucket; C: reject under a new contract | Security owner / Gateway owner | Resolved 2026-07-22 | Option A — bypass limiter, forward once, emit identity-unavailable signal, no quota headers |
| RESOLVED | Rejected-request TTL refresh and policy-state change lifecycle? | A: no rejected refresh + new state version; B: refresh all + reuse/clamp; C: refresh all + new version | Gateway/Platform owner | Resolved 2026-07-22 | Option A — rejected requests do not refresh TTL; material changes use a new state version |
| RESOLVED | Opaque digest encoding and enabled-secret minimum? | A: lowercase hex with at least 32 random bytes; B: unpadded Base64URL with at least 32 random bytes; C: provide another exact format/strength | Security owner / Gateway owner | Resolved 2026-07-22 | Option A — HMAC-SHA-256 lowercase hex; dedicated Base64 secret decoding to at least 32 random bytes; invalid enabled configuration fails startup |

## Constitutional Constraints

- **Service ownership**: `api-gateway` owns edge quota enforcement and ephemeral bucket coordination;
  it accesses no service database and owns no Product, stock, order or payment domain model.
- **External ingress**: The limiter runs only at the existing public Gateway route boundary.
- **API/event contracts**: Feature 012's HTTP contract must be amended before emitting headers. No
  Kafka contract or cross-service HTTP call is added.
- **Durable and hot-path data**: Redis contains expiring edge-coordination state only; PostgreSQL and
  downstream services retain durable business truth. Atomic shared-state design and expiry/recovery
  must be explicit in the plan.
- **Messaging reliability**: N/A; no Kafka producer, consumer, event, inbox or outbox is introduced.
- **Root infrastructure ownership**: Reuse root `infra/docker/compose.yml`; service dependency,
  runtime configuration, script resource and tests remain in `api-gateway`. No Kubernetes or
  monitoring asset is added in this initial feature unless the approved plan identifies a required
  root-owned delta.
- **Observability**: Existing liveness/readiness/declarative Prometheus exposure remains. New
  instrumentation uses Micrometer abstractions and safe trace correlation; no manual Prometheus
  registry or direct OpenTelemetry SDK coupling is allowed.
- **Verification**: Unit, real-Redis integration, Gateway contract, failure-injection,
  concurrency/measurement-only load and module Maven verification apply. Database, Kafka, migration
  and Kubernetes validation are omitted because no such artifact changes are in scope.
- **Architecture decisions**: ADR 0003 remains governing; a proposed ADR 0004 must record the new
  distributed rate-limiter mechanism, shared Redis assumption, failure policy and rollback.

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-07-22 | Initial risk-profile draft from the accepted MVP preset; three preset decisions were initially open | Codex | Pending Gateway/Security owner review | Draft |
| 2026-07-22 | Resolved unavailable direct-client-IP behavior as fail-open without a shared fallback bucket | Codex | Gateway/Platform owner (user) | Draft |
| 2026-07-22 | Resolved rejected-request TTL and policy-state version lifecycle | Codex | Gateway/Platform owner (user) | Draft |
| 2026-07-22 | Resolved opaque bucket digest encoding and enabled-secret validation | Codex | Gateway/Platform owner (user) | Draft |
| 2026-07-22 | Added blocking persistent/wrong-type Redis-state recovery decision discovered during cross-artifact audit | Codex | Pending Gateway/Platform owner decision | Draft |
| 2026-07-22 | Resolved persistent/wrong-type Redis-state recovery as one expiry-only quarantine mutation | Codex | Gateway/Platform owner (user) | Draft |
| 2026-07-23 | Approved Feature 013 governing artifacts after clean pre-approval analysis | Codex | Gateway/Platform owner (user) | Approved |
