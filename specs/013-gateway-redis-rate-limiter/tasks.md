---
description: "Risk-based implementation tasks for the Gateway Redis Rate Limiter"
---

# Tasks: Gateway Redis Rate Limiter

**Input**: Approved design artifacts from `/specs/013-gateway-redis-rate-limiter/`  
**Prerequisites**: `spec.md`, Accepted ADR 0004, both contracts, `plan.md`, `research.md`,
`data-model.md`, `quickstart.md`, and checklists  
**Task status**: Approved — artifact gate closed on 2026-07-23 by Gateway/Platform owner (user)  
**Approval gate**: The pre-approval `speckit-analyze` reported no unresolved HIGH/CRITICAL finding,
and T001 was completed by the human owner before any production-code task starts.

## Format: `[ID] [P?] [Story] Description (trace references)`

- **[P]**: Safe to execute in parallel after its stated prerequisite because files do not overlap.
- **[Story]**: Owning user story; omitted only for artifact/setup/cross-cutting tasks.
- **Evidence**: Actual red/pass command, environment, exit code, and result go to
  `specs/013-gateway-redis-rate-limiter/validation.md` during implementation. No evidence is
  pre-populated in this approved task list.

## Test and Validation Ordering

> Test-first is selected for new policy/secret validation, IP/HMAC isolation, integer token math,
> Redis Lua atomicity/TTL/concurrency, exact Gateway-owned 429/fallback headers, and typed fail-open
> behavior. Add the mapped test and record its expected failure before the corresponding
> implementation. Add downstream 429 pass-through as a characterization test before touching that
> boundary; it may pass at baseline and must stay passing. Documentation and declarative wiring follow
> approved risk-based ordering without artificial red tests. Required tests/evidence remain mandatory.

## Phase 1: Artifact Approval and Contract Readiness

**Purpose**: Close the human/architecture/contract gates before production changes.

- [x] T001 After pre-approval analysis is clean and the named owners explicitly approve `spec.md`, ADR 0004, both Feature 013 contracts, `plan.md`, and `tasks.md`, change the status fields from Draft to Approved and append approval-history entries in `specs/013-gateway-redis-rate-limiter/spec.md`, `specs/013-gateway-redis-rate-limiter/plan.md`, and `specs/013-gateway-redis-rate-limiter/tasks.md`; verify `.specify/feature.json` still targets Feature 013 and leave the gate closed if any reviewer has not approved (FR-001–026, NFR-001–006, INV-001–006)
- [x] T002 [P] Change ADR 0004 from Proposed to Accepted only after T001 approval and record approver/date in `docs/adr/0004-gateway-distributed-rate-limiter.md` (FR-004, FR-016–023, INV-002–006)
- [x] T003 [P] Change both Feature 013 contracts from Draft to Approved and add a non-destructive amendment pointer in `specs/012-gateway-rate-limit-contract/contracts/gateway-rate-limit-error-http.md` using `specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-http.md` and `specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-configuration.md` (FR-007–011, FR-015–021, NFR-002–005)
- [x] T004 [P] Reconcile approved delivery status, HTTP/header, and exact policy/configuration decisions in `docs/ratelimit/01-scope-and-status.md`, `docs/ratelimit/02-http-contract.md`, and `docs/ratelimit/03-policy-and-configuration.md` (FR-001–011, FR-015–021)
- [x] T005 [P] Reconcile approved integer-credit/result protocol, Redis state/TTL, lean Gateway package/dependency shape, HMAC/IP, and finite fail-open decisions in `docs/ratelimit/04-token-bucket-algorithm.md`, `docs/ratelimit/05-redis-lua-state.md`, `docs/ratelimit/06-gateway-architecture-and-build.md`, `docs/ratelimit/07-identity-and-security.md`, and `docs/ratelimit/08-failure-and-recovery.md` (FR-004–006, FR-012–023, INV-001–006)
- [x] T006 [P] Reconcile bounded telemetry, test/load, local runtime/Kubernetes deferral, and delivery status in `docs/ratelimit/09-observability.md`, `docs/ratelimit/10-testing-and-validation.md`, `docs/ratelimit/11-runtime-and-kubernetes.md`, `docs/ratelimit/12-delivery-slices-and-decisions.md`, and `docs/ratelimit/README.md` (FR-022–026, NFR-001–006)

**Checkpoint**: Approved scope, ADR, public/operator contracts, and living design docs agree. Production
work remains prohibited if any status or review is still pending.

---

## Phase 2: Foundational Types, Dependencies, and Default-Off Configuration

