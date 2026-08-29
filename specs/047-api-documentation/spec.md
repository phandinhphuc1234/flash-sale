# Feature Specification: Unified API Documentation

**Feature Branch**: `codex/api-documentation`

**Created**: 2026-08-28

**Status**: Implemented and verified (2026-08-29)

**Input**: User description: "Inventory every current API endpoint, confirm whether Swagger documentation exists, explain how to read it, and complete documentation for all implemented APIs."

## Problem and Scope

### Problem Statement

The repository has HTTP endpoints across seven implemented business services, but there is no
single trustworthy endpoint inventory. Live Swagger/OpenAPI exists only for Inventory Service, so a
developer must search controllers and historical feature documents to understand the current HTTP
surface. This makes onboarding, local testing, security review, and contract-drift detection harder.

### In Scope

- Inventory every supported business and service-to-service HTTP endpoint by method, path, owner,
  caller, and access boundary.
- Provide live OpenAPI documents for every implemented HTTP-owning business service.
- Provide one local Swagger UI catalog through API Gateway that links the service-owned documents.
- Keep API documentation disabled by default and require explicit local opt-in.
- Record that Cart Service and Notification Service currently expose no business HTTP endpoints.
- Document how to open the catalog, authorize calls, and distinguish public, internal, webhook,
  identity-trust, and operational endpoints.

### Out of Scope

- Adding, removing, renaming, or changing behavior of any business endpoint.
- Exposing Swagger/OpenAPI on the public cloud endpoint by default.
- Creating placeholder APIs for Cart Service or Notification Service.
- Changing response envelopes, authorization rules, database schemas, Kafka contracts, or business
  behavior.
- Treating Actuator, framework error handlers, or Swagger's own generated paths as business APIs.

## Baseline References

- `services/*/src/main/java/**` controller and framework endpoint definitions.
- `services/api-gateway/src/main/resources/application.yml` public route inventory.
- `docs/inventory/README.md` and Inventory's existing opt-in Springdoc implementation.
- `docs/current-system-implementation.md`, whose OpenAPI summary states that Inventory is the only
  service with live Swagger support.

## Requirement Delta

### ADDED

- A reviewed catalog for all 40 supported HTTP endpoints currently implemented in the repository.
- Opt-in, service-owned OpenAPI documents for Authentication, Product, Campaign, Flash Sale, Order,
  and Payment services, while preserving Inventory's existing document.
- One opt-in Swagger UI catalog at API Gateway for local development.

### MODIFIED

- **Before**: only Inventory can generate a live OpenAPI document, and developers must inspect code
  or feature-specific documents for every other service.
- **After**: every HTTP-owning business service can generate its own OpenAPI document, and developers
  can browse them from one local Gateway UI without changing any business endpoint.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read the Complete API Catalog (Priority: P1)

As a developer or reviewer, I want a single endpoint inventory so that I can understand the
system's HTTP boundary without searching every controller.

**Why this priority**: A correct inventory is the source for implementation, testing, onboarding,
and security review.

**Independent Test**: Read the catalog and verify that it reports 40 unique supported endpoints,
classifies 33 Gateway-public endpoints, 6 internal service endpoints, and 1 JWKS trust endpoint,
while listing Cart and Notification as zero-endpoint scaffolds.

**Acceptance Scenarios**:

1. **Given** the current repository, **When** the catalog is reviewed, **Then** each supported
   endpoint has one owner, HTTP method, path, caller/access classification, and short purpose.
2. **Given** generated framework and operational routes, **When** totals are calculated, **Then**
   Actuator, Swagger, error, and unsupported authorization-server routes are excluded from the
   business endpoint total.
3. **Given** Cart and Notification scaffolds, **When** the catalog is read, **Then** both are shown as
   having zero implemented business HTTP endpoints rather than invented placeholder routes.

---

### User Story 2 - Explore APIs with Swagger Locally (Priority: P2)

As a developer, I want to opt in to one local Swagger UI and inspect each owning service's generated
OpenAPI document so that I can understand models and exercise permitted calls.

**Why this priority**: Interactive documentation shortens onboarding and manual contract testing,
but it depends on the P1 inventory being correct.

**Independent Test**: Enable API documentation in the local Docker Compose environment, open the
Gateway Swagger UI, and load each of the seven service documents without a missing-document error.

**Acceptance Scenarios**:

1. **Given** API documentation is explicitly enabled locally, **When** the Gateway Swagger UI is
   opened, **Then** Authentication, Product, Campaign, Flash Sale, Inventory, Order, and Payment are
   selectable.
2. **Given** a service document is selected, **When** its OpenAPI JSON is loaded, **Then** the
   implemented controller paths and request/response schemas for that service are present.
3. **Given** a protected endpoint, **When** a developer reads the catalog, **Then** the required JWT
   authority, authentication boundary, or Stripe signature requirement is clear before execution.

---

### User Story 3 - Keep Documentation Off Public Cloud by Default (Priority: P3)

As an operator, I want documentation endpoints disabled unless explicitly enabled so that internal
topology and contracts are not accidentally published through the cloud Gateway.

**Why this priority**: Documentation is a development aid and must not silently broaden public
ingress.

**Independent Test**: Start or render the system with default configuration and verify that Swagger,
OpenAPI proxy paths, and service API-doc endpoints are unavailable while business and operational
health behavior remains unchanged.

**Acceptance Scenarios**:

1. **Given** no API documentation flag is set, **When** a client requests a documentation path,
   **Then** the Gateway does not expose the catalog and service documents are not generated.
2. **Given** cloud manifests, **When** configuration is reviewed, **Then** no manifest enables API
   documentation by default.
