# Implementation Plan: Product Catalog Schema

**Branch**: `[008-product-catalog-schema]` | **Date**: 2026-07-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/008-product-catalog-schema/spec.md`

**Plan status**: Approved

## Summary

Create and execute the first real `product-service` schema migration against Product-owned PostgreSQL. The migration creates five catalog tables with explicit Product ownership, Money integrity, deterministic category ordering, media physical constraints, and a composite Product/Variant media relationship. Add only JDBC/PostgreSQL runtime support and opt-in Testcontainers migration verification; do not add JPA, Product APIs, events, Redis, inventory, or outbox. Keep ordinary context tests database-independent, keep application-time migrations disabled by default, and run the local migration as an explicit one-off process before verifying an idempotent second run.

## Technical Context

**Language/Version**: Java 21

**Framework**: Spring Boot 3.5.16, Spring JDBC/Hikari auto-configuration, Liquibase 4.31.1

**Build**: Maven Wrapper; Product module remains independently buildable

**Primary Dependencies**:

- Existing `org.liquibase:liquibase-core`
- New `org.springframework.boot:spring-boot-starter-jdbc` for the Product datasource and connection pool
- New runtime `org.postgresql:postgresql` for PostgreSQL connectivity
- Existing `spring-boot-starter-test`
- New test-scoped `spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, and `org.testcontainers:postgresql`

**Dependency justification**: JDBC/Hikari and the PostgreSQL driver are the smallest runtime set that lets Spring Boot provide the datasource Liquibase requires; JPA is deliberately excluded because no entity/repository feature is approved. Testcontainers provides an isolated real PostgreSQL integration test without weakening ordinary context tests or adopting H2 behavior. All versions are managed by Spring Boot 3.5.16.

**Storage**: PostgreSQL 17 local validation target, logical database `product_db`, default `public` schema; five Product business tables plus Liquibase's two ledger tables

**Migration format**: Liquibase master YAML with one explicitly included PostgreSQL formatted-SQL changeset

**Communication**: N/A; no public API, gateway route, HTTP client, or Kafka contract

**Testing**: Database-independent Product context test; opt-in Failsafe `ProductMigrationIT` against PostgreSQL Testcontainers; local one-off Liquibase execution; SQL catalog/constraint/index checks; idempotent rerun; Product module and full Maven verification; Compose render

**Target Platform**: Local Docker Compose and Linux OCI Product image now; future Kubernetes migration Job remains the deployment direction

**Performance Goals**: Empty-database migration completes without data backfill; category listing has a deterministic supporting index on `(category_id, sort_order, product_id)`

**Constraints**: No destructive volume reset; no edit of an applied changeset; no `schema.sql`/`data.sql`; no Hibernate schema generation; no outbox; no stock/campaign/cart/order state; no cross-service FK; no hard-coded production credentials

**Scale/Scope**: One Product changeset, five business tables, three runtime/test dependency groups, one test profile, one opt-in migration integration test, one Compose datasource override, one living schema document, and local execution evidence

## Risk Classification

| Dimension | Level | Evidence | Required mitigation/verification |
|-----------|-------|----------|----------------------------------|
| Money/payment | Medium | Variant base price and currency gain durable meaning | Money checks and invalid-currency/negative-price tests |
| Inventory/concurrency | Low | No stock, quota, reservation, or concurrent mutation | Negative scope audit |
| Security/privacy | Low | No customer data or endpoint; local DB credentials remain external | Config/secret audit |
| Distributed consistency | Low | No cross-service call or event | No-outbox/no-contract audit |
| Contract/compatibility | Medium | First durable Product schema becomes future adapter contract | Exact schema contract and catalog inspection |
| Migration/rollback | High | First applied business changeset creates durable objects | Test-first PostgreSQL IT, transactional changeset, explicit rollback, local ledger/idempotency checks |
| Load/operability | Medium | Product startup now has datasource capability; local migration must be explicit | DB-independent smoke test, one-off runner, readiness/startup evidence, full build |

**Overall risk**: High for migration correctness even though no Product API exists, because an applied changeset becomes immutable shared history.

