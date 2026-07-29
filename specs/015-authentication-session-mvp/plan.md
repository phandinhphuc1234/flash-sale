# Implementation Plan: Authentication Session MVP

**Branch/Feature ID**: `015-authentication-session-mvp`
**Date**: 2026-07-25
**Spec**: [spec.md](spec.md)
**Status**: Approved

## Architecture Amendment: Feature-oriented package layout

The original layer-first package paths are replaced by a feature-oriented layout for navigation.
The refactor is mechanical and does not change public contracts, persistence semantics, security
claims, dependencies, or configured-deployment behavior. Conditional composition guards may keep
infrastructure-dependent adapters out of dependency-free test/bootstrap contexts. The accepted decision is recorded in
[ADR 0007](../../docs/adr/0007-feature-oriented-authentication-packages.md).

```text
authentication/
├── account/{domain,application,adapter}
├── session/{domain,application,adapter}
├── security/{password,token,jwks}
├── throttle/redis
├── cleanup/{application,adapter/in/scheduling}
├── websupport/{error,filter,context}
├── observability/
└── configuration/
```

Within every feature, source dependencies remain `adapter -> application -> domain`; configuration
may wire adapters and application services. HTTP DTOs, JPA entities, Redis types, Nimbus types, and
Spring Security types remain at their respective boundaries.

## Summary

Extend the verified JWT/JWKS foundation into a first-party browser Authentication MVP. Add
authentication-owned PostgreSQL account/session/refresh state, Argon2id password proof, RS256 access
token issuance, opaque single-use refresh rotation, reuse detection, session logout, and the approved
Redis abuse controls. Add only the required Gateway routes/security/trace/rate-limit extensions.

The Authentication Service follows the repository's Clean/Hexagonal direction:

```text
web/scheduler -> input port -> application use case -> domain
                                                -> output port <- JPA/Redis/JWT/password adapter

adapter -> application -> domain
configuration -> adapter + application
```

No production code is authorized by this plan until the plan, ADR, and a generated `tasks.md` are
human-approved.

## Technical Context

**Language/Version**: Java 21

**Framework**: Spring Boot 3.5.16; Spring MVC with virtual threads for authentication-service;
Spring Cloud Gateway WebFlux remains reactive

**Existing dependencies retained**: `spring-boot-starter-web`, Actuator,
`micrometer-registry-prometheus` runtime, Liquibase, `spring-security-oauth2-jose`

**New authentication-service production dependencies**:

- `spring-boot-starter-validation` — HTTP request-shape validation;
- `spring-boot-starter-data-jpa` — authentication-owned persistence adapters;
- `spring-boot-starter-data-redis` — blocking Redis adapter on MVC virtual threads;
- `spring-boot-starter-security` — password/security filter-chain support;
- `spring-boot-starter-oauth2-resource-server` — validate logout-all bearer JWT locally;
- `org.postgresql:postgresql` runtime — `auth_db` driver;
- `org.bouncycastle:bcprov-jdk18on:1.78.1` — required by Spring Security Argon2 encoder.

**New authentication-service test dependencies**:

- `spring-security-test`;
- `spring-boot-testcontainers`;
- `org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql`;
- Testcontainers `GenericContainer` for real Redis tests (no specialized Redis module).

**Not added**: Spring Authorization Server, MapStruct, Kafka, outbox, distributed-lock library,
another JWT library, `common-web`, Micrometer Tracing bridge, OpenTelemetry SDK/exporter, or a second
Redis instance.

**Storage**: authentication-owned PostgreSQL `auth_db`; existing shared platform Redis only for
expiring abuse-control state

**Testing**: JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, Testcontainers PostgreSQL/Redis,
Gateway WebTestClient and existing proxy harness

**Target platform**: local Docker Compose first; independently deployable Linux container and future
Kubernetes Service/DNS deployment

**Performance goals**: no public latency SLO was approved. Benchmark Argon2 verification on the
target VPS and tune toward Spring Security's adaptive-hash guidance without going below the approved
19 MiB/2-iteration/parallelism-1 preset. Measure login/refresh latency and Gateway Redis acquisition;
do not turn the measurement into an unapproved business guarantee.

