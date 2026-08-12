# Feature Specification: Cart Service Scaffold

**Feature Branch**: `[007-cart-service-scaffold]`

**Created**: 2026-07-15

**Status**: Approved

**Input**: User description: "Create a separate cart-service and set it up fully like the other services."

## User Scenarios & Testing

### User Story 1 - Work in an independent cart service (Priority: P1)

As a developer, I can build and start an independently owned `cart-service` shell from the monorepo so future cart behavior has a clear service boundary and a consistent Clean/Hexagonal structure.

**Why this priority**: The module and its boundary must exist before any cart use case can be specified or implemented safely.

**Independent Test**: Build only `cart-service` with its required reactor dependencies, run its Spring application-context test, and inspect the package markers without finding any speculative cart behavior.

**Acceptance Scenarios**:

1. **Given** the root Maven reactor, **When** the cart module is selected, **Then** it builds as an independent Spring Boot 3.x/Java 21 service.
2. **Given** the cart source tree, **When** a developer inspects it, **Then** the approved `domain`, `application`, `adapter`, and `configuration` zones are present with dependency direction `adapter -> application -> domain` and `configuration -> adapter + application`.
3. **Given** this setup-only feature, **When** production sources and Liquibase files are inspected, **Then** no Cart controller, DTO, use case, entity, repository, table, business changeset, event, or integration client exists.

---

### User Story 2 - Run the cart shell in the local topology (Priority: P2)

As a developer, I can include `cart-service` in the existing Compose `apps` profile and optionally expose a collision-free local debug port while shared infrastructure remains root-owned.

**Why this priority**: A service shell is useful only when it follows the same repeatable local container workflow as its peers.

**Independent Test**: Render the base Compose configuration and the development override with the `apps` profile, confirming that `cart-service` is resolvable on the internal network and only the development override publishes its port.

**Acceptance Scenarios**:

1. **Given** the base Compose topology, **When** configuration is rendered, **Then** `cart-service` uses internal port `8080`, has no host port, and inherits the same runtime baseline as other business services.
2. **Given** the development override, **When** configuration is rendered, **Then** `cart-service` publishes configurable host port `18089` to container port `8080`.
3. **Given** a newly initialized local PostgreSQL volume, **When** the bootstrap script runs, **Then** it provisions the cart-owned logical database `cart_db` without defining any cart tables.
4. **Given** the application configuration, **When** Spring Boot auto-configuration is active, **Then** health, liveness, readiness, info, and Prometheus endpoints are exposed declaratively without a manually constructed registry.

---

### User Story 3 - Understand the cart boundary before adding behavior (Priority: P3)

As a developer or reviewer, I can see why Cart is separate, what it owns, and what remains intentionally deferred before planning the first cart feature.

**Why this priority**: A new microservice adds operational cost and must have an explicit, reviewable boundary rather than being inferred from folders.

**Independent Test**: Follow the repository overview, architecture diagram, deployment guide, Clean Architecture guide, and ADR and obtain one consistent current topology and scope statement.

**Acceptance Scenarios**:

1. **Given** the architecture documentation, **When** the service topology is reviewed, **Then** it shows ten services including `cart-service` and a future cart-owned durable database.
2. **Given** the accepted ADR, **When** Cart ownership is reviewed, **Then** Cart is distinct from product catalog, flash-sale stock reservation, and durable order creation.
3. **Given** a future public Cart endpoint, **When** it is planned, **Then** the documentation makes clear that a documented contract and API Gateway route require a separate approved feature.

### Edge Cases

- Existing PostgreSQL volumes do not rerun initialization scripts; documentation must warn that `cart_db` is created automatically only for a fresh volume and must not instruct developers to destroy data implicitly.
- The service can expose Actuator endpoints while it has no business endpoint; observability must not be mistaken for approval of a Cart API.
- Liquibase can be wired with an empty master changelog while Compose disables it for the current no-datasource shell; no first business migration may be invented.
- The base Compose file must not expose a direct host port that bypasses the API Gateway policy.
- Adding a service must not cause existing debug ports, database names, or completed feature artifacts to be renumbered or rewritten.

## Requirements

### Functional Requirements

