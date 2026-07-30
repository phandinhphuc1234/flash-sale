# Tasks: Authentication Session MVP

**Input**: Approved design artifacts in `specs/015-authentication-session-mvp/`  
**Prerequisites**: Approved `spec.md`, approved `plan.md`, approved HTTP contract, and Accepted ADR
0006  
**Status**: Approved  
**Tests**: Required by the approved security/concurrency risk profile

**Architecture amendment**: ADR 0007 and the Feature 015 plan now approve a mechanical
package-by-feature refactor. It must preserve all observable behavior and is executed after the
current Authentication implementation compiles.

**Path note**: Earlier task descriptions retain their original layer-first paths as historical
references. For implementation, resolve those files under the current feature-first tree in ADR
0007 (for example, `account/adapter/out/persistence`, `session/application/login`,
`security/token`, and `websupport`).

**Architecture rule**: Keep `adapter -> application -> domain`; configuration may wire adapters and
application. Create a package only with its first real type. HTTP, Spring Security, JPA, Redis,
Nimbus, Micrometer, and scheduling types must not leak into application/domain.

**Execution rule**: Do not start production-code tasks until this file is human-approved. Execute
one task or one coherent group at a time. A test-authoring task may be checked after the test is
written and its expected pre-implementation state is recorded; implementation and checkpoint tasks
are complete only after the required tests pass and evidence is recorded below.

**Approval**: Approved by the platform/security owner on 2026-07-25. Cross-artifact analysis
remediation was applied on 2026-07-25; the mandatory analysis gate still runs before production
implementation begins.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: May run in parallel because it changes different files and does not depend on unfinished
  output.
- **[USN]**: Maps the task to the independently testable user story in `spec.md`.
- Every implementation task names its exact target path(s).

---

## Phase 1: Setup and Runtime Dependencies

**Purpose**: Add only the approved module dependencies and local runtime wiring needed by later
tasks. This phase exposes no new public authentication behavior.

- [x] T001 Update `services/authentication-service/pom.xml` with the approved Validation, JPA, Redis, Spring Security, OAuth2 Resource Server, PostgreSQL, Bouncy Castle, Spring Security Test, and Testcontainers dependencies plus the `auth-migration-it` Failsafe profile
- [x] T002 [P] Add datasource, JPA, Redis, virtual-thread, Argon2, JWT key-resource, cookie, trusted-origin, account-throttle, retention, scheduler, and health configuration with no usable secret defaults in `services/authentication-service/src/main/resources/application.yml`
- [x] T003 [P] Add the Auth database/Redis/HMAC/read-only RSA key mount/trusted-origin/cookie/Liquibase settings, remove Auth's Kafka startup dependency, add Gateway authentication-service URL and trusted-origin/CORS environment wiring, and document secret generation in `infra/docker/compose.yml`, `infra/docker/.env.example`, and `infra/docker/README.md`

**Checkpoint**: Dependencies and configuration keys match the approved plan; no endpoint behavior
has been implemented.

---

## Phase 2: Foundational Authentication Boundaries

**Purpose**: Establish the schema, domain invariants, replaceable persistence/security adapters,
safe HTTP foundation, and local JWT verification used by every story.

**⚠️ CRITICAL**: No user-story implementation begins until this phase passes its checkpoint.

### Foundation tests

