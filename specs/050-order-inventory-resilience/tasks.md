# Tasks: Order to Inventory Resilience

**Input**: Design documents from `/specs/050-order-inventory-resilience/`

**Prerequisites**: Approved [spec.md](spec.md), approved [plan.md](plan.md),
[research.md](research.md), [data-model.md](data-model.md),
[contracts/order-inventory-resilience-policy.md](contracts/order-inventory-resilience-policy.md), and
[quickstart.md](quickstart.md)

**Tests**: Unit, configuration, deterministic fault/concurrency, durable-recovery integration,
module regression, and Flash Sale regression checks are required by the approved specification and
plan. Test-first tasks must capture the expected pre-implementation failure before production code
is added.

**Organization**: Tasks are grouped by user story. Work proceeds one task or one coherent task group
at a time, and every completed task records its validation evidence in `validation.md`.

**Status**: Approved for implementation by the project owner on 2026-09-16. Tasks T001–T016 are
complete; the next execution begins with T017.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it touches a different file and has no dependency on another
  incomplete task in the same phase.
- **[Story]**: Maps implementation and verification to a user story in `spec.md`.
- Every task names the exact file or files it owns.

## Phase 1: Setup and Dependency Evidence

**Purpose**: Introduce only the approved service-local dependency and create the evidence ledger.

- [x] T001 Add the managed `io.github.resilience4j:resilience4j-spring-boot3` dependency only to `services/order-service/pom.xml`, run the Order-scoped Maven dependency tree, and stop for a plan update if the effective version is incompatible with the current Spring Boot/Spring Cloud baseline.
- [x] T002 [P] Create the Feature 050 validation ledger with command, scope, result, exit status, and CI/PR placeholders in `specs/050-order-inventory-resilience/validation.md`.

**Checkpoint**: The dependency is bounded to Order Service and its effective version is known before
any resilience behavior is implemented.

---

## Phase 2: Foundational Configuration (Blocking Prerequisites)

**Purpose**: Provide validated, externally configurable circuit-breaker and bulkhead policy shared
by all three user stories.

**⚠️ CRITICAL**: No user-story implementation begins until this phase passes.

- [x] T003 [P] Write configuration-binding and invalid-value tests for all approved defaults and invariants, including fixed zero-wait admission, in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceConfigurationTests.java`, and record the expected pre-implementation failure in `specs/050-order-inventory-resilience/validation.md`.
- [x] T004 Implement immutable startup-validated policy binding for sliding window, minimum calls, failure threshold, open wait, half-open probes, automatic transition, max concurrent calls, and zero-wait admission in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceProperties.java`.
- [x] T005 [P] Add the approved environment-overridable threshold defaults, keep zero-wait admission fixed in code, and do not change the existing 300 ms connect timeout, 800 ms read timeout, or Feign retry policy in `services/order-service/src/main/resources/application.yml`.
- [x] T006 Construct named managed `CircuitBreaker` and semaphore `Bulkhead` instances, keep breaker health contribution disabled, and expose only infrastructure-layer beans in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceConfiguration.java`.
- [x] T007 Run `OrderInventoryResilienceConfigurationTests` with the Maven wrapper and record the command, effective properties, result, and exit status in `specs/050-order-inventory-resilience/validation.md`.

**Checkpoint**: Invalid policy fails startup, valid defaults construct managed instances, readiness is
not coupled to Inventory, and no application/domain class imports Resilience4j.

---

## Phase 3: User Story 1 — Contain an Inventory Outage (Priority: P1) 🎯 MVP

**Goal**: Open the circuit after sustained infrastructure failures, fail later attempts quickly
without invoking Inventory, and preserve valid business rejections.

**Independent Test**: Drive the configured minimum evidence window with remote infrastructure
failures, then prove 95 or more of 100 subsequent attempts finish within 100 ms with zero delegate
invocations; repeat 100 documented business rejections and prove the breaker remains closed.

### Tests for User Story 1

- [x] T008 [US1] Write deterministic tests for success, the three ignored business failures, recorded ambiguous/unavailable failures, open-state fail-fast behavior, zero downstream invocations while open, and the 100-attempt latency target in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/ResilientInventoryRegularHoldClientAdapterTests.java`; record the expected pre-implementation failure in `specs/050-order-inventory-resilience/validation.md`.
- [x] T009 [P] [US1] Write a regular-checkout integration test proving an open circuit creates no false hold, Order, Payment, or completed checkpoint while leaving the request recoverable in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/integration/RegularPurchaseInventoryResilienceIntegrationTests.java`.

### Implementation for User Story 1

- [x] T010 [US1] Implement the explicit `CreateRegularStockHoldPort` decorator, infrastructure-versus-business exception predicate, open-state mapping to `INVENTORY_SERVICE_UNAVAILABLE`, original command/trace pass-through, and no retry/fallback in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/ResilientInventoryRegularHoldClientAdapter.java`.
- [x] T011 [US1] Wire the resilient decorator as the unambiguous primary `CreateRegularStockHoldPort` while retaining `InventoryRegularHoldClientAdapter` as the Feign translator in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceConfiguration.java` and `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/InventoryRegularHoldClientAdapter.java`.
- [x] T012 [US1] Run the User Story 1 unit and integration tests and record breaker counts, delegate invocation counts, p95/maximum fail-fast latency, durable checkpoint outcome, command, result, and exit status in `specs/050-order-inventory-resilience/validation.md`.

**Checkpoint**: US1 is independently demonstrable: outage amplification is contained, business
rejections remain truthful, and no fabricated business effect occurs.

---

## Phase 4: User Story 2 — Recover Without Duplicate Effects (Priority: P1)

**Goal**: Permit bounded half-open probes, return to normal traffic after recovery, and reuse every
original durable business identity.

**Independent Test**: Open the breaker, restore the delegate, advance through one configured cooling
interval, and prove bounded probes close the breaker using the original hold, purchase request,
Order, shopper, item, quantity, and canonical payload identities; a failed probe must reopen it.

### Tests for User Story 2

- [x] T013 [US2] Add deterministic half-open tests for bounded probes, failed-probe reopen, successful-probe close, and business-rejection exclusion in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/ResilientInventoryRegularHoldClientAdapterTests.java`.
- [x] T014 [P] [US2] Extend durable recovery integration coverage to assert exact `holdId`, `purchaseRequestId`, `orderId`, `shopperId`, item IDs, quantities, and canonical fingerprint reuse after circuit-open and ambiguous outcomes in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/integration/RegularPurchaseRecoveryIntegrationTests.java`.

### Implementation for User Story 2

- [x] T015 [US2] Complete bounded open-to-half-open-to-closed/reopened behavior without adding an automatic transition thread, replacement identity, or retry owner in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/ResilientInventoryRegularHoldClientAdapter.java` and `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceConfiguration.java`.
- [x] T016 [US2] Run the User Story 2 state-transition and durable-recovery tests and record probe counts, state transitions, identity equality, duplicate-effect assertions, command, result, and exit status in `specs/050-order-inventory-resilience/validation.md`.