**Constraints**:

- access 15 minutes, refresh inactivity 7 days, session absolute 30 days, expired-record retention
  30 days;
- login 10/min/direct-IP, refresh 30/min/direct-IP, identifier failures 5/15 minutes then 15-minute
  cooldown;
- strict single-use refresh and strict reuse compromise;
- `SameSite=Lax` plus exact trusted Origin/Referer;
- no raw password/token/cookie/private-key logging or persistence;
- Feature 014 issuer/audience/`kid`/`authorities` contract cannot drift.

**Scale/Scope**: personal-project MVP with multiple service/Gateway replicas still supported by
PostgreSQL row locks and shared Redis. One active RSA signing key; key-rotation automation deferred.

## Constitution Check — Before Design

| Gate | Result | Evidence/rationale |
|------|--------|--------------------|
| Specification traceability | PASS | Q1–Q3 approved; no clarification markers; US1–US4 map to FR/INV/HTTP outcomes |
| Independent service/data ownership | PASS | Auth owns `auth_db`, migrations, JPA models, runtime config, tests; no cross-service DB/shared domain |
| Controlled ingress/discovery | PASS | New public paths route only through api-gateway; service URL uses platform DNS |
| Contract first | PASS | [authentication-http.md](contracts/authentication-http.md) exists before implementation; no Kafka contract |
| Durable truth/Redis | PASS | PostgreSQL owns accounts/sessions/refresh; Redis is expiring abuse state only |
| Reliable messaging | N/A | No event publication or consumer; no outbox required |
| Observability | PASS | Existing Actuator/Prometheus auto-configuration retained; trace/log/metric design below |
| Root infrastructure ownership | PASS | Compose/secret mounts remain under `infra/docker`; migrations/config remain in Auth service |
| Layered verification | PASS | Unit, integration, contract, concurrency/burst, Maven, Compose checks planned; broad load rationale below |
| Architecture decision | PASS | [ADR 0006](../../docs/adr/0006-authentication-session-and-refresh-rotation.md) is Accepted; implementation still waits for approved `tasks.md` |

No constitutional exception is requested.

## Risk Classification

The Flash Sale risk profile is mandatory because this feature handles passwords, signing private
keys, long-lived browser credentials, authorization claims, session revocation, Redis failure modes,
TTL/retention, and concurrent single-use rotation.

Primary risks:

- credential leakage or weak password hashing;
- JWT claim/authority drift breaking existing consumers;
- two successful refresh rotations from one credential;
- false logout success after persistence failure;
- account enumeration or privilege assignment through register/login errors;
- CSRF on cookie-backed refresh/logout;
- bypass/denial during Redis failure;
- status/error ownership drift between Gateway and Auth.

## Context and Service Ownership

| Concern | Owner | Source of truth/contract |
|---------|-------|--------------------------|
| Account identity/password/role/status | authentication-service | `auth_db.users` |
| Device/browser session lifecycle | authentication-service | `auth_db.user_sessions` |
| Refresh rotation/reuse/revocation | authentication-service | `auth_db.refresh_tokens` |
| Access-token signing/JWKS | authentication-service | deployment key pair + Feature 014 |
| Access-token validation at edge | api-gateway | Feature 014 local resource server |
| Access-token validation/business authorization | each resource service | Feature 014 + owning service policy |
| Direct-IP quota | api-gateway | existing Redis token bucket/ADR 0004 |
| Login-identifier abuse state | authentication-service | expiring Redis keys; never durable truth |
| Public HTTP error before proxy | api-gateway | Feature 011/013 contracts |
| Authentication business HTTP response | authentication-service | Feature 015 HTTP contract |

User profile and all commerce data remain outside Auth. Public administrator provisioning remains
out of scope; tests may create an administrator fixture directly in an isolated test database.

## Architecture and Hexagonal Mapping

### Capability map