- [ ] T004 Add clean-apply, constraint/index, partial-current-token, and disposable rollback verification in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/persistence/AuthenticationMigrationIntegrationTests.java`
- [x] T005 [P] Add pure Account and LoginSession/RefreshCredential invariant tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/domain/account/AccountTests.java` and `services/authentication-service/src/test/java/com/philia/flashsale/authentication/domain/session/LoginSessionTests.java`
- [ ] T006 [P] Add JPA mapping, normalized-identifier uniqueness, session ownership, and pessimistic refresh-lock integration tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/persistence/AuthenticationPersistenceIntegrationTests.java`
- [ ] T007 [P] Add Argon2id parameter, opaque-refresh entropy, SHA-256 digest, RS256 claim/header, public-only JWKS, invalid-key startup, and local decoder tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/security/AuthenticationSecurityAdaptersTests.java` and `services/authentication-service/src/test/java/com/philia/flashsale/authentication/configuration/JwtSigningConfigurationTests.java`
- [ ] T008 [P] Add fail-fast binding tests for TTL, quota, HMAC, cookie, origin, retention, and key-resource properties in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/configuration/AuthenticationPropertiesTests.java`
- [ ] T009 [P] Add `{data,traceId}`/`{code,message,traceId}`, trace-header echo, safe 405/415, committed-response, and serialization-fallback tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/in/web/auth/AuthenticationHttpFoundationTests.java`
- [ ] T010 [P] Add Auth filter-chain tests for public register/login/refresh/logout, bearer-protected logout-all, public JWKS/Actuator endpoints, and deny-by-default behavior in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/configuration/AuthenticationSecurityConfigurationTests.java`

### Foundation implementation

- [x] T011 Create the service-owned `users`, `user_sessions`, and `refresh_tokens` schema with constraints, indexes, partial-current uniqueness, and formatted-SQL rollback in `services/authentication-service/src/main/resources/db/changelog/changes/001-create-authentication-schema.sql`, then include it from `services/authentication-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- [x] T012 Implement Java-only Account and LoginSession aggregate types and lifecycle failures in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/domain/account/Account.java`, `AccountRole.java`, `AccountStatus.java`, `AccountFailure.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/domain/session/LoginSession.java`, `LoginSessionStatus.java`, `RefreshCredential.java`, and `SessionFailure.java`
- [x] T013 Implement separate JPA entities, Spring Data repositories, boundary mappers, and the locked refresh query in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/persistence/account/AccountJpaEntity.java`, `AccountJpaRepository.java`, `AccountPersistenceMapper.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/persistence/session/LoginSessionJpaEntity.java`, `RefreshCredentialJpaEntity.java`, `LoginSessionJpaRepository.java`, `RefreshCredentialJpaRepository.java`, and `SessionPersistenceMapper.java`
- [x] T014 Implement password proof, access-token signing, opaque refresh generation, and refresh digest adapters in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/security/Argon2PasswordProofAdapter.java`, `JwtAccessTokenAdapter.java`, `SecureRefreshCredentialAdapter.java`, and `Sha256RefreshCredentialDigestAdapter.java`; the Argon2 adapter implements the application-facing `EncodePasswordPort` and `PasswordProofPort` capabilities without exposing Spring Security types
- [x] T015 Implement validated grouped runtime properties and extend the existing Feature 014 RSA configuration with PKCS#8 private-key loading, public/private matching, `NimbusJwtEncoder`, and local `NimbusJwtDecoder` in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationProperties.java`, `JwtTrustProperties.java`, `JwtPublicKeyConfiguration.java`, and `JwtSigningConfiguration.java`
- [x] T016 Implement Auth-owned success/error records, stable error taxonomy, safe exception translation, and trace-ID request context/filter in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/auth/AuthenticationApiResponse.java`, `AuthenticationErrorResponse.java`, `AuthenticationErrorCode.java`, `AuthenticationHttpExceptionHandler.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/AuthenticationRequestContext.java`, and `AuthenticationTraceFilter.java`
- [x] T017 Implement the servlet security boundary and Feature 014 claim/authority conversion without an HTTP self-call in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationSecurityConfiguration.java`
- [x] T018 Run `.\mvnw.cmd -pl services/authentication-service -am verify -Pauth-migration-it` and record command, scope, exit status, and result under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`

**Checkpoint**: Schema and shared boundaries compile and pass migration/domain/security/web tests;
there is still no implemented register/login/refresh/logout use case.

---

## Phase 3: User Story 1 — Register a Normal Account (Priority: P1) 🎯 First Slice

**Goal**: A public shopper registers exactly one active `ROLE_USER` account; caller privilege input
is rejected and normalized duplicates return the same 409 contract.

**Independent Test**: Through Gateway, register a new account, inspect only public output, retry
case/whitespace variants, send forbidden authority fields, and verify no raw password is stored.

### Tests for User Story 1

- [ ] T019 [P] [US1] Add registration use-case tests for normalization, 12–128 code-point password preservation, explicit password-encoding capability invocation, forced `ROLE_USER/ACTIVE`, and duplicate outcomes in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/application/registration/RegisterAccountServiceTests.java`
- [ ] T020 [P] [US1] Add PostgreSQL registration integration tests for encoded-password storage and concurrent email/username uniqueness races in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/persistence/account/AccountRegistrationPersistenceIntegrationTests.java`
- [ ] T021 [P] [US1] Add registration HTTP tests for 201, 400, 405, 409, 415, response shape, safe fields, and trace parity in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/in/web/auth/RegistrationControllerTests.java`
- [ ] T022 [P] [US1] Add Gateway wildcard-auth route, exact-origin credentialed CORS preflight/denial, trace propagation, method/body/query/header forwarding, downstream pass-through, unavailable-service, and preserved anonymous-401/authenticated-403 unknown-path tests in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/AuthenticationGatewayRouteTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/filter/global/AuthenticationCorrelationIdGlobalFilterTests.java`

### Implementation for User Story 1

- [x] T023 [US1] Implement registration command/result, separate `RegisterAccountPort` and `EncodePasswordPort` output capabilities, and the plain-Java use case in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/application/registration/RegisterAccountCommand.java`, `RegisterAccountResult.java`, `RegisterAccountUseCase.java`, `RegisterAccountPort.java`, `EncodePasswordPort.java`, and `RegisterAccountService.java`
- [x] T024 [US1] Implement the account persistence port adapter and transactional registration wiring that composes `RegisterAccountPort` with the separate `EncodePasswordPort` security adapter (password hashing must not live in persistence) in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/persistence/account/JpaAccountPersistenceAdapter.java` and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationApplicationConfiguration.java`
- [x] T025 [US1] Implement registration request/response mapping and controller boundary in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/auth/RegisterRequest.java`, `RegisterResponse.java`, `RegistrationWebMapper.java`, and `RegistrationController.java`
- [x] T026 [US1] Add the `authentication-api` Gateway route, authentication-service URL, register permission, exact trusted-origin credentialed CORS configuration with no wildcard, and route-scoped trace filter in `services/api-gateway/src/main/resources/application.yml`, `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`, and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/AuthenticationCorrelationIdGlobalFilter.java`
- [ ] T027 [US1] Run `.\mvnw.cmd -pl services/authentication-service,services/api-gateway -am verify` for US1 and record the independent registration result under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`

**Checkpoint**: US1 is independently usable through Gateway. It creates no login session and issues
no token.

---

## Phase 4: User Story 2 — Login Without Account Enumeration (Priority: P1)

**Goal**: An active account with correct credentials receives a 15-minute RS256 access token and
one refresh cookie/session; all durable credential/account-state failures share one 401 while the
approved Redis cooldown remains a separate 429.

**Independent Test**: Seed normal/admin accounts, compare public failures, exercise five failed
attempts and Redis loss, then prove a successful login atomically stores one session/current digest
and returns compatible claims/cookie attributes.

### Tests for User Story 2

- [ ] T028 [P] [US2] Add login orchestration tests for dummy-hash unknown-account work, generic 401 behavior, cooldown pre/recheck, role-to-authority mapping, signing failure, and atomic-success invocation in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/application/login/AuthenticateAccountServiceTests.java`
- [ ] T029 [P] [US2] Add real-Redis tests for keyed-HMAC identifiers, rolling 15-minute pruning, fifth-failure cooldown, positive `Retry-After`, successful-login non-reset, three contention retries, and fail-closed unavailability in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/redis/RedisLoginThrottleAdapterTests.java`
- [ ] T030 [P] [US2] Add PostgreSQL tests proving last-login/session/current-refresh atomicity, active-account recheck, rollback on persistence failure, and bounded session metadata persistence in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/persistence/session/SuccessfulLoginPersistenceIntegrationTests.java`
- [ ] T031 [P] [US2] Add login HTTP/JWT/cookie tests for 200, identical 401s, Auth-owned 429/503, `no-store`, no token in cookie/body crossover, bounded `deviceName`, bounded User-Agent capture, direct-peer IP capture without forwarded-header trust, `ROLE_USER`, and administrator `CATALOG_ADMIN` compatibility in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/in/web/auth/LoginControllerTests.java`
- [ ] T032 [P] [US2] Extend Gateway tests for exact login-route precedence, 10/minute/direct-IP burst rejection, `Retry-After`, typed Redis fail-open metric, cookie/Authorization/trace pass-through, and unchanged catalog policy in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/AuthenticationGatewayRouteTests.java`, `services/api-gateway/src/test/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitPropertiesTests.java`, and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/filter/global/GatewayRateLimitGlobalFilterTests.java`

### Implementation for User Story 2

- [x] T033 [US2] Implement login command/result, password/session/token/throttle output capability ports, capture optional `deviceName` as a bounded caller label, and use-case orchestration in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/application/login/AuthenticateAccountCommand.java`, `AuthenticationResult.java`, `AuthenticateAccountUseCase.java`, `LoadAccountForAuthenticationPort.java`, `PasswordProofPort.java`, `LoginThrottlePort.java`, `PersistSuccessfulLoginPort.java`, `IssueLoginCredentialsPort.java`, and `AuthenticateAccountService.java`
- [x] T034 [US2] Implement the HMAC-keyed Redis `WATCH`/`MULTI`/`EXEC` throttle with bounded contention and typed failure translation in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/redis/LoginThrottleKeyFactory.java` and `RedisLoginThrottleAdapter.java`
- [x] T035 [US2] Implement atomic successful-login persistence across account/session/current-refresh rows in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/persistence/session/JpaAuthenticationSessionPersistenceAdapter.java`
- [x] T036 [US2] Implement login DTO mapping/controller, bounded User-Agent/direct-peer metadata capture (never trusting forwarded client-IP headers), refresh-cookie issuance, and transaction/use-case composition in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/auth/LoginRequest.java`, `TokenResponse.java`, `LoginWebMapper.java`, `LoginController.java`, `RefreshCookieWriter.java`, and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationApplicationConfiguration.java`
- [x] T037 [US2] Generalize the validator to accept exactly the three approved Gateway rate-limit presets, configure the catalog and login selectors now, and add the exact login route/policy/security permission ahead of the wildcard route; the refresh selector remains disabled until T047 adds its route in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitProperties.java`, `services/api-gateway/src/main/resources/application.yml`, and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`
- [ ] T038 [US2] Run `.\mvnw.cmd -pl services/authentication-service,services/api-gateway -am verify` and record login, account-throttle concurrency, Gateway burst/fail-open, and Feature 014 claim evidence under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`

**Checkpoint**: US2 can establish a real browser session through Gateway without exposing account
existence or granting client-selected authority.

---

## Phase 5: User Story 3 — Rotate a Refresh Credential Safely (Priority: P1)

**Goal**: One current cookie rotates once, respects seven-day inactivity/30-day absolute expiry,
and strict replay compromises only the owning session.

**Independent Test**: Refresh successfully, replay the retired cookie, and race two requests with
the same cookie; prove at most one 200, no parallel current rows, committed compromise, exact cookie
replacement, and the approved Origin/Referer behavior.

### Tests for User Story 3

- [ ] T039 [P] [US3] Add refresh use-case tests for validity boundaries, successor lifetime capping, strict reuse compromise, ambiguous retry, and signing-after-commit failure in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/application/refresh/RefreshSessionServiceTests.java`
- [x] T040 [P] [US3] Add real-PostgreSQL row-lock tests for retire-before-insert ordering, one-success maximum, partial-current uniqueness, and committed session-chain compromise in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/persistence/session/RefreshRotationConcurrencyIntegrationTests.java`
- [ ] T041 [P] [US3] Add exact Origin/Referer, malformed/null/missing source, no-body, cookie replacement/clear, 200/400/401/403/503, no-store, and trace tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/in/web/auth/RefreshControllerTests.java` and `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/in/web/security/TrustedOriginFilterTests.java`
- [ ] T042 [P] [US3] Extend Gateway tests for exact refresh-route precedence, 30/minute/direct-IP burst rejection, Redis fail-open, Origin/Referer/Cookie/Set-Cookie pass-through, and downstream 401/403/503 byte preservation in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/AuthenticationGatewayRouteTests.java`, `services/api-gateway/src/test/java/com/philia/flashsale/gateway/configuration/GatewayRateLimitPropertiesTests.java`, and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayProxyPassThroughTests.java`

