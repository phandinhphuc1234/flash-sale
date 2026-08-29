# Validation Evidence: Authenticated Cart MVP

This ledger records G1 through G5 validation. Secret values, access tokens, Authorization headers, and
private response bodies must never be copied here.

| Task | Command / scope | Result | CI/PR reference |
|------|-----------------|--------|-----------------|
| T001 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am -DskipTests compile` | PASS (BUILD SUCCESS, exit 0) | Local |
| T002 | Same Cart compile gate; resource copy included `application.yml` and Liquibase changelog | PASS (exit 0) | Local |
| T003 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am '-Dtest=CartArchitectureTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (2 tests, 0 failures, exit 0) | Local |
| T004 | Evidence ledger created | PASS | |
| G1 gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am test` | PASS (Cart + common-web: 12 tests, 0 failures, 0 errors, exit 0) | Local |
| T005/T007 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/product-service -am '-Dtest=ProductVariantDisplayQueryServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (3 tests, 0 failures, 0 errors, exit 0); ordered de-duplication, empty input, and null validation covered | Local |
| T006/T009/T010 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/product-service -am '-Dtest=ProductVariantDisplayHttpTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (4 tests, 0 failures, 0 errors, exit 0); Cart subject/scope, 401/403, envelope, and trace contract covered | Local |
| T008 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/product-service -am '-Dtest=ProductVariantDisplayPersistenceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (1 Testcontainers PostgreSQL test, 0 failures, 0 errors, exit 0); ordered projection, sellability, current fields, fallback image, and missing variant covered | Local |
| T011/T012 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/authentication-service -am test` | PASS (65 tests, 0 failures, 0 errors, exit 0); typed Cart client bootstrap is limited to `catalog.variant-display.read` | Local |
| T013-T016 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am test` | PASS (8 tests, 0 failures, 0 errors, exit 0); capability mapping, deduplication, machine headers, trace propagation, and dependency failure mapping covered | Local |
| T017 / G2 gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/product-service,services/authentication-service,services/cart-service -am verify` | PASS (BUILD SUCCESS, exit 0); Product 43 tests, Authentication 65 tests, Cart 8 tests, no failures/errors | Local |
| T018-T024 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am test` | PASS (20 Cart tests, 0 failures, 0 errors); framework-free quantity/aggregate rules, Product-before-persistence ordering, replay-safe set, and idempotent remove covered | Local |
| T025-T027 | `CartMigrationCompatibilityIntegrationTests` and `CartPersistenceIntegrationTests` in the Cart module test gate | PASS (3 Testcontainers PostgreSQL tests, 0 failures, 0 errors); Liquibase created `carts`/`cart_items`, schema history was recorded, owner/item upserts were idempotent, and owner isolation/remove-no-op passed | Local |
| T028 | `CartMutationHttpTests` in the Cart module test gate | PASS (3 MockMvc contract tests, 0 failures, 0 errors); authenticated PUT/DELETE, validation/error mapping, empty 204 body, no-store, and trace header covered | Local |
| T029 / G3 gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am verify` | PASS (BUILD SUCCESS, exit 0; Cart 20 tests + common-web 9 tests, 0 failures, 0 errors); JAR/package verification completed | Local |
| T030/T032/T033 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am '-Dtest=GetCartUseCaseTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (5 application tests, 0 failures, 0 errors); absent/empty carts avoid Product calls, populated carts use one ordered batch, details refresh on every read, and dependency/missing/non-sellable states fail soft | Local |
| T031/T035 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am '-Dtest=GetCartHttpTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (3 MockMvc tests, 0 failures, 0 errors); GET response/enrichment, empty response, no-store and trace headers, and unauthenticated 401 mapping covered | Local |
| T034 | Cart persistence integration test in the Cart module gate | PASS (owner-scoped read test included; 3 Cart persistence tests and 1 migration compatibility test, 0 failures, 0 errors); ordered reads do not create a missing Cart row | Local |
| T036 / G4 gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/product-service -am verify` | PASS (BUILD SUCCESS, exit 0; Product 43 tests, Cart 29 tests, common-web 9 tests, 0 failures/errors; Cart JAR/package verification completed) | Local |
| T037 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=CartOwnershipUseCaseTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS (1 application test, 0 failures, 0 errors); repeated clear calls remain owner-scoped and do not invoke Product | Local |
| T038 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=CartOwnerIsolationIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS (1 Testcontainers PostgreSQL test, 0 failures, 0 errors); clear deletes only the selected owner's items, retains both Cart shells, and an absent owner creates no row | Local |
| T039 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am "-Dtest=CartSecurityContractTests,CartMutationHttpTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` | PASS (6 web/security tests, 0 failures, 0 errors); clear returns empty 204, requires JWT, ignores forged owner input, and existing mutation contracts remain green | Local |
| T040/T041 | Cart application, configuration, controller, and persistence implementation reviewed under the approved G5 plan | PASS; clear command/use case, owner-scoped adapter deletion, and public DELETE `/api/v1/cart` are wired without Product/Kafka/Redis calls | Local |
| T042 / G5 gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service -am verify` | PASS (BUILD SUCCESS, exit 0; Cart 34 tests + common-web 9 tests, 0 failures/errors; Cart JAR/package verification completed) | Local |

## Evidence rules

- Record the command, scope, exit status, and safe summary only.
- Keep `CART_CLIENT_SECRET` in ignored `infra/docker/.env`; automation may check presence without
  printing or persisting its value.
- G1 does not create database tables, call Product, or change Gateway/cloud runtime behavior.
- G2 adds only the internal Product display contract and Cart machine identity/client boundary;
  Cart tables, public Cart endpoints, Gateway routes, Kafka, Redis, outbox, and cloud deployment
  remain deferred. The real `CART_CLIENT_SECRET` was not read or persisted during validation.
- G3 adds only authenticated Cart item mutation. Product is validated before the Cart persistence
  transaction; PostgreSQL remains the Cart source of truth, and native upserts clear the JPA
  persistence context so repeated writes return the committed quantity. GET/clear, Gateway/Compose,
  Kafka, Redis, outbox, and cloud deployment remain deferred.
- G4 adds only the authenticated Cart read path. Saved Cart identities and quantities are loaded
  from Cart-owned PostgreSQL state, then enriched with one Product batch lookup; Product failures
  preserve saved intent with `detailsAvailable=false` and an explicit unavailable reason. Empty or
  absent Carts do not call Product or create durable rows. Clear, Gateway/Compose, Kafka, Redis,
  outbox, and cloud deployment remain deferred.
- G5 adds only owner-scoped Cart clearing. The authenticated JWT subject selects the Cart; clearing
  deletes its item rows and advances the retained Cart shell timestamp, while an absent Cart is a
  successful no-op. No schema migration, Product lookup, Kafka, Redis, outbox, or cloud behavior was
  introduced.