**Purpose**: Establish only the shared technical seams needed by all three stories.

- [x] T007 After T002–T006 complete, first capture `git status --short`, the relevant dirty Gateway/Feature 011–012 diff, and the pre-change `.\mvnw.cmd -pl services/api-gateway -am verify` result/test count in `specs/013-gateway-redis-rate-limiter/validation.md`; resolve or obtain owner acknowledgment for every pre-existing failure before modifying files, then add BOM-managed `spring-boot-starter-data-redis-reactive`, `spring-boot-starter-validation`, and test-scope `org.testcontainers:junit-jupiter` to `services/api-gateway/pom.xml` with no tracing/OTel/resilience/database dependency (FR-004, FR-015–016, FR-025–026)
- [x] T008 Add policy/configuration tests first for exact selector values, the 1–32-character environment regex, `p[1-9][0-9]{0,8}` state version, checked derivation and `2^53 - 1` boundary for every numeric script argument including `refillTokens`, derived `fullRefillMs <= 86_400_000`, disabled-without-secret behavior, and unique route-method selection; defer enabled Base64 decoding/redaction to T022 and record the expected red result in `specs/013-gateway-redis-rate-limiter/validation.md` from `services/api-gateway/src/test/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitPropertiesTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/RateLimitPolicyResolverTests.java` (FR-001–003, FR-015, FR-020–021)
- [x] T009 Implement immutable policy binding, exact identifier/enabled-policy validation, overflow-safe Java derivation and `2^53 - 1` validation of every numeric script argument including `refillTokens`, full-refill duration/24-hour bound and TTL, disabled secret optionality, and unique route-method selection in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitProperties.java`, `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitPolicy.java`, and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitPolicyResolver.java` until T008 passes; do not implement enabled secret decoding before T022 (FR-001–003, FR-015, FR-020–021)
- [x] T010 [P] Define the Redis-independent technical seams/models in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/DistributedRateLimiter.java`, `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitDecision.java`, `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitFailureType.java`, `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitCoordinatorException.java`, and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitIdentityResolver.java` (FR-002, FR-006, FR-014, FR-017–019, INV-001–004)
- [x] T011 Add the exact default-disabled service-owned policy, 50 ms timeout, `rl` prefix, `p1` state version, `server.forward-headers-strategy=none`, `management.health.redis.enabled=false`, and acquisition percentiles `0.5,0.95,0.99` to `services/api-gateway/src/main/resources/application.yml`; keep code-owned `k1` out of operator properties and do not enable tracing or change routes/security (FR-001–003, FR-012, FR-015–016, FR-020–026, NFR-006)

**Checkpoint**: The module has approved dependencies, pure edge policy/seams, and a disabled-by-default
configuration that does not require Redis or a secret.

---

## Phase 3: User Story 1 — Protect Public Catalog Reads (Priority: P1) 🎯 MVP

**Goal**: Share one atomic caller balance across Gateway replicas, forward allowed catalog GET once,
and stop an exhausted request with the owned 429 before downstream.

**Independent Test**: With a test identity and Redis 7.4, allowed requests invoke downstream once;
the first insufficient request invokes downstream zero times; the 100-call/three-client fixture yields
exactly 20 allowed and 80 rejected.

**Trace set**: FR-001–011, FR-015–016, FR-019–020; NFR-001–003; INV-001–005; US1 scenarios 1–5.

### Risk-mapped tests for User Story 1

- [x] T012 [US1] Add real Redis normal/missing-state token/refill/allowed/rejected/TTL/script-cache/concurrency tests first using the exact test-only 2-second timeout, including canonical unsigned-decimal validation, non-exponent persistence/result formatting, exact remaining credit, every numeric-argument boundary including `refillTokens`, and `2^53 - 1` round-trip; record expected red evidence in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiterTests.java` and `specs/013-gateway-redis-rate-limiter/validation.md` (FR-003–005, FR-015, FR-019–020, NFR-001, INV-002–003)
- [x] T013 [US1] Add tests first for `CatalogCorrelationIdGlobalFilter` at exact `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2` and `GatewayRateLimitGlobalFilter` at exact `- 1`, route/method ownership, valid normalized and missing/invalid generated exchange-cached `X-Trace-Id`, disabled-limiter propagation, strict admin-boundary non-regression, bypass/allowed/rejected/exactly-once behavior, and raw-path non-activation in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/filter/global/CatalogCorrelationIdGlobalFilterTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilterTests.java`; record expected red evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-001–002, FR-006–008, FR-026, INV-001–002)
- [x] T014 [US1] Before changing the writer/filter boundary, add the downstream-429 baseline characterization with byte-for-byte body, sentinel-header pass-through and no Gateway header injection in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayProxyPassThroughTests.java`, recording its actual baseline result; also add test-first exact owned-429, same-value trace, retry rounding, no-store, forbidden-header, zero-downstream, and log-capture assertions in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayHttpErrorWriterTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitContractTests.java`, proving a successful expected 429 does not call/log `GatewayErrorObservation`, serialization fallback has no quota headers and observes the central 500 exactly once, and other Gateway errors retain Feature 011 behavior; record red/pass evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-006–011, FR-025–026, NFR-002–003, INV-001, INV-005)

