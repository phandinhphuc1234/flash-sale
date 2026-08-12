# Feature Specification: Cross-Service HTTP Response Standardization

**Feature Branch**: `018-http-response-standardization`
**Created**: 2026-08-01
**Status**: Approved
**Input**: User request to apply `$standardize-api-response-error-handling` across Authentication, Product, Campaign, and Inventory services.
**Linked Business Requirements**: Technical enabler for one predictable backend HTTP contract.
**Business Owner**: Project owner
**Required Reviewers**: Project owner; architecture reviewer

## Problem and Scope

### Problem Statement

The four services expose similar but incompatible success and error JSON bodies. This forces clients,
Gateway contract tests, and operators to learn service-specific wrappers for ordinary HTTP behavior.

### In Scope

- Standardize successful JSON responses on `common-web.ApiResponse<T>`.
- Standardize failed JSON responses on `common-web.ApiErrorResponse` and `FieldViolation`.
- Standardize status-code mapping, validation errors, security failures, safe messages, and required
  response headers for Authentication, Product, Campaign, and Inventory.
- Preserve service-owned business error codes and bounded-context ownership.
- Update affected service and Gateway contract tests and API documentation.

### Out of Scope

- Kafka, gRPC, database schema, persistence, or domain-model changes.
- Changing service boundaries or adding dependencies.
- Replacing OpenTelemetry/Micrometer tracing; this feature only standardizes the existing
  `X-Trace-Id` HTTP correlation behavior.
- Refactoring API Gateway-owned failures unless required to keep its pass-through contract tests valid.

## Baseline References

- `libs/common-web` currently owns `ApiResponse`, `ApiErrorResponse`, `FieldViolation`, `PageResponse`,
  and `PageMeta`.
- Authentication and Campaign still expose local error/success records containing body `traceId`.
- Product has separate catalog/admin error records.
- Inventory already uses the shared error envelope but still needs explicit typed mappings and safe
  messages.
- Existing Gateway tests assert both Gateway-owned and downstream response behavior.

## User Scenarios & Testing

### User Story 1 - One shared HTTP shape (Priority: P1)

As a backend client, I want ordinary success, validation, and pagination responses to have one
shape across services, so that client handling is predictable.

**Why this priority**: It is the foundation for every service migration.
**Independent Test**: Exercise representative success, validation, not-found, conflict, and paginated
endpoints from the migrated services and compare them with the v1 contract.

**Acceptance Scenarios**:

1. **Given** a successful endpoint, **when** it returns JSON, **then** the body is `ApiResponse<T>`.
2. **Given** a validation failure, **when** the request is rejected, **then** the body is
   `ApiErrorResponse` with `FieldViolation` entries and HTTP 400.
3. **Given** a paginated result, **when** it is returned, **then** `PageResponse<T>` is inside the
   top-level `data` field.

### User Story 2 - Safe service error handling (Priority: P1)

As an operator, I want errors to have correct statuses, stable service-owned codes, safe messages,
and a trace header, so that failures are actionable without leaking internals.

**Independent Test**: Trigger business, validation, security, rate-limit where applicable, and
unexpected failures in each service and assert status, body, headers, and redaction.

**Acceptance Scenarios**:

1. **Given** an unauthenticated request, **when** security rejects it, **then** HTTP 401 is returned
   by the security boundary with the standard error shape.
2. **Given** an authenticated but forbidden request, **when** authorization rejects it, **then**
   HTTP 403 is returned with the standard error shape.
3. **Given** an unexpected infrastructure failure, **when** it reaches the web boundary, **then**
   HTTP 5xx is returned without stack traces, SQL, secrets, or raw internal details.

### User Story 3 - Coordinated migration (Priority: P2)

As a maintainer, I want the migration to preserve business error-code ownership and Gateway
pass-through behavior, so that services can be deployed and verified incrementally.

**Independent Test**: Run service contract tests and Gateway proxy tests during the migration and
verify no downstream response is wrapped or overwritten by the Gateway.

**Acceptance Scenarios**:

1. **Given** a service-specific business failure, **when** it is translated, **then** its code remains
   owned by that service and is not moved into `common-web`.
2. **Given** a committed downstream response, **when** the Gateway receives it, **then** the Gateway
   passes it through unchanged.

### Edge Cases

- HTTP 204 responses have no body.
- HTTP 201 responses may include `Location`; HTTP 202 is used only for documented asynchronous work.
- HTTP 429 includes `Retry-After` when a retry duration is known.
- Malformed JSON, missing headers, enum/type mismatch, and empty validation lists remain safe and
  deterministic.
