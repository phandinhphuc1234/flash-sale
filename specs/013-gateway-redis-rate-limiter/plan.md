# Implementation Plan: Gateway Redis Rate Limiter

**Branch**: `013-gateway-redis-rate-limiter` | **Date**: 2026-07-22 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/013-gateway-redis-rate-limiter/spec.md`  
**Plan status**: Approved | **Technical owner**: Gateway/Platform owner (user) | **Required reviewers**: Gateway, security, observability, HTTP-contract owners

> This approved plan translates the accepted preset and all resolved risk decisions into
> implementation decisions. Production-code changes must still follow the approved task order and
> validation gates.

## Summary

Protect only public `product-catalog` GET traffic with a default-disabled distributed token bucket.
One always-active catalog correlation filter propagates a normalized/generated `X-Trace-Id`; the
route-aware limiter resolves a direct-socket identity, transforms it into an opaque HMAC key, and
calls one O(1) Redis Lua acquisition against the existing root-owned Redis 7.4. The successful
decision either forwards once or uses the existing Gateway error boundary to return the verified
three-field 429 plus `Retry-After` and `Cache-Control: no-store` with the same correlation value.

Redis/identity uncertainty follows the approved typed fail-open behavior without retry or false 429.
The implementation stays in ADR 0003's lean edge packages, adds bounded Micrometer instrumentation,
keeps Redis outside readiness, and adds no business domain layer, database, Kafka, second Redis, or
OpenTelemetry runtime.

## Technical Context

**Language/Version**: Java 21  
**Framework**: Spring Boot 3.5.16; Spring Cloud 2025.0.3; Spring Cloud Gateway Server WebFlux 4.3.5  
**Build**: Maven wrapper; independently verifiable `services/api-gateway` module  
**Existing dependencies**: WebFlux Gateway, Spring Security/OAuth2 resource server, Actuator,
runtime Prometheus registry, Micrometer APIs supplied by Boot  
**New production dependencies**: `spring-boot-starter-data-redis-reactive` and
`spring-boot-starter-validation`, both Spring Boot BOM-managed  
**New test dependency**: `org.testcontainers:junit-jupiter`, Spring Boot BOM-managed  
**Storage**: One root Compose Redis 7.4 containing expiring technical bucket hashes; no PostgreSQL,
JPA, Liquibase, or durable state  
**Communication**: Existing public HTTP route and additive Gateway 429 contract; no new downstream
call, Kafka event, discovery mechanism, or service contract  
**Testing**: JUnit 5, AssertJ, Mockito, WebTestClient, JDK test downstream, real Redis 7.4 through
Testcontainers for state/concurrency, controlled reactive Redis test doubles for timeout/malformed
result, deterministic concurrency, k6 measurement-only exercise  
**Target platform**: Local/Docker MVP on the future Kubernetes deployment path; no Kubernetes manifest
change in this feature  
**Performance goals**: 50 ms acquisition timeout; exact 20 allowed/80 rejected deterministic
concurrency fixture; report rather than pre-claim production throughput/latency  
**Constraints**: Reactive/non-blocking request path; one downstream call maximum; no blind Redis
retry; direct-IP-only identity with forwarded-origin adaptation disabled; no raw/digested identity
telemetry; canonical exact Lua integer strings and every numeric script argument (including
`refillTokens`) no greater than `2^53 - 1`; derived full-refill duration no greater than 24 hours;
default disabled; fail open only for finite typed coordinator failures  
**Scale/scope**: One Gateway service, one route/method policy, one Redis key per active caller bucket,
three simulated limiter clients and 100 concurrent acquisitions in the correctness gate

## Risk Classification

| Dimension | Level | Evidence | Required mitigation/verification |
|-----------|-------|----------|----------------------------------|
| Money/payment | Low/N/A | No financial decision or state (`spec.md` out of scope) | Preserve downstream ownership; no payment tests required |
| Inventory/concurrency | High | Shared quota across replicas, FR-004/005, NFR-001 | One atomic Lua operation; real Redis exact 20/80 fixture; no negative balance |
| Security/privacy | High | Direct IP and HMAC secret, FR-012–014/021, NFR-005 | Length-prefixed HMAC, Base64/length startup validation, raw-data exposure tests |
| Distributed consistency | High | Redis time/state/timeout/TTL, FR-016–023 | Shared clock, integer credit, typed failure allow-list, no retry, TTL/cache-loss tests |
| Contract/compatibility | High | Public 429 and downstream pass-through, FR-006–011/026 | Approved contract amendment, writer/fallback tests, downstream byte/header regression |
| Migration/rollback | Medium | New dependency/config and versioned ephemeral keys | Default-off rollout, feature-flag rollback, old keys expire, ADR 0004 |
| Load/operability | High | Public ingress and new Redis latency/failure path, NFR-004/006 | 50 ms bound, metrics/Observation with explicit percentiles, failure injection, measurement report |

**Overall risk**: High. The feature is small in route scope but owns abuse control, sensitive identity,
public HTTP compatibility, and concurrent shared state at the only ingress.  
**Selected test ordering**: Test-first for new policy/HMAC boundaries, integer token math, Lua
atomicity/TTL, exact 429/fallback headers, and typed fail-open behavior. Add a characterization test
before touching the already supported downstream 429 pass-through; it may pass at baseline and must
remain passing. Required tests/evidence are mandatory for all changed risk; declarative docs/config
do not need artificial red tests.  
**Approval gates**: Gateway owner approves scope/quota/failure; security owner approves identity/
secret; client-contract reviewer approves headers/pass-through; observability owner approves bounded
signals; technical owner approves ADR/plan/tasks before implementation and validation evidence before
activation.

## Constitution Check

*GATE: performed before research; repeated after design and before task generation.*

- **Specification traceability**: PASS for planning. All four material human decisions are resolved,
  every design section maps to FR/NFR/INV, and implementation remains closed only because artifacts
  were Draft/Proposed pending explicit approval and are now approved/accepted.
- **Service ownership**: PASS. `api-gateway` owns only edge policy/coordination; no service database,
  shared entity, business Aggregate, or boundary change is introduced. ADR 0004 records the new
  distributed mechanism.
- **Communication**: PASS. Public traffic remains in Gateway; no discovery or cross-service protocol
  is added; downstream response ownership is preserved by the HTTP contract.
- **Data and messaging**: PASS. Redis is expiring coordination only; PostgreSQL business truth is
  untouched. Atomicity, expiry, loss, timeout, corruption, and recovery are explicit. Kafka/outbox/
  consumer idempotency are N/A.
- **Infrastructure ownership**: PASS. Shared Redis/Compose changes remain under root `infra/docker`;
  dependencies, runtime policy, Lua resource, and tests remain owned by `api-gateway`.
- **Observability**: PASS. Existing Actuator/Prometheus and correlation remain; bounded Micrometer
  hooks are added without a manual Prometheus registry or false OpenTelemetry claim.
- **Dependencies/contracts**: PASS for planning. Every added dependency is justified and the public
  plus operator contracts are created before code.
- **Validation**: PASS for planning. Unit, real-Redis integration, HTTP contract, failure, concurrency,
  load measurement, module/full Maven, and Compose validation are specified; migration/Kafka/Kubernetes
  omissions are justified.

**Gate result**: PASS FOR APPROVED PLANNING; IMPLEMENTATION MUST FOLLOW APPROVED TASK ORDER.  
**Violations or waivers**: None.

### Post-design re-check

Research, Redis data model, contracts, rollback, and exact validation paths preserve every resolved
rule above. No behavior marker or constitutional waiver remains. ADR 0003 remains governing for
package shape and Accepted ADR 0004 govern production implementation.

## Context and Service Ownership

| Capability/data | Owning context/service | Readers/callers | Allowed interaction | Prohibited interaction |
|-----------------|------------------------|-----------------|---------------------|------------------------|
| Public catalog route | `api-gateway` routes; Product owns business API | Shopper clients | Existing HTTP proxy route | Gateway catalog business logic or Product DB access |
| Edge quota policy | `api-gateway` | Gateway filter/operator config | Service-owned configuration | Dynamic policy DB or cross-service shared model |
| Bucket coordination state | `api-gateway` logical owner; root infra operates Redis | Rate-limit Redis adapter only | One atomic Lua acquisition by opaque key | Durable business truth, raw IP, scans, other service DB |
| Gateway-owned 429 | `api-gateway` | Public API clients | Feature 013 HTTP contract | Rewriting downstream-owned 429 |
| Redis local runtime | Root `infra/docker` | Gateway and future approved technical consumers | Existing Redis service/network with namespace isolation | Service-local Compose or second Redis instance |

**Context-map change**: None. No service boundary or downstream interaction is added.  
**Boundary decision**: Quota enforcement belongs at `api-gateway`, the only public ingress. Product
service remains owner of catalog data/validation and is unaware of Redis bucket state.

## Architecture and Lean Gateway Mapping

ADR 0003 intentionally replaces a business-service Hexagonal scaffold with responsibility-oriented
edge packages. Clean dependency direction is preserved at the technical boundary; no fake
`domain/application/adapter` tree is created.

| Concern | Planned package/path | Responsibility | Must not depend on |
|---------|----------------------|----------------|--------------------|
| Policy/decision model | `gateway/ratelimit` | Immutable technical policy, decision, controlled failure type | Web response DTO, downstream Product model, Redis implementation |
| Route/policy selection | `gateway/ratelimit/RateLimitPolicyResolver.java` | Resolve at most one policy from stable route ID + method | Raw path/query or Redis |
| Identity/key boundary | `gateway/ratelimit/RateLimitIdentityResolver.java`, `DirectClientIpRateLimitIdentityResolver.java`, `HmacRateLimitBucketKeyFactory.java` | Direct-address normalization and opaque HMAC key; fresh initialized `Mac` per derivation | Forwarding headers, shared mutable `Mac`, JWT payload, logging/metrics |
| Coordinator seam | `gateway/ratelimit/DistributedRateLimiter.java` | Reactive acquire contract for filter/test isolation | HTTP rendering and downstream chain |
| Driven Redis implementation | `gateway/ratelimit/redis/RedisTokenBucketRateLimiter.java`, `resources/redis/token_bucket.lua` | Script execution, timeout, result/failure translation | HTTP response or route matching |
| Catalog correlation boundary | `gateway/filter/global/CatalogCorrelationIdGlobalFilter.java` | Route-ID/method-scoped normalized/generated `X-Trace-Id` propagation at order `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2` | Limiter state, raw caller identity, admin validation changes |
| Driving edge filter | `gateway/filter/global/GatewayRateLimitGlobalFilter.java` | Route-aware flow and exactly-once chain/rejection selection | Redis driver types or Product behavior |
| Error boundary | existing `gateway/error/GatewayHttpErrorWriter.java` | Exact owned envelope and typed rate-limit headers | Redis/key/identity internals |
| Configuration/bootstrap | `gateway/configuration/GatewayRateLimitProperties.java`, `GatewayRateLimitConfiguration.java` | Bind/validate/wire conditional capability | Caller-specific decisions |
| Observability | `gateway/observability/GatewayRateLimitObservation.java` | Bounded metrics, Observation lifecycle, safe feature lifecycle/configuration logs | Raw identity/digest/key or direct OTel SDK |

**Dependency direction check**: `filter/global` depends on `ratelimit` abstractions and the existing
error/observability boundary; the correlation filter depends only on route metadata plus the existing
trace resolver; `ratelimit/redis` implements the coordinator seam; configuration wires both. No
Redis type enters filter/policy models. Package/import review plus focused tests enforce this without
adding ArchUnit solely for this small slice.

## Synchronous Flows

### Flow 0 — Protected catalog correlation

1. The already matched `product-catalog` route plus `GET` activates the always-on correlation filter
   at `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2`.
2. A normalized valid caller `X-Trace-Id` is reused; missing/invalid input produces one generated
   exchange-cached fallback. The downstream request is mutated to carry exactly that value.
3. The limiter runs one order later when enabled. Allowed/fail-open downstream traffic and an owned
   429 therefore reuse one value. Other routes and strict admin validation are unchanged.

### Flow 1 — Disabled or unmatched traffic

1. Conditional configuration omits the active limiter filter when global enablement is false; the
   catalog correlation filter remains active. When enabled, the limiter reads the already matched
   `GATEWAY_ROUTE_ATTR` and HTTP method.
2. A route/method without the one effective policy immediately continues the chain.
3. Redis and identity HMAC are not called; no quota header/metric masquerades as a decision.

**Sequence guarantee**: Existing routing/quota/status/security behavior is unchanged; the one
intentional additive delta is catalog `X-Trace-Id` propagation from Flow 0.  
**Timeout/retry**: N/A.  
**Duplicate/replay**: N/A; chain invoked once.

### Flow 2 — Protected request allowed

1. Filter matches `product-catalog` + `GET` and resolves the normalized direct address.
2. HMAC factory creates one opaque versioned key without retaining raw address bytes.
3. Coordinator runs one Lua acquisition with Redis time and 50 ms timeout.
4. `ALLOWED` continues the chain exactly once; no quota header is added.
5. Observation records bounded `allowed` outcome and acquisition timing.

**Sequence guarantee**: The script serializes same-key decisions atomically; no cross-key order.  
**Timeout/retry**: One command, no application retry; framework NOSCRIPT fallback only.  
**Duplicate/replay**: Each HTTP request is an independent cost; no durable idempotency record.

### Flow 3 — Protected request rejected

1. Successful script result reports `REJECTED` and exact `retryAfterMs`.
2. Filter does not call downstream.
3. Existing writer renders only `{code,message,traceId}` with the exchange correlation value and
   applies delay-seconds plus `no-store`.
4. No accounting header or identity/state detail is exposed.
5. The expected 429 is recorded by bounded limiter instrumentation and does not invoke the generic
   per-error WARN; only an unexpected serialization fallback records the central error once.

**Sequence guarantee**: Only the successful atomic result can reject.  
**Timeout/retry**: No retry.  
**Duplicate/replay**: Repeated requests are independently evaluated; rejection does not refresh TTL.

### Flow 4 — Identity unavailable

1. Missing/unresolved direct address yields an explicit empty identity outcome.
2. Filter skips HMAC/Redis and continues downstream once.
3. A distinct bounded metric/Observation signal is emitted with no quota/retry header.

### Flow 5 — Coordinator failure

1. Redis timeout/connection/script/state/result issue is translated inside the coordinator boundary.
2. Acquisition-local recovery converts only `RateLimitCoordinatorException` into an internal
   `fail_open` decision before the terminal response/downstream branch.
3. The terminal branch continues the chain exactly once; no 429 or quota/retry header is created.
4. Unexpected code, writer, or downstream errors bypass this catch and retain Feature 011 handling.

### Flow 6 — Downstream response

1. Allowed/fail-open flow calls the existing route chain.
2. Any normally obtained status/body/header, including HTTP 429, passes through untouched.
3. Limiter has no post-filter header mutation.

## Data, Transactions, and Concurrency

- **Source of truth**: Downstream PostgreSQL remains business truth; Redis bucket state is ephemeral
  edge coordination only.
- **Transaction boundary**: No database transaction. One O(1) Lua invocation is the entire atomic
  read/refill/consume/expiry boundary.
- **Hot-path state**: One `rl:k1:<digest>` hash with integer `credit`, `last_refill_ms`, and bounded TTL.
- **Numeric boundary**: Java and Lua accept only canonical unsigned decimal exact integers up to
  `2^53 - 1`; Lua emits state/results with `string.format("%.0f", value)`. Enabled configuration
  bounds every numeric script argument, including `refillTokens`, and rejects
  `fullRefillMs > 86_400_000`. Before a rejection, Lua proves the current finite TTL covers the exact
  deficit/refill horizon; a shorter TTL is invalid state, so expiry cannot precede an advertised
  retry horizon.
- **Concurrency control**: Redis atomic script plus coordinator time. Same-key callers cannot
  overspend; unrelated keys have no ordering promise.
- **Idempotency record**: N/A. The limiter does not replay HTTP business commands; ambiguous
  acquisitions are never retried.
- **Outbox/inbox**: N/A; no durable mutation or event.
- **Migration**: No schema/data migration. Policy/secret changes create new opaque namespaces; old
  keys expire naturally.
- **Detailed state contract**: [data-model.md](data-model.md).

## Contracts and Compatibility

| Contract | Producer/owner | Consumers | Version/change | Compatibility and rollout |
|----------|----------------|-----------|----------------|---------------------------|
| Gateway rate-limit HTTP | `api-gateway` | Web/mobile/API clients and Product downstream | Add `Retry-After` + `no-store` only to owned 429; add route-scoped catalog request correlation | Body/responses unchanged; additive downstream `X-Trace-Id` request header is always active; quota disabled by default |
| Gateway limiter configuration | `api-gateway` | Operators/local Compose | New default-off property/policy/secret contract | Enabled invalid config fails startup; no public error |
| Feature 012 historical contract | `api-gateway` | Reviewers/clients | Add amendment pointer after approval | Preserve Verified body/history; do not rewrite completed behavior |

**Contract file paths**:

- `specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-http.md`
- `specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-configuration.md`
- baseline `specs/012-gateway-rate-limit-contract/contracts/gateway-rate-limit-error-http.md`

**Breaking-change decision**: None. Client-visible headers are additive only on a newly activated
Gateway-owned 429; no success or downstream response contract changes. One intentional additive
downstream request-header delta (`X-Trace-Id`) activates independently of quota and is covered by
Product proxy/correlation regressions.

## Failure, Retry, and Compensation

| Failure mode | Detection | User/business outcome | Retry/compensation | Recovery/reconciliation | Evidence |
|--------------|-----------|-----------------------|--------------------|-------------------------|----------|
| Quota insufficient | Valid `REJECTED` result | Exact owned 429; downstream zero | None | Natural refill/expiry | HTTP + Lua tests |
| Direct identity unavailable | Empty resolver result | Downstream once, no quota headers | No Redis call | Fix network/proxy config; metric/Observation | Filter test |
| Redis timeout | outer `java.util.concurrent.TimeoutException`, direct `io.lettuce.core.RedisCommandTimeoutException`, or `org.springframework.dao.QueryTimeoutException` with that immediate Lettuce cause | Downstream once, `fail_open` | No retry/compensation | Redis recovery or disable feature | Virtual-time one-subscription/direct-wrapper test/metric |
| Redis connection failure | `org.springframework.data.redis.RedisConnectionFailureException` or direct `io.lettuce.core.RedisConnectionException` | Downstream once, `fail_open` | No retry | Restore Redis; limiter resumes per request | Direct/wrapped/near-miss mapping test |
| Missing key | Both fields absent and key not present | Start full; normal decision | N/A | State recreated on allowed result | Redis test |
| Invalid/future/no-TTL/short-rejection-TTL state | Stable Lua `ERROR/INVALID_STATE` tuple or strict result validation | Downstream once, `fail_open`; no false retry hint | No reset/retry | Attach only normal TTL once when `PTTL=-1`; preserve type/fields; later still-invalid calls do not refresh; otherwise-valid hash may resume; existing finite TTL only decreases; no scan | Seeded-state/deficit-horizon test |
| Invalid script result | Result mapper validation | Downstream once, `fail_open` | No retry | Deployment investigation | Adapter/filter test |
| Script cache miss | Spring Data executor | Normal public outcome | Framework `EVALSHA`→`EVAL` only | Automatic cache repopulation | Cache-flush test |
| Writer serialization failure | Existing writer fallback | Safe existing 500, no quota headers | None | Existing error observation | Writer test |
| Downstream 429/error | Normal proxy response | Pass through unchanged | Limiter does not retry/rewrite | Downstream owner | Proxy regression |
| Unexpected limiter bug | Existing WebExceptionHandler | Safe existing Gateway error when classified | No fail-open catch | Fix/rollback feature | Unexpected-error test |

No compensation exists because bucket state is ephemeral and an ambiguous acquisition is deliberately
not replayed.

The adapter's finite type map is exact: `org.springframework.data.redis.serializer.SerializationException` becomes
`SERIALIZATION`; Lua `INVALID_STATE` becomes `INVALID_STATE`; strict tuple/bound failure becomes
`INVALID_RESULT`; Lua `INVALID_ARGUMENT` and direct
`io.lettuce.core.RedisCommandExecutionException` become `SCRIPT`.
`org.springframework.dao.QueryTimeoutException` becomes `TIMEOUT` only when its immediate cause is
`io.lettuce.core.RedisCommandTimeoutException`.
`org.springframework.data.redis.RedisSystemException` is unwrapped only when its immediate cause is
one of the explicitly listed Lettuce timeout/connection/execution types. Generic
`org.springframework.dao.DataAccessException`, Redis base types, message parsing, broad cause
walking, and catch-all `Throwable` mapping are prohibited; near misses propagate. Exact 50 ms
behavior uses Reactor virtual time, while real Redis/cache-recovery fixtures use a test-only 2-second
timeout and separately verify production configuration remains 50 ms.

## Security and Abuse Controls

- **Authentication/authorization**: Public catalog remains anonymous/permit-all. Admin JWT/JWKS and
  `CATALOG_ADMIN` behavior is unchanged.
- **Trust boundaries**: Socket remote address is the only identity source. Gateway pins
  `server.forward-headers-strategy=none`; caller-controlled forwarding headers cannot rewrite the
  resolved peer. Kubernetes trusted ingress is explicitly deferred.
- **Sensitive data**: IP bytes and HMAC secret are sensitive; digest is a stable pseudonymous key.
  None enters HTTP, logs, metric tags, Observation high-cardinality values, traces, or baggage.
- **Secret validation**: Standard Base64 decode and at least 32 decoded bytes when enabled; dedicated
  key; no JWT reuse. Runtime cannot prove entropy, so operator generation uses a CSPRNG. Startup
  validation messages/log capture must never echo the supplied secret; tests use a distinctive
  sentinel to prove redaction.
- **Canonicalization**: Address bytes plus versioned length-prefixed components prevent textual and
  delimiter ambiguity. Output is full lowercase hex. The singleton key factory creates/initializes a
  fresh `Mac` per derivation and never shares a mutable `Mac` across concurrent requests.
- **Abuse controls**: One bucket per opaque direct-IP identity; no client bypass header; one policy;
  no user/API-key strategy.
- **Auditability**: Deployment config/change review records policy/state version without secret;
  runtime signals record bounded outcome/failure only. No per-caller audit history is retained.

## Observability and Operations

- **Health**: `management.health.redis.enabled=false` keeps aggregate health and liveness/readiness
  independent of Redis because the approved policy is fail-open. Root Compose overrides Gateway's
  inherited `depends_on` with `{}` so an unavailable backing service cannot block edge startup.
- **Metrics**: `gateway.rate.limit.decisions`, `gateway.rate.limit.errors`, and
  `gateway.rate.limit.acquire` Observation/timer with bounded policy/route/outcome/failure fields.
  Declarative client percentiles `0.5,0.95,0.99` are enabled only for that acquisition timer so the
  local Prometheus scrape can satisfy NFR-006 without claiming an SLO.
- **Tracing**: Preserve current `traceId` correlation. The always-active catalog correlation filter
  runs before the optional limiter and sends a normalized valid or generated fallback `X-Trace-Id`
  downstream; the owned 429 reuses the exchange-cached value. No Micrometer
  Tracing/OpenTelemetry bridge or W3C propagation claim is added; instrumentation uses Micrometer
  abstractions only.
- **Logs**: No limiter-specific per-request INFO/WARN log for allowed, rejected,
  identity-unavailable, or fail-open traffic; bounded counters/Observation are authoritative and
  prevent log amplification. Only safe enable/disable/configuration lifecycle logs may be added,
  with bounded policy/state metadata and never raw IP, secret, digest/key, query, or exception
  message. A successfully rendered expected 429 bypasses the generic per-error WARN; serialization
  fallback and other unexpected failures remain with the existing central error boundary.
- **Prometheus**: Existing runtime registry and declarative `/actuator/prometheus` exposure remain;
  no Java `PrometheusMeterRegistry` construction.
- **Alerts/SLOs**: Thresholds are deferred until load/baseline evidence; no production quota or
  throughput SLO is claimed.
- **Runbook**: Disable the feature flag for immediate recovery; inspect bounded metrics/Observation,
  safe lifecycle logs, and Redis reachability; never flush business/shared Redis or scan/delete
  limiter keys as routine rollback.

## Migration, Rollout, and Rollback

1. Run pre-approval analysis, then explicitly approve spec, ADR 0004, HTTP/config contracts,
   plan, and tasks.
2. Add dependencies, default-off configuration, implementation, and tests; amend Feature 012 with a
   link to the approved header delta.
3. Deploy/run with quota acquisition disabled and pass all existing Gateway regressions plus the
   intentional always-active catalog correlation propagation regression.
4. Configure existing Redis host plus one generated non-committed Base64 secret; keep all replicas on
   identical environment/key/state version/secret.
5. Explicitly enable in test/local Docker; run correctness, failure, health, contract, and measurement
   gates; review evidence before retaining activation.

**Rollback trigger**: Unexpected Gateway regression, response-contract drift, repeated coordinator
errors/fail-open, wrong rejection accuracy, or unacceptable measured overhead.  
**Rollback procedure**: Set `GATEWAY_RATE_LIMIT_ENABLED=false`, restart/redeploy Gateway, verify
catalog routing and existing suite, then investigate. Do not flush Redis/delete volumes; TTL removes
old keys.  
**Irreversible step**: None.

### Future Kubernetes extension (not implemented here)

Before production cluster activation, create a new approved feature/ADR for trusted ingress IP
resolution, Secret/external-secret distribution and rotation, Redis HA/TLS/credentials/resources,
multi-replica state-version rollout, root monitoring assets, and applicable Kustomize/Helm changes.
Run `kubectl apply --dry-run=client -k <overlay>` only when that future feature changes overlays.

## Verification Strategy and Evidence

| Requirement/risk | Verification type | Command/environment | Expected evidence | Task owner |
|------------------|-------------------|---------------------|-------------------|------------|
| FR-001–003/015 | Unit/config | `GatewayRateLimitPropertiesTests`, `RateLimitPolicyResolverTests` | Exact route/method/quota/full-refill bound; invalid enabled config fails; disabled bypass | Gateway owner |
| FR-012–014/021, NFR-005 | Unit/security | `DirectClientIpRateLimitIdentityResolverTests`, `HmacRateLimitBucketKeyFactoryTests`, enabled startup context | IP normalization, Boot forwarding disabled, secret rotation/secrecy, 64 hex, separation, zero exposure | Security owner |
| FR-004/005/008/019/020, NFR-001 | Redis integration | `RedisTokenBucketRateLimiterTests` against Redis 7.4 Testcontainer with exact test-only 2s timeout | Canonical integer round-trip, math/refill, short/safe TTL horizon, cache/corruption and exact 20/80 across three clients | Gateway owner |
| FR-016–019, NFR-004 | Adapter unit/failure | `RedisTokenBucketRateLimiterFailureTests` with Reactor virtual time/controlled results | Exact 50 ms timeout, one subscription/no retry, exact direct/wrapped/near-miss mapping, strict credit/result bounds | Gateway owner |
| FR-006–011/026, NFR-002/003 | Writer/Gateway contract | `GatewayHttpErrorWriterTests`, `GatewayRateLimitContractTests`, `GatewayProxyPassThroughTests`, `CatalogCorrelationIdGlobalFilterTests` | Exact owned 429/correlation, no expected-429 WARN, fallback safety, downstream pass-through | Contract owner |
| FR-014/016–018, NFR-004 | Failure injection | `GatewayRateLimitFailureTests` | Missing IP/timeout/connection/state/result outcomes; downstream once; no retry/false 429 | Gateway owner |
| FR-024–026 | Observability/health/regression | `GatewayRateLimitObservationTests`, `GatewayRateLimitDisabledTests`, existing Gateway suite | Bounded tags and safe lifecycle logs; no expected-429/per-request limiter WARN or OTel types; catalog trace is the sole intentional default-off delta; readiness/routing/security preserved | Observability owner |
| NFR-006 | Measurement-only load | k6 against exact empty-catalog GET plus `/actuator/prometheus` and Redis INFO capture | HTTP throughput/latency/statuses, acquisition timer p50/p95/p99, rejection accuracy, Redis stats/errors; no throughput threshold | Gateway/Platform owner |
| FR-022 | Compose config | `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config` | One Redis service; Gateway variables resolve; no secret committed | Platform owner |
| All | Module gate | `.\mvnw.cmd -pl services/api-gateway -am verify` | Exit 0 with required real-Redis tests | Gateway owner |
| Cross-cutting regression | Full reactor | `.\mvnw.cmd clean verify` | Exit 0 across monorepo | Technical owner |

The NFR-006 fixture is exact: root Compose starts PostgreSQL, the existing Redis, Product with
`PRODUCT_LIQUIBASE_ENABLED=true`, and Gateway with the approved limiter variables; Product needs no
seed because `GET /api/v1/catalog/products?page=0&size=20` deterministically returns a valid empty
page. One local k6 process calls that URI through `http://localhost:8080`. A fresh sample waits the
60-second bucket TTL (or uses a newly generated local-only secret), then recreates Gateway to obtain
a fresh `MeterRegistry`, waits for health only, and sends no catalog request before k6. It accepts
only HTTP 200/429 and requires both statuses in the burst phase. k6 reports HTTP throughput and HTTP
p50/p95/p99; `/actuator/prometheus` supplies `gateway.rate.limit.acquire` quantiles; Redis `INFO
stats`/`INFO memory` supply coordinator resource/error signals. Redis outage correctness belongs to
deterministic Maven failure tests and a Compose no-dependency startup check, not to the k6 scenario.