### Implementation for User Story 1

- [x] T015 [P] [US1] Implement the O(1), division-free normal/missing-state Redis `TIME` transition using Java-precomputed capacity credit, cost credit and full-refill duration; accept only canonical unsigned decimals up to `2^53 - 1`, persist/return them with `string.format("%.0f", value)`, add allowed persistence/`PEXPIRE`, rejected no-write/no-refresh, defensive `INVALID_ARGUMENT`/`INVALID_STATE` tuples, and no P0 expiry mutation yet in `services/api-gateway/src/main/resources/redis/token_bucket.lua` until T012 passes (FR-004–005, FR-019–020, INV-002–003)
- [x] T016 [US1] Implement singleton-script execution, strict normal/error tuple and canonical credit/result bounds, and exact Java `Math.ceilDiv` retry calculation in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiter.java` until T012's normal/cache/concurrency cases pass; leave 50 ms timeout/exception translation and P0 recovery to T029→T033 (FR-004–006, FR-019–020, NFR-001)
- [x] T017 [US1] Add conditional properties/script/coordinator wiring without an eager Redis call in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitConfiguration.java` after T016 provides the Redis adapter (FR-015–016, FR-022, FR-026)
- [x] T018 [P] [US1] Add one typed rate-limit rendering operation to `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayHttpErrorWriter.java` so headers apply only to a successfully rendered `RATE_LIMIT_EXCEEDED`, that expected 429 bypasses generic `GatewayErrorObservation` WARN, serialization fallback removes quota headers and records the central 500 exactly once, and other writer operations preserve Feature 011 behavior (FR-007–010, FR-025–026, NFR-002)
- [x] T019 [US1] After T017 and T018 join, implement route-ID/method-scoped correlation at exact order `RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 2` in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/CatalogCorrelationIdGlobalFilter.java`, then stable route-ID plus method limiting at exact order `- 1`, allowed exactly-once chain and rejected zero-chain behavior in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilter.java`; do not add coordinator fail-open before T030→T034 (FR-001–011, FR-026, INV-001–005)
- [x] T020 [US1] Complete `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitContractTests.java` with an enabled test configuration and deterministic test identity/coordinator so US1 is independently demonstrable without trusting forwarding headers or requiring US2's production resolver; prove allowed/rejected paths share the correlation value established by T019 (FR-001–011, FR-026, NFR-002, INV-001–005)
- [x] T021 [US1] Run the US1 focused `RedisTokenBucketRateLimiterTests`, `CatalogCorrelationIdGlobalFilterTests`, `GatewayRateLimitGlobalFilterTests`, `GatewayHttpErrorWriterTests`, `GatewayProxyPassThroughTests`, and `GatewayRateLimitContractTests` commands from `specs/013-gateway-redis-rate-limiter/quickstart.md` sections 6–7 and append actual command/environment/exit/result evidence to `specs/013-gateway-redis-rate-limiter/validation.md` (FR-001–011, FR-026, NFR-001–003, INV-001–005)

**Checkpoint**: Atomic coordination and owned rejection are independently demonstrated. The feature
must remain default-off until User Stories 2 and 3 complete.

---

## Phase 4: User Story 2 — Preserve HTTP and Caller Boundaries (Priority: P2)

**Goal**: Resolve one safe direct-IP bucket, protect it with the approved HMAC contract, and preserve
all response/identity/downstream ownership boundaries.

**Independent Test**: Direct/spoofed/missing identity inputs plus owned/downstream 429 responses prove
stable opaque separation, exact headers/body, pass-through, and zero raw/opaque identity exposure
outside the Redis key.

**Trace set**: FR-007–015, FR-020–021, FR-026; NFR-002–005; INV-001, INV-003–005; US2 scenarios 1–7.