### Implementation for User Story 3

- [x] T043 [US3] Implement refresh command/result, locked rotation capability ports, and use-case decisions in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/application/refresh/RefreshSessionCommand.java`, `RefreshSessionResult.java`, `RefreshSessionUseCase.java`, `RotateRefreshCredentialPort.java`, `IssueRefreshedCredentialsPort.java`, and `RefreshSessionService.java`
- [x] T044 [US3] Implement transactional locked rotation, retire-before-insert, reuse compromise, and session-chain revocation in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/persistence/session/JpaAuthenticationSessionPersistenceAdapter.java`
- [x] T045 [US3] Implement exact trusted Origin/Referer enforcement in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/security/TrustedOriginFilter.java`
- [x] T046 [US3] Implement refresh controller/mapping and transaction composition with post-commit JWT signing and fail-safe cookie clearing in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/auth/RefreshController.java`, `RefreshWebMapper.java`, `RefreshCookieWriter.java`, and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationApplicationConfiguration.java`
- [x] T047 [US3] Add the exact refresh route and approved 30/minute policy ahead of the wildcard route in `services/api-gateway/src/main/resources/application.yml` and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`
- [ ] T048 [US3] Run `.\mvnw.cmd -pl services/authentication-service,services/api-gateway -am verify` and record refresh rotation, strict reuse, PostgreSQL concurrency, Origin, cookie, and Gateway 30/minute evidence under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`

**Checkpoint**: US3 safely renews a session. A lost/ambiguous refresh response requires login again;
the old cookie is never replay-idempotent.

---

## Phase 6: User Story 4 — End One or Every Login Session (Priority: P1)

**Goal**: Current logout is cookie-based and idempotent; logout-all derives ownership only from the
verified JWT subject and revokes all owned sessions atomically.

**Independent Test**: Create two sessions, revoke one repeatedly without revealing existence,
verify the second still refreshes, then logout-all and prove neither refreshes; persistence failure
must clear the cookie but return 503 rather than false 204.

### Tests for User Story 4

- [ ] T049 [P] [US4] Add current/all logout application tests for subject ownership, idempotency, session isolation, bulk revocation, no blacklist assumption, and persistence failure in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/application/logout/LogoutSessionServiceTests.java`
- [x] T050 [P] [US4] Add PostgreSQL tests for current-session locking, credential-chain revocation, atomic logout-all, repeated requests, and rollback in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/persistence/session/LogoutPersistenceIntegrationTests.java`
- [ ] T051 [P] [US4] Add Auth and Gateway HTTP/security tests for empty 204, clearing cookie, trusted origin on current logout, verified `sub` on logout-all, Gateway 401, Auth 403/503, and downstream pass-through in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/in/web/auth/LogoutControllerTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/AuthenticationGatewayRouteTests.java`