Focused test commands must add `-Dsurefire.failIfNoSpecifiedTests=false` when Maven `-am` would
otherwise ask upstream modules to run Gateway-only test names. Actual commands, timestamp, scope,
exit code, Testcontainers/Redis version, Docker/k6 profile, and CI/PR reference are recorded in
`specs/013-gateway-redis-rate-limiter/validation.md` during implementation; that evidence file is not
created during planning.

**Required module build**: `.\mvnw.cmd -pl services/api-gateway -am verify`  
**Required full build**: `.\mvnw.cmd clean verify` because public ingress, root Compose, and repo-level
load assets change  
**Kubernetes validation**: N/A; no overlay/manifests change  
**Database/migration validation**: N/A; no database or Liquibase artifact  
**Kafka/contract validation**: Kafka N/A; HTTP contract tests required  
**Load/failure validation**: Required as described; performance is measurement-only, while script
completion, required fields, rejection correctness, and absence of setup/transport errors are gates.

## Project Structure

### Documentation (this feature and synchronized living design)

```text
specs/013-gateway-redis-rate-limiter/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── gateway-rate-limit-http.md
│   └── gateway-rate-limit-configuration.md
├── checklists/
│   ├── requirements.md
│   └── plan.md
└── tasks.md

docs/adr/0004-gateway-distributed-rate-limiter.md
docs/ratelimit/{01-scope-and-status.md..12-delivery-slices-and-decisions.md,README.md}
docs/technology/technology-problem-map.md
specs/012-gateway-rate-limit-contract/contracts/gateway-rate-limit-error-http.md
```

