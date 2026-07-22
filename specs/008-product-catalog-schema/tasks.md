# Tasks: Product Catalog Schema

**Input**: Design documents from `/specs/008-product-catalog-schema/`

**Prerequisites**: Approved `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/product-catalog-schema.md`, and `quickstart.md`

**Tests**: Migration work uses strict test-first ordering because the first applied changeset becomes immutable history. The PostgreSQL integration test must be written and observed failing before the SQL changeset is added.

## Phase 1: Setup and approval

**Purpose**: Freeze the approved schema boundary before implementation.

- [x] T001 Confirm the approved five-table scope, deferred concerns, and no-ADR decision across `specs/008-product-catalog-schema/spec.md`, `plan.md`, `data-model.md`, and `contracts/product-catalog-schema.md`
- [x] T002 Generate and review the feature execution list in `specs/008-product-catalog-schema/tasks.md`

---

## Phase 2: Foundational runtime and test plumbing

**Purpose**: Give Liquibase a Product-owned datasource while preserving a database-independent default test/build path.

- [x] T003 Add Spring JDBC, PostgreSQL runtime, Spring Boot Testcontainers, PostgreSQL Testcontainers, JUnit Testcontainers, and the opt-in Failsafe profile to `services/product-service/pom.xml`
- [x] T004 Configure externally supplied datasource behavior, default-disabled Liquibase, and disabled Spring SQL initialization in `services/product-service/src/main/resources/application.yml`
- [x] T005 [P] Keep the normal Product context test database-independent with `@ActiveProfiles("test")` in `services/product-service/src/test/java/com/philia/flashsale/product/ProductServiceApplicationTests.java` and datasource exclusion in `services/product-service/src/test/resources/application-test.yml`
- [x] T006 [P] Refactor the shared backend environment anchor and inject only the Product datasource into `product-service` in `infra/docker/compose.yml`, keeping normal replicas migration-disabled
- [x] T007 Validate the rendered Compose topology with `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml --profile apps config`

**Checkpoint**: Product can resolve a datasource when explicitly configured, while ordinary context tests do not require PostgreSQL.

---

## Phase 3: User Story 1 - Initialize durable Product catalog storage (Priority: P1)

**Goal**: Apply one Product-owned transactional changeset that creates exactly the approved five catalog tables.

**Independent Test**: Apply the changelog to an isolated PostgreSQL Testcontainer and verify the five business tables, Liquibase ledger, valid fixture insert, and idempotent second execution.

### Tests for User Story 1

- [x] T008 [US1] Write `ProductMigrationIT` first in `services/product-service/src/test/java/com/philia/flashsale/product/ProductMigrationIT.java`, covering exact business tables, ledger state, expected index/FK metadata, valid records, invalid invariants, and a second Liquibase invocation
- [x] T009 [US1] Run `.\mvnw.cmd -pl services/product-service -am -Pproduct-migration-it verify` before adding the changeset and record the expected missing-schema failure in the Evidence section below

### Implementation for User Story 1

- [x] T010 [US1] Create the PostgreSQL formatted SQL changeset at `services/product-service/src/main/resources/db/changelog/changes/001-create-product-catalog-schema.sql` with categories, products, variants, memberships, media, named constraints/indexes, and reverse-order rollback
- [x] T011 [US1] Replace the empty master ledger with an explicit relative include in `services/product-service/src/main/resources/db/changelog/db.changelog-master.yaml` and remove the obsolete changes directory placeholder if present
- [x] T012 [US1] Re-run `.\mvnw.cmd -pl services/product-service -am -Pproduct-migration-it verify` and confirm initial migration, valid fixture storage, ledger state, and idempotent second execution pass

**Checkpoint**: An empty PostgreSQL database can be initialized entirely from the Product service changelog.

---

## Phase 4: User Story 2 - Reject inconsistent Product data (Priority: P2)

**Goal**: Make every reviewed Money, categorization, media, lifecycle, natural-key, and ownership invariant database-enforced.

**Independent Test**: Run the focused PostgreSQL integration test and observe valid writes succeed while every mapped invalid write is rejected.

- [x] T013 [US2] Audit `ProductMigrationIT` against every integrity rule in `specs/008-product-catalog-schema/contracts/product-catalog-schema.md`, including VND Money, non-negative order, one primary category, positive measurements, status/media enums, natural keys, orphan FKs, and cross-Product Variant media
- [x] T014 [US2] Inspect PostgreSQL metadata assertions for the exact `(category_id, sort_order, product_id)` index and composite `(product_id, variant_id)` foreign key in `ProductMigrationIT`
- [x] T015 [US2] Document the final Product-owned table and constraint model in `docs/database/product-service-schema.md`
- [x] T016 [US2] Run `.\mvnw.cmd -pl services/product-service -am -Pproduct-migration-it verify` after the integrity audit and confirm all mapped cases pass

