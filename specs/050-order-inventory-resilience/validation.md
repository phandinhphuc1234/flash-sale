# Validation: Order to Inventory Resilience

## Evidence policy

For every completed task, record the exact command, scope, result, exit status, and relevant
CI/PR reference. A checked task without corresponding evidence is not complete. Secret values,
authorization headers, business identifiers, and raw request bodies must not be recorded.

## Review context

- Feature: `050-order-inventory-resilience`
- Source branch: `codex/order-inventory-resilience`
- Review PR: [#134](https://github.com/phandinhphuc1234/flash-sale/pull/134)
- Specification approved: 2026-09-16
- Plan approved: 2026-09-16
- Tasks approved: 2026-09-16

## T001 — Managed dependency compatibility

- Command: `.\mvnw.cmd -pl services/order-service -am dependency:tree "-Dincludes=io.github.resilience4j"`
- Scope: Maven reactor modules `flash-sale-engine`, `common-web`, `kafka-avro-contracts`, and
  `order-service`; dependency is declared only in `services/order-service/pom.xml`.
- Result: PASS — dependency management resolved `resilience4j-spring-boot3:2.2.0` and its
  Resilience4j modules consistently at `2.2.0`; no service-local version override was added.
- Exit status: `0`
- Completed: 2026-09-16
- CI/PR: PR #134; branch evidence pending push.

## T002 — Evidence ledger

- Command: Spec Kit artifact and checklist validation performed before production edits.
- Scope: `specs/050-order-inventory-resilience/` and `.specify/feature.json`.
- Result: PASS — active feature resolved correctly; `requirements.md` completed 16/16; no extension
  hooks registered; repository ignore rules already cover Java, Docker, Terraform state, local
  secrets, IDE files, and generated outputs.
- Exit status: `0`
- CI/PR: PR #134; implementation CI pending future commits.
- Completed: 2026-09-16

## T003 — Configuration tests before implementation

- Command: `.\mvnw.cmd -pl services/order-service -am test "-Dtest=OrderInventoryResilienceConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Scope: New Feature 050 configuration binding and managed-instance tests in Order Service.
- Result: EXPECTED FAIL — test compilation reported the intentionally absent
  `OrderInventoryResilienceProperties` and `OrderInventoryResilienceConfiguration` types. Upstream
  `common-web` and `kafka-avro-contracts` compilation passed; no unrelated failure was observed.
- Exit status: `1` (expected red phase)
- Completed: 2026-09-16
- CI/PR: PR #134; branch evidence pending push.

## T007 — Foundational configuration verification

### Policy decision and evidence boundary

- Owner decision: bulkhead permit wait is fixed at `0ms`; the per-replica concurrency threshold is
  externally configurable and starts at `16`.
- Feature 046 evidence: a short local Flash Sale-path run at 100 RPS reported p95 `54.563 ms`, zero
  HTTP/unexpected errors, and zero dropped iterations. Little's Law gives approximately 5.5
  in-flight requests at that measured end-to-end latency, but this is not a direct measurement of
  the regular Order-to-Inventory call.
- Feature 049 evidence: `RegularHoldLoadTests` passed 100 concurrent identical Inventory holds with
  a test-only 32-thread executor and 32-connection Hikari pool, proving idempotency/stock correctness
  but recording no latency or production-capacity result.
- Decision: `16` is a conservative starting limit for the current one-replica, one-CPU Order
  deployment. It provides headroom over the available healthy-path estimate without treating the
  test-only 32-thread fixture as a production default. A dedicated dependency benchmark is still
  required before claiming or further increasing capacity.

### T004–T006 implementation

- Immutable policy binding validates window size, minimum evidence, failure threshold, positive
  open duration, half-open probes, and the configurable positive concurrency limit during startup.
- `application.yml` exposes the approved thresholds and explicitly leaves the existing Inventory
  Feign `300 ms` connect timeout, `800 ms` read timeout, and `Retryer.NEVER_RETRY` behavior intact.
- Named process-local CircuitBreaker and semaphore Bulkhead instances are constructed through
  service-local registries. Permit wait is fixed with `Duration.ZERO`, and circuit-breaker health
  contribution is explicitly disabled so Inventory isolation cannot make Order unready.

- Command: `.\mvnw.cmd -pl services/order-service -am test "-Dtest=OrderInventoryResilienceConfigurationTests,OrderServiceApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false"`
- Scope: Feature 050 binding/configuration plus the full Order Service Spring application context;
  upstream reactor modules `common-web` and `kafka-avro-contracts` were included with `-am`.
- Effective defaults: count window `20`, minimum calls `10`, failure threshold `50%`, open wait
  `10s`, half-open probes `2`, automatic transition `false`, max concurrent calls `16`, fixed permit
  wait `0ms`.
- Result: PASS — 5 tests executed, 0 failures, 0 errors, 0 skipped. Both invalid-property cases
  failed their child application contexts as expected. The full Order application context started
  successfully with Spring Boot `3.5.16`.
- Exit status: `0`
- Boundary audit: PASS — Resilience4j imports are absent from `regularpurchase/application` and
  `regularpurchase/domain`; no global OpenFeign circuit-breaker property exists; the existing
  Inventory `Retryer.NEVER_RETRY` remains present; only Order Service declares
  `resilience4j-spring-boot3`; no configurable bulkhead-wait property remains; the `300/800 ms`
  Inventory timeouts are unchanged; breaker health is disabled; `git diff --check` passed.
- Completed: 2026-09-16
- CI/PR: PR #134; branch evidence pending push.

## T008–T009 — User Story 1 tests before implementation

- Command: `.\mvnw.cmd -pl services/order-service -am test
  "-Dtest=ResilientInventoryRegularHoldClientAdapterTests,RegularPurchaseInventoryResilienceIntegrationTests"
  "-Dsurefire.failIfNoSpecifiedTests=false"`
- Scope: deterministic Circuit Breaker behavior plus the regular-checkout durable boundary while
  the Inventory circuit is open.
- Result: EXPECTED FAIL — test compilation reported the intentionally absent
  `ResilientInventoryRegularHoldClientAdapter`; upstream `common-web` and
  `kafka-avro-contracts` compilation passed and no unrelated failure was observed.
- Exit status: `1` (expected red phase)
- Completed: 2026-09-16
- CI/PR: [PR #135](https://github.com/phandinhphuc1234/flash-sale/pull/135).

## T010–T012 — User Story 1 outage containment

### Behavior implemented

- The primary `CreateRegularStockHoldPort` is an explicit infrastructure decorator around the
  existing Feign translator. It forwards the original command object and trace value exactly once
  for an admitted call and adds no retry or fallback.
- `INVENTORY_INSUFFICIENT_STOCK`, `INVENTORY_ITEM_NOT_FOUND`, and
  `INVENTORY_HOLD_CONFLICT` are ignored by Circuit Breaker accounting. Ambiguous, unavailable, and
  unexpected outbound runtime failures are recorded.
- An open circuit maps to the existing sanitized, recoverable
  `INVENTORY_SERVICE_UNAVAILABLE` outcome without invoking Inventory.
- Spring selects the decorator as the single primary application port while retaining
  `InventoryRegularHoldClientAdapter` as the raw Feign/HTTP translator.

### Focused verification

- Command: `.\mvnw.cmd -pl services/order-service -am test
  "-Dtest=OrderInventoryResilienceConfigurationTests,ResilientInventoryRegularHoldClientAdapterTests,InventoryRegularHoldClientAdapterTests,RegularPurchaseInventoryResilienceIntegrationTests,RegularPurchaseRecoveryIntegrationTests,OrderServiceApplicationTests"
  "-Dsurefire.failIfNoSpecifiedTests=false"`
- Scope: Spring bean selection/application context, raw Feign translation, deterministic breaker
  classification and opening, open-state rejection, durable regular-purchase checkpoint, and
  existing recovery behavior.
- Result: PASS — 17 tests executed, 0 failures, 0 errors, 0 skipped; reactor modules
  `flash-sale-engine`, `common-web`, `kafka-avro-contracts`, and `order-service` succeeded.
- Exit status: `0`
- Breaker evidence: two recorded infrastructure failures opened the two-call deterministic test
  circuit; 100 ignored invocations for each documented business failure left it closed with zero
  recorded failures; one successful call remained successful.
- Open-state evidence: 100/100 attempts finished below 100 ms, p95 `5 ms`, maximum `72 ms`, zero
  delegate invocations, and 100 not-permitted calls.
- Durable evidence: repeated open-state checkout attempts left the request at
  `PRODUCT_VALIDATED`, retained the same request/hold/Order identities, created no hold, Order,
  Payment intent, or completed acceptance, made zero Inventory calls, and remained recoverable.
- Completed: 2026-09-16
- CI/PR: [PR #135](https://github.com/phandinhphuc1234/flash-sale/pull/135).

## T013–T016 — User Story 2 bounded recovery and identity reuse

### Recovery behavior

- A mutable test clock advances beyond the configured cooling boundary without sleeping or adding
  an automatic transition thread. The circuit remains `OPEN` until the next caller requests a
  permit, then changes to `HALF_OPEN`.
- Exactly two configured half-open probes are admitted concurrently; a third probe is rejected
  without reaching Inventory. Two successful probes close the circuit, while two failed probes
  reopen it.
- An Inventory business rejection during half-open is ignored by breaker accounting. It remains a
  truthful business result and does not prevent the following two successful probes from closing
  the circuit.
- The existing T006/T010 managed Circuit Breaker and explicit port decorator already provide the
  approved state machine, same-command forwarding, no automatic transition, and no retry owner.
  T015 therefore required no additional production behavior or dependency.

### Durable identity evidence

- Two ambiguous Inventory outcomes open the circuit; a request while open makes zero additional
  delegate calls; one explicitly admitted recovery probe succeeds and closes it.
- The three delegate invocations use equal `holdId`, `purchaseRequestId`, `orderId`, `shopperId`,
  item IDs, quantities, and trace-preserving command content. The durable request fingerprint is
  unchanged, no replacement identity is generated, and the final request state is `ACCEPTED`.

### Focused verification

- Command: `.\mvnw.cmd -pl services/order-service -am test
  "-Dtest=ResilientInventoryRegularHoldClientAdapterTests,RegularPurchaseRecoveryIntegrationTests"
  "-Dsurefire.failIfNoSpecifiedTests=false"`
- Scope: deterministic open/cooling/half-open/closed/reopened transitions, bounded probe admission,
  business-failure exclusion, ambiguous recovery, and exact durable identity reuse.
- Result: PASS — 12 tests executed, 0 failures, 0 errors, 0 skipped; all four reactor modules
  succeeded.
- Exit status: `0`
- Probe counts: configured `2`, admitted `2`, rejected `1`; successful recovery ended `CLOSED`;
  failed recovery ended `OPEN`.
- Identity counts: `3` Inventory delegate calls across two ambiguous attempts and one recovery
  probe; `0` replacement identities; final durable state `ACCEPTED`.
- Combined command: `.\mvnw.cmd -pl services/order-service -am test
  "-Dtest=OrderInventoryResilienceConfigurationTests,ResilientInventoryRegularHoldClientAdapterTests,RegularPurchaseInventoryResilienceIntegrationTests,RegularPurchaseRecoveryIntegrationTests"
  "-Dsurefire.failIfNoSpecifiedTests=false"`
- Combined result: PASS — 17 Feature 050 foundation, outage-containment, durable-checkpoint, and
  recovery tests executed with 0 failures, 0 errors, and 0 skipped; exit status `0`.
- Completed: 2026-09-16
- CI/PR: [PR #136](https://github.com/phandinhphuc1234/flash-sale/pull/136).

## T017–T022 — User Story 3 Bulkhead and bounded observability

### Protection and telemetry behavior

- The explicit decorator now composes a semaphore Bulkhead outside the named Circuit Breaker. The
  Bulkhead admits at most the configured per-pod calls and uses fixed zero-wait admission. A full
  Bulkhead maps to the existing recoverable `INVENTORY_SERVICE_UNAVAILABLE` outcome without
  invoking the Inventory delegate; an open Circuit Breaker remains the same safe outcome.
- Resilience4j tagged Micrometer binders expose the named Circuit Breaker and Bulkhead metrics
  through the existing registry. The tests allow only fixed `name`, `kind`, and `state` dimensions
  and verify the standard calls/not-permitted/state and bulkhead available/max metric names.
- `OrderInventoryResilienceEventLogger` subscribes to the Circuit Breaker state-transition publisher
  for race-free `CLOSED`, `OPEN`, and `HALF_OPEN` transitions. Rejections use only the fixed
  `open_circuit` and `bulkhead_full` outcomes. Trace values are normalized; shopper/order/hold/
  purchase IDs, tokens, request bodies, and URLs are not logged or labelled.
- Order readiness continues to check PostgreSQL and Order's own indicator. An `OPEN` Inventory
  breaker with a healthy PostgreSQL signal leaves readiness `UP`, while Inventory remains isolated.

### Focused verification

- Command: `.\mvnw.cmd -pl services/order-service -am test
  "-Dtest=OrderInventoryResilienceConfigurationTests,ResilientInventoryRegularHoldClientAdapterTests,RegularPurchaseInventoryResilienceIntegrationTests,RegularPurchaseRecoveryIntegrationTests,InventoryRegularHoldBulkheadTests,OrderInventoryResilienceObservabilityTests"
  "-Dsurefire.failIfNoSpecifiedTests=false"`
- Scope: managed configuration, existing outage/recovery/identity regression tests, two-permit
  latch-controlled concurrency, 100 zero-wait saturation rejections, Resilience4j metric binding,
  sanitized transition/rejection logs, and readiness independence.
- Result: PASS — 21 tests executed, 0 failures, 0 errors, 0 skipped; all four reactor modules
  (`flash-sale-engine`, `common-web`, `kafka-avro-contracts`, `order-service`) succeeded.
- Bulkhead evidence: configured/admitted maximum `2`, observed maximum `2`, `100/100` excess
  attempts rejected without delegate entry, p95 rejection `11 ms`, maximum `45 ms`, delegate
  calls remained `2`, and the bulkhead available gauge reached `0` while both calls were held.
- Observability evidence: exported names included
  `resilience4j.circuitbreaker.calls`, `resilience4j.circuitbreaker.not.permitted.calls`,
  `resilience4j.circuitbreaker.state`, `resilience4j.bulkhead.available.concurrent.calls`, and
  `resilience4j.bulkhead.max.allowed.concurrent.calls`; no unbounded identifier or secret appeared
  in metric tags or captured logs. Logs distinguished `stateFrom=CLOSED stateTo=OPEN`,
  `outcome=open_circuit`, and `outcome=bulkhead_full`.
- Readiness evidence: Inventory breaker `OPEN`, Order readiness `UP` with PostgreSQL available,
  consumer diagnostics non-gating.
- Exit status: `0`
- Completed: 2026-09-17
- CI/PR: pending push.

## T023–T026 — Repeatable runner, dependency audit, and Order regression gate

### Repeatable Feature 050 runner

- Command: `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-050-order-inventory-resilience.ps1 -Scenario Static`
- Result: PASS — `FEATURE_050_STATIC=PASS`; the static gate found the approved decorator composition,
  fail-fast bulkhead, breaker/full rejection mapping, bounded logging, and no secret/external-state
  access.
- Command: `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-050-order-inventory-resilience.ps1 -Scenario All -TimeoutSeconds 900`
- Result: PASS — `FEATURE_050_STATIC=PASS`, `FEATURE_050_FAULT=PASS`,
  `FEATURE_050_RECOVERY=PASS`, `FEATURE_050_BULKHEAD=PASS`,
  `FEATURE_050_OBSERVABILITY=PASS`, `FEATURE_050_ALL=PASS`.
- Exit status: `0`; the runner completed without reading secrets or changing Docker, Kubernetes,
  database, Kafka, Redis, or cloud state.

### Dependency and boundary audit

- Command: `.\mvnw.cmd -pl services/order-service -am dependency:tree "-Dincludes=io.github.resilience4j"`
- Result: PASS — the effective managed dependency is Resilience4j `2.2.0`, brought in by the
  service-local `resilience4j-spring-boot3` dependency and its expected modules.
- Audit result: PASS — no global OpenFeign circuit-breaker switch, automatic retry, fallback,
  TimeLimiter, RateLimiter, manual `PrometheusMeterRegistry`, migration, Kafka schema/topic,
  Redis, or HTTP contract change was introduced. No `pom.xml` or contract file changed in the
  resilience implementation. Existing `300/800 ms` Inventory timeouts and `Retryer.NEVER_RETRY`
  remain unchanged.
- Exit status: `0`.

### Full Order module verification

- Command: `.\mvnw.cmd -pl services/order-service -am verify`
- Reactor scope: `flash-sale-engine`, `common-web`, `kafka-avro-contracts`, `order-service`.
- Result: PASS — `Tests run: 228, Failures: 0, Errors: 0, Skipped: 8`; every reactor module
  succeeded and the packaged Order artifact was produced.
- Exit status: `0`; elapsed time approximately `11m42s` because Testcontainers created isolated
  PostgreSQL instances for integration contexts. Kafka connection warnings were expected for tests
  that intentionally run without a broker and did not fail the build.
- Completed: 2026-09-17

### Flash Sale and Order regression verification

- Command: `.\mvnw.cmd -pl services/flashsale-service,services/order-service -am verify`
- Reactor scope: `flash-sale-engine`, `common-web`, `kafka-avro-contracts`, `flashsale-service`,
  `order-service`.
- Result: PASS — Flash Sale `129` tests, `0` failures, `0` errors, `1` skipped; Order `228`
  tests, `0` failures, `0` errors, `8` skipped. All five reactor modules succeeded and both
  packaged service artifacts were produced.
- Exit status: `0`; elapsed time approximately `14m26s`. Testcontainers created isolated
  PostgreSQL/Redis instances for the integration suites. Kafka connection warnings were expected
  for tests without a broker and did not fail the build.
- Completed: 2026-09-17

## T028 — Quickstart reconciliation and rollback boundary

- Context commands:
  `Get-Content .specify\\feature.json` returned `specs/050-order-inventory-resilience`; branch
  was `codex/order-inventory-resilience-final-gates`; `git diff --check` passed.
- Re-run results: the dependency-tree command, Feature 050 `Static` and `All` runner commands,
  Order module verify, and Flash Sale + Order verify all passed with the evidence above. The
  dependency-tree command is intentionally quoted in PowerShell so `-Dincludes=...` is passed as
  one Maven argument.
- Rollback rehearsal: local boundary/static and identity-preservation checks passed; no database,
  Kafka, Redis, HTTP contract, or external cloud state was changed. A real EKS image rollback was
  not executed because this feature gate has no active cloud environment; cloud rollback remains
  explicitly deferred and must be validated operationally before a production rollout.
- Result: PASS for all available local gates; cloud-only rollback evidence is DEFERRED, not inferred.
- Completed: 2026-09-17