### Source, tests, runtime, and evidence (affected paths)

```text
services/api-gateway/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/philia/flashsale/gateway/
    │   │   ├── configuration/
    │   │   │   ├── GatewayRateLimitConfiguration.java
    │   │   │   └── GatewayRateLimitProperties.java
    │   │   ├── error/
    │   │   │   └── GatewayHttpErrorWriter.java
    │   │   ├── filter/global/
    │   │   │   ├── CatalogCorrelationIdGlobalFilter.java
    │   │   │   └── GatewayRateLimitGlobalFilter.java
    │   │   ├── observability/
    │   │   │   └── GatewayRateLimitObservation.java
    │   │   └── ratelimit/
    │   │       ├── DirectClientIpRateLimitIdentityResolver.java
    │   │       ├── DistributedRateLimiter.java
    │   │       ├── HmacRateLimitBucketKeyFactory.java
    │   │       ├── RateLimitCoordinatorException.java
    │   │       ├── RateLimitDecision.java
    │   │       ├── RateLimitFailureType.java
    │   │       ├── RateLimitIdentityResolver.java
    │   │       ├── RateLimitPolicy.java
    │   │       ├── RateLimitPolicyResolver.java
    │   │       └── redis/RedisTokenBucketRateLimiter.java
    │   └── resources/
    │       ├── application.yml
    │       └── redis/token_bucket.lua
    └── test/java/com/philia/flashsale/gateway/
        ├── GatewayRateLimitContractTests.java
        ├── GatewayRateLimitDisabledTests.java
        ├── GatewayRateLimitFailureTests.java
        ├── GatewayProxyPassThroughTests.java
        ├── configuration/GatewayRateLimitPropertiesTests.java
        ├── error/GatewayErrorCodeTests.java
        ├── error/GatewayHttpErrorWriterTests.java
        ├── filter/global/CatalogCorrelationIdGlobalFilterTests.java
        ├── filter/global/GatewayRateLimitGlobalFilterTests.java
        ├── observability/GatewayRateLimitObservationTests.java
        └── ratelimit/
            ├── HmacRateLimitBucketKeyFactoryTests.java
            ├── DirectClientIpRateLimitIdentityResolverTests.java
            ├── RateLimitPolicyResolverTests.java
            └── redis/
                ├── RedisTokenBucketRateLimiterFailureTests.java
                └── RedisTokenBucketRateLimiterTests.java

infra/docker/
├── compose.yml
├── .env.example
└── README.md

load-tests/k6/
├── README.md
└── scenarios/gateway-rate-limit.js

specs/013-gateway-redis-rate-limiter/validation.md  # created during implementation only
```

