# Feature Specification: Gateway Rate-Limit Error Contract

**Feature Branch**: `012-gateway-rate-limit-contract`
**Created**: 2026-07-22
**Status**: Verified
**Owner**: Gateway/Platform owner (user)
**Reviewers**: Gateway technical owner, observability owner, client-contract reviewer
**Input**: User description: "Add the 429 contract and keep distributed tracing standardized on Micrometer with OpenTelemetry."

> This is a compatibility-sensitive contract foundation. It does not choose or activate a quota,
> limiter algorithm, Redis policy, expiry window, or request-key strategy.

## Problem and Scope

**Business problem**: The Gateway error vocabulary has no approved response for a request rejected
by a future edge rate limiter. Adding the limiter first would let framework defaults leak into the
public API and could produce an inconsistent body or missing trace correlation.

**Goal**: Define and implement one additive, safe, traceable HTTP 429 error contract that a later
rate-limiting feature can invoke without changing the established Gateway error envelope.

**In scope**:

- Add `RATE_LIMIT_EXCEEDED` to the Gateway-owned public error vocabulary.
- Fix its HTTP status, safe message, JSON envelope, and trace-correlation behavior.
- Prove the existing centralized Gateway error writer renders the new code correctly.
- Record Micrometer Tracing with the OpenTelemetry bridge and OTLP export as the distributed-tracing
  standard for later runtime instrumentation.

**Out of scope**:

- Enabling a rate-limit filter or adding a Redis rate limiter.
- Choosing a key source, quota, burst capacity, replenishment interval, TTL, exemptions, or route policy.
- Choosing fail-open/fail-closed behavior when a limiter backend is unavailable.
- Defining or calculating `Retry-After` or any rate-limit accounting header.
- Adding Micrometer Tracing/OpenTelemetry dependencies, runtime configuration, Collector assets, or
  changing current `X-Trace-Id` behavior.

## Baseline References and Requirement Delta

| Reference | Governing content | Confirmed delta |
|-----------|-------------------|-----------------|
| [Feature 011](../011-gateway-error-handling/spec.md) | Seven Gateway-owned errors, stable `{code,message,traceId}` body, downstream response ownership | Add one eighth code for future Gateway-owned rate-limit rejection; all seven existing rows remain unchanged |
| [Gateway error contract 011](../011-gateway-error-handling/contracts/gateway-error-http.md) | Explicitly deferred HTTP 429 | Feature 012 owns and resolves only that deferred contract row |
| [Technology problem map](../../docs/technology/technology-problem-map.md) | OpenTelemetry Collector is planned | Clarify that service instrumentation uses Micrometer Tracing with the OpenTelemetry bridge and exports OTLP to the Collector |

## User Scenarios & Testing

### User Story 1 - Consume a Stable Rate-Limit Rejection (Priority: P1)

As an API client developer, I want a stable Gateway-owned 429 response contract so that clients can
handle a later rate-limit rejection without parsing framework-specific error bodies.

**Why this priority**: The public contract must exist before a limiter can safely reject production traffic.

**Independent Test**: Render `RATE_LIMIT_EXCEEDED` through the existing Gateway error boundary and
verify the exact status, body, content type, trace behavior, and absence of internal details without
activating a limiter.

**Use-case references**: Technical-enabler contract; no business-domain use case is introduced.

**Acceptance Scenarios**:

1. **Given** the Gateway boundary selects `RATE_LIMIT_EXCEEDED`, **When** it renders the response,
   **Then** the client receives HTTP 429 and exact JSON fields `code`, `message`, and non-blank `traceId`.
2. **Given** a valid caller correlation value, **When** the 429 body is rendered, **Then** that value
   follows the existing Gateway trace-correlation contract.
3. **Given** a downstream service normally returns HTTP 429, **When** the Gateway receives that HTTP
   response, **Then** its status and body remain downstream-owned and are not translated into the
   Gateway `RATE_LIMIT_EXCEEDED` envelope.

### Edge Cases and Failure Outcomes

- A missing or invalid caller correlation value follows Feature 011's existing server-generated
  error-correlation behavior; this feature does not alter Product Admin validation.
- A response already committed by another owner is not rewritten as a Gateway 429.
- No `Retry-After` value is fabricated while the rate-limit window remains undefined.
- The public message never contains quota state, Redis keys, client identity, exception details, or
  internal infrastructure addresses.

## Requirements

### Functional Requirements

- **FR-001**: The Gateway error vocabulary MUST include `RATE_LIMIT_EXCEEDED` with HTTP status 429.
- **FR-002**: Its exact safe message MUST be `Too many requests`.
- **FR-003**: A Gateway-owned 429 response MUST retain the established JSON envelope
  `{code,message,traceId}` and `application/json` content type.
