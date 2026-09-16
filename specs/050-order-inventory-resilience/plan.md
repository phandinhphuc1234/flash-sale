# Implementation Plan: Order to Inventory Resilience

**Branch**: `codex/order-inventory-resilience` | **Date**: 2026-09-16 | **Spec**: [spec.md](spec.md)

**Input**: Approved feature specification from `specs/050-order-inventory-resilience/spec.md`

**Status**: Approved for task generation — project owner approved this plan on 2026-09-16. Production implementation remains gated by the approved dependency-ordered `tasks.md`.

## Summary

Protect only the synchronous Order-to-Inventory regular-stock-hold capability with a service-local
Resilience4j circuit breaker and semaphore bulkhead. The existing OpenFeign adapter remains the HTTP
translator, the existing 300 ms connect and 800 ms read timeouts remain authoritative, and no client
retry or fallback is added. The protection is implemented as an explicit outbound-port decorator so
business rejections can be excluded from breaker statistics while transport and remote-service
failures are counted. Circuit-open and bulkhead-full outcomes map to the existing recoverable
`INVENTORY_SERVICE_UNAVAILABLE` failure. Feature 049 remains the sole durable retry/recovery owner.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.x

**Primary Dependencies**: Existing Spring Cloud OpenFeign and Micrometer/Actuator; add
`io.github.resilience4j:resilience4j-spring-boot3` to `order-service` only. The imported Spring Cloud
2025.0.3 dependency management currently resolves the compatible Resilience4j 2.2.x line; the
effective dependency must be recorded during implementation validation.

**Storage**: No new storage. PostgreSQL checkpoints from Feature 049 remain the durable source of
truth; breaker and bulkhead state are process-local.

**Testing**: JUnit 5, AssertJ, Mockito, Spring Boot configuration binding tests, existing PostgreSQL
Testcontainers integration tests, deterministic concurrency tests, and the existing Feature 049
regular-purchase regression suites.

**Target Platform**: Linux containers on Kubernetes; local Maven verification on Windows is also
supported.

**Project Type**: Maven monorepo with independently deployable Spring Boot microservices.

**Performance Goals**: When the breaker is open or the bulkhead is full, at least 95% of 100 local
fault-injection attempts return the existing safe failure within 100 ms and do not call Inventory.

**Constraints**: No global OpenFeign circuit-breaker switch; no automatic retry, fallback stock,
TimeLimiter, RateLimiter, database/schema/event/API change, readiness dependency, shared resilience
library, or change to the 300/800 ms network timeouts.

**Scale/Scope**: One outbound port, one adapter/decorator pair, one configuration namespace, one
service module, and the existing regular-purchase recovery path.

## Constitution Check

*GATE: Passed before research and re-checked after design.*

- **Specification traceability — PASS**: The design maps directly to FR-001 through FR-016 and does
  not add observable behavior beyond the approved failure isolation.
- **Service ownership — PASS**: Order owns the caller-side policy; Inventory continues to own stock
  and holds. No persistence or boundary ownership changes.
- **Communication — PASS**: The documented OpenFeign HTTP contract and Kubernetes DNS remain
  unchanged. No new synchronous or asynchronous relationship is introduced.
- **Data and messaging — PASS**: PostgreSQL recovery checkpoints and idempotent hold identities are
  preserved. Kafka, Redis, outbox, and inbox behavior are unchanged.
- **Root infrastructure ownership — PASS**: Runtime policy is service-owned configuration. A cloud
  environment override, if later required, belongs under `infra/k8s`; this plan does not require one.
- **Observability — PASS**: Existing Actuator and Prometheus integration is reused. No registry is
  constructed in business code and no business identifier becomes a metric label.
- **Contracts and dependencies — PASS**: The only new production dependency is justified below.
  Public/internal payloads and event contracts remain unchanged.
- **Validation — PASS**: Unit, configuration, deterministic fault/concurrency, module, and Feature
  049 regressions are required. Kafka/schema/migration tests are omitted because those surfaces do
  not change.

