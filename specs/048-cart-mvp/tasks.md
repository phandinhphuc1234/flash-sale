# Tasks: Authenticated Cart MVP

**Status**: Implementing — G5 complete

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/public-cart-http.md](contracts/public-cart-http.md),
[contracts/product-variant-display-http.md](contracts/product-variant-display-http.md), and
[quickstart.md](quickstart.md).

**Execution rule**: Complete one group at a time. Within a user-story group, add the specified
tests first and observe their expected failure before implementing the mapped behavior. Do not
start production code until this task list is reviewed and approved.

## G1 — Setup and executable boundaries

**Purpose**: Prepare the Cart module and its evidence/architecture gates without implementing
business behavior.

- [x] T001 Add the approved `common-web`, validation, JPA/PostgreSQL, security resource-server,
  OAuth2 client, OpenFeign, Actuator/Prometheus, Liquibase, Springdoc, Testcontainers PostgreSQL,
  Spring Security Test, and ArchUnit dependencies to `services/cart-service/pom.xml`.
- [x] T002 [P] Add default-safe Cart database, JWT trust, Product client, OAuth2 client,
  OpenFeign timeout/no-retry, Liquibase-disabled runtime, Actuator, metrics, and opt-in Springdoc
  properties to `services/cart-service/src/main/resources/application.yml`.
- [x] T003 [P] Add Clean/Hexagonal dependency tests that forbid Spring web, security, JPA, and
  Feign types in Cart domain/application packages in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/architecture/CartArchitectureTests.java`.
- [x] T004 [P] Create the evidence ledger with command, scope, exit-status, and CI/PR fields in
  `specs/048-cart-mvp/validation.md`.

**Checkpoint**: Cart compiles with approved dependencies and has no production behavior yet.

---

## G2 — Foundational Product and authentication contracts

**Purpose**: Establish the least-privilege Cart-to-Product capability required before any Cart
mutation or enriched read can be implemented.

**Independent test**: A `cart-service` client token with the internal audience and exact
`catalog.variant-display.read` scope can call the Product batch endpoint; wrong subject, audience,
or scope is rejected, while the existing Campaign contract remains unchanged.

- [x] T005 [P] Add Product application tests for ordered de-duplication, found/missing
  variants, sellability, current display fields, and empty input in
  `services/product-service/src/test/java/com/philia/flashsale/product/catalog/application/service/ProductVariantDisplayQueryServiceTests.java`.
- [x] T006 [P] Add Product HTTP/security contract tests for the exact method, path,
  envelopes, validation, trace header, audience, subject, and scope in
  `services/product-service/src/test/java/com/philia/flashsale/product/catalog/adapter/in/web/internal/ProductVariantDisplayHttpTests.java`.
- [x] T007 Define the Product input port, ordered result model, and query service in
  `services/product-service/src/main/java/com/philia/flashsale/product/catalog/application/port/in/LookupVariantDisplaysUseCase.java`,
  `services/product-service/src/main/java/com/philia/flashsale/product/catalog/application/result/VariantDisplayResult.java`,
  and `services/product-service/src/main/java/com/philia/flashsale/product/catalog/application/service/ProductVariantDisplayQueryService.java`.
- [x] T008 Extend Product-owned batch loading without cross-service data access in
  `services/product-service/src/main/java/com/philia/flashsale/product/catalog/application/port/out/LoadCatalogPort.java`,
  `services/product-service/src/main/java/com/philia/flashsale/product/catalog/adapter/out/persistence/ProductReadJpaRepository.java`,
  and `services/product-service/src/main/java/com/philia/flashsale/product/catalog/adapter/out/persistence/SpringDataJpaCatalogQueryAdapter.java`.
- [x] T009 Implement the internal request/response mapping and batch endpoint in
  `services/product-service/src/main/java/com/philia/flashsale/product/catalog/adapter/in/web/internal/VariantDisplayBatchRequest.java`,
  `services/product-service/src/main/java/com/philia/flashsale/product/catalog/adapter/in/web/internal/VariantDisplayBatchResponse.java`,
  and `services/product-service/src/main/java/com/philia/flashsale/product/catalog/adapter/in/web/internal/ProductVariantDisplayController.java`.
- [x] T010 Add an endpoint-specific Cart subject/scope rule and safe 401/403 mapping while
  preserving Campaign access in
  `services/product-service/src/main/java/com/philia/flashsale/product/configuration/ProductInternalSecurityConfiguration.java`,
  `services/product-service/src/main/java/com/philia/flashsale/product/configuration/ProductInternalSecurityFailureHandler.java`,
  and `services/product-service/src/main/resources/application.yml`.
- [x] T011 [P] Add Authentication contract tests proving `cart-service` receives only
  `catalog.variant-display.read` and cannot obtain Campaign or Inventory scopes in
  `services/authentication-service/src/test/java/com/philia/flashsale/authentication/serviceclient/contract/CartServiceClientCredentialsTests.java`.
- [x] T012 Provision the Cart machine client through typed properties and the existing durable
  bootstrap flow in
  `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/ServiceClientsProperties.java`,
  `services/authentication-service/src/main/java/com/philia/flashsale/authentication/serviceclient/adapter/in/oauth/ServiceClientBootstrapper.java`,
  and `services/authentication-service/src/main/resources/application.yml`.
- [x] T013 Define Cart-owned Product capability types and failure semantics in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/port/out/LoadProductDisplaysPort.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/result/ProductDisplay.java`,
  and `services/cart-service/src/main/java/com/philia/flashsale/cart/application/result/ProductDisplayBatch.java`.
