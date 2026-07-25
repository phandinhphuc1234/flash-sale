# Tasks: Gateway-Owned Error Handling

**Input**: Approved design documents from `specs/011-gateway-error-handling/`

**Prerequisites**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[data-model.md](data-model.md), [gateway-error-http.md](contracts/gateway-error-http.md), and
[quickstart.md](quickstart.md)

**Task status**: Verified — the Gateway/Platform owner approved Q1/Q2/Q3 Option A, all 25 tasks
were completed, and the required clean reactor gate passed on 2026-07-22.

**Tests**: Test-first ordering is required for the security and compatibility risks in this feature.
Each production task is complete only after its mapped focused tests and the required module reactor
gate pass. Evidence is recorded in `validation.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it touches a different file and does not depend on unfinished output
- **[US1]/[US2]/[US3]**: Maps to the independently testable user story in `spec.md`

## Phase 1: Setup and Approval Gate

**Purpose**: Prove the governing artifacts are decision-ready before production code.

- [x] T001 Confirm `.specify/feature.json` targets `specs/011-gateway-error-handling`, all three decisions are RESOLVED, `spec.md` and `plan.md` are Approved, and `checklists/requirements.md` has no unchecked item; record the pre-implementation gate in `specs/011-gateway-error-handling/validation.md`
- [x] T002 [P] Verify `services/api-gateway/pom.xml`, `services/api-gateway/src/main/resources/application.yml`, and `docs/adr/0003-lean-api-gateway-package-structure.md` require no dependency, route, timeout, retry, rate-limit, circuit-breaker, Actuator, or ADR delta; record the result in `specs/011-gateway-error-handling/validation.md`

**Checkpoint**: Approved scope and existing Gateway baseline are recorded; implementation may start.

---

## Phase 2: User Story 1 — Receive a Stable Gateway Failure (Priority: P1) 🎯 MVP

**Goal**: Every approved Gateway-owned failure returns the exact safe status/code/message contract,
preserves security semantics, and never writes twice after commit.

**Independent Test**: Trigger the seven approved categories through focused components/reactive HTTP
paths and verify exact status, code, message, content type, trace outcome, challenge semantics, and
committed-response behavior without invoking downstream business logic.

### Tests for User Story 1

- [x] T003 [P] [US1] Add the exact seven-row taxonomy contract test in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayErrorCodeTests.java` (FR-001, FR-002, FR-007)
- [x] T004 [P] [US1] Add caller normalization, 128-character boundary, generated and stable-per-exchange trace tests in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/observability/GatewayTraceIdResolverTests.java` (FR-008, NFR-004)
- [x] T005 [P] [US1] Add exact JSON/status/content-type, safe observation input and log-escaping, generated trace, serialization-fallback, and committed-failure propagation tests in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayHttpErrorWriterTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/observability/GatewayErrorObservationTests.java` (FR-001, FR-005, FR-006, FR-011, FR-016)
- [x] T006 [P] [US1] Freeze every included/excluded cause and the precedence from `plan.md` in routed connection, nested auth-infrastructure, explicit status delegation, unknown catch-all, and committed-response tests at `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayFailureClassifierTests.java` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayWebExceptionHandlerTests.java` (FR-004, FR-007, FR-014 through FR-016)
- [x] T007 [P] [US1] Add invalid-token versus the exact authentication-context allow-list, Bearer challenge, Product Admin 403, and generic 403 tests in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/security/GatewaySecurityErrorHandlerTests.java` (FR-009, FR-010, FR-014)
- [x] T008 [P] [US1] Update Product Admin 400/401/403 compatibility assertions so invalid caller traces now receive a generated non-blank error trace in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ProductAdminGatewayRouteTests.java` (FR-002, FR-008)
- [x] T009 [P] [US1] Add anonymous 401 and authenticated generic 403 deny-by-default HTTP tests for root and unconfigured Actuator paths, while retaining public configured health access, in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayUnknownPathSecurityTests.java` (FR-009, FR-010)
- [x] T010 [P] [US1] Add a test-only reactive JWT decoder contract test proving a generic invalid JWT stays 401 while an approved nested verification-infrastructure cause becomes safe 503 in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayAuthenticationFailureContractTests.java` (FR-005, FR-010, FR-014)

### Implementation for User Story 1

- [x] T011 [P] [US1] Add the four approved codes and concise taxonomy responsibility documentation to `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayErrorCode.java`; retain the immutable three-field record in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayErrorResponse.java`
- [x] T012 [P] [US1] Implement caller normalization and stable per-exchange UUID generation with comments only for the non-obvious validation/generation split in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/observability/GatewayTraceIdResolver.java`
- [x] T013 [P] [US1] Implement one parameterized safe operator record per Gateway-owned error and reversibly escape control characters at the logger boundary in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/observability/GatewayErrorObservation.java`
- [x] T014 [US1] Refactor `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayHttpErrorWriter.java` to use trace resolution/observation, propagate failures after commit, provide the safe serialization fallback, and accept only approved codes; refactor `services/api-gateway/src/main/java/com/philia/flashsale/gateway/filter/global/AdminCatalogRequestBoundaryFilter.java` to depend on the trace resolver instead of the writer for validation
- [x] T015 [US1] Implement the exact cause-chain allow-lists, exclusions, route-context guards, and precedence approved in `plan.md` in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayFailureClassifier.java` plus the ordered committed-safe reactive handler in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayWebExceptionHandler.java`; comment only classification precedence and delegation rules
- [x] T016 [US1] Narrow public Actuator matchers in `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`; update `GatewaySecurityErrorHandler.java` to preserve the Bearer challenge, distinguish verification infrastructure 503 from invalid-token 401, and choose route-specific versus generic 403 without importing authentication-service code
- [x] T017 [US1] Run the focused US1 tests from `specs/011-gateway-error-handling/quickstart.md` and record command, scope, exit status, and result in `specs/011-gateway-error-handling/validation.md`

