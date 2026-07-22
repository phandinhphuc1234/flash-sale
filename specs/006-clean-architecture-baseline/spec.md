# Feature Specification: Clean Architecture Working Baseline

**Feature Branch**: `[006-clean-architecture-baseline]`

**Created**: 2026-07-15

**Status**: Approved

**Input**: User description: "Complete the project's basic Clean Architecture structure to a usable baseline, keep it intentionally small, and do not implement the product read feature yet."

## User Scenarios & Testing

### User Story 1 - Start a service slice from a consistent baseline (Priority: P1)

As a developer, I can open any business service and immediately identify where domain rules, use-case input, use-case output, ports, adapters, and configuration belong, including a distinct location for read requests.

**Why this priority**: A small but complete baseline prevents every first feature from inventing a different package shape.

**Independent Test**: Inspect the eight business-service source trees and confirm that their existing Clean/Hexagonal zones include an empty query-model location while containing no new behavior.

**Acceptance Scenarios**:

1. **Given** any business service, **When** a developer plans a command or query use case, **Then** the source tree provides clear application locations for its input and result models.
2. **Given** the edge gateway, **When** its source tree is inspected, **Then** it remains lean and does not imply ownership of persistence or business models.

---

### User Story 2 - Place common Java concerns without breaking boundaries (Priority: P2)

As a developer coming from a layered monolith, I can look up where controllers, DTOs, mappers, exceptions, validation, persistence types, external clients, schedulers, security, and tests belong in this repository.

**Why this priority**: Names such as `dto`, `mapper`, and `service` are easy to centralize incorrectly and can quietly reverse the dependency direction.

**Independent Test**: Use the architecture guide and reviewer contract to classify each common concern without introducing a root-level technical package or a shared business type.

**Acceptance Scenarios**:

1. **Given** an HTTP request DTO, **When** a developer consults the guide, **Then** the DTO and its mapper are placed beside the inbound web adapter rather than in a shared root package.
2. **Given** a persistence entity, **When** a developer consults the guide, **Then** it remains inside the owning service's outbound persistence adapter and is not treated as the domain model.
3. **Given** a concern that no approved use case currently needs, **When** the developer applies the create-on-demand rule, **Then** no placeholder class or speculative nested package is added.

---

### User Story 3 - Preserve the empty business baseline (Priority: P3)

As a maintainer, I can verify that this setup change introduces architecture guidance only and does not silently begin the Product Catalog feature or another business slice.

**Why this priority**: Product API behavior, persistence, and contracts require their own detailed specification and must not be guessed during repository setup.

**Independent Test**: Inspect the changed Java, contract, migration, dependency, and gateway files and confirm that none were introduced or modified for business behavior.

**Acceptance Scenarios**:

1. **Given** the completed baseline, **When** Product service sources and gateway routes are inspected, **Then** no product controller, query use case, DTO, entity, repository, route, or `GET` endpoint has been created.
2. **Given** the completed baseline, **When** dependencies and migrations are inspected, **Then** no new production dependency or first business migration has been added.

### Edge Cases

- The gateway must remain an edge profile even though business services use a richer baseline.
- A future service may not need messaging, scheduling, security, or an external client; the guide must not make those packages mandatory.
- A future DTO may represent HTTP, Kafka, persistence, or an external provider; each representation must stay with its owning adapter rather than in one global `dto` package.
- Existing empty marker files and previously completed feature artifacts must remain intact.
- The word `query` in a package marker must not be interpreted as approval for any particular endpoint or database query.

## Requirements

### Functional Requirements

- **FR-001**: Each of the eight business services MUST retain its existing `domain`, `application`, `adapter`, and `configuration` zones.
- **FR-002**: Each business service MUST include an empty `application/query` location for future use-case query models without adding a query implementation.
- **FR-003**: `api-gateway` MUST remain lean and MUST NOT gain persistence, business-domain, messaging, or query-model placeholders from this feature.
- **FR-004**: The architecture guide MUST define placement for controllers, transport DTOs, application commands/queries/results, mappers, validation, exceptions, persistence types, external integrations, scheduling, security, configuration, and tests.
- **FR-005**: The guide MUST preserve dependency direction `adapter -> application -> domain` and `configuration -> adapter + application`.
- **FR-006**: The guide MUST require create-on-demand nested packages and MUST reject generic root packages such as `dto`, `mapper`, `entity`, `repository`, `service/impl`, `utils`, or `common` for business concerns.
- **FR-007**: The repository overview MUST link to the Clean/Hexagonal architecture guide.
- **FR-008**: This feature MUST NOT add or modify a production Java class, Maven dependency, runtime configuration, database changeset, HTTP/Kafka business contract, gateway route, or infrastructure asset.
- **FR-009**: This feature MUST NOT implement Product Catalog behavior, including a product controller, input port, application use case, DTO, entity, repository, migration, route, or `GET` endpoint.
- **FR-010**: Previously completed feature artifacts, especially `specs/001-*` and `specs/002-*`, MUST NOT be rewritten to describe this new delta.
- **FR-011**: Validation MUST verify the required markers, documentation rules, unchanged Java-source inventory, and successful repository build.

## Success Criteria

### Measurable Outcomes

- **SC-001**: All eight business services expose the same application input categories—command and query—without adding any implementation class.
- **SC-002**: A developer can classify every concern listed in FR-004 using one placement table in under five minutes.
- **SC-003**: Static inspection finds zero newly introduced business Java classes, business contracts, migrations, routes, or dependencies.
- **SC-004**: The gateway retains its lean profile and all nine existing application context tests remain buildable.
- **SC-005**: The root README provides a direct link to the architecture guide.

## Assumptions

- The existing feature 002 scaffold is the approved architectural starting point and should be refined, not replaced.
- Marker files are sufficient for otherwise-empty package locations.
- DTO, mapper, error, validation, persistence subpackages, security, scheduling, and provider packages are created only with the feature that needs them.
- No service boundary or dependency direction changes in this feature, so a new ADR is not required.

## Human Decisions Required

No unresolved business or architectural decision is required for this setup-only delta. Product Catalog behavior remains deliberately deferred to a future feature specification.

## Constitutional Constraints

- **Service ownership**: No schema, entity, repository, domain model, or service-to-service database access is introduced.
- **External ingress**: No public route or gateway behavior changes.
- **API/event contracts**: No HTTP or Kafka business contract is added or changed.
- **Durable and hot-path data**: No PostgreSQL schema, Redis key, or Redis Lua behavior is introduced.
- **Messaging reliability**: No producer, consumer, idempotency, outbox, retry, ordering, or recovery behavior is introduced.
- **Root infrastructure ownership**: Root infrastructure and service runtime assets are unchanged.
- **Observability**: Existing declarative health and Prometheus behavior remains unchanged; no tracing or registry code is introduced.
- **Verification**: Static structure and scope audits plus the full Maven build apply. Contract, migration, load, and Kubernetes tests are omitted because their corresponding artifacts and behaviors do not change.
- **Architecture decisions**: No ADR is required because the approved service boundaries, dependency direction, communication, persistence ownership, and deployment model remain unchanged.

