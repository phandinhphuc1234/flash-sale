# Feature Specification: Order to Inventory Resilience

**Feature Branch**: `codex/order-inventory-resilience`

**Created**: 2026-09-16

**Status**: Approved for implementation planning — project owner approved the specification and implementation plan on 2026-09-16

**Input**: User description: "Add a practical, non-overengineered circuit-breaker design with the
supporting timeout and bulkhead protections, starting with the Order to Inventory regular-stock-hold
call."

## Problem and Scope

### Problem Statement

Regular checkout requires Order Service to ask Inventory Service for an immediate, authoritative
regular-stock hold. The call already has a bounded network timeout and no automatic retry, but a
sustained Inventory slowdown can still make many Order requests wait for that timeout concurrently.
That consumes caller resources, amplifies pressure on the unhealthy dependency, and delays a clear
temporary-unavailable response. The existing durable regular-purchase intake can recover an
ambiguous hold request with the same identities, so the missing capability is bounded failure
isolation rather than another retry loop.

### In Scope

- Isolate only the Order-to-Inventory regular-stock-hold capability during sustained transport or
  server failures.
- Bound how many concurrent calls may enter that capability so one degraded dependency cannot
  consume all Order request capacity.
- Fail fast while the dependency is isolated and allow bounded recovery probes after a cooling
  interval.
- Preserve the existing network timeout, no-blind-retry policy, stable command identities, durable
  intake checkpoints, and recovery worker.
- Distinguish infrastructure failures from valid Inventory business rejections.
- Expose safe operational evidence for closed, open, half-open, rejected, failed, and recovered
  behavior without high-cardinality or secret data.

### Out of Scope

- Applying circuit breakers to Cart, Product, Payment, Authentication, Kafka, Redis, API Gateway,
  or every OpenFeign client.
- Adding client-side automatic retries, time-based asynchronous cancellation, fallback stock data,
  cached acceptance, or fabricated successful holds.
- Changing Inventory stock rules, the five-minute hold policy, checkout idempotency, payment rules,
  public APIs, Kafka schemas, database schemas, or the Flash Sale hot path.
- Making Order readiness depend directly on Inventory availability.
- Building a shared resilience framework or cross-service abstraction in this first slice.

### Non-goals

- The feature does not make Inventory optional for accepting a regular purchase.
- The feature does not promise zero failures during an outage; it limits outage amplification and
  preserves safe recovery.
- The feature does not replace the durable regular-purchase recovery worker.
- The feature does not treat business rejections as dependency instability.

## Baseline References

- `specs/049-regular-purchase-checkout/spec.md` — approved stock, idempotency, and recovery behavior.
- `specs/049-regular-purchase-checkout/plan.md` — Order-owned orchestration and durable intake checkpoints.
- `specs/049-regular-purchase-checkout/contracts/internal-checkout-http.md` — Inventory hold HTTP contract and no-blind-retry policy.
- `services/order-service/src/main/resources/application.yml` — existing 300 ms connect and 800 ms read timeouts.
- `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/InventoryRegularHoldClientAdapter.java` — current outbound failure translation.

## Requirement Delta

### ADDED

- Bounded failure isolation for the Order-to-Inventory regular-stock-hold call.
- Bounded concurrent admission for that call.
- Explicit recovery probing and safe operational evidence for the isolation lifecycle.

### MODIFIED

- **Before**: every regular-stock-hold attempt may reach Inventory and wait for the existing network
  timeout while Inventory is degraded.
- **After**: after a configurable infrastructure-failure boundary is reached, new attempts fail
  fast without calling Inventory until a bounded recovery probe is allowed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Contain an Inventory Outage (Priority: P1)

As a shopper starting a regular purchase, I want an Inventory outage to produce a prompt and safe
temporary-unavailable result rather than making many checkout requests wait and overload Order.

**Why this priority**: This is the direct cascading-failure risk and the smallest independently
valuable resilience slice.

**Independent Test**: Make Inventory return transport failures or exceed the existing response
timeout, submit enough regular purchases to cross the approved failure boundary, and verify later
attempts fail fast without invoking Inventory while no false hold, Order, or Payment is created.

**Acceptance Scenarios**:

1. **Given** Inventory repeatedly times out or returns a server failure, **When** the approved
   failure boundary is reached, **Then** subsequent regular-stock-hold attempts are rejected quickly
   without another Inventory call and expose the existing safe dependency-unavailable behavior.
2. **Given** the isolation is open, **When** a checkout attempt is rejected, **Then** its durable
   request identities and checkpoint remain eligible for the existing same-identity recovery path.
3. **Given** Inventory returns insufficient stock, item-not-found, or an identity conflict, **When**
   Order handles that response, **Then** the business outcome is preserved and does not contribute
   to opening the dependency isolation.

---