### Risk-mapped tests for User Story 2

- [ ] T022 [US2] Add tests first for enabled standard-Base64 ≥32-byte decoding, legal padding/invalid alphabets, missing/short secrets, startup-failure output that never echoes a distinctive secret sentinel, golden and concurrent multi-identity HMAC determinism/isolation, changed-secret namespace separation, environment/policy/state/type separation, IPv4/IPv6/mapped-address normalization, forwarding-header spoof, and missing IP in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitPropertiesTests.java`, `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/HmacRateLimitBucketKeyFactoryTests.java`, and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/DirectClientIpRateLimitIdentityResolverTests.java`; require a fresh initialized `Mac` per derivation and record expected red evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-012–015, FR-020–021, NFR-005, INV-003)
- [ ] T023 [US2] Extend enabled application-context/HTTP tests first for exact `server.forward-headers-strategy=none` preventing Boot pre-resolver forwarded-origin rewriting, missing identity bypass, no Redis call, no quota headers, exact 64-hex Redis key exposure boundary, and no secret/raw identity in captured response/log data in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitContractTests.java`; record expected red evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-012–015, FR-021, NFR-004–005)
- [ ] T024 [US2] Re-run and extend the T014 downstream-429 characterization under T020's enabled deterministic limiter configuration, before production identity implementation, in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayProxyPassThroughTests.java`; preserve byte-for-byte body, sentinel headers, and absence of Gateway header injection, require the test to remain passing through T027, and record the actual regression result in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-010–011, NFR-003, INV-005)

### Implementation for User Story 2

- [ ] T025 [US2] Implement versioned four-byte-length-prefixed HMAC-SHA-256 with full lowercase hex output and no secret/raw identity rendering in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/HmacRateLimitBucketKeyFactory.java` (FR-013, FR-020–021, NFR-005, INV-003)
- [ ] T026 [US2] Implement direct socket byte normalization, IPv4-mapped IPv6 normalization, forwarding-header distrust, and explicit identity-unavailable outcome in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/DirectClientIpRateLimitIdentityResolver.java` (FR-012–014, NFR-004–005)
- [ ] T027 [US2] Implement enabled secret decoding/validation with safe non-echoing messages, then wire the validated secret into production identity/filter beans in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitConfiguration.java` and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitProperties.java` until T022–T024 and all existing writer/proxy tests pass (FR-012–015, FR-020–021, FR-026)
- [ ] T028 [US2] Run the focused security/HTTP/pass-through commands from `specs/013-gateway-redis-rate-limiter/quickstart.md` sections 5 and 7 and append actual evidence to `specs/013-gateway-redis-rate-limiter/validation.md` (FR-007–015, FR-020–021, NFR-002–005)

**Checkpoint**: Caller identity and HTTP/downstream boundaries are complete without exposing raw or
stable pseudonymous identity outside the Redis key.

---

## Phase 5: User Story 3 — Degrade Safely and Diagnose the Limiter (Priority: P3)

**Goal**: Keep catalog traffic available with bounded delay and distinct safe signals when identity
or Redis coordination cannot be evaluated.

**Independent Test**: Missing identity, Redis connection failure, 50 ms timeout, invalid/future/no-TTL
state, invalid result, and script-cache loss demonstrate the approved outcomes, one/no downstream
call as applicable, no blind retry/false 429, bounded telemetry, and stable readiness.

**Trace set**: FR-014–026; NFR-004–006; INV-001–006; US3 scenarios 1–8.

### Risk-mapped tests for User Story 3

