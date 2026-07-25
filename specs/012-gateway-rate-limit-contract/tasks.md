# Tasks: Gateway Rate-Limit Error Contract

**Input**: Approved artifacts from `specs/012-gateway-rate-limit-contract/`
**Prerequisites**: `spec.md`, `plan.md`, `research.md`,
`contracts/gateway-rate-limit-error-http.md`, `quickstart.md`, and requirements checklist
**Task status**: Verified; all 9 tasks completed after approval by the Gateway/Platform owner through the explicit
2026-07-22 request to add the 429 contract and standardize future tracing documentation on
Micrometer Tracing with OpenTelemetry.

## Format: `[ID] [P?] [Story] Description (trace references)`

- **[P]**: Safe to run in parallel because files and unfinished dependencies do not overlap.
- **[Story]**: Owning independently testable user story.
- Test-first evidence is required for the public taxonomy and writer contract.

## Phase 1: Artifact Readiness

- [x] T001 Confirm `.specify/feature.json` targets `specs/012-gateway-rate-limit-contract`, the spec and plan are Approved, 14/14 requirement checks pass, no blocking clarification remains, and record the gate in `specs/012-gateway-rate-limit-contract/validation.md`
- [x] T002 Confirm `specs/012-gateway-rate-limit-contract/contracts/gateway-rate-limit-error-http.md` defines the additive 429 row before production code and explicitly defers quota, TTL, key, Redis failure, and rate-limit-header semantics (FR-001 through FR-006)

**Checkpoint**: The public contract and non-goals are approved before code.

---

## Phase 2: User Story 1 - Stable Rate-Limit Rejection Contract (Priority: P1)

**Goal**: Add an exact, safe, traceable `RATE_LIMIT_EXCEEDED` vocabulary row without activating a limiter.

**Independent Test**: Render the new code through `GatewayHttpErrorWriter` and verify exact HTTP 429,
content type, three-field body, and existing trace behavior; proxy a downstream 429 and verify it remains unchanged.

**Trace set**: FR-001 through FR-006, NFR-001, NFR-002, SC-001, SC-002

### Test-first contract work

- [x] T003 [US1] Extend `services/api-gateway/src/test/java/com/philia/flashsale/gateway/error/GatewayErrorCodeTests.java` and `GatewayHttpErrorWriterTests.java` with the exact eighth 429 row/body assertions plus absence of unapproved `Retry-After`, `RateLimit`, and `X-RateLimit-*` headers; run the focused command and record the expected pre-implementation failure in `specs/012-gateway-rate-limit-contract/validation.md` (FR-001 through FR-004, NFR-001)
- [x] T004 [US1] Extend `services/api-gateway/src/test/java/com/philia/flashsale/gateway/GatewayProxyPassThroughTests.java` with a downstream 429 status/body pass-through case (FR-005, NFR-002)

### Implementation

- [x] T005 [US1] Add only `RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests")` to `services/api-gateway/src/main/java/com/philia/flashsale/gateway/error/GatewayErrorCode.java`; do not modify `GatewayFailureClassifier` or add a limiter/filter/header policy (FR-001, FR-002, FR-006)
- [x] T006 [US1] Run the focused taxonomy/writer and downstream proxy commands from `specs/012-gateway-rate-limit-contract/quickstart.md` and record command, scope, exit status, and result in `validation.md` (SC-001, SC-002)

**Checkpoint**: The eighth code is independently renderable, and downstream 429 ownership is unchanged.

---

## Phase 3: Cross-Cutting Tracing Standard Documentation

**Goal**: Make the planned distributed-tracing layers and current implementation status unambiguous.

**Independent Test**: Documentation review identifies Micrometer Tracing as the application API,
OpenTelemetry as bridge/export implementation, OTLP as transport, and root-owned Collector as runtime infrastructure, while stating that no tracing runtime dependency is installed by this feature.

**Trace set**: FR-007, FR-008, NFR-003, SC-003

- [x] T007 Update `docs/technology/technology-problem-map.md` to document Micrometer Tracing -> OpenTelemetry bridge/OTLP exporter -> OpenTelemetry Collector -> Tempo, W3C propagation, and the rule against direct OpenTelemetry SDK coupling in application/business/policy code; retain Planned status (FR-007, FR-008, NFR-003)

**Checkpoint**: Documentation reflects the approved standard without claiming runtime tracing is implemented.

---

## Final Phase: Verification and Closure

- [x] T008 Run `.\mvnw.cmd -pl services/api-gateway -am verify` and record reactor scope, exit code, test count, and result in `specs/012-gateway-rate-limit-contract/validation.md`
- [x] T009 Reconcile `spec.md`, `plan.md`, `research.md`, the 429 contract, technology documentation, code, tests, and evidence; confirm no rate limiter/tracing dependency/configuration slipped into scope, then mark artifacts Verified

## Dependencies and Execution Order

- T001 and T002 block production code.
- T003 and T004 are test-first and precede T005.
- T006 follows T005.
- T007 is documentation-only and can run after the artifact gate independently of T003-T006.
- T008 and T009 follow all earlier tasks.

## Evidence Record

Evidence is recorded in `specs/012-gateway-rate-limit-contract/validation.md`; checked tasks do not replace command results.

## Notes

- Do not edit verified Feature 011 artifacts; Feature 012 additively resolves its deferred 429 item.
- Do not add Redis, limiter filters, quota/TTL, `Retry-After`, `RATE_LIMITING_UNAVAILABLE`, tracing dependencies, OTLP configuration, or Collector assets.
- Do not classify arbitrary exceptions as `RATE_LIMIT_EXCEEDED`; a future deliberate limiter rejection must select it explicitly.
