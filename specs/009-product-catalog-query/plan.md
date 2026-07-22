# Implementation Plan: Product Catalog Query

**Branch**: `009-product-catalog-query` | **Date**: 2026-07-17 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/009-product-catalog-query/spec.md`

**Status**: Verified

## Summary

Implement the first read-only product catalog query slice. Product-service owns catalog reads from its PostgreSQL schema and exposes public shopper HTTP endpoints. Api-gateway routes `/api/v1/catalog/**` to product-service. The implementation uses clean/hexagonal placement: web adapter -> application input port -> output port -> JPA persistence adapter. No product writes, stock, Redis, Kafka, outbox, migration, campaign, cart, order, or payment behavior is added.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.5.16

**Primary Dependencies**: Existing Spring Boot Web, Actuator, Liquibase, PostgreSQL driver, Testcontainers. Add `spring-boot-starter-data-jpa` to product-service for query persistence and repository/projection support.

**Storage**: Product-service PostgreSQL schema created by feature `008-product-catalog-schema`; no new migration.

**Testing**: JUnit 5, Spring Boot Test, MockMvc/TestRestTemplate style HTTP assertions, Testcontainers PostgreSQL, Liquibase-driven schema setup.

**Target Platform**: Maven monorepo services, local Docker Compose backing services, future Kubernetes Service/DNS.

**Project Type**: Java microservice monorepo with Spring Boot services.

**Performance Goals**: Bounded public catalog reads; product list uses pagination with deterministic ordering. Default page size is 20 and maximum accepted size is 100.

**Constraints**: Public traffic enters through api-gateway. Product visibility is exactly ACTIVE product + published timestamp + at least one ACTIVE variant. Prices are returned only at active variant level. Product-level display price is not computed. Category state and media presence do not decide product visibility. No cross-service database access.

**Scale/Scope**: Initial catalog browse/detail slice for product-service only, with enough query shape to learn JPA pagination/projections and avoid obvious N+1 behavior.

## Constitution Check

*GATE result before Phase 0 research: PASS. Re-check after Phase 1 design: PASS.*

- **Specification traceability**: FR-001 through FR-010 map to HTTP contracts, application use cases, JPA query adapter, gateway route, and Testcontainers integration tests.
- **Service ownership**: Product-service owns all catalog data access, JPA entities, repositories, application code, tests, and runtime configuration touched by this feature. No service reads another service database.
- **Communication**: Shopper ingress uses api-gateway. The synchronous contract is documented in `contracts/product-catalog-http.md`. No internal service-to-service call is introduced.
- **Data and messaging**: PostgreSQL remains durable truth. Redis Lua, Kafka, idempotent consumers, and outbox are out of scope because this feature is read-only and emits no events.
- **Root infrastructure ownership**: No root `infra/` change is required. Gateway route configuration remains service-owned runtime configuration in `services/api-gateway/src/main/resources/application.yml`.
- **Observability**: Existing Actuator liveness/readiness/Prometheus declarative setup remains. No `PrometheusMeterRegistry` is constructed in Java. Public route trace propagation is left to existing HTTP headers and future tracing work; no custom tracing library is added.
- **Contracts and dependencies**: New production dependency `spring-boot-starter-data-jpa` is justified for product-service query persistence. HTTP contract is documented before implementation. No Kafka contract.
- **Validation**: Product-service module verification is required. Integration tests cover catalog visibility, pagination, category filtering, detail shape, active variant prices, and hidden product behavior. Gateway context test covers route configuration presence. Load testing is not required for this first bounded slice.

## Phase 0 Research

See [research.md](research.md).

## Phase 1 Design

See [data-model.md](data-model.md), [contracts/product-catalog-http.md](contracts/product-catalog-http.md), and [quickstart.md](quickstart.md).

## Project Structure

### Documentation

```text
specs/009-product-catalog-query/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── product-catalog-http.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code

```text
services/product-service/
├── pom.xml
├── src/main/java/com/philia/flashsale/product/
│   ├── ProductServiceApplication.java
│   ├── application/
│   │   ├── port/in/BrowseCatalogUseCase.java
│   │   ├── port/out/LoadCatalogPort.java
│   │   └── service/ProductCatalogQueryService.java
│   ├── config/ProductCatalogConfiguration.java
│   ├── domain/model/*.java
│   └── adapter/
│       ├── in/web/*.java
│       └── out/persistence/*.java
├── src/main/resources/application.yml
└── src/test/java/com/philia/flashsale/product/
    ├── ProductServiceApplicationTests.java
    └── ProductCatalogQueryTests.java

services/api-gateway/
├── src/main/resources/application.yml
└── src/test/java/com/philia/flashsale/gateway/ApiGatewayApplicationTests.java
```

**Structure Decision**: Use the clean/hexagonal scaffold already chosen for the repository. Domain/application code does not import Spring MVC, JPA, Redis, Kafka, or provider SDKs. Spring MVC and Spring Data JPA stay in adapters/configuration.

## Verification Strategy

| Requirement | Unit | Integration | Contract | E2E | Load/Concurrency |
|-------------|------|-------------|----------|-----|------------------|
| FR-001 visible product browse | Application mapping where useful | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-002 category browse | Application mapping where useful | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-003 product detail visibility | Application mapping where useful | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-004 bounded paging | Controller/query integration | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-005 deterministic order | Query integration | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-006 response shape | Web DTO assertions | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-007 missing records | Web error assertions | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-008 gateway access | Gateway config test | Gateway context test | `product-catalog-http.md` | Not required | Not required |
| FR-009 variant-level price | Response assertions | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |
| FR-010 category/media non-visibility | Response assertions | Testcontainers HTTP test | `product-catalog-http.md` | Not required | Not required |

Required commands:

```powershell
.\mvnw.cmd -pl services/product-service -am verify
.\mvnw.cmd -pl services/api-gateway -am verify
```

Run `.\mvnw.cmd clean verify` only if the module changes reveal cross-reactor risk.

## Complexity Tracking

No constitutional violation or ADR exception is required.