- [ ] T029 [US3] Extend real Redis tests with exact test-only 2-second timeout for wrong-type/partial/non-canonical/non-numeric/future/no-expiry state, safe positive finite TTL, and structurally valid state whose short positive TTL cannot cover the rejection deficit; prove one `PEXPIRE(stateTtlMs)` only when `PTTL=-1`, unchanged type/fields, decreasing non-refreshed expiry on later still-invalid calls, otherwise-valid no-TTL hash resumption, preservation of an existing longer finite TTL, short-TTL `INVALID_STATE` without mutation, and state-version separation in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiterTests.java`; add deterministic Reactor-virtual-time adapter tests for exact 50 ms, one subscription/no retry, malformed tuples, exact direct timeout/connection/script/serialization mapping, `RedisSystemException` only with an immediate recognized Lettuce timeout/connection/execution cause, `QueryTimeoutException` only with immediate `RedisCommandTimeoutException`, near-miss propagation, and no message parsing/broad catch in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiterFailureTests.java`; record expected red evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-008, FR-016–020, NFR-004, INV-002–004)
- [ ] T030 [US3] Add filter failure tests first for identity unavailable, finite typed timeout/connection/state/result/script/serialization failure, acquisition-local recovery before the terminal branch, no broad `Throwable`/unexpected/downstream/writer catch, NPE/IllegalState propagation, exactly one downstream call, no retry/header, and no limiter 503 in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitFailureTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayErrorCodeTests.java`; record expected red evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-014, FR-016–019, NFR-004, INV-001, INV-004–005)
- [ ] T031 [US3] Add bounded counter/Observation/reactive-lifecycle/acquisition-percentile and no-per-request-log tests first in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/observability/GatewayRateLimitObservationTests.java`, including absence of IP, secret, trace tag, digest/key, raw path/query, exception message, and direct OTel type; record expected red evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-024–025, NFR-004–006)
- [ ] T032 [US3] Add disabled-limiter and Redis-down aggregate-health/liveness/readiness/regression tests first in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitDisabledTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayRateLimitFailureTests.java`; verify no startup/eager Redis call and record expected red evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-015, FR-017, FR-025–026, SC-005)

### Implementation for User Story 3

- [ ] T033 [US3] After T029 is red, add only the P0 expiry-normalization and rejection-expiry-safety branches to `services/api-gateway/src/main/resources/redis/token_bucket.lua`—`PEXPIRE(stateTtlMs)` when wrong-type/hash state has `PTTL=-1`, no type/field repair/reset/delete/marker, current `INVALID_STATE`, later still-invalid no-refresh, otherwise-valid hash resumption, and division-free proof that finite TTL covers the deficit/refill horizon before `REJECTED`—and implement the exact 50 ms Reactor timeout plus predicate-scoped direct/immediate-cause translation (including `QueryTimeoutException` only with immediate `RedisCommandTimeoutException`) for only `TIMEOUT`, `CONNECTION`, `INVALID_STATE`, `INVALID_RESULT`, `SCRIPT`, and `SERIALIZATION` without message parsing, generic base mapping, catch-all, arbitrary cause walking, or application retry in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiter.java` and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/RateLimitCoordinatorException.java` until T029 passes (FR-008, FR-016–020, INV-002–004)
- [ ] T034 [US3] Add only the typed coordinator fail-open and identity-unavailable branches to `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilter.java`; leave unexpected/writer/downstream errors to existing handling (FR-014, FR-016–019, INV-001, INV-004–005)
- [ ] T035 [US3] Implement bounded Micrometer counters, subscription-scoped Observation, and safe lifecycle/configuration logs with no limiter-specific per-request INFO/WARN logs in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/observability/GatewayRateLimitObservation.java` and integrate it into `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilter.java` and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/redis/RedisTokenBucketRateLimiter.java` (FR-014, FR-017, FR-024–026, NFR-004–005)
- [ ] T036 [US3] Finalize exact `management.health.redis.enabled=false`, acquisition-percentile, forwarded-header-none, and no-OTel-runtime declarative behavior in `services/api-gateway/src/main/resources/application.yml` until T031–T032 and existing Actuator regressions pass (FR-012, FR-017, FR-025–026, NFR-006)
- [ ] T037 [US3] Wire Gateway to the existing Redis and explicit enable/environment/secret variables, override its inherited Compose `depends_on` with `{}`, and map Product's deterministic empty-catalog opt-in exactly as `SPRING_LIQUIBASE_ENABLED: ${PRODUCT_LIQUIBASE_ENABLED:-false}` without adding topology in `infra/docker/compose.yml`, `infra/docker/.env.example`, and `infra/docker/README.md` (FR-015, FR-021–023, INV-006)
- [ ] T038 [US3] Run the failure/health/observability commands from `specs/013-gateway-redis-rate-limiter/quickstart.md` section 7 and append actual evidence to `specs/013-gateway-redis-rate-limiter/validation.md` (FR-014–026, NFR-004–005, INV-001–006)

**Checkpoint**: All approved failure branches are bounded, observable, non-retrying, and compatible;
Redis outage does not silently turn the public Gateway unready.

---

## Phase 6: Cross-Cutting Verification and Release Readiness

- [ ] T039 [P] Remove only now-obsolete markers `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/.gitkeep`, `services/api-gateway/src/main/java/com/philia/flashsale/gateway/ratelimit/.gitkeep`, and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/observability/.gitkeep`; preserve unused ADR-reserved `services/api-gateway/src/main/java/com/philia/flashsale/gateway/routing/.gitkeep`, `services/api-gateway/src/main/java/com/philia/flashsale/gateway/faulttolerance/.gitkeep`, and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/route/.gitkeep` markers (ADR 0003, FR-026)
- [ ] T040 [P] Create the measurement-only one-caller steady/burst scenario for exact `GET /api/v1/catalog/products?page=0&size=20`, accepting only 200/429 and requiring both in the burst, with no outage simulation or production throughput threshold in `load-tests/k6/scenarios/gateway-rate-limit.js` (NFR-006, SC-001)
- [ ] T041 Document exact local k6 binary/version, empty-catalog Product migration setup, one-caller/60-second TTL reset followed by Gateway recreation for a fresh `MeterRegistry`, health-only readiness with no pre-k6 catalog request, HTTP 200/429 checks, Prometheus acquisition quantile scrape, Redis INFO capture, report fields, and non-SLO interpretation in `load-tests/k6/README.md` without renaming/deleting the pre-existing empty `load-tests/k6/scenerios/` directory unless separately approved (NFR-006)
- [ ] T042 Run `speckit-converge` against the implemented code before final verification; append only genuine missing work to `specs/013-gateway-redis-rate-limiter/tasks.md`, stop for explicit approval of every appended task/artifact delta, and complete approved additions before T043 (governance gate)
- [ ] T043 Validate the one-Redis topology and rendered Gateway `depends_on: {}` with `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config`; stop both Gateway and Redis, cold-start Gateway with `--no-deps`, prove it becomes healthy without Redis, then restore Redis plus the normal healthy Gateway/Product topology before T045 and record every command/output/exit in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-017, FR-022–023, INV-006)
- [ ] T044 Run a dependency/sensitive-data review of `services/api-gateway/pom.xml` and all Feature 013 production paths, recording that no OTel/resilience/database dependency, shared mutable `Mac`, broad fail-open catch, or raw identity/secret/digest telemetry was introduced in `specs/013-gateway-redis-rate-limiter/validation.md` (FR-013, FR-021, FR-024–026, NFR-005)
- [ ] T045 With Docker/Testcontainers available, run `.\mvnw.cmd -pl services/api-gateway -am verify` and record Docker version, reactor, test count, Redis image, exit, and CI/PR evidence in `specs/013-gateway-redis-rate-limiter/validation.md` (NFR-001–005, SC-001–005)
- [ ] T046 With Docker/Testcontainers still available, run `.\mvnw.cmd clean verify` and record Docker version plus full-reactor exit/test evidence in `specs/013-gateway-redis-rate-limiter/validation.md`; do not close the feature while any required test fails (FR-026, SC-005)
- [ ] T047 After the bucket has had a fresh 60-second window (or an uncommitted fresh local secret is configured), recreate Gateway to obtain a fresh `MeterRegistry`, wait on `/actuator/health` only without consuming the catalog bucket, then run `k6 run -e BASE_URL=http://localhost:8080 load-tests/k6/scenarios/gateway-rate-limit.js`, scrape `gateway_rate_limit_acquire_seconds` quantiles from `/actuator/prometheus`, and run the Redis INFO commands from `specs/013-gateway-redis-rate-limiter/quickstart.md`; separately record HTTP throughput/p50/p95/p99/status accuracy, limiter p50/p95/p99, Redis signals, tool versions, transport/setup errors, environment, and non-threshold interpretation in `specs/013-gateway-redis-rate-limiter/validation.md` (NFR-006, SC-001)
- [ ] T048 Update implemented/verified status and Feature 013 links only after evidence passes in `docs/technology/technology-problem-map.md`, `docs/ratelimit/01-scope-and-status.md`, `docs/ratelimit/06-gateway-architecture-and-build.md`, and `docs/ratelimit/README.md`; do not claim OpenTelemetry/Kubernetes/production quota completion (FR-003, FR-025–026)
- [ ] T049 Complete the FR/NFR/INV/SC-to-task-to-evidence audit and reviewer sign-off in `specs/013-gateway-redis-rate-limiter/validation.md`, including justified N/A database/Kafka/Kubernetes checks (FR-001–026, NFR-001–006, INV-001–006, SC-001–005)
- [ ] T050 Re-run `speckit-analyze` read-only against `specs/013-gateway-redis-rate-limiter/spec.md`, `specs/013-gateway-redis-rate-limiter/plan.md`, and `specs/013-gateway-redis-rate-limiter/tasks.md`; if a HIGH/CRITICAL finding requires an edit, obtain explicit approval, return to T042, and rerun every affected T043–T049 gate before completion (governance gate)
- [ ] T051 Only after T043–T050 pass and required reviewers sign `specs/013-gateway-redis-rate-limiter/validation.md`, change `specs/013-gateway-redis-rate-limiter/spec.md`, `specs/013-gateway-redis-rate-limiter/plan.md`, and `specs/013-gateway-redis-rate-limiter/tasks.md` from Approved to Verified; change `specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-http.md` and `specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-configuration.md` from Approved to Verified; keep `docs/adr/0004-gateway-distributed-rate-limiter.md` Accepted and append final verification history/sign-off (constitutional completion gate)