### Implementation for User Story 4

- [x] T052 [US4] Implement current/all logout commands, input/output ports, and use cases in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/application/logout/LogoutCurrentSessionCommand.java`, `LogoutAllSessionsCommand.java`, `LogoutSessionUseCase.java`, `LogoutAllSessionsUseCase.java`, `RevokeAuthenticationSessionsPort.java`, and `LogoutSessionService.java`
- [x] T053 [US4] Implement idempotent current-session and atomic subject-owned bulk revocation in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/persistence/session/JpaAuthenticationSessionPersistenceAdapter.java`
- [x] T054 [US4] Implement current/all logout controllers, verified-subject mapping, cookie clearing, and transactional wiring in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/auth/LogoutController.java`, `LogoutAllController.java`, `LogoutWebMapper.java`, and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationApplicationConfiguration.java`
- [x] T055 [US4] Permit current logout, require a Feature 014-valid bearer token for logout-all, and preserve deny-by-default behavior in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`
- [ ] T056 [US4] Run `.\mvnw.cmd -pl services/authentication-service,services/api-gateway -am verify` and record current/all logout, idempotency, ownership, empty-body, cookie-clear, and failure evidence under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`

**Checkpoint**: All four user stories operate through Gateway and remain independently testable.

---

## Phase 7: Retention, Observability, Hardening, and Final Verification