**Selected test ordering**: Strict test-first ordering applies to the migration slice. Create the PostgreSQL integration test and observe failure because the Product schema is absent, then add the changeset and make it pass. Ordinary context tests remain fast and database-independent. After automated verification, run the changeset against local `product_db`, rerun it for idempotency, and inspect the live catalog.

## Constitution Check

*GATE result before research: PASS. Re-check after design: PASS.*

- **Specification traceability**: PASS. Tables and fields map to FR-001 through FR-016; runtime/configuration maps to FR-017 through FR-019; migration verification and exclusions map to FR-020 through FR-023.
- **Service ownership**: PASS. Only `product-service` owns the changelog and all five tables. No shared entity/repository/domain type or cross-service database access exists.
- **Communication**: PASS. No ingress, HTTP, Kafka, gRPC, or service-discovery behavior changes.
- **Data and messaging**: PASS. PostgreSQL becomes durable Product catalog truth. Redis, stock, campaign snapshot, events, outbox, idempotency, retry, and reconciliation are outside scope.
- **Root infrastructure ownership**: PASS. The migration and runtime config stay under `services/product-service`; root Compose only provides Product's datasource connection to the already provisioned `product_db`.
- **Observability**: PASS. Existing health/liveness/readiness/Prometheus configuration is retained. Actual Product startup fails before readiness if datasource or migration initialization fails; no registry/tracing code changes.
- **Contracts and dependencies**: PASS. The schema reviewer contract is documented before implementation. Every new runtime/test dependency is justified above; JPA is not added.
- **Validation**: PASS. Unit-level business tests are inapplicable because no Java business behavior exists. PostgreSQL migration integration, constraint/catalog checks, module/full builds, and Compose rendering are mandatory. HTTP/Kafka/Redis/load/Kubernetes tests are omitted because those artifacts do not change.
- **Architecture decisions**: PASS. No ADR is required; the feature implements the Product database-per-service and Liquibase ownership already established by the Constitution and ADR 0001.

## Product Schema Design

### `categories`

- UUID primary key generated by PostgreSQL
- Optional self-referencing parent category
- Unique code and slug
- Name, description, lifecycle status, sort order
- Created/updated timestamps and optimistic version
- Parent/order browse index

### `products`

- UUID primary key generated by PostgreSQL
- Unique code and slug
- Name, short/full descriptions, object-shaped JSON attributes
- Lifecycle status and optional publication timestamp
- Created/updated timestamps and optimistic version
- Status/publication browse index

### `product_variants`

- UUID primary key; required Product owner
- Unique SKU and optional unique barcode
- Name and object-shaped JSON attributes
- `base_price NUMERIC(19,4)` and `currency CHAR(3)` on the same row
- VND-only initial currency, non-negative amount
- Lifecycle status, optional positive weight, non-negative order
- Created/updated timestamps and optimistic version
- Additional unique `(product_id, id)` target for the composite media FK

### `product_categories`

- Composite primary key `(product_id, category_id)`
- Primary-category flag, non-negative order, creation timestamp
- At most one primary membership per Product
- Required browse index `(category_id, sort_order, product_id)`

### `product_media`

- UUID primary key; required Product owner; optional Variant owner
- Media type `IMAGE` or `VIDEO`, URL and optional metadata
- Optional positive width, height, and file size
- Non-negative order and constrained lifecycle status
- Composite FK `(product_id, variant_id)` to prevent cross-Product Variant media
- Product/Variant/order browse index

### Dependency order

```text
categories
products
  -> product_variants
  -> product_categories <- categories
  -> product_media -> optional product_variants
```

### Explicit exclusions

- Brand table or canonical brand ownership
- Inventory, stock, quota, reservation, campaign/sale price
- Cart, order, payment, identity, or other service data
- Outbox and integration events
- Search/trigram indexes, primary-membership browse partial index, and speculative query indexes
- JPA entities/repositories and Product business Java classes

## Migration and Runtime Design

### Changeset