## Dependencies and Execution Order

```text
T001 human approval/status update
  └─ T002–T006 ADR/contract/living-doc synchronization
       └─ T007–T011 foundational dependencies and technical seams
            └─ US1 T012–T021 (atomic coordinator + owned rejection)
                 └─ US2 T022–T028 (production identity + compatibility/security)
                      └─ US3 T029–T038 (typed failure + observability/operations)
                           └─ T039–T041 preparation
                                └─ T042 converge/approval checkpoint
                                     └─ T043–T050 final evidence and analysis
                                          └─ T051 final status
```

- T001 is complete; approval came from the Gateway/Platform owner (user) on 2026-07-23.
- The formal read-only `speckit-analyze` is a precondition to T001, not a post-code substitute.
- T002–T006 may run in parallel after T001 because they own disjoint files; all must finish before
  T007 starts production setup.
- T008 must record expected failure before T009; T022 before T025–T027; T029–T032 before T033–T037.
- T012's real Redis normal/concurrency tests precede Lua/adapter implementation. T013–T014 precede
  correlation/filter/writer behavior; T014 owns the pre-change downstream-429 baseline, and T024
  reruns/extends it after US1. T019 cannot start until parallel T018 and sequential T017 have joined.
- US2 depends on US1 because it replaces test identity with the production HMAC/IP boundary and proves
  complete HTTP compatibility.