**Purpose**: Complete cross-story operational behavior and collect the evidence required to call
the Authentication MVP implementation-ready for local use.

- [ ] T057 [P] Add cleanup eligibility, delete ordering, 30-day retention, active-record safety, concurrent idempotency, and scheduler-trigger/activation tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/application/cleanup/CleanupExpiredSessionsServiceTests.java`, `services/authentication-service/src/test/java/com/philia/flashsale/authentication/adapter/out/persistence/session/AuthenticationCleanupIntegrationTests.java`, and `services/authentication-service/src/test/java/com/philia/flashsale/authentication/configuration/AuthenticationSchedulingConfigurationTests.java`
- [x] T058 Implement cleanup input/output ports, use case, persistence deletion, daily scheduler, and explicit scheduling activation in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/application/cleanup/CleanupExpiredSessionsUseCase.java`, `PurgeExpiredAuthenticationStatePort.java`, `CleanupExpiredSessionsService.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/scheduling/ExpiredAuthenticationStateCleanupScheduler.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/persistence/session/JpaAuthenticationSessionPersistenceAdapter.java`, and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationSchedulingConfiguration.java` (`@EnableScheduling`)
- [ ] T059 [P] Add bounded-tag metric, safe structured-log, Actuator liveness/readiness/Prometheus, Redis component-failure, trace-path, and no-manual-registry tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/integration/AuthenticationObservabilityTests.java`
- [x] T060 Instrument approved registration/login/refresh/revocation/Redis/cleanup counters and timers without core Micrometer imports in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/web/auth/AuthenticationHttpMetrics.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/out/redis/LoginThrottleMetrics.java`, and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/adapter/in/scheduling/AuthenticationCleanupMetrics.java`
- [ ] T061 [P] Add the complete Auth status matrix, secret-leak storage/log/response scan, and raw-identifier Redis-key regression suite in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/integration/AuthenticationSecurityRegressionTests.java` and `AuthenticationHttpStatusMatrixTests.java`
- [x] T062 [P] Add Feature 014 regression evidence that an issued admin fixture token is accepted at the Gateway/Product `CATALOG_ADMIN` boundary while a normal token is denied in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/integration/JwtTrustCompatibilityIntegrationTests.java`, `services/api-gateway/src/test/java/com/philia/flashsale/gateway/security/GatewayJwtTrustConfigurationTests.java`, and `services/product-service/src/test/java/com/philia/flashsale/product/config/ProductJwtTrustConfigurationTests.java`
- [X] T063 Update implemented configuration, local secret/key generation, no-OAuth2 scope, Redis failure split, and client retry guidance in `services/authentication-service/README.md` and `specs/015-authentication-session-mvp/quickstart.md`
- [X] T064 Run `.\mvnw.cmd -pl services/authentication-service -am verify` and record scope, exit status, and result under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`
- [X] T065 Run `.\mvnw.cmd -pl services/authentication-service -am verify -Pauth-migration-it` and record forward/rollback migration evidence under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`
- [X] T066 Run `.\mvnw.cmd -pl services/api-gateway -am verify` and record route/security/trace/rate-limit/pass-through results under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`
- [X] T067 Run `.\mvnw.cmd clean verify` and record the full cross-module compatibility result under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`
- [X] T068 Validate `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml config` and record secret interpolation, read-only key mount, Auth PostgreSQL/Redis-only dependency, and loopback binding evidence under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`
- [X] T069 Execute `specs/015-authentication-session-mvp/quickstart.md` through Gateway, including both 429 owners, Redis fail-open/fail-closed split, refresh reuse, logout, JWKS public-only output, trace parity, and secret-log inspection; record results under `Validation Evidence` in `specs/015-authentication-session-mvp/tasks.md`
- [X] T070 Audit `specs/015-authentication-session-mvp/spec.md`, `plan.md`, `contracts/authentication-http.md`, `tasks.md`, `docs/adr/0006-authentication-session-and-refresh-rotation.md`, and affected source for drift, then update feature/task status only if every required gate has evidence

**Checkpoint**: Feature 015 is complete only when T064–T070 pass. No Kubernetes dry-run applies
because this feature changes no Kubernetes manifest or overlay.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 — Setup**: starts after this `tasks.md` is approved.
- **Phase 2 — Foundation**: depends on T001–T003 and blocks every user story.
- **US1–US4**: each starts after T018. They use separate application/controller packages and can be
  independently tested with fixtures, but the recommended single-developer order is US1 → US2 →
  US3 → US4.
- **Phase 7**: cleanup can start after Foundation; final observability/security/compatibility and all
  verification tasks depend on the selected user stories being complete. T070 depends on T064–T069.

### User-story dependency graph

```text
Setup
  -> Foundation
       ├── US1 Register
       ├── US2 Login
       ├── US3 Refresh
       └── US4 Logout