### User Story 2 - Recover Without Duplicate Effects (Priority: P1)

As an operator, I want Order to probe Inventory after a bounded cooling interval and resume normal
traffic only after a successful probe so recovery does not require a restart or create duplicate
holds.

**Why this priority**: Isolation without automatic, safe recovery would turn a transient outage into
a manual incident.

**Independent Test**: Open the isolation with deterministic failures, restore Inventory, wait one
configured cooling interval, and verify bounded probe traffic closes the isolation while using the
original hold, purchase-request, Order, shopper, and item identities.

**Acceptance Scenarios**:

1. **Given** the isolation is open and Inventory remains unhealthy, **When** a bounded recovery
   probe fails, **Then** normal traffic remains isolated and no replacement business identity is
   generated.
2. **Given** the isolation is open and Inventory is healthy again, **When** the approved recovery
   probes succeed, **Then** normal traffic resumes without restarting Order.
3. **Given** an earlier Inventory outcome was ambiguous, **When** recovery retries the request,
   **Then** the exact original identities and canonical payload are reused and Inventory idempotency
   prevents duplicate stock effects.

---

### User Story 3 - Protect Order Concurrency and Diagnose State (Priority: P2)

As an operator, I want bounded concurrent Inventory calls and low-cardinality telemetry so I can
see whether failures come from Inventory, an open isolation boundary, or local saturation.

**Why this priority**: A circuit breaker limits repeated failures, while concurrency isolation
prevents a slow dependency from consuming the caller's entire request capacity.

**Independent Test**: Hold Inventory calls open, exceed the approved concurrent-call limit, and
verify excess attempts are rejected immediately, the admitted call count stays bounded, readiness
remains healthy, and metrics/logs distinguish saturation from remote failure.

**Acceptance Scenarios**:

1. **Given** the maximum permitted Inventory calls are in flight, **When** another hold attempt
   arrives, **Then** it fails fast without entering the Inventory client and remains recoverable.
2. **Given** a breaker transition, remote failure, or concurrency rejection, **When** operators
   inspect metrics and sanitized logs, **Then** they can distinguish the outcome without seeing
   shopper IDs, hold IDs, tokens, request bodies, or unbounded labels.
3. **Given** Inventory is unavailable while Order's own database and runtime are healthy, **When**
   readiness is checked, **Then** Order remains ready and reports dependency isolation through
   operational telemetry instead of taking itself out of service.

### Edge Cases

- A business `409` occurs between transport failures.
- Several requests race exactly when the isolation changes from closed to open.
- Recovery probes run while the durable recovery worker and a new request reference the same
  purchase identity.
- Inventory successfully commits a hold but the response times out.
- All concurrent permits are occupied by slow calls when the isolation opens.
- A service restart occurs while isolation was open; durable business recovery must remain correct
  even though the in-memory isolation state is reset.
- Configuration is invalid or missing at startup.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST apply failure isolation only to Order's regular-stock-hold capability
  toward Inventory in this feature.
- **FR-002**: The system MUST continue using the existing bounded connect and response timeouts for
  every Inventory hold call.
- **FR-003**: The system MUST count transport failures, connection failures, timeouts, and remote
  server failures as dependency failures eligible to open the isolation.
- **FR-004**: The system MUST NOT count documented Inventory business rejections as dependency
  failures and MUST preserve their existing shopper-visible outcomes.
- **FR-005**: After the approved dependency-failure boundary is reached, new hold attempts MUST fail
  fast without invoking Inventory until recovery probing is permitted.
- **FR-006**: Recovery probing MUST be bounded; successful probes MUST restore normal traffic and
  failed probes MUST return the isolation to its open state.
- **FR-007**: The system MUST bound concurrent admitted calls to the Inventory hold capability and
  MUST reject excess calls without waiting for a permit.
- **FR-008**: A circuit-open or concurrency-rejected attempt MUST map to the existing sanitized,
  recoverable dependency-unavailable outcome and MUST NOT claim that stock was rejected or held.
- **FR-009**: The system MUST NOT add an automatic client retry. Re-execution remains owned by the
  existing durable regular-purchase recovery path.
- **FR-010**: Every recovery attempt MUST reuse the original `holdId`, `purchaseRequestId`,
  `orderId`, `shopperId`, item identities, quantities, and canonical payload.
- **FR-011**: The system MUST NOT return fallback stock, fabricate a successful hold, or create an
  Order or Payment without Inventory's authoritative hold result.
- **FR-012**: The isolation state MUST remain in memory and MUST NOT add or reinterpret durable
  business data; a restart may reset isolation state but MUST NOT reset durable checkout recovery.
- **FR-013**: Order readiness MUST NOT become unhealthy solely because Inventory is unavailable or
  the isolation is open.