No constitutional violation or ADR is required. A later cross-service resilience platform would be
a separate architectural decision.

## Research Decisions

Detailed evidence and rejected alternatives are in [research.md](research.md).

### D1 — Protect the outbound capability through an explicit decorator

Create `ResilientInventoryRegularHoldClientAdapter` implementing `CreateRegularStockHoldPort`. It
delegates to the existing `InventoryRegularHoldClientAdapter` and composes the semaphore bulkhead
outside the circuit breaker:

```text
RegularPurchase use case
  -> CreateRegularStockHoldPort
  -> semaphore bulkhead (zero wait)
  -> circuit breaker
  -> existing InventoryRegularHoldClientAdapter
  -> OpenFeign client
  -> Inventory Service
```

This keeps resilience in the outbound adapter boundary, leaves the application use case unaware of
Resilience4j, and avoids enabling Spring Cloud OpenFeign circuit breakers for unrelated clients.

### D2 — Preserve failure meaning

- Ignore breaker statistics for `INVENTORY_INSUFFICIENT_STOCK`, `INVENTORY_ITEM_NOT_FOUND`, and
  `INVENTORY_HOLD_CONFLICT` because they are authoritative business outcomes.
- Record `INVENTORY_HOLD_AMBIGUOUS`, `INVENTORY_SERVICE_UNAVAILABLE`, and unexpected runtime
  failures because they indicate dependency or integration instability.
- Convert `CallNotPermittedException` and `BulkheadFullException` to the existing
  `INVENTORY_SERVICE_UNAVAILABLE` outcome. Do not claim stock was rejected and do not fabricate a
  hold.
- Do not retry inside the decorator. The existing durable recovery job re-enters the same use case
  with the original identities.

### D3 — Conservative externally configurable defaults

Initial defaults, subject to deterministic test evidence:

| Control | Default | Reason |
|---|---:|---|
| Sliding-window type | count-based | Deterministic first slice and easy fault testing |
| Sliding-window size | 20 calls | Enough evidence without prolonged outage amplification |
| Minimum calls | 10 | Prevents opening on one or two failures |
| Failure-rate threshold | 50% | Opens after sustained mixed failure, not a single error |
| Open-state wait | 10 seconds | Short bounded recovery interval for the internal service |
| Half-open permitted calls | 2 | Bounded recovery probes |
| Automatic open-to-half-open transition | false | Avoids a dedicated transition thread; the next call probes |
| Bulkhead max concurrent calls | 8 | Bounded caller pressure; validate against pool/load evidence |
| Bulkhead max wait | 0 | Fail fast instead of creating another queue |
| Health indicator | false | Inventory failure must not make Order unready |

All values bind under `order.regular-purchase.inventory-resilience`, are validated at startup, and
are overridable through environment variables. Invalid values fail startup rather than silently
disabling protection.

### D4 — Metrics and logs stay bounded

Use Resilience4j Micrometer integration exposed by the existing Prometheus endpoint. Add transition
and rejection logs with only fixed state/outcome names plus the existing normalized trace ID. Never
label metrics with shopper, order, hold, purchase-request, token, request body, or raw URL values.

## Dependency Decision

Add `io.github.resilience4j:resilience4j-spring-boot3` only to
`services/order-service/pom.xml`, without an inline version when dependency management resolves the
approved compatible version. It supplies configuration binding, registries, actuator integration,
and Micrometer bindings without creating a repository-wide abstraction.

The implementation uses the native programmatic API rather than annotations. This avoids proxy
self-invocation surprises, keeps exception classification explicit, and does not require a new AOP
programming model. The implementation task must capture `mvn dependency:tree` evidence and must stop
if the effective version is not compatible with the current Spring Boot/Spring Cloud baseline.

## Project Structure

### Documentation (this feature)

```text
specs/050-order-inventory-resilience/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── order-inventory-resilience-policy.md
└── checklists/
    └── requirements.md
```

