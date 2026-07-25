# Research: Gateway Redis Rate Limiter

**Feature**: `013-gateway-redis-rate-limiter`  
**Status**: Supporting design input — governed by `plan.md` status  
**Date**: 2026-07-22

This document resolves implementation-level questions for the accepted preset behavior, including
the owner-approved expiry-only quarantine for persistent invalid Redis state. It does not approve
production code by itself; governing artifacts are controlled by the approved `spec.md`, `plan.md`,
`tasks.md`, contracts, and Accepted ADR.

## Decision 1: Use a custom reactive coordinator behind a small Gateway boundary

**Decision**: Add `spring-boot-starter-data-redis-reactive` and implement the exact Feature 013
semantics behind a small `DistributedRateLimiter` interface owned by the Gateway. Keep catalog
correlation and quota selection in two ordered, route-aware `GlobalFilter` boundaries; keep Redis
execution in `ratelimit/redis`.

**Rationale**: Spring Cloud Gateway 4.3.5 provides `RequestRateLimiter` and a Redis token-bucket
implementation, but Feature 013 has stricter behavior than its public configuration contract:
rejected requests must not refresh TTL, missing identity must bypass with a distinct signal, Redis
failure must fail open without a false 429, corrupt state must not reset silently, and the existing
three-field Gateway error writer must remain the sole error renderer. A narrow custom boundary makes
those decisions testable without creating business-service `domain/application/adapter` packages.

**Alternatives considered**:

- Built-in `RedisRateLimiter`: rejected because its lifecycle and failure behavior do not express all
  approved Feature 013 rules.
- In-memory buckets: rejected because Gateway replicas would overspend independently.
- Bucket4j plus a distributed backend: rejected because it adds another production dependency and
  abstraction without removing the Redis/failure/contract work required by this MVP.

**Primary source**: [Spring Cloud Gateway 4.3.5 RequestRateLimiter](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html)

## Decision 2: One O(1) Lua acquisition and framework-owned script cache recovery

**Decision**: Execute one singleton `DefaultRedisScript<List>` for each protected acquisition. Pass
the bucket key only through `KEYS[1]` and policy values through `ARGV`. Let Spring Data Redis try
`EVALSHA` and fall back to `EVAL` on script-cache miss. Do not add `SCRIPT LOAD`, a custom cache, or an
application retry loop.

**Rationale**: The decision reads time, reads/refills state, checks cost, optionally mutates state,
sets expiry, and returns one result atomically. Spring Data Redis already owns script serialization
and cache fallback; a singleton avoids recomputing the SHA on every call. Timeout and connection
failure have an ambiguous mutation outcome, so a second acquisition is unsafe.

**Alternatives considered**:

- Several reactive Redis commands: rejected because another replica could interleave between them.
- `WATCH`/`MULTI`: rejected because contention introduces retries and a more complex ambiguous-outcome
  path.
- Blind retry after timeout: prohibited by FR-016.

**Primary sources**:

- [Spring Data Redis 3.5 scripting](https://docs.spring.io/spring-data/redis/reference/3.5/redis/scripting.html)
- [Redis EVAL command](https://redis.io/docs/latest/commands/eval/)
- [Redis Lua atomic execution](https://redis.io/docs/latest/develop/interact/programmability/eval-intro/)

## Decision 3: Use Redis time and exact integer credit units

**Decision**: Use Redis `TIME`, truncated to milliseconds, as the coordinator clock. Represent bucket
balance as integer credit units instead of a floating-point token count:

```text
periodMs       = refillPeriod in whole milliseconds
capacityCredit = capacity * periodMs
costCredit     = requestCost * periodMs
refillCredit   = elapsedMs * refillTokens
currentCredit  = saturatingMin(capacityCredit, storedCredit + refillCredit)
```

The script first checks whether elapsed time is at least a full-refill duration; if so it assigns
capacity directly. Otherwise it compares refill credit with `capacityCredit - storedCredit` before
addition and assigns capacity when that difference is covered. This saturation order avoids an
out-of-range intermediate. Configuration validation rejects non-integral-millisecond periods, any
scaled value outside Redis Lua's exact-integer range, any numeric script argument (including
`refillTokens`) above `2^53 - 1`, and a derived `fullRefillMs` above `86_400_000`. The last bound
ensures normal script-owned bucket expiry cannot reset quota before the maximum one-request retry
horizon.

Java also precomputes and passes `capacityCredit`, `requestCostCredit`, `fullRefillMs`,
`refillTokens`, and `stateTtlMs`; Lua therefore needs neither division nor an unsafe full-refill
multiplication. Lua returns exact integer `remainingCredit`. Java validates that credit
against the selected policy and derives rejection delay with
`Math.ceilDiv(costCredit - remainingCredit, refillTokens)`. Boundary tests cover the largest accepted
scaled values and ensure retry delay remains within
`1..Math.ceilDiv(costCredit, refillTokens)`.

Every numeric `ARGV`, stored numeric field, and returned numeric field uses canonical unsigned
decimal text: exactly `0` or `[1-9][0-9]*`. Lua rejects leading signs/zeroes, fractions, exponent
notation, NaN/infinity, and values above `2^53 - 1` before mutation, then persists and returns
accepted numbers with `string.format("%.0f", value)` so no scientific notation is emitted. Tests
round-trip the largest accepted exact integer (`9007199254740991`) and representative
non-canonical inputs.

**Rationale**: Every replica uses one clock. Credit units preserve fractional token progress exactly
at millisecond resolution without decimal serialization or an arbitrary token scale. The MVP
`60/30 per 1000 ms/cost 1` becomes `60000` capacity credits, `1000` cost credits, and `30` credits per
elapsed millisecond.

A timestamp later than Redis time is invalid state and follows FR-019; it is not clamped silently.

**Alternatives considered**:

- Java wall clock: rejected because replicas can disagree.
- Floating-point tokens: rejected because repeated serialization and rounding make exact concurrency
  assertions harder.
- Fixed micro-token scale: workable, but credit units are exact for the approved millisecond model
  and need no arbitrary scale constant.

**Primary source**: [Redis TIME](https://redis.io/docs/latest/commands/time/)

## Decision 4: Persist only allowed decisions and use bounded inactivity expiry

**Decision**: Store one Redis hash with `credit` and `last_refill_ms`. A missing key starts full. An
allowed decision writes both fields and applies `PEXPIRE`. A rejected decision performs no write and
does not call `PEXPIRE`, so its existing TTL keeps decreasing. The TTL is computed outside the script
from FR-020 and passed as milliseconds; the MVP value is 60,000 ms.

An existing key with the wrong Redis type, incomplete/non-numeric fields, a future timestamp, no
expiry (`PTTL = -1`), or an invalid script result is a typed coordinator failure. If an invalid key
has `PTTL = -1`, the same atomic script applies only `PEXPIRE(stateTtlMs)`, leaves its type/fields
untouched, and still returns `INVALID_STATE`. Once `PTTL >= 0` (including imminent-expiry value `0`),
later invalid acquisitions do not mutate or refresh the key.

This is expiry-only normalization, not a remembered quarantine state: because Option A forbids a
marker or field rewrite, an otherwise-valid hash whose sole defect was `PTTL = -1` can pass normal
validation on its next acquisition. If it is allowed, normal state persistence/TTL refresh resumes;
wrong-type or still-corrupt data continues to fail open without expiry refresh. A pre-existing
invalid key that already has a finite TTL retains only that decreasing TTL even when it exceeds the
normal policy TTL; Feature 013 does not shorten it. Operators may disable the feature or advance the
policy-state version if waiting for such externally-created state is unacceptable.

Before returning `REJECTED`, the script also proves the current finite TTL covers the exact
deficit/refill horizon. This remains division-free: `PTTL >= fullRefillMs` is safe; otherwise
`PTTL * refillTokens < capacityCredit`, so that exact product can be compared with the deficit. A
shorter TTL returns `INVALID_STATE` without mutation and fails open, preventing reset-by-expiry from
accepting earlier than the advertised `Retry-After`.

Lua reports recognized state corruption as the bounded tuple `["ERROR","INVALID_STATE","0"]` and
defensive bad arguments as `["ERROR","INVALID_ARGUMENT","0"]`; Java does not parse exception
messages. The latter maps to `SCRIPT`, while an unknown tuple shape/value maps to `INVALID_RESULT`.
This one-time expiry-only normalization satisfies bounded cleanup without silently granting a fresh
bucket, rewriting corrupt state, parsing exception messages, or requiring a keyspace scan.

**Rationale**: This is the exact owner-approved Option A lifecycle. Not persisting a rejected refill
is still mathematically correct because the next acquisition recomputes elapsed credit from the last
allowed timestamp. Old keys disappear naturally after material policy changes.

**Alternatives considered**:

- Refresh expiry on rejection: rejected because it contradicts FR-020.
- Reset malformed state to full: rejected because it turns corruption into unrecorded quota reset.
- Scan/delete keys on policy change: rejected because versioned state plus expiry is bounded and safer.

**Primary sources**:

- [Redis PEXPIRE](https://redis.io/docs/latest/commands/pexpire/)
- [Redis PTTL](https://redis.io/docs/latest/commands/pttl/)

## Decision 5: Canonical HMAC input and opaque Redis keys

**Decision**: Normalize a direct socket IP to its address bytes: 4 bytes for IPv4, 16 bytes for IPv6,
with an IPv4-mapped IPv6 address normalized to the IPv4 form. Build a versioned, length-prefixed byte
sequence containing:

1. key schema marker;
2. environment;
3. policy-state version;
4. policy ID;
5. identity type;
6. normalized address bytes.

Each variable segment is preceded by a four-byte unsigned big-endian length. Compute HMAC-SHA-256
with the dedicated decoded secret and emit the full 32-byte digest as 64 lowercase hexadecimal
characters. The physical key is `rl:k1:<digest>`; `k1` is the key-schema version and is distinct from
the policy-state version inside the HMAC input.

The configured secret uses Java 21's Basic RFC 4648 Base64 decoder (not the URL-safe or MIME decoder):
standard `+`/`/` alphabet and legal optional `=` padding are accepted, while URL/MIME alphabets and
whitespace are rejected. It must decode successfully to at least 32 bytes.
It is validated only when the limiter is enabled. Runtime can validate encoding and decoded length;
it cannot prove that an operator generated random bytes. Quickstart therefore uses a CSPRNG. The
secret must not reuse JWT or application signing material.

Enabled-startup validation errors name only the property and safe reason (for example invalid
encoding or insufficient decoded length); they never interpolate the supplied secret. A test uses a
distinctive sentinel secret and verifies it is absent from captured startup-failure output.

**Rationale**: Length prefixes prevent delimiter collisions. Raw IP never enters the Redis key,
response, log, metric, trace, or baggage. Including environment and policy-state version prevents
cross-environment/state reuse. A separate key-schema marker allows a future encoding migration to be
recognized without pretending it is a quota-policy change.

**Alternatives considered**:

- Concatenation with `|`: rejected because variable components can create ambiguous byte sequences.
- Plain or reversible IP key: rejected because it exposes stable caller data.
- Truncated digest: rejected because the full digest has bounded length and avoids unnecessary
  collision analysis.

**Primary sources**:

- [RFC 2104: HMAC](https://www.rfc-editor.org/rfc/rfc2104.html)
- [Java 21 Basic Base64 decoder](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Base64.html)

## Decision 6: Correlation then route-aware limiting before downstream URL routing

**Decision**: An always-active `CatalogCorrelationIdGlobalFilter` reads the already matched route
from `GATEWAY_ROUTE_ATTR`, applies only to `product-catalog` `GET`, and runs at
`RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2`. It reuses the existing
`GatewayTraceIdResolver` to normalize a valid caller `X-Trace-Id` or generate/cache one fallback,
then sets that value on the downstream request. Invalid public-catalog input receives a fallback;
the existing strict admin-catalog validation remains unchanged.

The limiter then matches the stable route ID plus HTTP method at
`RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 1`. It bypasses unmatched routes/methods. For
the protected route it resolves identity, performs one acquisition, and either calls the chain once
or writes the owned 429 without calling the chain. Because correlation runs first and uses the same
exchange-scoped resolver, allowed/fail-open downstream requests and a Gateway-owned 429 share one
safe value even when limiting is disabled.

Spring Security remains a separate WebFilter chain. This feature protects only the already public
catalog GET, so it does not introduce a user-principal dependency or change admin authorization.

**Rationale**: Route ID is stable and low-cardinality; raw paths are neither policy identifiers nor
metric tags. The selected order is after route matching and before downstream URL creation/proxying.

**Alternative considered**: path-prefix matching inside the filter was rejected because it duplicates
route ownership and can drift from declarative Gateway configuration.

**Primary sources**:

- [Spring Cloud Gateway GlobalFilter ordering](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/global-filters.html)
- [Spring Cloud Gateway request flow](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/how-it-works.html)

## Decision 7: Central writer owns the special 429 headers

**Decision**: Extend the existing `GatewayHttpErrorWriter` with a typed rate-limit write operation.
It applies `Retry-After` and `Cache-Control: no-store` only after the intended response still renders
as `RATE_LIMIT_EXCEEDED`. If JSON serialization falls back to the existing safe 500 envelope, quota
headers are not applied. No second error writer or rate-limit response DTO is introduced.

A successfully rendered expected `RATE_LIMIT_EXCEEDED` response does not invoke the existing
per-error `GatewayErrorObservation` WARN logger; bounded limiter counters/Observation own that
expected high-volume outcome. If rate-limit serialization falls back to the unexpected 500, the
central error observation is invoked exactly once. Every other Feature 011 Gateway-owned error keeps
its existing observation/log behavior.

`Retry-After` is `max(1, ceil(retryAfterMs / 1000))` in decimal delay-seconds. `Cache-Control:
no-store` is an explicit project contract for this Gateway-owned rejection. No accounting header is
emitted. Downstream responses, including downstream 429 and their headers, are never passed to this
write operation and remain untouched.

**Rationale**: RFC 9110 permits delay-seconds and RFC 6585 defines 429 plus optional `Retry-After`.
The stricter `no-store` rule is project-owned. Typed rendering prevents invalid header/error-code
combinations and preserves the single Gateway error boundary.

**Primary sources**:

- [RFC 6585 section 4: 429](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)
- [RFC 9110 section 10.2.3: Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3)

## Decision 8: Fail open only for an explicit technical failure allow-list

**Decision**: Apply a 50 ms Reactor timeout only around the Redis acquisition. Translate recognized
Redis connection, timeout, script/state, serialization, and invalid-result failures to a finite
controlled failure type. Classification is by exact type/predicate, never exception message:

| Observed condition/type | Controlled category |
|-------------------------|---------------------|
| outer Reactor `java.util.concurrent.TimeoutException`, direct `io.lettuce.core.RedisCommandTimeoutException`, or `org.springframework.dao.QueryTimeoutException` whose immediate cause is that Lettuce timeout | `TIMEOUT` |
| `org.springframework.data.redis.RedisConnectionFailureException` or direct `io.lettuce.core.RedisConnectionException` | `CONNECTION` |
| `org.springframework.data.redis.serializer.SerializationException` | `SERIALIZATION` |
| Lua `ERROR/INVALID_STATE` | `INVALID_STATE` |
| malformed/unknown tuple or strict decoder bound failure | `INVALID_RESULT` |
| Lua `ERROR/INVALID_ARGUMENT` or direct `io.lettuce.core.RedisCommandExecutionException` | `SCRIPT` |

`org.springframework.data.redis.RedisSystemException` is unwrapped only when its immediate cause is
one of the explicitly listed Lettuce types and maps to that same category. Near-miss wrappers and
every other failure propagate. There is no `UNKNOWN` category, generic
`org.springframework.dao.DataAccessException`/Redis base-class mapping, or catch/map of every
`Throwable`. The filter converts only an error from `DistributedRateLimiter.acquire(...)` to an
internal bounded
fail-open outcome, before the terminal `flatMap` that either invokes `chain.filter(exchange)` once or
writes rejection once. Missing identity bypasses Redis through its own explicit branch. Unexpected
programming errors, HTTP writer errors, and downstream failures are not caught by the fail-open
branch and continue to the existing Gateway error boundary.

The exact 50 ms and one-subscription/no-retry semantics are tested with Reactor virtual time. Real
Redis/Testcontainers and script-cache recovery use an exact test-only 2-second command timeout so
container/CI cold-start latency cannot falsify correctness; a separate production-configuration test
still proves 50 ms.

**Rationale**: A broad `onErrorResume(Throwable.class)` could hide defects, swallow a downstream
exception, or invoke downstream twice. The allow-list preserves INV-001 and INV-004 while keeping
Feature 011 ownership intact.

**Alternatives considered**:

- Fail closed with a new 503: rejected by FR-018.
- Circuit breaker: deferred; the 50 ms bound and feature flag are sufficient for this MVP.
- Generic retry: rejected because execution outcome can be unknown.

## Decision 9: Micrometer instrumentation without claiming tracing runtime

**Decision**: Add `GatewayRateLimitObservation` using the already available Micrometer
`MeterRegistry`/`ObservationRegistry`. Start observations at reactive subscription (`Mono.defer`) and
stop once on terminal signal. Use bounded policy, route, outcome, failure-mode, and controlled
error-type values. Do not add tracing bridge, OpenTelemetry SDK/exporter, Collector, Tempo, dashboard,
or alert dependencies/assets.

Metrics:

- `gateway.rate.limit.decisions` counter: `policy`, `route`, `outcome`;
- `gateway.rate.limit.errors` counter: `policy`, `error_type`;
- `gateway.rate.limit.acquire` Observation/timer: low-cardinality outcome and failure mode.

The existing Prometheus endpoint publishes client-side `0.5`, `0.95`, and `0.99` percentiles for
the acquisition timer through
`management.metrics.distribution.percentiles.gateway.rate.limit.acquire=0.5,0.95,0.99`. These are
local measurement outputs, not production SLOs or alert thresholds.

Allowed, rejected, identity-unavailable, and fail-open traffic produces no limiter-specific
per-request INFO/WARN log; bounded counters/Observation are the authoritative runtime signal and
avoid attacker-controlled log amplification. Only safe feature lifecycle/configuration logs are
added, containing bounded policy/state metadata and never raw/digested identity, Redis key, secret,
or raw exception message. The typed expected-429 writer path likewise bypasses the generic
per-error WARN, while its unexpected serialization fallback and other Gateway errors remain owned by
the existing central error boundary.

**Rationale**: Observation is the repository's framework-neutral instrumentation boundary. It can be
bridged to OpenTelemetry later without coupling the limiter to an exporter today.

**Primary source**: [Micrometer 1.15 Observation components](https://docs.micrometer.io/micrometer/reference/1.15/observation/components.html)

## Decision 10: Redis is not a Gateway readiness dependency for this fail-open policy

**Decision**: Set `management.health.redis.enabled=false` in Gateway configuration. Keep existing
liveness/readiness probes and aggregate health independent of Redis because the limiter is fail-open.
Redis degradation is reported through limiter metrics/Observation and failure tests. Root Compose must also
override the Gateway's inherited backing-service `depends_on` with an empty map so a Redis outage at
startup cannot block the Gateway container itself.

**Rationale**: The approved runtime outcome is to keep catalog routing available when quota cannot be
evaluated. Marking the only ingress unready would let the platform remove healthy routing replicas
and contradict that availability choice.

**Alternative considered**: include Redis in readiness; rejected because it implements fail-closed at
the deployment layer while HTTP behavior claims fail-open.

**Primary source**: [Spring Boot 3.5 health endpoints and groups](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html#actuator.endpoints.health)

## Decision 11: One local Redis, explicit activation, coordinated state changes

**Decision**: Reuse `infra/docker/compose.yml` Redis 7.4. Gateway configuration is disabled by
default. Root Compose only passes connection and explicit enable/secret/environment variables; it
does not add another Redis topology. `.env.example` documents CSPRNG secret generation but contains
no deployable secret.

All replicas must use the same secret, environment, key schema, and policy-state version. Secret or
material policy rotation creates a new bucket namespace. For this local MVP, change those values only
while the limiter is disabled or all replicas can switch together; production rolling dual-read or
grace semantics require a later approved feature.

**Rationale**: Namespace isolation is sufficient for ephemeral MVP state. Coordinated activation
prevents a rolling deployment from temporarily granting parallel capacity.

Incoming forwarded-origin adaptation is pinned off with `server.forward-headers-strategy=none` for
this direct-peer MVP. This makes `ServerHttpRequest.getRemoteAddress()` the actual socket peer even
when callers send `Forwarded` or `X-Forwarded-For`. Trusted-proxy interpretation remains deferred.

The singleton HMAC key factory never shares a mutable `javax.crypto.Mac` across concurrent Reactor
requests. The simple MVP rule is to create and initialize a fresh `Mac` per key derivation; a
concurrent golden-vector test protects deterministic bucket separation.

## Dependency decision

| Dependency | Scope | Reason | Version ownership |
|------------|-------|--------|-------------------|
| `spring-boot-starter-data-redis-reactive` | Production | Reactive Lettuce connection and Spring Data script executor | Spring Boot 3.5.16 BOM |
| `spring-boot-starter-validation` | Production | Startup validation for enabled policy/configuration | Spring Boot 3.5.16 BOM |
| `org.testcontainers:junit-jupiter` | Test | Real Redis 7.4 integration and deterministic concurrency tests | Spring Boot 3.5.16 BOM |

No new MapStruct, persistence, Kafka, resilience, Micrometer Tracing, OpenTelemetry, or Prometheus
registry dependency is required.