- US3 depends on US1/US2 because fail-open must reuse the already verified success/rejection and
  identity boundaries without adding a broad catch.
- Root infrastructure changes remain under `infra/docker`; service-owned configuration/script/tests
  remain in `services/api-gateway`.
- T051 cannot precede actual evidence and review; checkboxes alone are not completion proof.
- T042 precedes final validation; any approved convergence change forces all affected T043–T049
  evidence to be regenerated before T050/T051.

## Parallel Execution Examples

### After T001

```text
Agent A: T002 ADR acceptance metadata
Agent B: T003 contracts + Feature 012 pointer
Agent C: T004 HTTP/policy living docs
Agent D: T005 or T006 remaining living docs
```

### User Story 1 after tests are red

```text
Agent A: T015 Lua resource
Agent B: T018 central writer operation
Main sequence: T016 Redis adapter -> T017 wiring; join T018 -> T019 correlation/filter -> T020 contract completion
```

### Final cross-cutting preparation

```text
Agent A: T039 marker cleanup
Agent B: T040 k6 scenario
Then T041 documents the completed scenario before convergence T042 and validation T043–T050.
```

## Implementation Strategy

1. **Approval first**: review approved artifacts and confirm T001 is complete; never infer approval
   from file existence alone.
2. **MVP correctness first**: finish US1 with real Redis atomicity and owned rejection while keeping
   runtime disabled.
3. **Boundary hardening**: finish US2 identity/HMAC and downstream/HTTP compatibility before enabling
   any local traffic.
4. **Operational safety**: finish US3 typed fail-open, health, metrics/Observation, safe lifecycle
   logging, and root Compose wiring.
5. **Evidence before status**: run Compose/module/full/load/audit/convergence gates and only then mark
   Verified.

## Requirement Coverage Index