`tasks.md` was generated after plan approval and owns dependency-ordered implementation execution.

### Source Code (planned)

```text
services/order-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/philia/flashsale/order/regularpurchase/
    │   │   ├── application/port/out/
    │   │   │   └── CreateRegularStockHoldPort.java          # unchanged contract
    │   │   └── adapter/out/client/inventory/
    │   │       ├── InventoryRegularHoldClientAdapter.java    # existing Feign translator
    │   │       ├── ResilientInventoryRegularHoldClientAdapter.java
    │   │       ├── OrderInventoryResilienceConfiguration.java
    │   │       └── OrderInventoryResilienceProperties.java
    │   └── resources/application.yml
    └── test/java/com/philia/flashsale/order/regularpurchase/
        ├── adapter/out/client/inventory/
        │   ├── ResilientInventoryRegularHoldClientAdapterTests.java
        │   └── OrderInventoryResilienceConfigurationTests.java
        └── integration/
            └── RegularPurchaseInventoryResilienceIntegrationTests.java
```

**Structure Decision**: Keep the domain and application port framework-free. Place Resilience4j
configuration and exception translation beside the concrete outbound Inventory client adapter. A
Spring configuration explicitly exposes the resilient decorator as the primary
`CreateRegularStockHoldPort`; component scanning must not leave two ambiguous beans.

## Implementation Strategy

### G1 — Dependency and validated configuration

- Add the one service-local dependency.
- Add immutable validated properties and conservative defaults to `application.yml`.
- Construct named circuit-breaker and bulkhead instances through managed registries.
- Keep breaker health contribution disabled and document environment mappings.

### G2 — Explicit resilience decorator

- Decorate `CreateRegularStockHoldPort` without leaking Resilience4j types into application/domain.
- Configure exception predicates according to D2.
- Map open/full outcomes to the existing safe failure.
- Preserve the original command object, trace ID, and downstream payload on every admitted call.

### G3 — Deterministic tests

- Prove business rejections do not open the breaker.
- Prove infrastructure failures open it after the configured evidence window.
- Prove open calls do not invoke the delegate and meet the local fast-failure target.
- Prove half-open success closes and half-open failure reopens.
- Prove concurrency never exceeds the bulkhead limit and zero-wait excess calls fail fast.
- Prove recovery uses the same identities and readiness remains independent.

### G4 — Regression and fault-drill evidence

- Run the Order module verification and targeted Feature 049 suites.
- Run a deterministic local fault drill, record invocation counts/latency/state transitions, and
  confirm no duplicate durable effects.
- Render relevant configuration and capture the effective dependency version.
- Update `validation.md` only when implementation tasks define the required evidence format.

## Verification Plan

Minimum gates before implementation can be called complete:

```powershell
.\mvnw.cmd -pl services/order-service -am dependency:tree `
  -Dincludes=io.github.resilience4j

.\mvnw.cmd -pl services/order-service -am test `
  -Dtest=ResilientInventoryRegularHoldClientAdapterTests,OrderInventoryResilienceConfigurationTests `
  -Dsurefire.failIfNoSpecifiedTests=false

.\mvnw.cmd -pl services/order-service -am verify
```

The task phase must add the exact deterministic fault/concurrency command once its test fixture is
named. Full-monorepo `clean verify` remains a final regression gate if the approved tasks require it.

## Rollout and Rollback

1. Ship with conservative defaults and existing regular-purchase runtime flags unchanged.
2. Observe breaker/bulkhead metrics and sanitized transitions during a controlled Inventory fault
   drill before considering tuning.
3. Roll back by restoring the prior Order image/configuration. No database, Kafka, Redis, or HTTP
   contract rollback is required because this feature creates none.
4. Do not tune thresholds during an incident without recording the before/after configuration and
   evidence; configuration is operational policy, not a substitute for fixing Inventory.

## Complexity Tracking

No constitution violations require justification. The explicit decorator is deliberately local to
one capability; a global switch or shared framework would add more coupling and a larger failure
surface than the approved scope.