| Capability | Driving adapter | Input port/use case | Domain responsibility | Output capabilities | Driven adapters |
|------------|-----------------|---------------------|-----------------------|--------------------|-----------------|
| Registration | Auth REST controller | `RegisterAccountUseCase` | identifier/password/role invariants | `RegisterAccountPort`, `EncodePasswordPort` | JPA account, Argon2 |
| Login | Auth REST controller | `AuthenticateAccountUseCase` | account eligibility and session creation | load account, password proof, login policy, persist successful login, issue token/create refresh | JPA, Redis, Argon2, Nimbus/SecureRandom |
| Refresh | Auth REST controller + trusted-origin filter | `RefreshSessionUseCase` | session/credential state and reuse decision | lock/load session, persist rotation/compromise, issue token/create refresh | JPA, Nimbus/SecureRandom |
| Logout current | Auth REST controller + trusted-origin filter | `LogoutSessionUseCase` | idempotent session revocation | resolve/revoke session | JPA |
| Logout all | Auth REST controller + verified JWT | `LogoutAllSessionsUseCase` | revoke only subject-owned sessions | bulk revoke by verified user ID | JPA/resource server |
| Retention cleanup | scheduled inbound adapter | `CleanupExpiredSessionsUseCase` | eligibility boundary | purge retained expired state | JPA |

### Boundary model rules

```text
RegisterRequest -> RegisterAccountCommand -> Account -> AccountJpaEntity
LoginRequest -> AuthenticateAccountCommand -> LoginSession/RefreshCredential
Authentication result -> TokenResponse/RegisterResponse
Redis records/keys never become domain objects
JWT/JWK/JPA/Servlet types never cross into application/domain
```

- HTTP DTOs/mappers/errors stay under `adapter/in/web/auth`.
- JPA entities/repositories/mappers stay under `adapter/out/persistence/<capability>`.
- Spring Redis/key serialization stays under `adapter/out/redis`.
- Argon2, Nimbus JWT, RSA parsing, SHA-256, HMAC, and SecureRandom stay under outbound security
  adapters/configuration.
- Registration password hashing is an explicit `EncodePasswordPort` owned by the application boundary;
  `Argon2PasswordProofAdapter` implements it alongside password verification. The JPA account adapter
  implements only `RegisterAccountPort` and never hashes or verifies passwords.
- Domain failures live near `account` or `session`; unsuccessful use-case outcomes without domain
  meaning use capability-local application failures/results; the web adapter maps them to status.
- No global `dto`, `mapper`, `exception`, `repository`, `utils`, or `service/impl` package is created.
- Manual mapping is sufficient for three small boundary models; MapStruct is not added.

### Transaction wiring

Application services remain plain Java. `AuthenticationApplicationConfiguration` uses
`TransactionTemplate` to wrap registration, refresh, logout, logout-all, and cleanup mutation input
ports, following the established Product Service configuration pattern.

Login keeps Argon2 outside the database transaction. After verification and a second Redis cooldown
check, an atomic persistence capability rechecks active account state and commits last-login,
session, and refresh digest together. Refresh keeps the row lock and all lifecycle decisions inside
one configured transaction.

## Synchronous Flows

### Register

```text
Client -> Gateway authentication-api route -> Auth web validation
  -> normalize identifiers -> encode Argon2id -> create Account
  -> transaction insert users -> 201 {data,traceId}
```

Duplicate pre-check or database uniqueness race maps to the same 409. Role-like fields map to 400.

### Login

```text
Client -> Gateway authentication-login route
  -> Gateway direct-IP token bucket (10/min; typed Redis failure fail-open)
  -> Auth identifier HMAC cooldown pre-check (Redis failure -> 503)
  -> load account + constant public failure behavior
  -> Argon2 verify
  -> on failure: atomic rolling-window record, fifth starts cooldown
  -> on success: cooldown recheck
  -> transaction: recheck ACTIVE + update last_login + insert session + refresh digest
  -> sign 15-minute access JWT
  -> 200 body + HttpOnly refresh cookie
```