**Structure decision**: Only real edge responsibilities are added. The Redis implementation is kept
behind a small technical coordinator seam, the filter contains no driver code, mappers/DTO packages
are unnecessary, and existing `routing`, `security`, `faulttolerance`, and business-service modules
are not reorganized. Obsolete `.gitkeep` files are removed only when a real class occupies that
package; the unused ADR-reserved markers remain.

## Complexity Tracking

No constitutional violation or waiver is required.

| Accepted complexity | Why needed | Simpler alternative rejected because | ADR/approval | Review point |
|---------------------|------------|--------------------------------------|--------------|--------------|
| Custom Redis Lua coordinator rather than built-in filter | Exact rejected-TTL, corrupt-state, fail-open, identity, writer, and compatibility semantics | Built-in public configuration does not expose all approved behavior | Accepted ADR 0004 | Revisit after Feature 013 evidence |
| HMAC/versioned key boundary | Prevent raw identity exposure/collision and support bounded lifecycle | Raw IP or delimiter-only hash is unsafe/ambiguous | FR-013/021; ADR 0004 | Kubernetes identity feature |

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-07-22 | Draft implementation plan created from approved MVP decisions and risk-profile analysis | Codex | Pending Gateway/Platform owner review | Draft |
| 2026-07-23 | Approved plan after clean pre-approval analysis and explicit Feature 013 artifact approval | Codex | Gateway/Platform owner (user) | Approved |