- **FR-004**: Its `traceId` MUST follow the existing Feature 011 correlation contract and MUST be
  non-blank; this feature MUST NOT create a second distributed-tracing mechanism.
- **FR-005**: A downstream HTTP 429 response MUST remain downstream-owned and pass through without
  Gateway translation.
- **FR-006**: This feature MUST NOT activate rate limiting or infer quota, TTL, key, Redis failure,
  retry, exemption, or rate-limit-header semantics.
- **FR-007**: The documented runtime tracing standard MUST be Micrometer Tracing with its
  OpenTelemetry bridge, W3C trace-context propagation, and OTLP export to an OpenTelemetry Collector.
- **FR-008**: Application and Gateway policy code MUST use Micrometer's tracing abstraction when
  runtime tracing is implemented and MUST NOT couple business or policy logic directly to an
  OpenTelemetry SDK implementation.

### Non-Functional Requirements

- **NFR-001**: All eight Gateway error taxonomy rows MUST have exact automated status/code/message coverage.
- **NFR-002**: The change MUST be backward compatible with all seven Feature 011 Gateway errors and
  with downstream HTTP response pass-through.
- **NFR-003**: The contract and operational documentation MUST distinguish application
  instrumentation, OTLP transport, and Collector ownership unambiguously.

## Distributed-System Risk Decisions

| Risk area | Decision and required behavior | Requirement/scenario reference |
|-----------|--------------------------------|--------------------------------|
| Money/payment | N/A; no financial operation is changed. | Scope |
| Inventory/oversell | N/A; no stock operation is changed. | Scope |
| Concurrency | N/A for this contract-only slice; limiter atomicity remains future work. | FR-006 |
| Idempotency/deduplication | N/A; no command or durable effect is introduced. | Scope |
| Consistency/ordering | Downstream HTTP responses remain pass-through; no shared state is introduced. | FR-005 |
| Retry/timeout/compensation | N/A; no limiter execution or retry policy is introduced. | FR-006 |
| Security/authorization | Public 429 body is safe and reveals no client identity or quota internals. | FR-002, Edge Cases |
| TTL/quota/retention | No value is selected in this feature; all quota/window/TTL decisions remain blocked until a limiter feature is specified. | FR-006 |

## Dependencies and Compatibility

- **Upstream dependencies**: A future Gateway rate-limit capability will select this code.
- **Downstream consumers**: Public clients may add handling for the new additive 429 code.
- **Compatibility promise**: No existing status, code, message, trace behavior, security challenge,
  or downstream body is changed. The new enum member and contract row are additive.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Automated tests verify the exact 429 status and three-field body in 100% of the
  approved rate-limit contract scenarios.
- **SC-002**: All existing Gateway tests continue to pass with zero changed Feature 011 expectations.
- **SC-003**: The technology guide names one application tracing abstraction, one OpenTelemetry
  bridge/export path, and one Collector ownership boundary without introducing runtime dependencies.

## Assumptions

- `RATE_LIMIT_EXCEEDED` represents only a deliberate Gateway limiter rejection, not a downstream 429.
- The later rate-limiting feature will amend this contract if it approves `Retry-After` or other headers.

## Constitutional Constraints

- **Service ownership**: `api-gateway` owns this edge contract; no database or service-owned model is accessed.
- **External ingress**: The additive contract belongs at the existing public Gateway boundary; no route changes.
- **API/event contracts**: Feature-local HTTP contract is updated before code; no Kafka contract applies.
- **Durable and hot-path data**: No PostgreSQL, Redis, Lua, quota state, TTL, or migration is introduced.
- **Messaging reliability**: N/A; no messaging change.
- **Root infrastructure ownership**: No infrastructure asset is added. A future Collector belongs under
  root `infra/monitoring/`, while service tracing dependencies/configuration remain service-owned.
- **Observability**: Existing Actuator/Prometheus behavior remains unchanged. Runtime distributed
  tracing is standardized on Micrometer Tracing plus the OpenTelemetry bridge and OTLP Collector,
  but its dependencies and configuration are explicitly outside this feature.
- **Verification**: Gateway unit/contract tests and the api-gateway Maven reactor gate apply.
  Database, Kafka, load, migration, and Kubernetes tests are not applicable because no such runtime
  behavior or asset changes.
- **Architecture decisions**: ADR 0003 remains governing. No service boundary or architecture change occurs.

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-07-22 | Added the requested additive 429 contract scope and Micrometer/OpenTelemetry tracing constraint | Codex | Gateway/Platform owner (user) | Approved |
| 2026-07-22 | Verified the exact 429 contract, downstream pass-through, scope exclusions, and 105-test Gateway module gate | Codex | Automated evidence recorded in `validation.md` | Verified |
