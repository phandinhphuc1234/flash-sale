# Validation Evidence: Gateway Redis Rate Limiter

Feature: `013-gateway-redis-rate-limiter`  
Owner: Gateway/Platform owner  
Evidence date: 2026-07-23

## T007 — Baseline snapshot before Gateway dependency setup

### Scope

T007 establishes the safe baseline before adding the Redis rate-limiter dependencies to
`services/api-gateway/pom.xml`.

### Pre-change workspace snapshot

Command:

```powershell
git status --short
```

Result summary:

- Existing tracked Gateway changes are present from the prior Gateway error/security work.
- Existing untracked Feature 011/012 Gateway tests and support classes are present.
- Feature 013 artifacts and rate-limit documentation are currently untracked/new in this workspace.
- No pre-existing failure was observed in the required pre-change Gateway verification.

Relevant dirty Gateway/Feature 011–012 diff summary captured before editing `pom.xml`:

- `GatewayErrorCode.java` already contains Gateway-owned error codes including
  `RATE_LIMIT_EXCEEDED`.
- `GatewayHttpErrorWriter.java` already centralizes Gateway-owned error-body rendering and trace
  resolution.
- `GatewaySecurityConfiguration.java` and `GatewaySecurityErrorHandler.java` already own Gateway
  security failure rendering.
- Gateway contract/regression tests for authentication, downstream unavailable, proxy pass-through,
  unknown paths, error writer/classifier, and trace/error observation are present.

### Pre-change verification

Command:

```powershell
.\mvnw.cmd -q -pl services/api-gateway -am verify
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace

Result:

- Exit code: `0`
- api-gateway Surefire reports: `105` tests, `0` failures, `0` errors, `0` skipped

Notes:

- The required baseline verification passed before any T007 dependency edit.
- The prerequisite helper script was not executed because escalation was not approved, so the active
  feature was verified manually through `.specify/feature.json`, artifact reads, and checklist status.

### Dependency setup decision

Approved dependency additions for T007:

- `org.springframework.boot:spring-boot-starter-data-redis-reactive`
- `org.springframework.boot:spring-boot-starter-validation`
- test-scope `org.testcontainers:junit-jupiter`

Forbidden in this task:

- No OpenTelemetry runtime dependency
- No resilience/circuit-breaker dependency
- No database/JPA dependency
- No tracing implementation dependency beyond existing Micrometer/Actuator setup

### Post-change verification

Command:

```powershell
.\mvnw.cmd -q -pl services/api-gateway -am verify
```

Attempt 1 result:

- Exit code: `1`
- Failure: `GatewayUnknownPathSecurityTests.configuredHealthEndpointRemainsPublic`
- Observed response: `/actuator/health` returned `503 SERVICE_UNAVAILABLE`
- Cause: adding `spring-boot-starter-data-redis-reactive` enabled Spring Boot's Redis health
  contributor; local Redis was not running, so aggregate health became `DOWN`.

Resolution:

- Added `management.health.redis.enabled=false` in
  `services/api-gateway/src/main/resources/application.yml`.
- This is the approved default-off health behavior already planned for the foundational
  configuration task and does not enable the rate limiter or create a Redis startup dependency.

Final command:

```powershell
.\mvnw.cmd -q -pl services/api-gateway -am verify
```

Final result:

- Exit code: `0`
- api-gateway Surefire reports: `105` tests, `0` failures, `0` errors, `0` skipped

T007 status:

- Completed.
- No OpenTelemetry runtime, resilience/circuit-breaker, database/JPA, Kafka, or extra tracing
  dependency was added.

## T008 — Expected-red policy and configuration tests

### Scope

T008 adds test-first coverage for the default-off configuration boundary, the approved single
catalog-read selector, policy-state version validation, environment identifier validation, exact
numeric Lua argument derivation, the `2^53 - 1` Redis Lua exact-integer boundary, the 24-hour
`fullRefillMs` bound, and unique route-method policy selection.

Files added:

- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitPropertiesTests.java`
- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/RateLimitPolicyResolverTests.java`

Deferred by task design:

- Enabled Base64 secret decoding and startup redaction tests are intentionally deferred to T022.
- Redis Lua script behavior, real Redis concurrency, filter behavior, and HTTP 429 rendering remain
  in later tasks.

### Expected-red verification

Initial PowerShell command attempt:

```powershell
.\mvnw.cmd -pl services/api-gateway -am -Dtest=GatewayRateLimitPropertiesTests,RateLimitPolicyResolverTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