**Checkpoint**: The database rejects the inconsistent Product states identified by the approved design.

---

## Phase 5: User Story 3 - Operate the migration safely and repeatedly (Priority: P3)

**Goal**: Apply and inspect the changeset on the repository's local PostgreSQL without deleting existing volumes or enabling migration on normal replicas.

**Independent Test**: Run the one-off Product migration twice against local `product_db`; verify exact tables, one Product ledger row, an unlocked migration ledger, and no second execution.

- [x] T017 [US3] Update `infra/docker/README.md` with Product datasource ownership, the one-off migration command, existing-volume database creation, and the future Kubernetes migration Job direction
- [x] T018 [US3] Start or confirm local PostgreSQL and non-destructively verify that `product_db` exists; create only the missing logical database if required
- [x] T019 [US3] Inspect `product_db` before migration and stop rather than dropping or adopting unmanaged target-name tables
- [x] T020 [US3] Build the Product image and run the one-off non-web Product process with `SPRING_LIQUIBASE_ENABLED=true`
- [x] T021 [US3] Query `databasechangelog`, `databasechangeloglock`, `information_schema`, `pg_indexes`, and `pg_constraint` to verify the applied live schema
- [x] T022 [US3] Run the same one-off migration a second time and confirm the Product changeset count and execution order do not change

**Checkpoint**: The requested local Product database is initialized and the repeatable operational procedure is documented.

---

## Phase 6: Polish and regression validation

**Purpose**: Prove the schema change did not leak into unapproved Product behavior or break the monorepo.

- [x] T023 Run `.\mvnw.cmd -pl services/product-service -am test` and confirm the ordinary Product context test passes without a running database
- [x] T024 Run `.\mvnw.cmd -pl services/product-service -am verify` and confirm default Product verification remains Docker-independent
- [x] T025 Run `.\mvnw.cmd clean verify` and confirm every monorepo module passes
- [x] T026 Search affected Product source and migration artifacts for prohibited JPA entities/repositories, Product APIs, Outbox, stock, campaign price, Redis, Kafka, and cross-service database references
- [x] T027 Reconcile `specs/008-product-catalog-schema/tasks.md` with actual command evidence and leave no completed task unchecked or unverified task checked

---

## Dependencies and execution order

- T001-T002 approve the execution boundary.
- T003-T007 are blocking prerequisites for all migration work.
- T008 must complete before T009; T009 must show the expected failure before T010-T011.
- T010-T012 deliver User Story 1 and block the integrity audit in T013-T016.
- T017-T022 require a passing automated migration test and operate only on the local Product database.
- T023-T027 run after all implementation and local migration work.

## Evidence

Record concise command results here while implementing:

- Expected test-first failure: PASS as expected — Testcontainers PostgreSQL 17 started, Liquibase found zero changesets, and Failsafe failed on missing `product_media`/catalog tables before SQL implementation.
- PostgreSQL Testcontainers verification: PASS — 13 tests on PostgreSQL 17; one changeset executed, all integrity/catalog checks passed, and the in-test second run executed zero changesets.
- Compose render: PASS — Product retains shared backend variables, receives only the `product_db` datasource, and renders `SPRING_LIQUIBASE_ENABLED=false` for normal replicas.
- Local first migration: PASS — fresh Compose volume bootstrapped empty `product_db`; the one-off Product process executed `philia:001-create-product-catalog-schema` once and released the lock.
- Local idempotent rerun: PASS — rebuilt image uses `SPRING_LIQUIBASE_ENABLED`; rerun reported `Run: 0`, `Previously run: 1`, with one ledger row and no invalid Liquibase environment warning.
- Product default verification: PASS — both focused `test` and default `verify` completed without a running Product database; the test profile excludes datasource auto-configuration and keeps Liquibase disabled.
- Full monorepo verification: PASS — `.\mvnw.cmd clean verify` completed with all 11 reactor modules successful in 3 minutes 27 seconds.
- Negative scope audit: PASS — Product production code contains only the application bootstrap plus JDBC/Liquibase setup; no Product API, DTO, JPA entity/repository, Kafka, Redis, Outbox, stock/inventory, campaign price, cross-service database reference, `schema.sql`, or `data.sql` was introduced.