- **FR-001**: The root Maven reactor MUST include an independently buildable `services/cart-service` module.
- **FR-002**: `cart-service` MUST use the repository's Spring Boot 3.x parent baseline, Java 21, and the standard business-service dependencies for Spring MVC, Actuator, the runtime Prometheus registry, Liquibase, and Spring Boot tests.
- **FR-003**: The production entry point MUST use package `com.philia.flashsale.cart`, class `CartServiceApplication`, and application name `cart-service`.
- **FR-004**: The module MUST contain the canonical marker-only business-service structure for domain model/value objects/policies/events/exceptions, application commands/queries/results/use cases/input and output ports, inbound web/messaging adapters, outbound persistence/HTTP/messaging adapters, and configuration.
- **FR-005**: Runtime configuration MUST declaratively expose `health`, `info`, and `prometheus`, including liveness and readiness probes, on internal port `${SERVER_PORT:8080}`.
- **FR-006**: Production code MUST NOT instantiate or manually configure a Prometheus meter registry.
- **FR-007**: The module MUST include Liquibase with an empty master changelog and an empty changes location, but MUST NOT include a Cart schema or business changeset.
- **FR-008**: The module MUST have a service-local multi-stage Dockerfile that builds from the monorepo root and runs as a non-root user on port `8080`.
- **FR-009**: Root-owned Compose configuration MUST include `cart-service` in the `apps` profile, use DNS name `cart-service`, avoid a base host-port publication, and provide optional development mapping `${CART_SERVICE_PORT:-18089}:8080`.
- **FR-010**: Local PostgreSQL bootstrap configuration MUST provision logical database `cart_db` only; every future schema and migration remains owned by `cart-service`.
- **FR-011**: The environment example MUST define the cart image and development port without renumbering existing services.
- **FR-012**: An accepted ADR MUST document the new service boundary, alternatives, ownership, consequences, migration impact, and rollback path before production module implementation.
- **FR-013**: Current repository, deployment, Clean Architecture, technology ownership, Kubernetes-readiness, and system-overview documentation MUST include `cart-service` where the live topology or future deployment skeleton is enumerated.
- **FR-014**: This feature MUST NOT create a Cart API or gateway route, controller, transport DTO, mapper, domain object, use case, persistence entity, repository, database table, Kafka contract, producer/consumer, Redis integration, or inter-service client.
- **FR-015**: Existing services, their port assignments, and completed feature artifacts `001` through `006` MUST remain behaviorally unchanged.
- **FR-016**: Verification MUST include the cart module build, the full Maven build, both base and development Compose renders, a source-scope audit, and documentation consistency checks.

### Key Entities

This setup feature defines no business entity. `Cart`, `CartItem`, pricing snapshots, ownership, expiration, merge rules, and checkout behavior require a later specification and first migration.

## Success Criteria

### Measurable Outcomes

- **SC-001**: The reactor contains ten service modules, and the isolated `cart-service` verification completes successfully.
- **SC-002**: All ten Spring application-context tests pass in the full repository verification.
- **SC-003**: Static inspection finds exactly one cart production Java class and one cart test Java class, with zero Cart business classes, routes, contracts, tables, or changesets.
- **SC-004**: Base and development Compose configurations render successfully; only the development render publishes cart host port `18089`.
- **SC-005**: The cart service configuration exposes all five operational endpoints: health, liveness, readiness, info, and Prometheus.
- **SC-006**: Every current-topology document reviewed by this feature consistently includes `cart-service`, `cart_db` where durable ownership is shown, and a link to the accepted service-boundary ADR.

## Assumptions

- The user has approved Cart as an independent service boundary by explicitly requesting a separate `cart-service`.
- `product-service` remains responsible for catalog data, `flashsale-service` for atomic hot-path reservation, and `order-service` for durable order lifecycle.
- `cart-service` will use Spring MVC and may use virtual threads through the existing container environment, matching ordinary business services rather than the reactive API Gateway.
- PostgreSQL is the future durable source of truth for Cart; Redis may be proposed later only as an acceleration mechanism with explicit semantics.
- No public Cart use case is sufficiently specified yet, so no gateway route or business protocol belongs in this feature.

## Human Decisions Required

No unresolved decision blocks this scaffold. Cart behavior, storage schema, retention, anonymous/authenticated ownership, item limits, price revalidation, availability checks, checkout coordination, and events are deliberately deferred to later Spec Kit features.

## Constitutional Constraints

- **Service ownership**: `cart-service` owns future cart state, database access, entities, repositories, and migrations; this feature creates no schema and accesses no other service database.
- **External ingress**: No public route is added. Future external traffic must enter through `api-gateway` after a documented HTTP contract is approved.
- **API/event contracts**: No business HTTP or Kafka contract is added or changed. Actuator endpoints are the existing operational contract.
- **Durable and hot-path data**: Local infrastructure provisions `cart_db`; no table, Redis key, Lua operation, or durable record is introduced.
- **Messaging reliability**: No producer, consumer, outbox, idempotency, retry, ordering, or reconciliation behavior is introduced.
- **Root infrastructure ownership**: Shared Compose and PostgreSQL bootstrap changes remain under root `infra/`; runtime configuration, Dockerfile, and empty migrations remain service-owned.
- **Observability**: Liveness, readiness, info, and Prometheus use Spring Boot auto-configuration; no manual registry code or new trace instrumentation is introduced.
- **Verification**: Context, Maven reactor, Compose-render, static scope, and documentation checks apply. Business unit, integration, contract, load, migration, and Kubernetes manifest tests are omitted because no business behavior, contract, schema, load target, or Kubernetes manifest is introduced.
- **Architecture decisions**: `docs/adr/0002-cart-service-boundary.md` is required and must be accepted before the module is implemented.
