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
| T043 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/api-gateway,services/cart-service -am '-Dtest=CartGatewayRouteConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` | PASS (4 Gateway tests, 0 failures/errors); Cart route target, OpenAPI proxy, authenticated boundary, and absence of an internal Product route verified | Local |
| T044/T045 | Gateway/Cart compile plus `CartGatewayRouteConfigurationTests`; Cart controller/OpenAPI metadata review | PASS; `/api/v1/cart/**` and `/openapi/cart-service` are wired at the Gateway, Cart docs are opt-in, and the bearer security scheme is declared | Local |
| T046/T047 | `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config --quiet` | PASS (exit 0); Cart runtime, one-off `cart-migration`, Gateway URL, database URL, OAuth variables, and health check render without secret values | Local |
| T048 | `pwsh -NoLogo -NoProfile -File .\\infra\\docker\\smoke\\tests\\feature-048-cart.tests.ps1` | PASS (`PHASE_048_STATIC=PASS`); bounded parameters, secret-name-only checks, cleanup/evidence markers, Gateway wiring, Compose migration, and OpenAPI guardrails verified | Local |
| T049 | PowerShell parse/static validation of `infra/docker/smoke/feature-048-cart.ps1` plus affected module gate | PASS; bounded local fixture/CRUD/replay/isolation/Product-degradation runner implemented; live execution is intentionally deferred to G8/T055-T056 | Local |
| G6 gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/cart-service,services/api-gateway -am verify` | PASS (BUILD SUCCESS, exit 0; API Gateway 200 tests and Cart 34 tests, 0 failures/errors; total 4m16s) | Local |
| T050 | `pwsh -NoLogo -NoProfile -File .\\infra\\scripts\\docs\\verify-api-documentation.ps1` | PASS (`API_DOCUMENTATION=PASS`; 45 supported endpoint rows, 8 service documents, defaults disabled) | Local |
| T051 | Review of `docs/api/frontend-integration-guide.md` against the approved Cart contracts | PASS; API-041 through API-044 include request/response payloads, `detailsAvailable` and unavailable states, idempotent PUT/DELETE behavior, `Cache-Control: no-store`, error codes, and the purchase-flow boundary. API-045 is explicitly marked service-to-service and not frontend-facing | Local |
| T052 | `git diff --check`; unresolved-marker scan from `specs/048-cart-mvp/quickstart.md` across spec/plan/research/data-model/contracts | PASS (no whitespace errors; `UNRESOLVED_MARKERS=PASS`) | Local |
| T053 / G7 module gate | `./mvnw.cmd --batch-mode --no-transfer-progress -pl "services/cart-service,services/product-service,services/authentication-service,services/api-gateway" -am verify` | PASS (BUILD SUCCESS, exit 0; API Gateway, Authentication, Product, and Cart tests all passed with 0 failures/errors; Cart 34 tests, Product 43 tests, Authentication 65 tests) | Local |
| T054 | `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml config --quiet` | PASS (`COMPOSE_CONFIG=PASS`; Cart, migration, Gateway, Product, Authentication, database, OAuth, health-check, and secret references rendered without displaying values) | Local |
| G7 gate | T050-T054 documentation, static, module, and Compose checks | PASS; the public Cart/frontend handoff and internal Product display contract are documented. Live Cart execution remains deferred to G8/T055-T056 | Local |

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
- G6 integrates the completed Cart feature into the local ingress/runtime only. The Gateway routes
  authenticated `/api/v1/cart/**` requests and exposes an opt-in `/openapi/cart-service` proxy; the
  Cart runtime uses its own PostgreSQL database and a one-off `cart-migration` profile with runtime
  Liquibase disabled. The local smoke runner includes security, CRUD, replay, owner isolation,
  Product outage/recovery, and read-p95 checks with bounded execution and secret-safe output. No
  cloud/Kubernetes, Kafka, Redis, or outbox changes were introduced; live execution remains G8.
- G7 publishes the accepted 45-endpoint API inventory (37 Gateway-public, 7 internal, and 1
  identity endpoint), including Cart API-041 through API-044 and the internal Product API-045.
  The frontend handoff documents complete Cart payloads, fail-soft Product enrichment, ownership,
  idempotency, cache policy, and the purchase boundary. Documentation verification, whitespace and
  unresolved-marker scans, the affected module gate, and Compose rendering all passed. No runtime
  secret was read or changed, and live Cart smoke remains deferred to G8.
- G8 validates the local runtime without adding cloud scope: the Cart client secret is checked by
  name/nonblank status only, the additive Liquibase migration is repeatable, the expanded schema is
  compatible with the pre-feature persistence shell, and the bounded smoke proves health, trace,
  security, CRUD, replay, owner isolation, Product outage/recovery, and read latency. The final
  full reactor gate and static audits passed. No Cart data was dropped and no secret value was
  printed or recorded.

## G8 — Live local validation and closure

| Task | Command / scope | Result | CI/PR reference |
|------|-----------------|--------|-----------------|
| T055 | `infra/docker/smoke/feature-048-cart.ps1 -Scenario All -TimeoutSeconds 900` Cart client-credential preflight | PASS; `CART_CLIENT_SECRET` was confirmed nonblank by the runner in process memory only. The value was not printed, copied, persisted, or recorded | Local |
| T056 migration | `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile migrations run --build --rm --no-deps cart-migration --spring.main.web-application-type=none` (executed twice) | PASS; both Liquibase runs exited 0 and were repeatable without destructive changes. The long-running Cart service keeps Liquibase disabled | Local |
| T056 compatibility | `CartMigrationCompatibilityIntegrationTests` in the full reactor gate | PASS; expanded `carts`/`cart_items` schema and Liquibase history were accepted by the compatibility test, with no table or data drop | Local |
| T056 smoke | `infra/docker/smoke/feature-048-cart.ps1 -Scenario All -TimeoutSeconds 900` | PASS; `FEATURE_048_MODULES`, `SECURITY`, `CRUD`, `REPLAY` (100 replacements), `OWNERSHIP`, `PRODUCT_DEGRADATION`, `PERFORMANCE` (20 reads, p95 112.1 ms), and `LOCAL_GATE` all passed. Fixtures were cleaned up | Local |
| T057 reactor | `./mvnw.cmd clean verify` | PASS (`BUILD SUCCESS`, exit 0; all 13 reactor modules completed with no test failures or errors; Cart 34 tests passed) | Local |
| T057 audit | `git diff --check`; `feature-048-cart.tests.ps1`; `verify-api-documentation.ps1`; `docker compose ... config --quiet`; Cart dependency/log/trace review | PASS; whitespace/static/docs/Compose gates passed, no Kafka/Redis/outbox/cloud change was introduced by Cart, and no secret/token/Authorization value was recorded | Local |
| G8 gate | T055-T057 closure | PASS; Cart MVP is locally validated and remains intentionally out of cloud deployment scope | Local |
