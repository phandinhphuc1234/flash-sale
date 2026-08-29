# Implementation Plan: Authenticated Cart MVP

**Branch**: `codex/cart-mvp` | **Date**: 2026-08-29 | **Spec**: [spec.md](spec.md)

**Input**: Approved feature specification from `specs/048-cart-mvp/spec.md`

**Status**: Approved — implementation tasks may be generated

## Summary

Implement an authenticated, durable Cart capability in `cart-service` with four shopper operations:
read cart, set an absolute item quantity, remove one item, and clear the cart. Cart persists only
shopper intent (`variantId`, quantity) in `cart_db`; it obtains current display data through one
batch HTTP query to Product Service and never owns price, stock, reservation, order, or payment.

Cart uses PostgreSQL atomic upsert semantics for concurrent idempotent mutation, Spring Security JWT
resource-server validation for shopper ownership, and an OpenFeign adapter with a dedicated OAuth2
client-credentials identity for Product lookup. Product reads fail soft, returning saved intent with
`detailsAvailable=false`; mutations fail closed before any database change. No Kafka, Redis, outbox,
checkout orchestration, cart expiry, or cloud rollout is added.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.16; Spring Cloud 2025.0.3; `common-web`; Spring Web,
Validation, Data JPA, Security OAuth2 Resource Server, OAuth2 Client, OpenFeign, Actuator,
Liquibase, PostgreSQL driver, Springdoc; ArchUnit 1.4.2 in test scope

**Storage**: Cart-owned PostgreSQL `cart_db`; additive `carts` and `cart_items` tables; no Product
foreign key and no cross-service database access

**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers PostgreSQL, JDK test HTTP
server for exact Product client contract tests, ArchUnit, and a bounded PowerShell local smoke runner

**Target Platform**: Independently built Spring Boot container; local Docker Compose behind API
Gateway in this feature; Linux/Kubernetes deployment deliberately deferred

**Project Type**: Maven monorepo with independently deployable web services

**Performance Goals**: At least 95% of local Cart reads finish within one second while Product is
available; one Product batch request per non-empty Cart read; 100 repeated identical mutations
produce one semantic result

**Constraints**: Authenticated shoppers only; quantity 1–10; owner is derived from JWT subject;
Product lookup occurs outside Cart database transactions; Product timeout/auth/5xx never yields
fabricated catalog data; no automatic HTTP retry; no expiry; no Kafka/Redis/outbox; all public
traffic enters through API Gateway

**Scale/Scope**: Four public Cart endpoints, one internal Product batch endpoint, two new Cart-owned
tables, one new machine client/scope, one local migration runner, and focused changes to Cart,
Product, Authentication, API Gateway, root Compose, API documentation, and tests

## Constitution Check

*GATE: Passed before Phase 0 research and re-checked after Phase 1 design.*

| Gate | Result | Design evidence |
|------|--------|-----------------|
| Specification traceability | PASS | Every behavior maps to FR-001–FR-017; no quota, TTL, checkout, or messaging behavior is added. |
| Service ownership | PASS | Cart owns `cart_db` tables; Product only reads its own schema; no entity, repository, or domain type is shared. ADR 0002 already approves the boundary. |
| Communication | PASS | Shopper traffic uses API Gateway. Cart → Product is one documented blocking HTTP batch query over service DNS, not a per-item loop. |
| Data and messaging | PASS | PostgreSQL is Cart durable truth. Redis, Kafka, stock mutation, and outbox are not applicable. |
| Root infrastructure ownership | PASS | Compose and smoke orchestration stay in `infra/`; Cart migrations and runtime configuration stay in `services/cart-service`. |
| Observability | PASS | Existing health/Prometheus endpoints remain declarative; HTTP boundaries propagate `X-Trace-Id` and W3C context; no manual registry is created. |
| Contracts and dependencies | PASS | Public and internal HTTP contracts are in `contracts/`; every new Cart dependency is listed and justified below. |
| Validation | PASS | Domain, application, web, persistence, client-contract, security, architecture, module, full-reactor, and local E2E gates are defined. Kafka/load layers are explicitly not applicable beyond the focused Cart profile. |

### Dependency Justification

| Dependency | Scope | Reason |
|------------|-------|--------|
| `common-web` | production | Reuse the repository success/error envelopes instead of creating Cart-specific top-level envelopes. |
| `spring-boot-starter-validation` | production | Enforce transport quantity and identifier shape before invoking application behavior. |
| `spring-boot-starter-data-jpa` + PostgreSQL driver | production/runtime | Cart owns durable relational state and uses Spring Data JPA as its default persistence technology. |
| `spring-boot-starter-security` + OAuth2 Resource Server | production | Revalidate shopper JWT issuer, audience, type, and subject inside Cart. |
| `spring-boot-starter-oauth2-client` | production | Obtain and cache a short-lived Cart service token without forwarding the shopper token. |
| `spring-cloud-starter-openfeign` | production | Implement the approved synchronous Product batch lookup behind a Cart-owned output port. |
| `springdoc-openapi-starter-webmvc-ui` | production | Publish the Cart public contract into the existing local aggregated API documentation. |
| Testcontainers PostgreSQL + Spring Security Test | test | Verify real PostgreSQL constraints/upserts and authenticated ownership behavior. |
| ArchUnit 1.4.2 | test | Prevent Feign, web, JPA, and security types from leaking into application/domain packages. |

