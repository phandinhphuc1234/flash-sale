# Tasks: Product Catalog Query

**Input**: Design documents from `specs/009-product-catalog-query/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/product-catalog-http.md`, `quickstart.md`

## Phase 1: Setup

- [x] T001 Add `spring-boot-starter-data-jpa` to `services/product-service/pom.xml`
- [x] T002 Configure JPA validation and disable Open Session in View in `services/product-service/src/main/resources/application.yml`
- [x] T003 Add declarative api-gateway route for `/api/v1/catalog/**` in `services/api-gateway/src/main/resources/application.yml`

## Phase 2: Foundational

- [x] T004 Create domain catalog records in `services/product-service/src/main/java/com/philia/flashsale/product/domain/model/`
- [x] T005 Create input and output ports in `services/product-service/src/main/java/com/philia/flashsale/product/application/port/`
- [x] T006 Create application service and bean configuration in `services/product-service/src/main/java/com/philia/flashsale/product/application/service/ProductCatalogQueryService.java` and `services/product-service/src/main/java/com/philia/flashsale/product/config/ProductCatalogConfiguration.java`
- [x] T007 Create persistence JPA entities, projections, repositories, and adapter in `services/product-service/src/main/java/com/philia/flashsale/product/adapter/out/persistence/`
- [x] T008 Create web DTOs, controller, and exception handler in `services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/`

## Phase 3: User Story 1 - Browse Visible Products (P1)

**Goal**: Shoppers can browse a bounded, deterministic list of visible products.

**Independent Test**: Seed visible and hidden products, call the product list endpoint, and verify only ACTIVE/published products with at least one ACTIVE variant are returned with bounded paging.

- [x] T009 [US1] Add Testcontainers HTTP tests for visible product browse in `services/product-service/src/test/java/com/philia/flashsale/product/ProductCatalogQueryTests.java`
- [x] T010 [US1] Implement product browse endpoint behavior in `services/product-service/src/main/java/com/philia/flashsale/product/adapter/in/web/ProductCatalogController.java`
- [x] T011 [US1] Verify product browse behavior with `.\mvnw.cmd -pl services/product-service -am verify`

## Phase 4: User Story 2 - Browse Products by Category (P2)

**Goal**: Shoppers can filter visible products by an existing category.

**Independent Test**: Seed categories and memberships, call the category-filtered endpoint, and verify matching visible products, empty category results, and missing category error behavior.

- [x] T012 [US2] Add category browse and category-filtered product tests in `services/product-service/src/test/java/com/philia/flashsale/product/ProductCatalogQueryTests.java`
- [x] T013 [US2] Implement category browse and category filter behavior in product-service web/application/persistence files
- [x] T014 [US2] Verify category browse behavior with `.\mvnw.cmd -pl services/product-service -am verify`

## Phase 5: User Story 3 - View Product Detail (P3)

**Goal**: Shoppers can view visible product detail with active variant prices, categories, and active media.

**Independent Test**: Seed one visible product with active/inactive variants and media, call detail endpoint, and verify active variant prices and active media are returned while hidden products return not found.

- [x] T015 [US3] Add product detail tests in `services/product-service/src/test/java/com/philia/flashsale/product/ProductCatalogQueryTests.java`
- [x] T016 [US3] Implement product detail assembly in product-service web/application/persistence files
- [x] T017 [US3] Verify product detail behavior with `.\mvnw.cmd -pl services/product-service -am verify`

## Phase 6: Gateway and Polish

- [x] T018 Add api-gateway route configuration test in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/ApiGatewayApplicationTests.java`
- [x] T019 Verify gateway module with `.\mvnw.cmd -pl services/api-gateway -am verify`
- [x] T020 Update task completion evidence in `specs/009-product-catalog-query/tasks.md`

## Dependencies

- Phase 1 before Phase 2.
- Phase 2 before all user stories.
- US1 is MVP and should complete before US2 and US3.
- US2 and US3 can be implemented after foundational work, but both reuse US1 visibility behavior.
- Gateway validation can run after route configuration is present.

## Parallel Opportunities

- T003 can run independently from product-service code after T001.
- T009, T012, and T015 touch the same test file and should be sequenced.
- Persistence and web DTO code can be drafted in parallel only if file paths do not overlap.

## Implementation Strategy

Deliver US1 first to prove visible product browse. Then add category filtering and category browse. Finish with product detail assembly. Keep all behavior inside product-service and api-gateway route configuration; do not add Kafka, Redis, migrations, or root infrastructure changes.

## Validation Evidence

- 2026-07-17: `.\mvnw.cmd -pl services/product-service -am verify` -> PASS. Scope: product-service and upstream reactor modules. Result: 5 tests passed, build success.
- 2026-07-17: `.\mvnw.cmd -pl services/api-gateway -am verify` -> PASS. Scope: api-gateway and upstream reactor modules. Result: 2 tests passed, build success.
- 2026-07-17: `rg --line-number "org\.springframework|jakarta\.persistence|org\.springframework\.data|kafka|redis" services\product-service\src\main\java\com\philia\flashsale\product\domain services\product-service\src\main\java\com\philia\flashsale\product\application` -> PASS. Scope: product-service domain/application core. Result: no matches.
- 2026-07-17: `rg --line-number "PrometheusMeterRegistry|MeterRegistry" services\product-service\src\main\java services\api-gateway\src\main\java` -> PASS. Scope: product-service and api-gateway Java source. Result: no matches.
