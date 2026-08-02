# Implementation Plan: Cross-Service HTTP Response Standardization

**Branch**: `018-http-response-standardization` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)
**Status**: Approved — trace-body decision resolved; ready for task-by-task implementation.

## Summary

Adopt the existing `libs/common-web` success/error envelopes across Authentication, Product,
Campaign, and Inventory. Migrate web adapters and exception/security handlers in small endpoint
groups, preserve business error-code ownership, and verify Gateway pass-through behavior.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.x, Spring MVC, Spring WebFlux Gateway, Jakarta Validation,
`libs/common-web` (existing internal module)
**Storage**: N/A; no persistence changes
**Testing**: JUnit 5, Spring web slices, contract tests, Maven Wrapper
**Target Platform**: Independently deployable Spring Boot services
**Project Type**: Maven monorepo of microservices
**Performance Goals**: No additional response-processing hop or blocking operation
**Constraints**: Preserve service ownership, `X-Trace-Id`, security semantics, and Gateway pass-through
**Scale/Scope**: Four services, their HTTP adapters, and affected Gateway contract tests

## Constitution Check

- **Specification traceability**: PASS for the resolved Q1 A decision; final implementation gate remains artifact approval.
- **Service ownership**: PASS; only generic transport types remain shared, business codes remain local.
- **Communication**: PASS; no HTTP route, Kafka, or gRPC topology change.
- **Data and messaging**: PASS; no database, Redis, Kafka, or outbox behavior.
- **Root infrastructure ownership**: PASS; no infrastructure changes.
- **Observability**: PASS; preserve `X-Trace-Id` and existing Actuator configuration.
- **Contracts/dependencies**: PASS after `http-response-v1.md` is approved; services may add the
  existing internal `common-web` module, with no new external production dependency.
- **Validation**: PASS when service, security, contract, and cross-service tests are implemented.

## Design

### Canonical types

- Success: `com.philia.flashsale.common.web.ApiResponse<T>`.
- Error: `com.philia.flashsale.common.web.ApiErrorResponse`.
- Validation: `com.philia.flashsale.common.web.FieldViolation`.
- Pagination: `ApiResponse<PageResponse<T>>`.

### Boundary rules

- Authentication, Product, Campaign, and Inventory retain feature-owned error codes.
- Domain/application code does not import HTTP, Spring MVC, ResponseEntity, or JPA types.
- Servlet services use `@RestControllerAdvice`, `AuthenticationEntryPoint`, and `AccessDeniedHandler`.
- Gateway remains WebFlux and keeps its reactive error writer; it does not wrap committed downstream bodies.
- Controllers map application results to response DTOs and do not catch business exceptions.

### Migration order

1. Inventory: confirm shared envelope, typed error mapping, safe messages, and trace headers.
2. Authentication: migrate success/error handlers while preserving cookie and `no-store` behavior.
3. Campaign: migrate admin/internal handlers and field violations while preserving ETag/Location/trace headers.
4. Product: migrate catalog and admin endpoint groups, then remove local duplicate wrappers.
5. Gateway: update proxy/owned-error contract tests; change Gateway body only if explicitly included in the approved compatibility decision.

## Project Structure

### Documentation

```text
specs/018-http-response-standardization/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── http-response-v1.md
│   └── service-error-code-matrix.md
├── checklists/requirements.md
└── tasks.md
```

### Source paths

```text
libs/common-web/src/main/java/com/philia/flashsale/common/web/
services/authentication-service/src/main/java/com/philia/flashsale/authentication/websupport/error/
services/product-service/src/main/java/com/philia/flashsale/product/*/adapter/in/web/
services/campaign-service/src/main/java/com/philia/flashsale/campaign/websupport/error/
services/inventory-service/src/main/java/com/philia/flashsale/inventory/websupport/error/
services/api-gateway/src/test/java/com/philia/flashsale/gateway/
```

**Structure Decision**: Keep existing package-by-feature and service technical packages. Replace
duplicate transport records only after each endpoint group uses `common-web`.

## Test Strategy

- Unit tests for error-code/status mapping and sanitization.
- MVC/web-slice tests for body, status, validation, headers, security, 201/202/204, and 429 cases.
- Gateway WebFlux contract tests for owned errors and downstream pass-through.
- Module verification for each service, then full reactor verification.
- No load test or persistence integration test is required because no state or hot-path behavior changes.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| None | N/A | The plan reuses existing shared types and package conventions. |

## Approval Gate

Implementation MUST NOT begin until:

1. The Q1 A header-only decision is recorded in `spec.md` and the HTTP contract.
2. `spec.md`, this `plan.md`, contracts, and `tasks.md` are approved.
3. The active feature pointer targets Feature 018.