Recommended learning/delivery path:
US1 Register -> US2 Login -> US3 Refresh -> US4 Logout -> Final verification
```

The stories are independently testable after Foundation by inserting isolated account/session
fixtures. For the actual user journey, login naturally consumes an account, refresh consumes a
session created by login, and logout operates on those sessions.

### Within each story

1. Add mapped domain/application/integration/HTTP tests.
2. Implement commands/results/ports and use-case orchestration.
3. Implement or extend driven adapters.
4. Implement the web boundary and Spring composition.
5. Extend Gateway behavior only where that story requires it.
6. Run the story checkpoint and record evidence before moving on.

---

## Parallel Opportunities

- T002 and T003 may run in parallel with each other after dependency names are confirmed by T001.
- T005–T010 touch independent test boundaries and may be prepared in parallel.
- Within US1, T019–T022 are independent test files.
- Within US2, T028–T032 cover separate application, Redis, persistence, HTTP, and Gateway boundaries.
- Within US3, T039–T042 cover separate application, PostgreSQL, Auth HTTP/security, and Gateway tests.
- Within US4, T049–T051 cover separate application, persistence, and HTTP/Gateway boundaries.
- T057, T059, T061, and T062 are independent cross-cutting test suites once their story code exists.
- Do not run parallel tasks that edit `AuthenticationApplicationConfiguration.java`,
  `JpaAuthenticationSessionPersistenceAdapter.java`, Gateway `application.yml`,
  `GatewaySecurityConfiguration.java`, or `GatewayRateLimitProperties.java` at the same time.

### Parallel example: User Story 3

```text
Task T039: Refresh application behavior tests
Task T040: PostgreSQL rotation/concurrency tests
Task T041: Auth Origin/cookie/HTTP contract tests
Task T042: Gateway route/rate/pass-through tests
```

After those tests define the boundaries, execute T043–T047 in dependency order and finish with
T048.

---

## Implementation Strategy

### Smallest demonstrable slice

1. Complete Setup and Foundation.
2. Complete US1 registration.
3. Stop at T027 and demonstrate normalization, privilege denial, safe password storage, and Gateway
   pass-through.

US1 alone is a valid independently testable slice, but it is not yet a useful sign-in product.

### Practical Authentication MVP

1. Setup + Foundation.
2. US1 Register.
3. US2 Login — first point where clients can obtain a real access token.
4. US3 Refresh — makes the 15-minute access-token lifetime usable.
5. US4 Logout — completes session termination.
6. Phase 7 hardening and final evidence.

For this security feature, production/VPS exposure must wait for the complete practical MVP and
T064–T070. Do not deploy only the register/login slice as a finished session system.

---

## Validation Evidence

Fill this table while implementing. A task checkbox without the corresponding required evidence is
not completion evidence.

| Task/checkpoint | Command or scenario | Scope | Exit/result | CI/PR reference | Date |
|-----------------|---------------------|-------|-------------|-----------------|------|
| T018 | `.\mvnw.cmd -pl services/authentication-service -am verify -Pauth-migration-it` | Authentication foundation + migration | PASS; 38 unit/integration tests plus 3 migration tests, exit 0 | Local | 2026-07-29 |
| T027 | Pending | US1 Register + Gateway | Pending | Local/PR pending | Pending |
| T038 | Pending | US2 Login/throttle/JWT + Gateway | Pending | Local/PR pending | Pending |
| T048 | Pending | US3 Refresh/concurrency/origin + Gateway | Pending | Local/PR pending | Pending |
| T056 | Pending | US4 Logout/current/all + Gateway | Pending | Local/PR pending | Pending |
| T064 | `.\mvnw.cmd -pl services/authentication-service -am verify` | Authentication module | PASS; 38 tests, exit 0 | Local | 2026-07-29 |
| T065 | `.\mvnw.cmd -pl services/authentication-service -am verify -Pauth-migration-it` | Auth Testcontainers migration profile | PASS; 38 tests plus 3 migration tests, exit 0 | Local | 2026-07-29 |
| T066 | `.\mvnw.cmd -pl services/api-gateway -am verify -q` | Gateway route/security/trace/rate-limit module | PASS; exit 0 | Local | 2026-07-26 |
| T067 | `.\mvnw.cmd clean verify -q "-Dlogging.level.root=WARN"` | Full Maven reactor | PASS; 266 tests, 0 failures/errors/skips, exit 0 | Local | 2026-07-29 |
| T068 | `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps config` + Compose status | Actual local VPS-like Compose configuration | PASS; ignored `.env` interpolates, RSA keys are external/read-only, Auth depends on PostgreSQL/Redis, app ports bind loopback, Kafka healthy | Local | 2026-07-27 |
| T069 | Compose register/login/refresh/reuse/logout/logout-all; 12-request burst; Redis stop/start; internal JWKS; actuator and log checks | Gateway/Auth/Product runtime | PASS; 201/200/200/401 reuse/204/204, 2 Gateway 429s after 10 accepted attempts, Redis-down typed 503 with Gateway fail-open, public-only JWKS, trace parity, no secret/token/private-key log matches | Local | 2026-07-27 |

### Current implementation evidence

| Scope | Command | Result | Date |
|-------|---------|--------|------|
| Authentication source compilation | `./mvnw.cmd -pl services/authentication-service -am -DskipTests compile -q` | PASS | 2026-07-26 |
| Authentication unit/context tests | `./mvnw.cmd -pl services/authentication-service -am test -q` | PASS (8 tests) | 2026-07-26 |
| Feature 015 audit-gap regression suite | `.\mvnw.cmd -pl services/authentication-service -am test` | PASS: 38 tests; refresh-reuse commit outcome, strict origin handling, dummy Argon2 proof, fail-fast Argon2 properties, Auth 429 `Retry-After`, post-commit signing failure, JWT audience/authority validation, PostgreSQL refresh concurrency, and logout rollback covered | 2026-07-29 |
| Feature 015 authentication verification | `.\mvnw.cmd -pl services/authentication-service -am verify` | PASS; 38 tests, exit 0; packaged authentication-service JAR | 2026-07-29 |
| Real PostgreSQL refresh/logout verification | `.\mvnw.cmd -pl services/authentication-service -am "-Dtest=RefreshRotationConcurrencyIntegrationTests,LogoutPersistenceIntegrationTests" test` | PASS: 6 Testcontainers tests; strict concurrent reuse leaves zero active credentials and failed logout rolls back atomically | 2026-07-29 |
| Focused Gateway JWT trust compatibility | `.\mvnw.cmd -pl services/api-gateway -am -Dtest=GatewayJwtTrustConfigurationTests test` | PASS; 4 tests | 2026-07-28 |
| Current combined Gateway/Auth verification | `.\mvnw.cmd clean verify -q "-Dlogging.level.root=WARN"` | PASS: Gateway taxonomy includes Feature 016 `INVENTORY_ADMIN_REQUIRED`; full 12-module reactor passed with 266 tests, 0 failures/errors/skips | 2026-07-29 |
| Authentication migration profile | `.\mvnw.cmd -pl services/authentication-service -am verify -Pauth-migration-it` | PASS: 38 Authentication tests plus Testcontainers forward migration, idempotent rerun, and rollback directive checks (3 tests) | 2026-07-29 |
| Gateway compatibility tests | `./mvnw.cmd -pl services/api-gateway -am test -q` | PASS | 2026-07-26 |
| Gateway verification | `./mvnw.cmd -pl services/api-gateway -am verify -q` | PASS: route, security, trace, rate-limit, error, and pass-through test suite | 2026-07-26 |
| Full Maven reactor | `./mvnw.cmd clean verify -q` | PASS: all reactor modules and tests | 2026-07-26 |
| Runtime/deployment guidance | `services/authentication-service/README.md`, `specs/015-authentication-session-mvp/quickstart.md`, `infra/docker/README.md` | PASS: real-key, migration, Redis failure, no-OAuth2, retry, and loopback/TLS guidance documented | 2026-07-26 |
| Compose interpolation (real local secrets) | `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps config` | PASS; ignored `.env` and external RSA key files exist; values were not printed | 2026-07-27 |
| Compose build/start | `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps build authentication-service api-gateway product-service` + `up -d` | PASS; images built and app/platform containers healthy | 2026-07-27 |
| Runtime actuator probes | Gateway host probes plus internal Compose probes for Authentication/Product | PASS; health, liveness, readiness, Prometheus all HTTP 200 | 2026-07-27 |
| Runtime security smoke | Temporary account through `http://127.0.0.1:18080` | PASS; `at+jwt` accepted Gateway/Auth, refresh reuse detected, logout/logout-all authorized, trace echoed | 2026-07-27 |
| Rate-limit/Redis fault smoke | 12 login requests; stop/start project Redis | PASS; 2 Gateway 429 responses after 10 accepted attempts; Redis-down request forwarded and Auth returned typed 503 | 2026-07-27 |
| JWKS/log safety smoke | Internal JWKS endpoint; recent container log scan | PASS; one RSA/RS256 public key with kid, no private fields, no password/bearer/refresh-value/private-key matches | 2026-07-27 |
| Refreshed full-topology smoke | Gateway/Auth/Product/Inventory with PostgreSQL/Redis; temporary account removed after use | PASS: register 201, login/refresh 200, user Inventory denial 403, admin Product 200, Inventory PostgreSQL not-found 404, logout-all 204, Redis-down Gateway fail-open/Auth fail-closed, eventual reconnect recovery | 2026-07-29 |