**Checkpoint**: US2 is independently demonstrable: a transient outage recovers without restart and
without duplicate or replacement business effects.

---

## Phase 5: User Story 3 — Protect Concurrency and Diagnose State (Priority: P2)

**Goal**: Bound concurrent Inventory calls with zero-wait admission and provide low-cardinality
signals that distinguish remote failure, open circuit, saturation, and recovery.

**Independent Test**: Block admitted delegate calls, submit more callers than the configured limit,
and prove concurrent delegate execution never exceeds that limit, excess calls fail immediately,
Order remains ready, and metrics/logs expose only bounded sanitized state/outcome dimensions.

### Tests for User Story 3

- [x] T017 [P] [US3] Write a latch-controlled concurrency test for max admitted calls, zero-wait excess rejection, and no duplicate delegate execution in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/InventoryRegularHoldBulkheadTests.java`.
- [x] T018 [P] [US3] Write observability/readiness tests for breaker states, bulkhead saturation, fixed metric dimensions, sanitized transition logs, and Inventory-independent readiness in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/integration/OrderInventoryResilienceObservabilityTests.java`.

### Implementation for User Story 3

- [x] T019 [US3] Compose the zero-wait semaphore bulkhead outside the circuit breaker and map `BulkheadFullException` to the existing recoverable `INVENTORY_SERVICE_UNAVAILABLE` outcome in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/ResilientInventoryRegularHoldClientAdapter.java`.
- [x] T020 [US3] Add bounded circuit transition and rejection logging with only fixed state/outcome values plus normalized trace ID in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceEventLogger.java` and register it in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/OrderInventoryResilienceConfiguration.java`.
- [x] T021 [P] [US3] Document the protection flow, environment variables, metric-state interpretation, readiness independence, tuning rules, and prohibited high-cardinality labels in `services/order-service/README.md`.
- [x] T022 [US3] Run the User Story 3 concurrency and observability tests and record maximum observed concurrency, rejection latency, readiness result, exported bounded metric names, redaction assertions, command, result, and exit status in `specs/050-order-inventory-resilience/validation.md`.

**Checkpoint**: US3 is independently demonstrable: one slow dependency cannot occupy unbounded
Order callers, and operators can diagnose the protection state without sensitive identifiers.

---

## Phase 6: Polish, Fault Drill, and Regression Gates

**Purpose**: Package repeatable validation, prove compatibility, and close the evidence ledger.

- [ ] T023 Create a repeatable PowerShell runner for the focused configuration, outage, recovery, concurrency, observability, and identity-preservation checks in `infra/docker/smoke/feature-050-order-inventory-resilience.ps1`.
- [ ] T024 Run `infra/docker/smoke/feature-050-order-inventory-resilience.ps1`, require every Feature 050 marker to pass, and record measured evidence plus exit status in `specs/050-order-inventory-resilience/validation.md`.
- [ ] T025 Run the managed Resilience4j dependency tree check and audit that no global OpenFeign circuit-breaker switch, retry, fallback, TimeLimiter, RateLimiter, manual `PrometheusMeterRegistry`, database migration, Kafka schema/topic, Redis, or public/internal HTTP contract change was introduced; record the audit in `specs/050-order-inventory-resilience/validation.md`.
- [ ] T026 Run `.\mvnw.cmd -pl services/order-service -am verify` and record the exact command, reactor scope, test totals, result, and exit status in `specs/050-order-inventory-resilience/validation.md`.
- [ ] T027 Run `.\mvnw.cmd -pl services/flash-sale-service,services/order-service -am verify` to cover the required Flash Sale and Order regressions, then record the exact command, reactor scope, test totals, result, and exit status in `specs/050-order-inventory-resilience/validation.md`.
- [ ] T028 Re-run every command in `specs/050-order-inventory-resilience/quickstart.md`, reconcile completed checkboxes with captured evidence, and document rollback rehearsal results and any deferred cloud-only verification in `specs/050-order-inventory-resilience/validation.md` and `specs/050-order-inventory-resilience/tasks.md`.

**Checkpoint**: Feature 050 may be reported complete only when all required local gates pass and no
checked task lacks evidence. Cloud-only work, if unavailable, remains explicitly uncompleted rather
than being inferred from local success.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: Starts immediately after task approval.
- **Phase 2 — Foundational**: Depends on T001–T002 and blocks every user story.
- **Phase 3 — US1**: Depends on Phase 2 and is the MVP.
- **Phase 4 — US2**: Depends on US1 because it extends the same breaker and decorator state machine,
  but remains independently verifiable through recovery tests.
- **Phase 5 — US3**: Depends on US1's decorator; its test files and documentation tasks marked `[P]`
  can be prepared independently after Phase 2.
- **Phase 6 — Polish**: Depends on all selected user stories.

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 (outage containment) -> US2 (recovery)
                                      |
                                      +-----------> US3 (concurrency/diagnostics)
US1 + US2 + US3 -> Fault drill and regression gates
```