Unknown account verification uses a configured dummy Argon2 hash so public timing does not skip the
adaptive hash path. The result remains the same generic 401.

### Refresh and strict reuse

```text
Client -> Gateway authentication-refresh route
  -> Gateway direct-IP token bucket (30/min; typed Redis failure fail-open)
  -> Auth exact Origin/Referer check
  -> SHA-256(cookie)
  -> transaction SELECT refresh+session FOR UPDATE
     current/valid -> retire old -> insert successor -> link -> update activity -> commit
     already used  -> compromise session -> revoke chain -> commit -> 401 reuse
     other invalid -> no issuance -> 401 invalid
  -> sign JWT after successful commit -> 200 body + rotated cookie
```

The raw successor exists in memory before commit so its digest can be inserted. If commit fails, the
raw value is discarded and never sent. If JWT signing fails after rotation commits, return 503 and
clear the cookie; the session remains recoverable only by a new login because the unsent successor
cannot be reconstructed. This is a deliberate fail-safe consequence, not a transaction rollback
across cryptographic response generation.

### Logout

- Current logout validates Origin/Referer, hashes the cookie, and idempotently revokes the owning
  session/chain. Missing/unknown/already-revoked cookie returns 204.
- Logout-all is authenticated by Feature 014 JWT and revokes all sessions for `sub`; caller user IDs
  are ignored/not accepted.
- Both always emit a clearing cookie. Persistence failure returns 503 instead of false 204.
- Already-issued access tokens remain valid until their 15-minute expiry.

## Data and Concurrency Design

Canonical details are in [data-model.md](data-model.md).

### PostgreSQL

- Liquibase formatted-SQL change `001-create-authentication-schema.sql`, included by the existing
  master YAML.
- Tables: `users`, `user_sessions`, `refresh_tokens`.
- Unique normalized identifiers and refresh digest.
- Partial unique index ensures one current refresh row per session.
- Pessimistic write lock serializes refresh/reuse decisions.
- Rotation order retires old before successor insert, correcting the supplied draft.
- Retained records are purged only after the 30-day post-expiry/revocation boundary.

### Redis

- Gateway reuses ADR 0004 token-bucket keys/policy state for exact auth route IDs.
- Auth uses `auth:failed:v1:{hmac}` rolling-window sorted set and
  `auth:cooldown:v1:{hmac}` expiring marker.
- Auth HMAC secret is separate from Gateway's rate-limit secret and decodes to at least 32 bytes.
- Auth uses `WATCH`/`MULTI`/`EXEC`, not Lua, with three immediate contention retries; exhaustion is
  503.
- No recovery/reconciliation into PostgreSQL. Expiry/eviction forgets abuse history but cannot create
  valid durable state.

## Contract and Compatibility Plan

Canonical HTTP/claim/cookie/status behavior is in
[contracts/authentication-http.md](contracts/authentication-http.md).

Compatibility rules:

- Add `/api/v1/auth/**`; do not change Product paths.
- Preserve Feature 014 RS256, issuer, audience, JWKS path, `kid`, `sub`, time claims, and
  `authorities`.
- Persisted admin maps to both `ROLE_ADMIN` and `CATALOG_ADMIN`; normal users never receive admin.
- Gateway-owned `{code,message,traceId}` remains unchanged; Auth uses the same error shape and
  Gateway passes downstream bytes/headers.
- Successful Auth bodies use `{data,traceId}`; no `meta.requestId` alias and no shared `common-web`
  dependency.
- Login/refresh add no-store headers; logout uses empty 204; both 429 owners return `Retry-After`.
- Exact login/refresh routes precede wildcard auth route so distinct quotas are stable.

### Gateway change boundary

- Add the three routes and `AUTHENTICATION_SERVICE_URL` in Gateway `application.yml`.
- Permit register/login/refresh/logout, require authentication for logout-all, and preserve deny-all
  for unknown paths.
- Add `AuthenticationCorrelationIdGlobalFilter` for the three auth route IDs; do not rename/refactor
  the existing catalog filters in this feature.