- [x] T014 [P] Add Cart outbound contract tests for one batch call, response identity
  validation, stable order, trace propagation, no shopper-token relay, no retry, and mapping of
  timeout/connect/401/403/5xx/malformed responses in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/adapter/out/client/product/ProductDisplayClientAdapterTests.java`.
- [x] T015 Implement the Feign wire DTO/client and safe provider-to-capability mapping in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/client/product/ProductDisplayFeignClient.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/client/product/ProductDisplayClientAdapter.java`,
  and `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/client/product/ProductDisplayWireModels.java`.
- [x] T016 Implement cached client-credentials token acquisition, `Retryer.NEVER_RETRY`, explicit
  300 ms connect/600 ms read timeouts, safe logging, and trace propagation in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/configuration/CartProductClientConfiguration.java`.
- [x] T017 Add Cart JWT/owner-boundary tests and implement issuer, audience, token
  type, nonblank UUID subject, authenticated request rules, and safe 401 handling in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/security/AuthenticatedShopperTests.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/security/AuthenticatedShopper.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/configuration/CartJwtTrustConfiguration.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/configuration/CartSecurityConfiguration.java`,
  and `services/cart-service/src/main/java/com/philia/flashsale/cart/security/CartSecurityErrorHandler.java`;
  run `./mvnw -pl services/product-service,services/authentication-service,services/cart-service -am verify`
  and record the G2 contract/security result in `specs/048-cart-mvp/validation.md`.

**Checkpoint**: Product lookup and machine identity are independently verified; no Cart table has
been created or mutated.

---

## G3 — User Story 1: Maintain My Cart (Priority: P1) 🎯 MVP

**Goal**: An authenticated shopper can set an absolute quantity and remove a variant idempotently,
with Product validation before mutation and no stock/order/payment side effects.

**Independent test**: Set quantity 1 and 10, reject 0 and 11, repeat the same PUT 100 times, replace
the quantity, remove twice, and verify one durable line and no state change after Product rejection
or outage.

### Tests for User Story 1

- [x] T018 [P] [US1] Add failing domain tests for quantity 1–10 and one item per variant in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/domain/CartTests.java`.
- [x] T019 [P] [US1] Add failing application tests proving Product validation precedes persistence,
  same-request replay is idempotent, remove is a no-op when absent, and dependency/not-found/
  not-sellable failures preserve prior state in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/application/MaintainCartUseCaseTests.java`.
- [x] T020 [P] [US1] Add failing PostgreSQL integration tests for owner uniqueness, composite item
  uniqueness, quantity checks, concurrent first write, same-quantity replay, different-quantity
  serial outcomes, forward migration re-run, and compatibility of the pre-feature Cart shell with
  the expanded schema in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/adapter/out/persistence/CartPersistenceIntegrationTests.java`
  and
  `services/cart-service/src/test/java/com/philia/flashsale/cart/adapter/out/persistence/CartMigrationCompatibilityIntegrationTests.java`.
- [x] T021 [P] [US1] Add failing web/security contract tests for authenticated PUT/item DELETE,
  200/204 envelopes, 400/401/404/409/503/500 mappings, empty 204 bodies, no owner input, no-store,
  and trace headers in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/adapter/in/web/CartMutationHttpTests.java`.

### Implementation for User Story 1