MapStruct, WireMock, Resilience4j, Kafka, Redis, and a cache dependency are intentionally omitted.
The feature has small explicit mappings, uses a JDK HTTP test server, forbids automatic fallback
data, and does not need asynchronous delivery or a hot-path cache.

## Architecture and Runtime Flows

### Read flow

```text
Frontend
  -> API Gateway validates public JWT
  -> Cart web adapter revalidates JWT and derives owner subject
  -> GetCartUseCase
  -> Cart persistence adapter loads saved variantId + quantity
  -> ProductDisplayPort (one batch call; no Cart DB transaction is open)
       -> OAuth2 client credentials: cart-service / catalog.variant-display.read
       -> Product internal batch endpoint
       -> Product-owned display projection
  -> Cart query result
  -> ApiResponse<CartResponse>
```

An empty Cart skips Product entirely. A Product token, timeout, connection, malformed-response,
401/403, or 5xx failure is translated by the outbound adapter into `DETAILS_UNAVAILABLE`; the HTTP
read remains `200` with persisted identities and quantities, nullable Product fields, and
`detailsAvailable=false`. No stale or invented price is returned.

### Set quantity flow

```text
PUT quantity
  -> validate JWT owner + quantity 1..10
  -> SetCartItemUseCase
  -> ProductDisplayPort validates one variant outside transaction
       -> missing: 404; not sellable: 409; dependency unavailable: 503
  -> atomic Cart persistence capability
       -> create/find Cart by owner
       -> upsert unique (cart_id, variant_id) quantity
  -> 200 CartItemResponse using the verified Product result
```

The remote query cannot hold a Cart database connection or lock. Product rejection or dependency
failure occurs before persistence. PostgreSQL uniqueness plus `ON CONFLICT ... DO UPDATE` inside the
persistence adapter provides idempotent same-quantity replay and a valid last-committed value under
different concurrent quantities.

### Remove and clear flow

Remove deletes the owner's matching item and clear deletes every item belonging to the authenticated
owner. Both are transactional, return `204`, are no-ops when already absent, and never call Product.
The owner identifier is never accepted from path, query, or body input.

## Security and Failure Policy

- Public Cart endpoints require a user access token with the configured public issuer,
  `flash-sale-api` audience, `at+jwt` type, and nonblank subject.
- Gateway forwards the shopper token only to Cart; Cart never forwards it to Product.
- Authentication Service provisions `cart-service` with only
  `catalog.variant-display.read`. Product revalidates internal issuer, signature, expiry,
  `flash-sale-internal-api` audience, exact subject, and exact scope.
- Cart Product connect timeout is 300 ms and read timeout is 600 ms. Feign retry is disabled.
- `X-Trace-Id` is echoed at public boundaries and propagated to Product; W3C trace headers remain
  instrumentable. Authorization headers, tokens, secrets, and response bodies are never logged.
- Expected Product business outcomes map by status plus stable error code; Feign/provider exception
  types and raw messages never leave the outbound adapter.
- The ignored `infra/docker/.env` requires a manually generated `CART_CLIENT_SECRET`. Implementation
  updates `.env.example` and stops before live smoke validation to ask the user to populate the
  ignored value. The agent does not read, print, commit, or manufacture the user's runtime secret.

## Persistence and Migration Strategy

- Cart uses a service-owned additive Liquibase changeset. Runtime replicas keep Liquibase disabled.
- Root Compose gains a one-off `cart-migration` service under the `migrations` profile; it runs before
  Cart application startup in validation.
- The change is expand-only: create `carts`, create `cart_items`, add primary/unique/check/foreign-key
  constraints and indexes. It does not alter an existing table or data.
- Re-running migration is safe through Liquibase history. Application rollback restores the prior
  image without dropping tables. Destructive table rollback is never part of an automated runtime
  path; local disposable-database rollback may be tested separately.
- Cart application transactions are short and contain no network call. Product has no schema
  migration for its additive read contract.

## Project Structure

### Documentation (this feature)

```text
specs/048-cart-mvp/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── contracts/
    ├── public-cart-http.md
    └── product-variant-display-http.md
```

### Source Code (repository root)