- Generalize `GatewayRateLimitProperties` from one hard-coded policy to exactly the approved catalog,
  auth-login, and auth-refresh preset selectors; preserve all Feature 013 validation.
- Configure narrow credentialed CORS for `/api/v1/auth/**` using the same exact trusted-origin list;
  no wildcard origin is permitted. Same-origin deployments need no cross-origin allowance.
- Extend existing Gateway route/security/rate-limit/pass-through tests with allowed-origin preflight,
  disallowed-origin rejection, credential allowance, and no-wildcard assertions; add no business core.

## Failure, Retry, and Recovery

| Failure | Public outcome | Durable/ephemeral state | Recovery |
|---------|----------------|-------------------------|----------|
| Unknown/wrong/locked/disabled login | 401 generic | failure window records attempt | Correct credentials after state/cooldown permits |
| Auth throttle Redis unavailable | 503 | no account/session mutation | Retry after Redis recovery |
| Gateway limiter Redis typed failure | request forwarded + metric | quota may be forgotten/overspent | Redis recovers; no client retry forced |
| Fifth failed identifier attempt | 429 + `Retry-After` | 15-minute cooldown key | Wait until expiry |
| Duplicate register race | 409 | one account only | Login/recover through later features |
| Invalid/expired/revoked refresh | 401 invalid | no new token | Login again |
| Used refresh replay | 401 reuse | session compromised and chain revoked | Login again |
| Concurrent same refresh | at most one 200; later request 401 reuse | one successor then compromised under strict rule | Login again after ambiguity |
| DB failure before commit | 503 | transaction rollback | Safe retry except unknown refresh outcome rules |
| JWT signing fails after login commit | 503 + clear/no cookie | session/current digest may exist but raw value was not delivered | Login again; cleanup/revocation later |
| JWT signing fails after refresh commit | 503 + clear cookie | successor digest exists but raw value was not delivered | Login again; strict old-token retry prohibited |
| Logout persistence failure | 503 + clearing cookie | server revocation unconfirmed | Re-authenticate/retry after recovery; access may last 15 minutes |
| Response lost after refresh commit | client sees unknown | old credential already retired | Do not retry old cookie; login again |

No application retry is applied to JWT signing, database writes, or ambiguous refresh. Redis WATCH
contention retry is internal, bounded, and cannot duplicate durable effects.

## Security Boundaries

### Passwords

- Email required; username optional; password 12–128 Unicode code points and never trimmed.
- Argon2id: salt 16 bytes, hash 32 bytes, memory 19,456 KiB, iterations 2, parallelism 1; properties
  are explicit and fail invalid startup.
- Dummy hash verification for unknown accounts; generic response for all credential/account states.
- No password/hash in DTO response, log, metric, trace, or exception message.

### Signing keys and JWT

- Existing public-key PEM configuration remains supported for Feature 014 compatibility.
- Add preferred public/private key Resource locations for secret mounts plus private-key PEM test
  override. Missing, malformed, mismatched, non-RSA, or RSA below 2048 bits fails startup.
- Private key is PKCS#8; public key remains X.509 SubjectPublicKeyInfo.
- Only the public JWK is returned. One key is active; automated overlap/rotation is deferred.
- Logout-all decoder uses public key locally with Feature 014 validators.

### Refresh credential/cookie

- SecureRandom 32 bytes or more; Base64 URL-safe no padding; SHA-256 digest only in DB.
- HttpOnly, host-only, Path `/api/v1/auth`, SameSite=Lax, Secure except local.
- Exact configured Origin or parsed Referer fallback; neither header means 403.
- No-store responses and no raw token logging.

### Authorization

- Register request cannot bind role/authority.
- Account role -> JWT authorities mapping is server-owned.
- Logout-all subject comes only from verified JWT.
- Resource services remain responsible for ownership rules such as `order.user_id == JWT.sub`.

## Observability

### Trace/correlation