The authentication module, real Testcontainers migration profile, focused Gateway/Product/Inventory
JWT trust checks, the final 2026-07-29 full reactor, actual Compose smoke, runtime probes, and final
drift audit have passing evidence. The former Feature 016 Gateway error-taxonomy test drift is fixed.
The local environment is suitable for a private development VPS only; public production exposure
still requires operator-managed secret rotation, TLS/reverse proxy, firewall policy, backups, and an
Argon2 benchmark on the target VPS.

### Architecture refactor evidence

| Task | Command or scenario | Result | Date |
|------|---------------------|--------|------|
| T071 | `./mvnw.cmd -pl services/authentication-service -am test -q` (plus source compile) | PASS: package move compiles and Authentication tests pass | 2026-07-26 |
| T072 | `./mvnw.cmd -pl services/authentication-service,services/api-gateway -am verify -q` | PASS: Authentication/Gateway verification and unchanged HTTP contract tests | 2026-07-27 |

## Phase 8: Feature-oriented package refactor

- [X] T071 Move Authentication types into the approved `account`, `session`, `security`, `throttle`,
  `cleanup`, `websupport`, and `observability` feature-oriented packages; update package/import
  references, mirror test paths, and remove obsolete empty scaffold markers without changing runtime
  contracts. Source scope:
  `services/authentication-service/src/main/java/com/philia/flashsale/authentication/` and
  `services/authentication-service/src/test/java/com/philia/flashsale/authentication/`.
- [X] T072 Run `./mvnw.cmd -pl services/authentication-service,services/api-gateway -am verify` and
  record compilation, Authentication tests, Gateway tests, and unchanged HTTP contract evidence.

## Notes

- No Kafka producer/consumer, outbox, OAuth2/OIDC server, access-token blacklist, second Redis,
  MapStruct, common-web dependency, distributed lock, new Lua script, or Kubernetes resource belongs
  to this task graph.
- Gateway remains a lean WebFlux edge service; Authentication business behavior remains in
  authentication-service.
- Public registration never creates an administrator. Administrator fixtures are test-only and do
  not authorize a provisioning endpoint.
- Argon2 must be benchmarked on the target VPS before public deployment; the approved parameters are
  the minimum configurable preset, not a latency promise.