- **FR-014**: Operators MUST be able to distinguish successful calls, business rejections, remote
  failures, open-state rejections, concurrency rejections, and recovery transitions using bounded,
  sanitized signals.
- **FR-015**: All isolation and concurrency thresholds MUST be externally configurable, validated
  at startup, and documented with conservative defaults.
- **FR-016**: Existing public HTTP, internal HTTP payloads, error codes, Kafka contracts, database
  schemas, Payment behavior, and Flash Sale behavior MUST remain backward compatible.

### Non-Functional Requirements

- **NFR-REL-001**: No protection-layer decision may create an additional hold, Order, Payment, or
  stock effect for one durable purchase identity.
- **NFR-PERF-001**: While isolation is open or concurrency admission is exhausted, at least 95% of
  affected attempts MUST return the safe temporary-unavailable outcome within 100 ms in the
  approved local fault-injection test, excluding caller/network overhead outside Order.
- **NFR-OBS-001**: Telemetry MUST use a fixed allowlist of outcome and state values and MUST NOT use
  shopper, order, hold, purchase-request, token, or raw URL values as metric labels.
- **NFR-COMPAT-001**: The protected and prior Order versions MUST use the same Inventory HTTP
  contract and durable recovery identities so deployment and rollback require no data migration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A deterministic outage test opens the isolation after its approved minimum evidence
  window, and 95% or more of the next 100 attempts are rejected safely within 100 ms without an
  Inventory invocation.
- **SC-002**: Documented Inventory business rejections can be repeated 100 times without opening the
  isolation.
- **SC-003**: After Inventory recovery, the isolation returns to normal operation within one
  configured cooling interval plus the bounded probe duration, without restarting Order.
- **SC-004**: A concurrency test never observes more admitted Inventory calls than the configured
  limit and produces no duplicate hold, Order, Payment, or stock effect.
- **SC-005**: Replay and recovery tests confirm that all business identities remain unchanged across
  timeout, open-state rejection, concurrency rejection, and later successful recovery.
- **SC-006**: Existing Order module, Feature 049 regular-purchase, and Flash Sale purchase regression
  suites remain green with no public or event contract change.
- **SC-007**: An operator can identify the isolation state and failure category from sanitized
  metrics/logs without inspecting a shopper or business identifier.

## Dependencies and Compatibility

- Depends on the Feature 049 regular-purchase intake, idempotent Inventory hold contract, and
  durable recovery worker.
- Uses the existing synchronous Order-to-Inventory HTTP boundary and does not add another service
  dependency.
- The requested implementation library is introduced only in Order Service and must be justified,
  version-managed, and tested in the approved implementation plan.
- No database, HTTP payload, stable error-code, Kafka topic/schema, or rollout-order change is
  expected.

## Assumptions

- The existing 300 ms connect and 800 ms read timeouts remain the network time budget for this
  first slice; changing them requires a reviewed plan/spec amendment.
- Inventory hold creation remains idempotent for the same purchase request and fingerprint.
- The durable recovery worker remains the only retry/resume owner.
- The first release uses one service-local protection policy and does not create a shared library.
- Threshold defaults are technical tuning values chosen in the plan, externally configurable, and
  must be validated by deterministic fault and concurrency tests before wider rollout.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Order owns the outbound protection and recovery orchestration; Inventory
  continues owning stock and holds. No database ownership changes.
- **External ingress**: No Gateway route or public ingress change.
- **API/event contracts**: The existing Inventory hold HTTP contract is preserved; no Kafka contract
  changes are allowed by this feature.
- **Durable and hot-path data**: No schema or Redis change; PostgreSQL durability and the Flash Sale
  Redis Lua path remain unchanged.
- **Messaging reliability**: Existing outbox/inbox and Kafka recovery semantics remain unchanged;
  this feature protects only a pre-acceptance synchronous call.
- **Root infrastructure ownership**: Service-owned dependency/configuration stays in Order Service;
  shared monitoring or deployment overrides, if needed, stay under root `infra/`.
- **Observability**: Existing Actuator/Prometheus auto-configuration and trace propagation are
  reused; business code may not construct a registry or expose high-cardinality identifiers.
- **Verification**: Unit, adapter integration, deterministic fault/concurrency, Order module, Feature
  049 regression, and configuration-render validation are required. Kafka/schema/migration tests are
  omitted because those surfaces do not change.
- **Architecture decisions**: No ADR is expected because service boundaries, communication style,
  durability, ownership, and deployment coupling do not change. A reusable cross-service resilience
  platform would require a separate review.

## Approval and History

- 2026-09-16 — Minimal Order-to-Inventory resilience direction selected after repository review.
- 2026-09-16 — Project owner requested commit/merge of prior work and authorized Spec Kit planning.
- 2026-09-16 — Project owner approved `plan.md` and authorized dependency-ordered task generation.