- Gateway normalizes/generates `X-Trace-Id` for auth routes and forwards it.
- Auth echoes it in response header and every non-empty body, and includes it in safe structured logs.
- This remains the current compatibility correlation ID. Full W3C Trace Context,
  `micrometer-tracing-bridge-otel`, OTLP exporter, Collector, and Tempo are a future observability
  feature; Feature 015 does not pretend `X-Trace-Id` is an OpenTelemetry span ID.

### Metrics

Use Micrometer interfaces only in adapter/observability code; never construct a Prometheus registry.
Low-cardinality metrics:

- `auth.registration.total{outcome}`;
- `auth.login.total{outcome=success|invalid|throttled|unavailable}`;
- `auth.refresh.total{outcome=success|invalid|reuse|unavailable}`;
- `auth.session.revocation.total{scope=current|all,outcome}`;
- `auth.login.throttle.redis.failure.total`;
- timers for password verification and refresh transaction;
- cleanup deleted-row counts.

No user ID, email, username, IP, HMAC digest, session ID, token ID, exception message, or URI value is
a metric tag.

### Logs and health

- Structured safe fields: traceId, userId only after verified/known, sessionId when safe, operation,
  outcome, stable reason code, and exception class for operator-only unexpected failures.
- Never log password/hash, access/refresh token, Authorization/Cookie, RSA private key, raw
  email/username/IP, or Redis HMAC digest.
- Liveness remains process-only. Readiness includes database reachability; Redis is not added to the
  aggregate readiness group because approved refresh/logout behavior can remain useful while only
  login throttle fails closed. Redis failure is exposed through its component health/metric and
  login 503.
- Existing declarative Actuator liveness/readiness/prometheus endpoints remain; no manual registry.

## Infrastructure, Migration, and Rollout

### Service-owned changes

- Authentication POM, source, config, tests, and Liquibase migration stay under
  `services/authentication-service`.
- Add `auth-migration-it` Failsafe profile mirroring the Product migration pattern.
- `application.yml` gains datasource, Redis, Argon2, JWT private/public location, cookie, origin,
  throttle, retention, and scheduler properties; secrets have no usable committed default.

### Root Compose changes

- `auth_db` already exists in PostgreSQL bootstrap; do not add another database/instance.
- Configure authentication-service datasource, Redis password, throttle HMAC secret, key mount,
  trusted origins, local `Secure=false`, and Auth-specific Liquibase flag.
- Override inherited dependencies so Auth waits on PostgreSQL and Redis only, not Kafka.
- Add Gateway authentication-service URL, auth policies, and trusted-origin/CORS configuration.
- Mount an absolute host key directory read-only at `/run/secrets/auth-jwt`; do not put private key
  content in `.env` or Git.
- Update `.env.example`/README with placeholders and generation instructions only.

### Kubernetes

`infra/k8s` currently contains guidance only and no bases/overlays. Feature 015 does not introduce the
first Kubernetes deployment set. The future Kubernetes feature must use Secret volume mounts for RSA
and HMAC/database/Redis credentials, a service-owned migration Job, probes, Service/DNS, and exact
trusted origins. Therefore no `kubectl apply --dry-run` command applies to Feature 015 unless an
implementation review explicitly expands scope and updates this plan/ADR/tasks first.

### Rollout order

1. Accept ADR 0006 and approve this plan/tasks.
2. Apply/verify authentication schema.
3. Deploy Auth with key pair and dependencies while auth Gateway routes remain absent/disabled.
4. Verify JWKS and internal Auth contract.
5. Deploy Gateway routes/security/limit policies and run proxy/E2E smoke.
6. Enable frontend usage only after secret/log/concurrency/status evidence passes.

### Rollback

- Remove/disable Gateway auth routes first.
- Restore previous Auth image; JWKS-only Feature 014 remains.
- Retain authentication tables for forward recovery; do not auto-drop user/session data.
- Redis keys expire; no flush or second topology is needed.

## Verification Strategy