- Legacy body `traceId` fields are removed by this migration; correlation is carried by the
  `X-Trace-Id` response/request header only.

## Requirements

### Functional Requirements

- **FR-001**: Each migrated success endpoint MUST return `ApiResponse<T>` or an explicitly approved
  compatibility response.
- **FR-002**: Each migrated failure endpoint MUST return `ApiErrorResponse` with service-owned error
  codes and client-safe messages.
- **FR-003**: Validation failures MUST map to `FieldViolation` without exposing framework exception
  details.
- **FR-004**: Controllers MUST NOT catch expected business exceptions; web error handlers MUST map
  them centrally.
- **FR-005**: Security filter failures MUST use the service's entry point/denied handler, and the
  WebFlux Gateway MUST use its reactive error writer.
- **FR-006**: Migrated responses MUST propagate and echo `X-Trace-Id` in the HTTP header only;
  migrated success and error JSON MUST NOT contain a `traceId` field.
- **FR-007**: JPA entities, domain objects, persistence exceptions, and secrets MUST NOT cross the
  HTTP boundary.
- **FR-008**: Existing business error-code ownership MUST remain inside its service or feature.
- **FR-009**: Contract tests MUST assert status, body shape, required headers, redaction, and Gateway
  pass-through behavior.

### Non-Functional Requirements

- **NFR-001**: No new external production dependency is introduced; migrated services may depend on
  the existing internal `libs/common-web` module for the canonical HTTP transport types.
- **NFR-002**: The four services remain independently buildable and deployable.
- **NFR-003**: Contract migration must be reviewable service-by-service and must not mix persistence or
  domain behavior changes.

### Key Entities

- **HTTP success envelope**: Shared transport wrapper for successful service data.
- **HTTP error envelope**: Shared transport wrapper for safe failures and validation violations.
- **Service error code**: Stable business/application identifier owned by one bounded context.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of newly migrated endpoint contract tests use the shared success/error envelope.
- **SC-002**: All migrated 4xx/5xx tests assert the expected HTTP status and stable service error code.
- **SC-003**: No migrated error response contains a stack trace, SQL, token, password, secret, or
  internal class/package name.
- **SC-004**: All affected service modules and the Gateway compatibility suite pass verification.

## Dependencies and Compatibility

- `libs/common-web` is the canonical generic contract library.
- Public field removal or body-shape changes require contract-test and documentation updates before
  implementation.
- Q1 A is approved: the coordinated backend migration removes legacy JSON `traceId` fields and uses
  `X-Trace-Id` as the canonical correlation channel. External-client compatibility is not required
  for this self-project.

## Assumptions

- The existing `X-Trace-Id` header remains the correlation mechanism for this feature.
- Service-specific error code names remain stable unless a later contract migration explicitly
  approves a rename.
- No database or event consumer depends on HTTP response wrapper classes.

## Compatibility Decision

- **2026-08-01 — Q1 A approved by the project owner:** remove `traceId` from Authentication,
  Campaign, Product, and Gateway-owned JSON bodies. Keep `X-Trace-Id` as the response/request header
  used for correlation. A future W3C `traceparent` propagation change can be added at the transport
  boundary without reintroducing `traceId` into business JSON.

## Constitutional Constraints

- **Service ownership**: Each service keeps its own errors, exceptions, DTOs, and database ownership; only generic transport types are shared.
- **External ingress**: Public traffic remains through Gateway; Gateway pass-through behavior is preserved.
- **API/event contracts**: HTTP contracts are updated before implementation; Kafka and gRPC are unchanged.
- **Durable and hot-path data**: No persistence, PostgreSQL, Redis, or stock behavior changes.
- **Messaging reliability**: No Kafka producer/consumer or outbox changes.
- **Root infrastructure ownership**: No infrastructure changes.
- **Observability**: Preserve Actuator configuration and `X-Trace-Id`; do not construct a Prometheus registry.
- **Verification**: Unit, web/security, contract, module, and cross-service Gateway tests apply; load tests are not required for a response-envelope-only change.
- **Architecture decisions**: No service-boundary ADR is required; contract compatibility is recorded in this feature's contract files.

## Approval and History

- 2026-08-01 — Draft created from the approved standardization direction.
- 2026-08-01 — Q1 A approved: header-only trace correlation.
- 2026-08-01 — Feature 018 spec, plan, contracts, and tasks approved by the project owner.