| Requirement | Primary task coverage |
|-------------|-----------------------|
| FR-001 | T008–T009, T013, T019–T020 |
| FR-002 | T009–T010, T013, T019 |
| FR-003 | T008–T009, T011–T012, T015–T016 |
| FR-004 | T012, T015–T016, T045 |
| FR-005 | T012, T015–T016 |
| FR-006 | T010, T013–T014, T016, T018–T020 |
| FR-007 | T003–T004, T014, T018, T020 |
| FR-008 | T003–T004, T013–T014, T018–T020, T029, T033 |
| FR-009 | T003–T004, T014, T018, T020 |
| FR-010 | T003–T004, T014, T018, T020, T024 |
| FR-011 | T003–T004, T014, T019–T020, T024 |
| FR-012 | T022–T023, T026–T028 |
| FR-013 | T022–T023, T025, T031, T044 |
| FR-014 | T010, T022–T023, T026–T028, T030, T034–T035 |
| FR-015 | T008–T009, T011, T017, T022, T027, T032, T037 |
| FR-016 | T010–T012, T016–T017, T029–T030, T033–T034 |
| FR-017 | T010, T029–T038 |
| FR-018 | T010, T030, T034 |
| FR-019 | T010, T012, T015–T016, T029–T034 |
| FR-020 | T008–T009, T011–T012, T015–T016, T022, T025, T027, T029, T033 |
| FR-021 | T008–T009, T011, T022–T023, T025, T027, T037, T044 |
| FR-022 | T017, T037, T043 |
| FR-023 | T011, T037, T043, T049 |
| FR-024 | T031, T035, T044 |
| FR-025 | T006–T007, T011, T031–T036, T044, T048 |
| FR-026 | T006–T007, T011, T013–T014, T017–T021, T027, T031–T039, T044–T049 |
| NFR-001 | T012, T015–T016, T021, T045 |
| NFR-002 | T014, T018–T021, T045 |
| NFR-003 | T014, T024, T028, T045 |
| NFR-004 | T022–T023, T029–T035, T038, T045 |
| NFR-005 | T022–T023, T025–T028, T031, T035, T044–T045 |
| NFR-006 | T006, T011, T031, T036, T040–T041, T047, T049 |
| INV-001 | T010, T013–T014, T019–T021, T030, T034, T038 |
| INV-002 | T010, T012–T013, T015–T016, T029, T033 |
| INV-003 | T012, T015–T016, T022, T025, T029 |
| INV-004 | T010, T029–T035, T038 |
| INV-005 | T019–T021, T024, T030, T034 |
| INV-006 | T005–T006, T037–T038, T043, T049 |
| SC-001 | T012, T021, T040, T045, T047 |
| SC-002 | T014, T020–T021, T024, T028, T045 |
| SC-003 | T029–T038, T045 |
| SC-004 | T022–T028, T031, T035, T044–T045 |
| SC-005 | T032, T036, T045–T046 |

## Evidence Record

Actual evidence is added during implementation in `validation.md`; this approved task list
intentionally contains no fabricated PASS row.

| Task | Command/environment | Result | Evidence link/output | Date/owner |
|------|---------------------|--------|----------------------|------------|
| Pending | Pending implementation execution | NOT RUN | `validation.md` will be created by the first test-first task | Pending |

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-07-22 | Draft task graph created from Feature 013 design artifacts | Codex | Pending Gateway/Platform owner review | Draft |
| 2026-07-23 | Approved task graph and completed T001-T003 artifact gate after explicit Feature 013 artifact approval | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T004-T006 living documentation synchronization for approved Feature 013 decisions | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T007 dependency setup and recorded baseline/post-change Gateway verification evidence | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T008 expected-red policy/configuration tests before production implementation | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T009 immutable policy/configuration implementation and focused green verification | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T010-T011 foundational rate-limit seams and default-off declarative Gateway configuration | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T012 expected-red real Redis token-bucket correctness/concurrency tests before Lua/adapter implementation | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T013 expected-red catalog correlation and rate-limit filter tests before filter implementation | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T014 expected-red Gateway-owned 429 writer/contract tests and downstream 429 characterization before writer/filter implementation | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T015 Redis Lua token-bucket script resource before Java adapter implementation | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-23 | Completed T016-T021 US1 Redis adapter, default-off wiring, owned 429 writer, correlation/filter behavior, contract completion, and focused verification | Codex | Gateway/Platform owner (user) | Approved |

## Notes

- Do not introduce a limiter 503, second Redis, trusted-proxy parsing, user/API-key policy,
  OpenTelemetry runtime, dashboard/alert, Kubernetes manifest, Kafka, database, or Product behavior.
- T007 pulled forward only `management.health.redis.enabled=false` from T011 to keep the newly added
  Redis dependency default-off and preserve local `/actuator/health`; T011 remains open for the rest
  of the approved foundational configuration.
- Do not catch broad exceptions in the fail-open branch or call downstream more than once.
- Do not change quota/TTL/secret/state-version semantics without updating and re-approving the
  governing spec, contracts, ADR, plan, and tasks.
- Preserve dirty Feature 011/012 Gateway work; touch baseline files only where tasks explicitly name
  the compatible amendment/regression change.