| Requirement/acceptance | Unit | Integration | Contract/E2E | Concurrency/burst |
|------------------------|------|-------------|--------------|-------------------|
| US1 register/normalization/role denial | Account/value policy | PostgreSQL uniqueness + Liquibase | 201/400/405/409/415 via Gateway; unknown paths retain Gateway 401/403 | concurrent duplicate insert |
| US2 login/generic failure/authority map | Login service + password adapter | PostgreSQL + Redis + JWT signing | 200/401/429/503 and cookie | five-attempt window; concurrent fifth failure/correct login |
| US3 refresh/expiry/reuse | Session aggregate/use case | locked JPA rotation with real PostgreSQL | 200/401/403/429/503 and Set-Cookie | same token race; one rotation maximum; strict compromise |
| US4 logout/current/all | Revocation use cases | transactional bulk/current revocation | 204/401/403/503; empty body/cookie clear | repeated/idempotent requests |
| Feature 014 compatibility | JWT claims/authority unit | local encoder/decoder | Gateway/Product valid admin token regression | N/A |
| Gateway IP policy | policy/property unit | existing real Redis tests | Gateway 429/pass-through | deterministic 10/30 burst tests |
| Secret/log safety | logger/exception unit | storage inspection | response/JWKS/repository scan | N/A |
| Cleanup retention | eligibility unit | PostgreSQL deletion/FK tests | scheduler smoke | parallel idempotent invocation |

Required commands/evidence during implementation:

```powershell
.\mvnw.cmd -pl services/authentication-service -am verify
.\mvnw.cmd -pl services/authentication-service -am verify -Pauth-migration-it
.\mvnw.cmd -pl services/api-gateway -am verify
.\mvnw.cmd clean verify
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml config
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up --build
```

Quickstart smoke must verify register/login/refresh/reuse/logout/logout-all, both 429 owners, Redis
fail-open/fail-closed split, trace propagation, JWKS private-field exclusion, and no raw secret logs.

**Broad load-test omission**: A sustained production capacity/SLO profile is not approved for this
personal MVP. Deterministic Gateway burst tests, Redis account-window concurrency, PostgreSQL refresh
race tests, and Argon2 benchmark evidence cover the changed correctness risks. A production load
profile becomes required before public scale claims.

## Project Structure

Only packages receiving real types are created.

### Feature documentation

```text
specs/015-authentication-session-mvp/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── authentication-http.md
└── checklists/
    └── requirements.md

docs/adr/
└── 0006-authentication-session-and-refresh-rotation.md
```

### Authentication Service planned source

```text
services/authentication-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/philia/flashsale/authentication/
    │   │   ├── AuthenticationServiceApplication.java
    │   │   ├── domain/
    │   │   │   ├── account/          # Account, role/status, identifier/password invariants/failures
    │   │   │   └── session/          # LoginSession, RefreshCredential, lifecycle/reuse failures
    │   │   ├── application/
    │   │   │   ├── registration/     # command/result/RegisterAccountPort/EncodePasswordPort/use case
    │   │   │   ├── login/            # command/result/input+output ports/use case
    │   │   │   ├── refresh/          # command/result/input+output ports/use case
    │   │   │   ├── logout/           # current/all commands/results/ports/use cases
    │   │   │   └── cleanup/          # retention input/output port and use case
    │   │   ├── adapter/
    │   │   │   ├── in/
    │   │   │   │   ├── web/
    │   │   │   │   │   ├── JwksController.java
    │   │   │   │   │   ├── auth/     # controller, DTOs, mapper, error/status handler
    │   │   │   │   │   └── security/ # trusted Origin/Referer filter
    │   │   │   │   └── scheduling/   # expired session/credential cleanup trigger
    │   │   │   └── out/
    │   │   │       ├── persistence/
    │   │   │       │   ├── account/  # Account JPA entity/repository/mapper/adapter
    │   │   │       │   └── session/  # Session/token JPA types, locked repository, adapter
    │   │   │       ├── redis/         # keyed identifier + WATCH/MULTI throttle adapter
    │   │   │       └── security/      # Argon2, JWT issuer, refresh generator/digest adapters
    │   │   └── configuration/         # properties, key pair, decoder/filter chain, scheduling, transaction wiring
    │   └── resources/
    │       ├── application.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/001-create-authentication-schema.sql
    └── test/java/com/philia/flashsale/authentication/
        ├── domain/                    # pure invariant tests
        ├── application/               # use cases with fake ports
        ├── adapter/in/web/            # MockMvc/error/cookie/origin/JWKS contracts
        ├── adapter/out/persistence/    # PostgreSQL migration/lock/constraint integration
        ├── adapter/out/redis/          # real Redis window/concurrency/failure integration
        ├── adapter/out/security/       # Argon2/JWT/secret-leak tests
        └── integration/                # register-to-logout service scenarios
```