3. **Given** documentation is enabled locally, **When** a protected business API is executed from
   Swagger, **Then** its existing authentication and authorization checks still apply.

### Edge Cases

- A controller is conditional on a runtime feature flag; the catalog records it as implemented but
  the live document contains it only when that owning feature is active.
- A framework owns a supported endpoint such as `POST /oauth2/token`; it must be catalogued even
  though no controller annotation exists.
- A service document contains internal endpoints; the Gateway may display them locally but must not
  create a public business route for those endpoints.
- One service is unavailable while Swagger UI is open; the other service documents remain
  independently selectable and the failed document reports a bounded load error.
- Duplicate HTTP paths with different methods count as separate endpoints; duplicate method/path
  pairs are not allowed in the catalog.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The repository MUST contain one canonical human-readable catalog of all supported HTTP
  endpoints implemented at the time of this feature.
- **FR-002**: The catalog MUST count endpoints by unique HTTP method plus normalized path.
- **FR-003**: The catalog MUST distinguish 33 public Gateway routes, 6 internal service-to-service
  routes, and 1 JWKS trust route, for 40 supported endpoints in total.
- **FR-004**: Each catalog entry MUST identify the owning service, access boundary, and purpose.
- **FR-005**: Authentication, Product, Campaign, Flash Sale, Inventory, Order, and Payment services
  MUST each provide an opt-in OpenAPI JSON document and Swagger UI.
- **FR-006**: API Gateway MUST provide an opt-in local Swagger UI catalog that links to the seven
  service-owned OpenAPI documents without becoming the owner of their contracts.
- **FR-007**: Documentation generation and Gateway document proxying MUST be disabled by default.
- **FR-008**: Enabling documentation MUST NOT bypass existing endpoint authentication,
  authorization, ownership, service identity, or webhook signature checks.
- **FR-009**: Internal endpoints MAY appear in local documentation, but MUST NOT become public
  business routes through API Gateway.
- **FR-010**: The documentation MUST explain local startup, direct service URLs, the aggregated
  Gateway URL, JWT authorization, and the difference between business and operational endpoints.
- **FR-011**: Cart Service and Notification Service MUST be explicitly recorded as having zero
  implemented business HTTP endpoints.
- **FR-012**: A repeatable validation MUST detect an endpoint-count mismatch, duplicate catalog
  rows, a missing service document definition, or documentation being enabled by default.

### Non-Functional Requirements

- **NFR-SEC-001**: Default local, Docker, and cloud configurations MUST keep API documentation
  disabled unless an operator opts in.
- **NFR-COMPAT-001**: The feature MUST not change any existing business method, path, status,
  envelope, header, or authorization rule.
- **NFR-MAINT-001**: The Springdoc version MUST be managed centrally so all documenting modules use
  the same compatible release.
- **NFR-VERIFY-001**: Cross-cutting build verification and static catalog validation MUST pass
  before completion.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A reviewer can identify all 40 supported endpoints and their access boundary from one
  document in under five minutes.
- **SC-002**: With one explicit local opt-in, all seven service OpenAPI documents are selectable from
  one Gateway Swagger UI.
- **SC-003**: With default configuration, zero Swagger/OpenAPI catalog paths are exposed through the
  public Gateway.
- **SC-004**: Automated validation reports exactly 40 unique supported method/path pairs and no
  undocumented owning service.
- **SC-005**: Existing cross-cutting Maven verification completes without business contract test
  regressions.

## Dependencies and Compatibility

- This is an additive documentation capability. Existing clients, Kafka consumers, database
  schemas, and deployment ordering are unchanged.
- Live documents are generated from each service's web boundary. The human-readable catalog remains
  the reviewed classification source for framework-owned and conditional endpoints.
- The Gateway only aggregates documents while explicitly enabled; it does not copy or redefine
  service-owned request/response contracts.

## Assumptions

- “Endpoint count” means supported application HTTP method plus path, excluding Actuator, Swagger,
  generic framework error, and unsupported authorization-server endpoints.
- Swagger/OpenAPI is intended for local development and controlled debugging, not public cloud
  exposure.
- Existing endpoint annotations, validation types, and response DTOs remain canonical; this feature
  does not normalize legacy response envelopes.
- The user's request to complete missing API documentation approves the documented additive scope
  and its no-business-behavior-change constraint.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Each service owns its generated document; no database or domain ownership
  changes.
- **External ingress**: Public business routes remain unchanged and continue through API Gateway;
  document aggregation is opt-in and disabled by default.
- **API/event contracts**: No observable business contract changes. Documentation describes the
  existing HTTP boundary; Kafka contracts are unaffected.
- **Durable and hot-path data**: No PostgreSQL, Redis, or Redis Lua changes.
- **Messaging reliability**: No Kafka consumer, outbox, retry, ordering, or recovery changes.
- **Root infrastructure ownership**: Shared Compose opt-in belongs under root `infra/`; service-owned
  OpenAPI configuration stays inside each service. No ADR exception is required.
- **Observability**: Actuator liveness, readiness, Prometheus, and trace propagation remain unchanged
  and are explicitly excluded from business endpoint totals.
- **Verification**: Static documentation validation and cross-cutting Maven verification are
  required. Database, Kafka, load, and migration tests are omitted because no business/data/message
  behavior changes.
- **Architecture decisions**: No service boundary, ingress ownership, persistence, or communication
  architecture changes; no ADR is required.

## Approval and History

- 2026-08-28 — Approved from the user's explicit request to inventory and complete API documentation.
- 2026-08-29 — Implementation verified with static catalog checks, affected-module compilation,
  Gateway verification (196 tests), and Docker Compose rendering.