- [x] T022 [P] [US1] Implement the framework-free Cart aggregate, item entity, and validated
  quantity value object in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/domain/model/Cart.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/domain/model/CartItem.java`, and
  `services/cart-service/src/main/java/com/philia/flashsale/cart/domain/valueobject/CartQuantity.java`.
- [x] T023 [P] [US1] Define set/remove commands, input ports, and intent-focused persistence ports in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/command/SetCartItemCommand.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/command/RemoveCartItemCommand.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/port/in/SetCartItemUseCase.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/port/in/RemoveCartItemUseCase.java`, and
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/port/out/MaintainCartPort.java`.
- [x] T024 [US1] Implement Product-before-transaction set behavior and idempotent remove behavior in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/usecase/MaintainCartService.java`.
- [x] T025 [US1] Add the expand-only `carts`/`cart_items` Liquibase changeset and include it from
  `services/cart-service/src/main/resources/db/changelog/changes/001-create-cart-schema.yaml` and
  `services/cart-service/src/main/resources/db/changelog/db.changelog-master.yaml`.
- [x] T026 [US1] Implement Cart-owned JPA entities and repositories without Product fields after
  T025 fixes the accepted migration shape in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/entity/CartJpaEntity.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/entity/CartItemJpaEntity.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/repository/CartJpaRepository.java`,
  and `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/repository/CartItemJpaRepository.java`.
- [x] T027 [US1] Implement the persistence adapter with short transactions and PostgreSQL atomic
  owner/item upserts inside the adapter boundary in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/CartPersistenceAdapter.java`.
- [x] T028 [US1] Implement mutation request/response DTOs, explicit mapper, controller methods, and
  stable Cart exception mapping in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/SetCartItemRequest.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartItemResponse.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartWebMapper.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartController.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/websupport/error/CartErrorCode.java`, and
  `services/cart-service/src/main/java/com/philia/flashsale/cart/websupport/error/CartHttpExceptionHandler.java`.
- [x] T029 [US1] Run the US1 domain, application, persistence, and web tests with
  `./mvnw -pl services/cart-service -am verify` and record the checkpoint in
  `specs/048-cart-mvp/validation.md`.

**Checkpoint**: User Story 1 works independently as the Cart MVP.

---

## G4 — User Story 2: View Current Product Details (Priority: P2)

**Goal**: Cart reads return saved intent plus current Product-owned display fields, and degrade
safely without inventing Product data.

**Independent test**: Read an empty Cart without a Product call; read a populated Cart with one
batch call; change Product display data and see it on the next GET; stop Product and receive the
saved identities/quantities with `detailsAvailable=false`.

### Tests for User Story 2

- [x] T030 [P] [US2] Add failing application tests for empty Cart, one ordered batch lookup,
  current-detail refresh, missing/non-sellable variants, and fail-soft dependency behavior in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/application/GetCartUseCaseTests.java`.
- [x] T031 [P] [US2] Add failing GET contract tests for `CartResponse`, stable item order, aggregate
  counts, nullable Product fields, unavailable reasons, no-store, 401, trace header, and no durable
  row creation for an absent Cart in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/adapter/in/web/GetCartHttpTests.java`.

### Implementation for User Story 2

- [x] T032 [P] [US2] Define the query, input/output ports, and Cart read result types in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/query/GetCartQuery.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/port/in/GetCartUseCase.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/port/out/LoadCartPort.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/result/CartResult.java`, and
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/result/CartItemResult.java`.
- [x] T033 [US2] Implement Cart loading, empty-call avoidance, one-batch enrichment, stable order,
  and missing/non-sellable/dependency-unavailable mapping in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/usecase/GetCartService.java`.
- [x] T034 [US2] Extend the persistence adapter with owner-scoped read ordering in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/CartPersistenceAdapter.java`.
- [x] T035 [US2] Add GET response mapping and the public GET endpoint without exposing Cart/owner
  identifiers in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartResponse.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartWebMapper.java`, and
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartController.java`.
- [x] T036 [US2] Run the US2 application/web tests and the Product client contract tests with
  `./mvnw -pl services/cart-service,services/product-service -am verify` and record the checkpoint
  in `specs/048-cart-mvp/validation.md`.

**Checkpoint**: User Stories 1 and 2 work independently; Product outage does not hide saved intent.

---

## G5 — User Story 3: Clear and Isolate My Cart (Priority: P3)

**Goal**: A shopper can clear their own Cart, while every operation derives ownership only from the
validated JWT subject.

**Independent test**: Create two shoppers' Carts, clear one twice, verify the other is unchanged,
reject unauthenticated calls, and prove no request field can select another owner.

### Tests for User Story 3

- [x] T037 [P] [US3] Add failing application tests for idempotent clear and authenticated-owner
  isolation across read, set, remove, and clear in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/application/CartOwnershipUseCaseTests.java`.