### Gateway planned changes

```text
services/api-gateway/
├── src/main/resources/application.yml
├── src/main/java/com/philia/flashsale/gateway/
│   ├── configuration/GatewayRateLimitProperties.java
│   ├── filter/global/AuthenticationCorrelationIdGlobalFilter.java
│   └── security/GatewaySecurityConfiguration.java
└── src/test/java/com/philia/flashsale/gateway/
    ├── AuthenticationGatewayRouteTests.java
    ├── GatewayProxyPassThroughTests.java
    ├── configuration/GatewayRateLimitPropertiesTests.java
    ├── filter/global/AuthenticationCorrelationIdGlobalFilterTests.java
    └── filter/global/GatewayRateLimitGlobalFilterTests.java
```

### Root local infrastructure planned changes

```text
infra/docker/
├── compose.yml
├── .env.example
└── README.md
```

The existing `infra/docker/postgres/init/01-create-databases.sql` already creates `auth_db`; it is
verified but not changed unless implementation inspection finds drift.

## Constitution Check — After Design

| Gate | Result after Phase 1 | Notes |
|------|----------------------|-------|
| Spec/contract traceability | PASS | HTTP/status/claims/cookie and risk decisions are canonical and linked |
| Service/data ownership | PASS | Domain, JPA, migrations, config, and tests remain Authentication-owned |
| Clean/Hex dependencies | PASS | Framework/provider types terminate in adapters/configuration; capability grouping avoids dumping grounds |
| Ingress/communication | PASS | Gateway-only public routing; Feature 014 local validation; no cross-service DB |
| PostgreSQL/Redis semantics | PASS | Durable truth, lock/index rotation, expiry/failure/recovery documented |
| Messaging/outbox | N/A | No integration event |
| Observability | PASS | Declarative Actuator/Prometheus retained; bounded metrics and trace correlation planned |
| Infrastructure | PASS | Shared Compose/key mounts under root; migrations/service config stay in module; K8s explicitly deferred |
| Dependencies | PASS | Every new dependency is purpose-limited and justified; rejected additions recorded |
| Verification | PASS | Risk-based test matrix and commands defined; broad load/K8s omissions justified |
| ADR | PASS | ADR 0006 was Accepted with this plan on 2026-07-25 |

## Complexity Tracking

No constitution violation or exception is proposed.

The main intentional complexity is the retained refresh-token chain plus pessimistic locking. The
simpler single mutable refresh row was rejected because the approved reuse-detection behavior needs
history. The Redis `WATCH` transaction is limited to the account throttle; a simpler counter was
rejected because it cannot preserve the approved rolling-window/fifth-failure behavior safely under
concurrency.

## Approval and History

- 2026-07-25 — Draft plan generated after Q1 A, Q2 A, and Q3 A were approved.
- 2026-07-25 — Platform/security owner approved this plan, its referenced HTTP/data/research
  artifacts, and ADR 0006; `$speckit-tasks` is authorized.
- 2026-07-25 — Corrected the derived 404 entry to retain Feature 011's already-approved
  deny-by-default Gateway 401/403 behavior for unknown public paths.
- 2026-07-25 — Remediated cross-artifact analysis findings: explicit password-encoding port,
  Gateway credentialed-CORS tests/configuration, scheduler activation, session metadata capture,
  rate-policy ordering, and test-task completion semantics.