- File: `services/product-service/src/main/resources/db/changelog/changes/001-create-product-catalog-schema.sql`
- Stable identity: `philia:001-create-product-catalog-schema`
- PostgreSQL precondition; one transaction
- No `IF NOT EXISTS`: unmanaged name collisions fail visibly instead of being adopted
- Explicit reverse-order rollback without `CASCADE`
- Master file uses one explicit relative include, never `includeAll`

### Runtime configuration

- `spring.liquibase.enabled` defaults to `false` for normal Product processes.
- `spring.sql.init.mode` is `never`; `schema.sql` and `data.sql` remain absent.
- Standard `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` supply credentials externally.
- The normal Product context test activates a test profile that excludes datasource auto-configuration and disables Liquibase.
- Compose provides Product's internal URL `jdbc:postgresql://postgres:5432/product_db` and keeps migrations disabled on normal replicas.
- Local migration runs as a one-off Product container with Liquibase enabled and the web application disabled. It does not remove PostgreSQL data or volumes.

### Local execution flow

```text
start/confirm postgres
  -> non-destructively confirm product_db exists
  -> build product-service image
  -> run one-off product-service with SPRING_LIQUIBASE_ENABLED=true
  -> inspect DATABASECHANGELOG and PostgreSQL catalog
  -> run one-off migration again
  -> confirm changeset count/order unchanged
```

### Rollback strategy

The changeset documents reverse dependency order:

```text
product_media
product_categories
product_variants
products
categories
```

Rollback is destructive and is not executed against the requested local `product_db` after successful initialization. Before shared data exists, it may be exercised only in an isolated disposable migration-test database. After any environment contains Product data, rollback requires a separate data-safe plan; normal fixes are forward changesets.

## Verification Strategy and Evidence

| Requirement/risk | Verification | Command or evidence | Expected result |
|------------------|-------------|---------------------|-----------------|
| FR-001-FR-016 | Test-first real PostgreSQL migration IT | `.\mvnw.cmd -pl services/product-service -am -Pproduct-migration-it verify` | Five tables, ledger, constraints, index order, valid/invalid writes, second Liquibase call |
| FR-017-FR-019 | Product smoke test | `.\mvnw.cmd -pl services/product-service -am test` | Context starts without Docker/database |
| FR-018 | Compose render | `docker compose ... --profile apps config` | Product inherits backend environment plus Product datasource; normal Liquibase false |
| FR-020 | Local first run | One-off Product migration container | One Product changeset executed successfully |
| FR-020 | Live catalog audit | `psql` queries over `information_schema`, `pg_constraint`, `pg_indexes`, `databasechangelog` | Exact tables/constraints/indexes and unlocked ledger |
| FR-020 | Local idempotency | Capture ledger, rerun one-off migration, recapture | Count and max execution order unchanged |
| FR-021-FR-023 | Negative scope audit | Search migration/source/contracts | No outbox, stock, API, event, JPA, Redis, or cross-service FK |
| Regression | Product module build | `.\mvnw.cmd -pl services/product-service -am verify` | Product succeeds |
| Cross-cutting regression | Full build | `.\mvnw.cmd clean verify` | Parent plus all ten service modules succeed |

## Project Structure

### Documentation (this feature)

```text
specs/008-product-catalog-schema/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── product-catalog-schema.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Repository paths affected

```text
services/product-service/
├── pom.xml
└── src/
    ├── main/resources/
    │   ├── application.yml
    │   └── db/changelog/
    │       ├── db.changelog-master.yaml
    │       └── changes/001-create-product-catalog-schema.sql
    └── test/
        ├── java/com/philia/flashsale/product/
        │   ├── ProductServiceApplicationTests.java
        │   └── ProductMigrationIT.java
        └── resources/application-test.yml
infra/docker/compose.yml
docs/database/product-service-schema.md
specs/008-product-catalog-schema/*
```

**Structure decision**: Keep the schema, test, and datasource runtime responsibility inside `product-service`. Modify root Compose only to inject Product's already-owned database connection. Add no Java architecture packages or business types.

## Complexity Tracking

No constitutional violation, waiver, service-boundary change, or ADR is required. The opt-in Testcontainers/Failsafe profile is accepted test complexity because the first immutable production changeset requires real PostgreSQL verification while the default full build must remain Docker-independent.