- [x] T038 [P] [US3] Add failing PostgreSQL isolation tests for two owners and owner-scoped clear in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/adapter/out/persistence/CartOwnerIsolationIntegrationTests.java`.
- [x] T039 [P] [US3] Extend the existing security/web contract tests with two authenticated shoppers,
  forged owner fields in path/query/body, cross-owner read/mutation attempts, unauthenticated
  non-disclosure, and repeatable DELETE `/api/v1/cart` in
  `services/cart-service/src/test/java/com/philia/flashsale/cart/security/CartSecurityContractTests.java`.

### Implementation for User Story 3

- [x] T040 [P] [US3] Define the owner-scoped clear command and input port in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/command/ClearCartCommand.java`,
  and `services/cart-service/src/main/java/com/philia/flashsale/cart/application/port/in/ClearCartUseCase.java`.
- [x] T041 [US3] Implement owner-scoped clear in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/application/usecase/MaintainCartService.java`,
  `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/CartPersistenceAdapter.java`,
  and `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartController.java`.
- [x] T042 [US3] Run the complete US3 ownership/security test set with
  `./mvnw -pl services/cart-service -am verify` and record the two-shopper isolation checkpoint in
  `specs/048-cart-mvp/validation.md`.

**Checkpoint**: All three user stories are independently functional and ownership-safe.

---

## G6 — Gateway, local runtime, OpenAPI, and smoke automation

**Purpose**: Expose Cart through the approved ingress and make the complete local journey
repeatable without expanding cloud deployment scope.

- [ ] T043 [P] Add failing Gateway route/security/OpenAPI tests for authenticated
  `/api/v1/cart/**`, no internal Product route, and default-disabled documentation in
  `services/api-gateway/src/test/java/com/philia/flashsale/gateway/CartGatewayRouteConfigurationTests.java`.
- [ ] T044 Add the Cart service route, service URL, and opt-in OpenAPI proxy/definition while
  preserving unknown-path deny rules in `services/api-gateway/src/main/resources/application.yml`
  and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`.
- [ ] T045 [P] Add the Cart OpenAPI title/security metadata and public endpoint documentation in
  `services/cart-service/src/main/java/com/philia/flashsale/cart/configuration/CartOpenApiConfiguration.java`.
- [ ] T046 [P] Document `CART_CLIENT_ID`, an empty `CART_CLIENT_SECRET` placeholder, Product URL,
  Cart database/runtime image, and Cart port without a real secret in `infra/docker/.env.example`.
- [ ] T047 Add the one-off `cart-migration` profile and local Cart runtime wiring, health checks,
  service OAuth variables, and Gateway Cart URL to `infra/docker/compose.yml`; keep runtime
  Liquibase disabled.
- [ ] T048 [P] Add static/Pester coverage for bounded timeouts, secret-name-only validation,
  repeatability, cleanup, and expected evidence markers in
  `infra/docker/smoke/tests/feature-048-cart.tests.ps1`.
- [ ] T049 Implement the bounded local fixture/CRUD/replay/isolation/Product-outage/recovery/read-p95
  runner in `infra/docker/smoke/feature-048-cart.ps1`; verify Cart liveness, readiness, Prometheus
  endpoint availability, and Gateway-to-Cart-to-Product trace propagation without printing JWTs,
  secrets, Authorization headers, or Product response bodies.

**Checkpoint**: The complete Cart feature is reachable locally only through API Gateway and is
repeatably testable.

---

## G7 — Documentation and frontend handoff

**Purpose**: Publish the accepted contract and user flow so the frontend does not infer ownership,
price, stock, or checkout behavior.

- [ ] T050 [P] Add all four public Cart endpoints, authentication, error codes, and the internal
  Product endpoint to `docs/api/README.md` while keeping public/internal counts explicit.
- [ ] T051 [P] Add frontend Cart payloads, `detailsAvailable` handling, unavailable states,
  idempotent PUT/DELETE behavior, no-store expectations, and the boundary to the existing purchase
  flow in `docs/api/frontend-integration-guide.md`.
- [ ] T052 Run `git diff --check` and the unresolved-marker scan from
  `specs/048-cart-mvp/quickstart.md`; record results in `specs/048-cart-mvp/validation.md`.
- [ ] T053 Run the affected module gate
  `./mvnw -pl services/cart-service,services/product-service,services/authentication-service,services/api-gateway -am verify`
  and record test counts/exit status in `specs/048-cart-mvp/validation.md`.
- [ ] T054 Run `docker compose --env-file infra/docker/.env -f infra/docker/compose.yml config`
  and verify Cart, migration, Gateway, Product, Authentication, database, and secret references
  render without exposing values; record the result in `specs/048-cart-mvp/validation.md`.

---

## G8 — Live local validation and closure

**Purpose**: Prove the approved behavior once, then close the ledger without deploying Cart to
cloud.

- [ ] T055 Stop before live smoke and ask the user to place `CART_CLIENT_SECRET` in the ignored
  `infra/docker/.env`; automation may parse the value in process memory only to assert it is
  nonblank, while the agent and evidence must never inspect, print, commit, or copy the value into
  `specs/048-cart-mvp/validation.md`.
- [ ] T056 Run the Cart migration twice, start the pre-feature Cart shell against the expanded
  schema to prove non-destructive application rollback compatibility, restore the new Cart image,
  start the local topology, and execute
  `infra/docker/smoke/feature-048-cart.ps1 -Scenario All -TimeoutSeconds 900`; record migration,
  rollback compatibility, liveness, readiness, Prometheus, trace propagation, security, CRUD,
  100-replay, owner-isolation, Product degradation/recovery, and p95 evidence in
  `specs/048-cart-mvp/validation.md` without dropping Cart data.
- [ ] T057 Run `./mvnw clean verify`, audit trace propagation/safe logs/package dependencies and
  the absence of Kafka/Redis/outbox/cloud changes, then update completion status in
  `specs/048-cart-mvp/spec.md`, `specs/048-cart-mvp/plan.md`, and
  `specs/048-cart-mvp/tasks.md` only after every required gate passes.

---

## Dependencies and execution order

```text
G1 T001-T004
  -> G2 T005-T017
     -> G3 / US1 T018-T029
        -> G4 / US2 T030-T036
           -> G5 / US3 T037-T042
              -> G6 T043-T049
                 -> G7 T050-T054
                    -> G8 T055-T057
```

- G2 blocks every user story because valid Cart mutation requires Product verification and the
  dedicated service identity.
- US1 establishes the aggregate and persistence model used by US2 and US3.
- US2 and US3 are independently testable after US1; they may be implemented in parallel in
  separate worktrees, but tasks sharing `CartController`, `MaintainCartService`, or
  `CartPersistenceAdapter` must not be edited concurrently in one worktree.
- G6–G8 integrate and validate only the already-complete user stories; they add no new business
  behavior.

## Parallel opportunities

- G1: T002, T003, and T004 touch distinct files after T001 defines the dependency baseline.
- G2: T005/T006/T011/T014 are independent failing-test tasks; Product, Authentication, and Cart
  adapter implementation can be reviewed separately before T017.
- US1: T018–T021 can be authored in parallel; T022 and T023 are independent core-model tasks; T026
  is intentionally sequential after T025 fixes the migration shape.
- US2: T030 and T031 can be authored in parallel; T032 can proceed independently before T033.
- US3: T037–T039 can be authored in parallel; T040 can proceed before integration in T041/T042.
- G6/G7: Gateway tests, Cart OpenAPI, environment documentation, smoke tests, and API documentation
  touch separate files and can be reviewed in parallel.

## Implementation strategy

1. Complete G1–G2 to establish contracts, security, and dependency boundaries.
2. Deliver G3/US1 as the independently testable MVP: durable set/remove with validation and replay.
3. Add G4/US2 current Product display enrichment and graceful read degradation.
4. Add G5/US3 clear and full owner-isolation evidence.
5. Integrate Gateway/Compose/OpenAPI/smoke once in G6, then update documentation and run all gates.
6. Commit by coherent group if desired, but create one reviewed source PR after G8 to avoid repeated
   cloud image builds; Cart cloud rollout remains explicitly deferred.

## Scope guardrails

- Do not add Cart checkout, stock reservation, price snapshots, Kafka, Redis, outbox, expiry,
  Notification integration, cloud Kustomize, ECR promotion, or Argo CD changes.
- Do not access Product or Authentication databases from Cart and do not share JPA/domain models.
- Do not forward a shopper token to Product or reuse Campaign credentials/scopes.
- Do not hold a Cart transaction or database connection during the Product HTTP call.
- Do not mark a task complete when its required test/evidence is missing or failing.