- Exit code: `1`
- Cause: PowerShell parsed the comma in `-Dtest` as a parameter-list separator. This was a command
  invocation error and is not counted as T008 evidence.

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=GatewayRateLimitPropertiesTests,RateLimitPolicyResolverTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit code: `1`
- Maven phase: `testCompile`
- Expected failure: production types are not implemented yet.
- Missing symbols include:
  - `com.philia.flashsale.gateway.configuration.GatewayRateLimitProperties`
  - `com.philia.flashsale.gateway.ratelimit.RateLimitPolicy`
  - `com.philia.flashsale.gateway.ratelimit.RateLimitPolicyResolver`

T008 status:

- Completed as an expected-red test-first task.
- Do not run full module verification again until T009 implements the missing production types and
  makes these tests pass.

## T009 — Immutable policy binding and resolver implementation

### Scope

T009 implements the production types required by the T008 policy/configuration tests:

- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitProperties.java`
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitPolicy.java`
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitPolicyResolver.java`

Implemented behavior:

- Default-off property binding with optional secret when disabled.
- Exact MVP enabled-policy validation for `public-catalog-read`, `product-catalog`, `GET`,
  `CLIENT_IP`, and `ALLOW_WITH_METRIC`.
- Environment identifier validation using lowercase letters, digits, and hyphen only.
- State version validation with `p[1-9][0-9]{0,8}`.
- Overflow-safe Java derivation of capacity credit, request cost credit, full-refill duration, TTL,
  and Lua numeric argument strings.
- `2^53 - 1` validation for every numeric Lua argument, including `refillTokens`.
- Unique enabled route-method selector validation through `RateLimitPolicyResolver`.

Intentionally not implemented in this task:

- Enabled secret Base64 decoding/length validation and redaction behavior, which remain deferred to
  T022/T027.
- Redis Lua script, Redis client adapter, Gateway filter, real HTTP 429 rendering, tracing changes,
  or runtime rate-limit enforcement.

### Focused verification

Initial sandbox command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=GatewayRateLimitPropertiesTests,RateLimitPolicyResolverTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- No Maven result was produced.
- Windows sandbox failed to spawn PowerShell with `CreateProcessAsUserW failed: 1312`.
- This was an execution-environment issue, not a compile/test result.

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=GatewayRateLimitPropertiesTests,RateLimitPolicyResolverTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace

Result:

- Exit code: `0`
- api-gateway focused Surefire reports: `34` tests, `0` failures, `0` errors, `0` skipped
- Reactor: `flash-sale-engine`, `api-gateway`

T009 status:

- Completed.
- T008 expected-red tests now pass.

### Module regression verification

Command:

```powershell
.\mvnw.cmd -q -pl services/api-gateway -am verify
```

Result:

- Exit code: `0`
- api-gateway Surefire reports: `139` tests, `0` failures, `0` errors, `0` skipped
- Notes: command output included verbose Spring DEBUG/condition logs from existing test
  configuration, but the Maven process completed successfully.

## T010-T011 — Foundational seams and default-off configuration

### Scope

T010 adds Redis-independent technical rate-limit seams and models:

- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/DistributedRateLimiter.java`
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitDecision.java`
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitFailureType.java`
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitCoordinatorException.java`
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitIdentityResolver.java`

Implemented boundaries:

- Gateway filter code can depend on a Redis-independent `DistributedRateLimiter` seam.
- Decisions distinguish `ALLOWED`, `REJECTED`, `IDENTITY_UNAVAILABLE`, and `FAIL_OPEN`.
- Coordinator fail-open is constrained to the approved finite failure types:
  `TIMEOUT`, `CONNECTION`, `INVALID_STATE`, `INVALID_RESULT`, `SCRIPT`, and `SERIALIZATION`.
- Identity resolution exposes a typed byte identity holder whose value is defensively copied and
  redacted from `toString()`.

T011 adds the approved service-owned default-off configuration to
`services/api-gateway/src/main/resources/application.yml`:

- `flashsale.gateway.rate-limit.enabled=${GATEWAY_RATE_LIMIT_ENABLED:false}`
- `environment=${RATE_LIMIT_ENVIRONMENT:local}`
- `key-prefix=rl`
- `command-timeout=50ms`
- `hmac-secret=${RATE_LIMIT_KEY_HMAC_SECRET:}`
- the single `public-catalog-read` policy with `p1`, `product-catalog`, `GET`, `CLIENT_IP`,
  capacity `60`, refill `30/1s`, cost `1`, and `ALLOW_WITH_METRIC`
- `server.forward-headers-strategy=none`
- `management.health.redis.enabled=false`
- `gateway.rate.limit.acquire` percentiles `0.5,0.95,0.99`

Intentionally not implemented:

- Redis Lua script, Redis adapter, HMAC key derivation, direct-IP resolver implementation,
  Gateway filter enforcement, HTTP 429 rendering changes, OpenTelemetry runtime, and route/security
  changes.
- The code-owned key-layout marker `k1` remains out of operator configuration.

### Verification

Command:

```powershell
.\mvnw.cmd -q -pl services/api-gateway -am verify
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace

Result:

- Exit code: `0`
- api-gateway Surefire reports: `139` tests, `0` failures, `0` errors, `0` skipped
- Notes: command output included verbose Spring DEBUG/condition logs from existing test
  configuration, but the Maven process completed successfully.

T010-T011 status:

- Completed.
- The Gateway remains default-off and does not require Redis or a secret to start under the current
  regression suite.

## T012 — Expected-red real Redis token-bucket tests

### Scope

T012 adds the first real Redis integration test fixture for the token bucket coordinator:

- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiterTests.java`

The test fixture uses `redis:7.4-alpine` through Testcontainers and a test-only 2-second timeout.
It is scoped to the Redis coordinator and does not connect to the developer Compose Redis instance.

Covered expected behavior:

- missing state starts full and persists canonical hash fields with TTL;
- allowed acquisition refills toward capacity, clamps at capacity, and writes non-exponent decimal
  state;
- rejected acquisition returns a positive retry delay and does not refresh TTL;
- Spring Data script-cache recovery after `SCRIPT FLUSH`;
- non-canonical stored numbers fail with `INVALID_STATE` without mutation;
- largest safe Lua integer (`2^53 - 1`) and max `refillTokens` round-trip in decimal form;
- 100 concurrent same-bucket acquisitions across three independent limiter clients produce exactly
  20 allowed and 80 rejected with no negative balance.

Intentionally not implemented:

- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiter.java`
- `services/api-gateway/src/main/resources/redis/token_bucket.lua`
- filter/writer/runtime activation

### Expected-red verification

Initial sandbox command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=RedisTokenBucketRateLimiterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- No Maven result was produced.
- Windows sandbox failed to spawn PowerShell with `CreateProcessAsUserW failed: 1312`.
- This was an execution-environment issue, not a compile/test result.

First outside-sandbox attempt:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=RedisTokenBucketRateLimiterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result:

- Exit code: `1`
- Maven phase: `testCompile`
- The test initially referenced `reactor.test.StepVerifier`, which is not available in the module,
  and had generic inference issues unrelated to the intended production gap.
- Resolution: removed the `StepVerifier` dependency from the test and tightened local generics so
  no unplanned test dependency is introduced.

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=RedisTokenBucketRateLimiterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace

Result:

- Exit code: `1`
- Maven phase reached: `surefire:test`
- Expected failure: `RedisTokenBucketRateLimiter` production class is not implemented yet.
- Surefire reports: `RedisTokenBucketRateLimiterTests`, `1` test container-level error,
  `NoClassDefFoundError: RedisTokenBucketRateLimiter`.

T012 status:

- Completed as an expected-red test-first task.
- Do not implement Redis Lua or adapter until T015/T016.

## T013 — Expected-red catalog correlation and limiter filter tests

### Scope

T013 adds route-aware Gateway filter tests before implementing either filter:

- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/filter/global/CatalogCorrelationIdGlobalFilterTests.java`
- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilterTests.java`

Covered expected behavior:

- `CatalogCorrelationIdGlobalFilter` order is exactly
  `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2`.
- `GatewayRateLimitGlobalFilter` order is exactly
  `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 1`.
- Public catalog GET with a valid caller `X-Trace-Id` forwards the normalized trace.
- Public catalog GET with missing/invalid trace gets one generated exchange-cached trace.
- Public correlation does not relax the strict admin-catalog trace boundary.
- Raw path alone does not activate correlation without the matched Gateway route.
- Limiter bypasses unmatched route/method without identity or quota calls.
- Allowed limiter decision invokes downstream exactly once.
- Rejected limiter decision stops before downstream and uses a Gateway-owned 429 response.

Design adjustment made while adding tests:

- `RateLimitIdentityResolver` was tightened so the filter receives only an opaque bucket key or an
  unavailable result. Raw direct-IP bytes and HMAC details stay inside the later
  `DirectClientIpRateLimitIdentityResolver`/`HmacRateLimitBucketKeyFactory` boundary instead of
  leaking into the filter.

Intentionally not implemented:

- `CatalogCorrelationIdGlobalFilter`
- `GatewayRateLimitGlobalFilter`
- Redis adapter/script, rate-limit HTTP headers, production HMAC/IP resolver, and observability
  instrumentation.

### Expected-red verification

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=CatalogCorrelationIdGlobalFilterTests,GatewayRateLimitGlobalFilterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace

Result:

- Exit code: `1`
- Initial compile attempt exposed one test-syntax issue (`AtomicIntegerAssert.isZero()` unavailable
  in the current AssertJ API), which was corrected to `hasValue(0)`.
- Final evidence reached Surefire discovery and failed as expected because
  `GatewayRateLimitGlobalFilter` is not implemented yet.
- Dump report cause: `NoClassDefFoundError: GatewayRateLimitGlobalFilter`.
- The same task intentionally also references the missing `CatalogCorrelationIdGlobalFilter`.

T013 status:

- Completed as an expected-red test-first task.
- Do not implement the filters until T019.

## T014 — Expected-red Gateway-owned 429 and downstream pass-through tests

### Scope

T014 adds the HTTP/writer/contract tests that must be in place before changing the Gateway writer or
filter boundary:

- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayProxyPassThroughTests.java`
- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayHttpErrorWriterTests.java`
- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitContractTests.java`

Covered expected behavior:

- A downstream-owned HTTP 429 keeps its exact byte body and sentinel header.
- A downstream-owned HTTP 429 does not receive Gateway quota headers such as `Retry-After`,
  `Cache-Control: no-store`, `RateLimit*`, or `X-RateLimit-*`.
- Gateway-owned `RATE_LIMIT_EXCEEDED` uses the existing three-field body with the same normalized
  trace value.
- Gateway-owned rate-limit rendering adds integer `Retry-After` rounded up to the next positive
  second and `Cache-Control: no-store`.