### Within Each User Story

1. Write the mapped test and capture its expected pre-implementation failure where required.
2. Implement only the behavior owned by that story.
3. Run the focused tests and capture evidence before moving to the next story.
4. Preserve the unchanged HTTP contract, original identities, existing timeout, and single durable
   recovery owner throughout.

## Parallel Opportunities

- T002 can run beside T001 because it creates only the evidence ledger.
- T003 and T005 can run in parallel after T001 because they touch separate test/configuration files.
- T009 can be prepared beside T008 because it is a separate integration test file.
- T014 can be prepared while T013 is implemented because it extends a separate recovery test file.
- T017, T018, and T021 can run in parallel after the foundational configuration and US1 decorator
  contracts are stable.
- Validation tasks that append to `validation.md` are intentionally sequential to avoid evidence
  conflicts.

## Parallel Example: User Story 3

```text
Task T017: Write latch-controlled bulkhead concurrency tests.
Task T018: Write metrics, sanitized-log, and readiness integration tests.
Task T021: Document runtime controls and operator interpretation.
```

## Implementation Strategy

### MVP First — User Story 1

1. Complete T001–T007.
2. Complete T008–T012.
3. Stop and demonstrate deterministic breaker opening, truthful business rejection handling, and
   fail-fast behavior before implementing recovery or observability refinements.

### Incremental Delivery

1. **Foundation**: dependency, validated policy, managed instances.
2. **US1**: outage containment with no false business effects.
3. **US2**: bounded recovery and exact-identity replay.
4. **US3**: concurrency containment and operational evidence.
5. **Final gates**: repeatable fault drill, regressions, audit, rollback evidence.

## Notes

- `[P]` means different files with no incomplete same-phase dependency; it does not waive review or
  evidence requirements.
- No task authorizes an API, Kafka, database, Redis, Payment, Flash Sale behavior, or service-boundary
  change.
- Do not enable `spring.cloud.openfeign.circuitbreaker.enabled` globally.
- Do not add automatic retry, fallback stock, TimeLimiter, RateLimiter, or a shared resilience
  library.
- Commit after each approved task or coherent group, and never mark a task complete while its
  required test fails or its validation evidence is absent.