```text
services/cart-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/philia/flashsale/cart/
    │   │   ├── domain/
    │   │   │   ├── model/Cart.java
    │   │   │   ├── model/CartItem.java
    │   │   │   └── valueobject/CartQuantity.java
    │   │   ├── application/
    │   │   │   ├── command/
    │   │   │   ├── query/
    │   │   │   ├── result/
    │   │   │   ├── port/in/
    │   │   │   ├── port/out/
    │   │   │   └── usecase/
    │   │   ├── adapter/
    │   │   │   ├── in/web/
    │   │   │   └── out/
    │   │   │       ├── persistence/jpa/
    │   │   │       └── client/product/
    │   │   ├── security/
    │   │   ├── websupport/error/
    │   │   └── configuration/
    │   └── resources/
    │       ├── application.yml
    │       └── db/changelog/
    └── test/java/com/philia/flashsale/cart/
        ├── domain/
        ├── application/
        ├── adapter/in/web/
        ├── adapter/out/persistence/
        ├── adapter/out/client/product/
        ├── security/
        └── architecture/

services/product-service/src/
├── main/java/com/philia/flashsale/product/catalog/
│   ├── application/port/in/LookupVariantDisplaysUseCase.java
│   ├── application/result/VariantDisplayResult.java
│   ├── application/service/ProductVariantDisplayQueryService.java
│   └── adapter/in/web/internal/
└── test/java/com/philia/flashsale/product/catalog/

services/authentication-service/src/main/
├── java/com/philia/flashsale/authentication/configuration/
└── resources/application.yml

services/api-gateway/src/main/
├── java/com/philia/flashsale/gateway/security/
└── resources/application.yml

infra/docker/
├── compose.yml
├── .env.example
└── smoke/feature-048-cart.ps1

docs/api/
├── README.md
└── frontend-integration-guide.md
```

**Structure Decision**: Cart currently has one cohesive capability, so the existing direct
`domain/application/adapter` service structure is retained instead of introducing redundant
`cart/cart`. HTTP, Product DTOs, persistence entities, application models, and domain models remain
separate. Product adds a narrow query use case inside its existing `catalog` capability rather than
a Cart-owned package. Configuration wires adapters but does not contain business rules.

## Delivery Sequence

1. Approve this plan and both HTTP contracts.
2. Generate dependency-ordered `tasks.md`; run `speckit-analyze` before code.
3. Add tests/contracts and required dependencies.
4. Add Product batch projection and endpoint, then Authentication machine identity.
5. Add Cart domain/application behavior and PostgreSQL persistence/migration.
6. Add Product client, shopper security, web/error/OpenAPI boundaries, and Gateway routes.
7. Add Compose migration/runtime wiring and the bounded local smoke runner.
8. Run module checks, cross-service checks, full reactor verification, and smoke evidence.
9. Update API inventory/frontend guide and record validation results before commit/push.

Cloud Kustomize, ECR repositories, eight-service delivery expansion, Argo CD, and external exposure
are deferred to a later combined Cart/Notification deployment feature after both local MVPs pass.

## Validation Strategy

| Layer | Required evidence |
|-------|-------------------|
| Domain | Quantity accepts 1–10 only; one variant identity per Cart; no framework imports. |
| Application | Ownership is injected from authenticated context; Product validation precedes mutation; read failure degrades per item; remove/clear are idempotent. |
| Persistence | PostgreSQL Testcontainers proves tables, constraints, owner isolation, atomic same/different-quantity concurrency, and no duplicate Cart/item. |
| Product contract | Exact method/path/body/response order, not-found/non-sellable representation, subject/scope/audience rejection, and trace propagation. |
| Cart Product adapter | Success mapping; response identity validation; no retry; timeout/connect/401/403/5xx/malformed response mapping; no shopper-token relay. |
| Web/security | Shared envelopes, statuses, 204 empty bodies, JWT issuer/audience/type/subject, no owner input, trace header, and safe errors. |
| Architecture | ArchUnit enforces `adapter -> application -> domain` and prevents JPA/Feign/web/security imports in the core. |
| Module | `./mvnw -pl services/cart-service,services/product-service,services/authentication-service,services/api-gateway -am verify`. |
| Full reactor | `./mvnw clean verify`. |
| Local E2E | Migration, auth, Product fixture, Cart CRUD/replay/isolation, Product outage read degradation, mutation 503/no-change, recovery, and a bounded 100-replay profile. |

Kafka contract tests are not applicable because the feature has no events. Seckill load tests are not
applicable because Cart is outside the atomic purchase hot path; the focused replay/read profile is
sufficient for SC-002 and SC-004. Kubernetes dry-run is deferred with cloud scope.

## Post-Design Constitution Re-check

PASS. Phase 1 does not introduce a service-boundary change, shared business model, cross-database
access, uncontrolled ingress, undocumented contract, Redis/Kafka misuse, runtime migration race, or
unjustified dependency. No constitution exception or new ADR is required.

## Complexity Tracking

No constitutional violations require justification.