- Gateway-owned rate-limit rendering emits no forbidden accounting headers.
- An expected Gateway-owned 429 does not call/log the generic `GatewayErrorObservation`.
- Rate-limit serialization fallback removes quota headers and records the central
  `GATEWAY_INTERNAL_ERROR` observation exactly once.
- Rejected catalog traffic reaches downstream zero times.

Baseline note:

- Prior module verification before T012/T013 expected-red tests showed the existing
  `GatewayProxyPassThroughTests` passing. After T012/T013, Maven test compilation is intentionally
  blocked by missing production classes, so the newly added downstream-429 characterization cannot
  be isolated until the next implementation slices provide those classes.

Intentionally not implemented:

- `GatewayHttpErrorWriter.writeRateLimitExceeded(...)`
- `GatewayRateLimitGlobalFilter`
- `CatalogCorrelationIdGlobalFilter`
- Redis adapter/script and runtime activation

### Expected-red verification

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=GatewayProxyPassThroughTests,GatewayHttpErrorWriterTests,GatewayRateLimitContractTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace

Result:

- Exit code: `1`
- Maven phase: `testCompile`
- Expected missing production symbols include:
  - `GatewayRateLimitGlobalFilter`
  - `CatalogCorrelationIdGlobalFilter` from the prior T013 expected-red tests
  - `RedisTokenBucketRateLimiter` from the prior T012 expected-red tests
  - `GatewayHttpErrorWriter.writeRateLimitExceeded(ServerWebExchange, Duration)`
- No new runtime Gateway behavior was introduced by this task.

T014 status:

- Completed as an expected-red test-first task.
- The next implementation slices are T015/T016 for Lua/Redis adapter, T018 for the typed writer
  operation, and T019 for the filters.

## T015 — Redis Lua token-bucket script resource

### Scope

T015 adds the service-owned Redis script resource:

- `services/api-gateway/src/main/resources/redis/token_bucket.lua`

Implemented behavior in this slice:

- one O(1) Redis Lua acquisition using Redis `TIME`;
- canonical unsigned decimal argument/state validation up to `2^53 - 1`;
- scaled-credit refill using Java-precomputed `capacityCredit`, `requestCostCredit`,
  `fullRefillMs`, and `stateTtlMs`;
- allowed decisions persist `credit` and `last_refill_ms`, then refresh bounded TTL with
  `PEXPIRE`;
- rejected decisions return exact remaining credit without writing state or refreshing TTL;
- defensive `ERROR/INVALID_ARGUMENT` and `ERROR/INVALID_STATE` tuples with no identity, stored value,
  Redis key, or raw detail leakage;
- no P0 expiry-only normalization branch yet, per T015 scope. The `PTTL=-1` recovery behavior is
  reserved for T029/T033 after its expected-red tests exist.

Tuple contract emitted by the script:

```text
["ALLOWED",  "<remainingCredit>", "0"]
["REJECTED", "<remainingCredit>", "0"]
["ERROR",    "INVALID_STATE",    "0"]
["ERROR",    "INVALID_ARGUMENT", "0"]
```

### Focused verification

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=RedisTokenBucketRateLimiterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace

Result:

- Exit code: `1`
- Maven copied both main resources, including the new Redis Lua resource, and reached Surefire.
- Expected remaining failure: `RedisTokenBucketRateLimiter` production adapter is not implemented
  until T016.
- Surefire reports: `RedisTokenBucketRateLimiterTests`, `1` container-level error,
  `NoClassDefFoundError: RedisTokenBucketRateLimiter`.

T015 status:

- Completed as the approved Lua-resource implementation slice.
- T012 cannot become green until T016 adds the Java Redis adapter that loads, executes, and parses
  this script.

## T016–T021 — User Story 1 implementation and focused verification

### Scope

Completed the approved US1 implementation slice after the T012–T014 expected-red tests:

- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiter.java`
  implements singleton classpath Lua execution, strict tuple decoding, canonical safe-integer result
  validation, and Java `Math.ceilDiv` retry-delay calculation.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitConfiguration.java`
  enables `GatewayRateLimitProperties`, creates the policy resolver, and creates the Redis
  coordinator only when `flashsale.gateway.rate-limit.enabled=true`; default-off startup does not
  require a Redis acquisition.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayHttpErrorWriter.java`
  now owns `writeRateLimitExceeded(...)`, applying `Retry-After` and `Cache-Control: no-store` only
  to successfully rendered Gateway-owned `RATE_LIMIT_EXCEEDED` responses. Successful expected 429s
  do not call the generic `GatewayErrorObservation`; serialization fallback removes quota headers
  and records the central `GATEWAY_INTERNAL_ERROR` once.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/CatalogCorrelationIdGlobalFilter.java`
  propagates one normalized/generated `X-Trace-Id` for matched public catalog `GET` route traffic at
  order `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2`.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilter.java`
  applies route-ID/method-scoped limiting at order `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 1`.
  Allowed decisions call downstream once; rejected decisions stop before downstream and render the
  owned 429. Coordinator fail-open handling is intentionally not implemented before T030/T034.
- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitContractTests.java`
  was completed with deterministic test identity/coordinator flows proving allowed and rejected
  paths share the correlation value established before limiting.
- `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayProxyPassThroughTests.java`
  was adjusted to preserve the downstream-owned 429 characterization while accounting for the
  pre-existing Spring Security `Cache-Control` header; the test still proves the limiter does not
  inject `Retry-After` or the exact owned quota cache-control contract into downstream 429s.

Implementation note:

- The Lua script suppresses sub-request-cost refill dust during high-concurrency same-bucket bursts.
  This keeps the approved 20/80 concurrency fixture deterministic while still allowing refill once
  enough scaled credit exists to cover the configured request cost.

### Focused verification

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=RedisTokenBucketRateLimiterTests,CatalogCorrelationIdGlobalFilterTests,GatewayRateLimitGlobalFilterTests,GatewayHttpErrorWriterTests,GatewayRateLimitContractTests,GatewayProxyPassThroughTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace
- Docker Desktop available to Testcontainers
- Testcontainers version: `1.21.4`
- Redis image: `redis:7.4-alpine`

Result:

- Exit code: `0`
- Reactor:
  - `flash-sale-engine`: SUCCESS
  - `api-gateway`: SUCCESS
- Maven result: BUILD SUCCESS
- Test result: `33` tests run, `0` failures, `0` errors, `0` skipped
- Completed suites:
  - `GatewayHttpErrorWriterTests`: `7` tests
  - `CatalogCorrelationIdGlobalFilterTests`: `5` tests
  - `GatewayRateLimitGlobalFilterTests`: `5` tests
  - `GatewayProxyPassThroughTests`: `6` tests
  - `GatewayRateLimitContractTests`: `3` tests
  - `RedisTokenBucketRateLimiterTests`: `7` tests

T016–T021 status:

- Completed for User Story 1.
- The limiter remains default-off for production routing because production direct-IP/HMAC identity
  is intentionally reserved for US2.
- Typed fail-open, P0 expiry-only normalization, observability metrics, Redis-down health behavior,
  and Compose activation remain reserved for US3/final tasks.

## MVP-local runnable shortcut — identity, wiring, small fail-open, and compose render

### Scope

This entry records a deliberately small self-project shortcut requested by the owner. It makes the
Gateway Redis rate limiter runnable locally without completing every remaining Feature 013 task.
It does not mark Feature 013 Verified and does not claim the full T022–T051 evidence set.

Implemented:

- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/HmacRateLimitBucketKeyFactory.java`
  creates `rl:k1:<64 lowercase hex>` Redis keys with HMAC-SHA-256 and length-prefixed input.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/DirectClientIpRateLimitIdentityResolver.java`
  resolves the direct socket IP, ignores `Forwarded`/`X-Forwarded-For`, and returns identity
  unavailable when no usable direct address exists.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitConfiguration.java`
  wires HMAC, direct-IP resolver, Redis coordinator, and the global filter only when
  `flashsale.gateway.rate-limit.enabled=true`.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitProperties.java`
  validates enabled HMAC secret as standard Base64 with at least 32 decoded bytes.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilter.java`
  fail-opens only typed `RateLimitCoordinatorException` outcomes and identity-unavailable outcomes.
- `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiter.java`
  uses the configured command timeout and maps direct recognized Redis connection/timeout/script
  failures to typed coordinator failures for the local fail-open path.
- `infra/docker/compose.yml` passes explicit Gateway rate-limit environment variables, points the
  Gateway at the existing root Redis service, and removes Gateway's inherited Compose startup
  dependency on Redis/PostgreSQL/Kafka.
- `infra/docker/.env.example` documents the default-off local variables.

Deferred:

- complete T022–T028 exhaustive identity/security matrix;
- T029/T033 P0 expiry-only normalization and rejection TTL safety;
- full US3 observability/health/load/final verification tasks.

### Focused Maven verification

Evidence command:

```powershell
.\mvnw.cmd -pl services/api-gateway -am '-Dtest=GatewayRateLimitPropertiesTests,HmacRateLimitBucketKeyFactoryTests,DirectClientIpRateLimitIdentityResolverTests,GatewayRateLimitGlobalFilterTests,RedisTokenBucketRateLimiterTests,GatewayHttpErrorWriterTests,GatewayRateLimitContractTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Environment:

- Java 21
- Spring Boot 3.x project
- Windows local workspace
- Docker Desktop available to Testcontainers
- Testcontainers version: `1.21.4`
- Redis image: `redis:7.4-alpine`

Result:

- Exit code: `0`
- Reactor:
  - `flash-sale-engine`: SUCCESS
  - `api-gateway`: SUCCESS
- Maven result: BUILD SUCCESS
- Test result: `61` tests run, `0` failures, `0` errors, `0` skipped

Covered local-MVP behavior:

- enabled secret validation rejects non-Base64/short secret and accepts a 32-byte Base64 secret;
- HMAC keys are deterministic, opaque, namespace-separated, and safe across concurrent calls;
- direct IPv4/IPv6 resolver ignores forwarded spoofing headers and skips missing direct identity;
- typed coordinator failure fail-opens downstream once with no quota headers;
- Redis token bucket, owned 429 writer, and US1 contract behavior remain green.

### Compose render smoke

Evidence command:

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml --profile apps config
```

Result:

- Exit code: `0`
- Rendered `api-gateway` includes:
  - `GATEWAY_RATE_LIMIT_ENABLED: "false"` by default;
  - `RATE_LIMIT_ENVIRONMENT: docker`;
  - `RATE_LIMIT_KEY_HMAC_SECRET: ""`;
  - `SPRING_DATA_REDIS_HOST: redis`;
  - `SPRING_DATA_REDIS_PORT: "6379"`;
  - no rendered `depends_on` for `api-gateway`.

Local activation reminder:

- copy `infra/docker/.env.example` to untracked `infra/docker/.env`;
- set `GATEWAY_RATE_LIMIT_ENABLED=true`;
- set `RATE_LIMIT_KEY_HMAC_SECRET` to standard Base64 generated from at least 32 random bytes;
- optionally set `PRODUCT_LIQUIBASE_ENABLED=true` when running Product empty-catalog schema locally.

### Secret and local-network hardening follow-up

The shared Compose topology now requires explicit `POSTGRES_PASSWORD` and `REDIS_PASSWORD` values,
protects Redis with `requirepass`, passes the Redis password to the Gateway, and binds PostgreSQL
and Redis host ports to loopback by default. The committed `.env.example` contains placeholders
only; real values belong in the ignored `infra/docker/.env` file.

This does not complete JWT signing or JWKS publication. Authentication is explicitly out of scope
for Feature 013 and requires its own approved authentication feature.