**Checkpoint**: The seven Gateway-owned categories, trace behavior, and reactive security semantics
work independently of downstream business services.

---

## Phase 3: User Story 2 — Preserve Downstream Service Ownership (Priority: P2)

**Goal**: Downstream HTTP errors remain byte-for-byte service-owned while a routed no-response
connection failure receives the Gateway's generic 503.

**Independent Test**: Route to an isolated HTTP upstream returning representative 400/404/409/500
payloads and then to an unavailable local port; compare status/body bytes and the Gateway 503 body.

### Tests and Verification for User Story 2

- [x] T018 [P] [US2] Add a JDK test-upstream proxy contract test for exact downstream 400, 404, 409, and 500 status/body pass-through in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayProxyPassThroughTests.java` (FR-003, FR-004, NFR-002)
- [x] T019 [P] [US2] Add selected-route connection-refused tests with preserved/generated trace and no host/port leakage in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayDownstreamUnavailableTests.java` (FR-005, FR-015)
- [x] T020 [US2] Run the focused US2 proxy/pass-through tests from `specs/011-gateway-error-handling/quickstart.md` and record command, scope, exit status, and result in `specs/011-gateway-error-handling/validation.md`

**Checkpoint**: Obtained downstream errors pass through unchanged; only a routed exchange with no
HTTP response is Gateway-owned.

---

## Phase 4: User Story 3 — Diagnose a Gateway Failure Safely (Priority: P3)

**Goal**: Every written Gateway-owned error is correlatable while raw tokens, request bodies,
internal destinations, and exception messages stay out of the client response and safe observation.

**Independent Test**: Inject a Gateway exception containing sensitive sentinel values and verify the
stable 500 body, trace correlation, safe observation fields, and committed-response behavior.

### Tests and Verification for User Story 3

- [x] T021 [P] [US3] Add a test-only throwing GlobalFilter HTTP scenario and sensitive-sentinel leakage assertions in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayUnexpectedFailureTests.java` (FR-005, FR-016, NFR-003)
- [x] T022 [US3] Run the focused US3 and all Gateway error tests from `specs/011-gateway-error-handling/quickstart.md` and record command, scope, exit status, and result in `specs/011-gateway-error-handling/validation.md`

**Checkpoint**: Operators receive a safe trace-correlated record and clients receive no diagnostic
secret or raw failure detail.

---

## Phase 5: Final Validation and Artifact Closure

- [x] T023 Run `.\mvnw.cmd -pl services/api-gateway -am clean verify`; record the complete command, affected reactor scope, exit code, test count/result, and environment limitation if any in `specs/011-gateway-error-handling/validation.md`
- [x] T024 Review production comments for Clean Code: keep type-level responsibility and non-obvious reactive/security rationale, remove comments that merely narrate getters/constructors, and record the review in `specs/011-gateway-error-handling/validation.md`
- [x] T025 Reconcile `specs/011-gateway-error-handling/spec.md`, `plan.md`, `contracts/gateway-error-http.md`, `tasks.md`, implementation, and test evidence; mark the feature Verified only when all required checks pass

## Dependencies and Execution Order

### Phase Dependencies

- Phase 1 blocks all production work.
- Phase 2 supplies the classifier/writer/security foundation and blocks the User Story 2 unavailable
  scenario and User Story 3 catch-all scenario.
- Phase 3 and Phase 4 can proceed in parallel after T015/T016 pass focused tests.
- Phase 5 requires every selected story checkpoint.

### Within User Story 1

- T003–T010 are test-first tasks and can be authored in parallel because they touch distinct files.
- T011–T013 can be implemented in parallel after their corresponding tests exist.
- T014 depends on T012 and T013.
- T015 depends on T011, T012, and T014's writer API.
- T016 depends on T011, T014, and T015's authentication classifier API.
- T017 validates the complete P1 slice.

### User Story Independence

- **US1** is the MVP and is independently testable without a downstream business service.
- **US2** independently proves the ownership distinction using a test upstream and closed port.
- **US3** independently proves safe diagnostic behavior using a test-only failure filter.

### Parallel Example

```text
After Phase 1:
- T003 GatewayErrorCodeTests.java
- T004 GatewayTraceIdResolverTests.java
- T007 GatewaySecurityErrorHandlerTests.java
- T008 ProductAdminGatewayRouteTests.java

After the US1 foundation passes:
- T018 GatewayProxyPassThroughTests.java
- T019 GatewayDownstreamUnavailableTests.java
- T021 GatewayUnexpectedFailureTests.java
```

## Implementation Strategy

1. Complete and validate US1 as the minimum stable Gateway-owned error slice.
2. Add US2 contract evidence without changing downstream response handling code.
3. Add US3 leakage/failure evidence and review comments.
4. Run the clean module reactor gate and close artifacts only on exit code 0.

No task may introduce service-specific error imports, common-web coupling, a retry/timeout policy,
rate limiting, circuit breaking, a new route, a new production dependency, or a configuration/ADR
change. A discovered need for any of those changes returns to the specification/plan gate.
