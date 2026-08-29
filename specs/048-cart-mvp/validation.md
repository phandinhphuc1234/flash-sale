# Validation Evidence: Authenticated Cart MVP

This ledger records G1 and G2 validation. Secret values, access tokens, Authorization headers, and
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

## Evidence rules

- Record the command, scope, exit status, and safe summary only.
- Keep `CART_CLIENT_SECRET` in ignored `infra/docker/.env`; automation may check presence without
  printing or persisting its value.
- G1 does not create database tables, call Product, or change Gateway/cloud runtime behavior.
- G2 adds only the internal Product display contract and Cart machine identity/client boundary;
  Cart tables, public Cart endpoints, Gateway routes, Kafka, Redis, outbox, and cloud deployment
  remain deferred. The real `CART_CLIENT_SECRET` was not read or persisted during validation.
