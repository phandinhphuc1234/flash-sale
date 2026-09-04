# Tasks: Regular Purchase Checkout

**Status**: Approved for implementation by project owner on 2026-09-03

**Input**: Approved design documents from `specs/049-regular-purchase-checkout/`

**Prerequisites**: Approved `spec.md` and `plan.md`; `research.md`, `data-model.md`, `contracts/`,
and `quickstart.md` are complete.

**Tests**: Every test and validation named by the specification, plan, contracts, constitution, and
the tasks below is required. A checked task must have command/result evidence in `validation.md`.

**Organization**: Tasks are grouped by user story. The sequence also preserves the plan's G1–G8
contract-first, local-first, one-cloud-release strategy.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel after its stated phase prerequisites because it owns different files.
- **[Story]**: Maps the task to US1, US2, or US3 from `spec.md`.
- Production implementation was authorized by the project owner on 2026-09-03.

## Phase 1: Setup and Disabled Runtime Boundaries

**Purpose**: Add only dependency/configuration scaffolding needed by every story, with regular
purchase entry points and producers disabled by default.

- [X] T001 Add the already governed OpenFeign/OAuth2 client dependencies to `services/order-service/pom.xml` and Kafka/Avro dependencies to `services/inventory-service/pom.xml` and `services/cart-service/pom.xml`
- [X] T002 [P] Add disabled Cart reconciliation consumer/topic properties without Secret values in `services/cart-service/src/main/resources/application.yml`
- [X] T003 [P] Add disabled regular-hold API, consumer, publisher, expiry, topic, five-minute TTL, and configurable 90-second maximum clock-skew properties in `services/inventory-service/src/main/resources/application.yml`
- [X] T004 [P] Add disabled regular-intake/recovery/producer properties plus Cart/Product/Inventory URLs, OAuth registration, timeouts, topics, and DLTs in `services/order-service/src/main/resources/application.yml`
- [X] T005 [P] Add Order-only purchase-quote subject/scope properties without changing existing Cart/Campaign contracts in `services/product-service/src/main/resources/application.yml`
- [X] T006 Add `ORDER_CLIENT_ID`/`ORDER_CLIENT_SECRET`, internal URLs, topics, and disabled feature flags to `infra/docker/.env.example` and `infra/docker/compose.yml`
- [X] T007 Add matching non-secret values and Secret boundaries to `infra/k8s/overlays/cloud/config/cart-service-runtime-config.yaml`, `infra/k8s/overlays/cloud/config/inventory-service-runtime-config.yaml`, `infra/k8s/overlays/cloud/config/order-service-runtime-config.yaml`, `infra/k8s/overlays/cloud/config/product-service-runtime-config.yaml`, `infra/k8s/overlays/cloud/config/authentication-service-runtime-config.yaml`, `infra/k8s/overlays/cloud/config/kustomization.yaml`, and `infra/scripts/gitops/phase15-secrets.ps1`
- [X] T008 Create the bounded, validation-only Feature 049 scenario runner shell and evidence ledger in `infra/docker/smoke/feature-049-regular-purchase.ps1` and `specs/049-regular-purchase-checkout/validation.md`

**Checkpoint**: Configuration renders with the new feature disabled and no Secret value committed.

---

## Phase 2: Foundational Contracts, Migrations, and Machine Trust

**Purpose**: Complete G1 and shared blockers before any public story implementation.

**Critical**: No user story work begins until this phase passes contract, migration, security, and
static infrastructure gates.

### Versioned Kafka contracts

- [X] T009 [P] Add `ConfirmRegularStockHoldV1.avsc` and `ReleaseRegularStockHoldV1.avsc` under `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.inventory.regular-hold.commands.v1/`
- [X] T010 [P] Add `RegularStockHoldConfirmedV1.avsc`, `RegularStockHoldReleasedV1.avsc`, and `RegularStockHoldExpiredV1.avsc` under `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.inventory.regular-hold.events.v1/`
- [X] T011 [P] Add `ReconcilePurchasedCartSnapshotV1.avsc` under `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.cart.checkout.commands.v1/`
- [X] T012 [P] Add regular-purchase `OrderCreatedV2.avsc`, `OrderConfirmedV2.avsc`, `OrderCancelledV2.avsc`, and `OrderExpiredV2.avsc` under `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.order.events.v1/` without modifying V1 schemas
- [X] T013 Add field/default, identity, logical-type, keying, and BACKWARD_TRANSITIVE compatibility tests for every Feature 049 record in `contracts/kafka-avro-contracts/src/test/java/com/philia/flashsale/contract/regularpurchase/RegularPurchaseSchemaTests.java`

### Topic and Schema Registry provisioning

- [X] T014 [P] Add idempotent local creation for three main topics and three consumer-specific DLTs in `infra/docker/kafka/init-regular-purchase-topics.sh`
- [X] T015 [P] Add source and DLT TopicRecordNameStrategy registration/compatibility checks in `infra/docker/schema-registry/register-regular-purchase-schemas.ps1`
- [X] T016 Extend the reviewed cloud topic/subject inventory and validation/apply flow in `infra/scripts/gitops/phase20-kafka-contracts.ps1`

### Expand-first database migrations

- [X] T017 [P] Add Cart/cart-item revision columns and Cart reconciliation inbox with backfill and rollback SQL documentation in `services/cart-service/src/main/resources/db/changelog/changes/002-add-checkout-revisions-and-inbox.yaml`
- [X] T018 [P] Add regular hold/items, command inbox, Inventory outbox envelope/lease fields, indexes, and cross-field constraints in `services/inventory-service/src/main/resources/db/changelog/changes/002-add-regular-stock-holds.sql`
- [X] T019 [P] Add regular request intake, Order/Saga source and participant fields, legacy backfill/nullability checks, and outbox/inbox extensions in `services/order-service/src/main/resources/db/changelog/changes/003-add-regular-purchase-checkout.sql`
- [X] T020 [P] Include the Cart changeset and verify fresh plus current-schema upgrade in `services/cart-service/src/main/resources/db/changelog/db.changelog-master.yaml` and `services/cart-service/src/test/java/com/philia/flashsale/cart/integration/CartCheckoutMigrationIntegrationTests.java`
- [X] T021 [P] Include the Inventory changeset and verify fresh plus current-schema upgrade in `services/inventory-service/src/main/resources/db/changelog/db.changelog-master.yaml` and `services/inventory-service/src/test/java/com/philia/flashsale/inventory/integration/RegularHoldMigrationIntegrationTests.java`
- [X] T022 [P] Include the Order changeset and verify Flash Sale backfill/current-schema upgrade in `services/order-service/src/main/resources/db/changelog/db.changelog-master.yaml` and `services/order-service/src/test/java/com/philia/flashsale/order/integration/RegularPurchaseMigrationIntegrationTests.java`

### Machine client and exact internal authorization

- [X] T023 Add the `order-service` client with only the three approved scopes in `services/authentication-service/src/main/resources/application.yml` and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/ServiceClientsProperties.java`
- [X] T024 Add provisioning, token-claim, forbidden-scope, and existing-client non-regression tests in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/security/OrderServiceClientCredentialTests.java`
- [X] T025 [P] Add exact Order subject/audience/type/scope security chain and failure tests for Cart snapshot in `services/cart-service/src/main/java/com/philia/flashsale/cart/configuration/CartInternalSecurityConfiguration.java` and `services/cart-service/src/test/java/com/philia/flashsale/cart/security/CartInternalSecurityConfigurationTests.java`
- [X] T026 [P] Add an independent exact Order purchase-quote security chain and tests in `services/product-service/src/main/java/com/philia/flashsale/product/configuration/ProductPurchaseQuoteSecurityConfiguration.java` and `services/product-service/src/test/java/com/philia/flashsale/product/configuration/ProductPurchaseQuoteSecurityTests.java`
- [X] T027 [P] Add an exact Order regular-hold security chain without broadening Campaign allocation access in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/configuration/InventoryRegularHoldSecurityConfiguration.java` and `services/inventory-service/src/test/java/com/philia/flashsale/inventory/configuration/InventoryRegularHoldSecurityConfigurationTests.java`
- [X] T028 Add Order OAuth2 authorized-client manager, bounded token cache, propagation interceptor, and focused tests in `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderInternalClientConfiguration.java` and `services/order-service/src/test/java/com/philia/flashsale/order/configuration/OrderInternalClientConfigurationTests.java`
- [X] T029 Add Clean/Hex rules preventing web/Feign/JPA/Kafka/Avro leakage into Feature 049 domain/application packages in `services/order-service/src/test/java/com/philia/flashsale/order/architecture/OrderArchitectureTests.java`, `services/cart-service/src/test/java/com/philia/flashsale/cart/architecture/CartArchitectureTests.java`, and `services/inventory-service/src/test/java/com/philia/flashsale/inventory/architecture/InventoryArchitectureTests.java`
- [X] T030 Run the contract module, three migration tests, PowerShell syntax checks, Compose render, and cloud Kustomize dry-run; record commands/results in `specs/049-regular-purchase-checkout/validation.md`

**Checkpoint**: G1 passes; schemas/topics/migrations/security boundaries exist, remain disabled, and
all three stories can build on them.

---

## Phase 3: User Story 1 — Buy One Normal Product Now (Priority: P1) — MVP

**Goal**: A shopper completes Buy Now for one sellable normal variant through the existing hosted
Payment flow, producing one confirmed Order and one exact Inventory deduction while Cart is unchanged.

**Independent Test**: Through Gateway, submit one Buy Now request, complete Stripe test payment,
verify one Order/Payment/hold, Order `CONFIRMED`, hold `CONFIRMED`, one stock deduction, unchanged
Cart, and exact replay for the same shopper/key/body.

### Tests for User Story 1

- [X] T031 [P] [US1] Add Product purchase-quote application/HTTP/security tests for found, missing, unsellable, current price/currency/version, order preservation, and bounded batch input in `services/product-service/src/test/java/com/philia/flashsale/product/catalogquery/PurchaseQuoteTests.java`
- [X] T032 [P] [US1] Add Inventory hold domain tests for five-minute TTL, availability equation, item canonicalization, hold confirmation, and illegal transitions in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/regularhold/domain/RegularStockHoldTests.java`
- [X] T033 [P] [US1] Add Inventory atomic persistence/concurrency tests for same-request replay, conflict, deterministic row locking, one-line oversell prevention, and exact movement in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/regularhold/integration/RegularHoldPersistenceIntegrationTests.java`
- [ ] T034 [P] [US1] Add Buy Now request/fingerprint, price/sellability/stock rejection, owner derivation, five-second budget, replay/conflict, and response contract tests in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/BuyNowUseCaseTests.java` and `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/BuyNowHttpTests.java`
- [X] T035 [P] [US1] Add multi-line-capable Order/source/participant/total and existing Flash Sale construction regression tests in `services/order-service/src/test/java/com/philia/flashsale/order/order/domain/RegularOrderDomainTests.java`
- [ ] T036 [P] [US1] Add regular confirm-command/result outbox, inbox replay, Order terminalization, and unchanged `PaymentRequestedV1` mapping tests in `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/RegularHoldPaidSagaTests.java`

### Product authoritative quote

- [X] T037 [US1] Implement Product-owned purchase quote query/result/port/use case with no Cart or Order wire types in `services/product-service/src/main/java/com/philia/flashsale/product/catalogquery/application/PurchaseQuoteService.java`
- [X] T038 [US1] Implement the Order-only batch quote web DTOs/controller/mapper in `services/product-service/src/main/java/com/philia/flashsale/product/catalogquery/adapter/in/web/PurchaseQuoteController.java`

### Inventory hold creation and paid confirmation

- [X] T039 [US1] Implement `RegularStockHold`, item, status, TTL, availability, and transition policies in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/domain/model/RegularStockHold.java`
- [X] T040 [US1] Implement create/confirm input ports, commands/results, and use cases with one capability-level atomic persistence port in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/application/usecase/RegularStockHoldService.java`
- [X] T041 [US1] Implement JPA entities/repositories/mappers and deterministic Inventory-row locking for atomic hold creation/confirmation in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/adapter/out/persistence/jpa/RegularStockHoldPersistenceAdapter.java`
- [X] T042 [US1] Implement the idempotent internal regular-hold request/response DTOs and controller in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/adapter/in/web/RegularStockHoldController.java`
- [X] T043 [US1] Implement strict `ConfirmRegularStockHoldV1` mapper/listener, command inbox transaction, and consumer-specific DLT routing in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/adapter/in/messaging/kafka/RegularHoldCommandKafkaConsumer.java`
- [X] T044 [US1] Generalize Inventory outbox claim/retry dispatch through the application port and JPA adapter in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/outbox/adapter/out/persistence/jpa/RegularHoldOutboxDispatchPersistenceAdapter.java`, then publish stable confirmed facts in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/adapter/out/messaging/kafka/RegularHoldOutcomePublisher.java`

### Order Buy Now intake and paid Saga

- [X] T045 [US1] Generalize Order/OrderLine/PurchaseSaga domain models for purchase source, participant type/reference, non-empty line lists, and regular paid transition without changing Flash Sale semantics in `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/Order.java` and `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/domain/model/PurchaseSaga.java`
- [ ] T046 [US1] Implement canonical regular request, line/price, intake state, idempotency, and five-minute/deadline policies in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/domain/model/RegularPurchaseRequest.java`
- [ ] T047 [US1] Implement regular-intake JPA entities/repositories/mappers and atomic accepted commit of intake, Order/lines, Saga, OrderCreatedV2, and PaymentRequestedV1 outbox in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/persistence/jpa/RegularPurchasePersistenceAdapter.java`
- [ ] T048 [P] [US1] Implement the Product purchase-quote Feign wire models/adapter and error mapping in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/product/ProductPurchaseQuoteClientAdapter.java`
- [ ] T049 [P] [US1] Implement the idempotent Inventory regular-hold Feign wire models/adapter and ambiguous-timeout handling in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/inventory/InventoryRegularHoldClientAdapter.java`
- [ ] T050 [US1] Implement the resumable Buy Now orchestration use case without holding an Order transaction across HTTP in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/application/usecase/RegularPurchaseCheckoutService.java`
- [ ] T051 [US1] Implement Buy Now request/response/price-conflict DTOs, authenticated controller, `Idempotency-Key`, replay headers, no-store/trace headers, and standard error classification in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/in/web/RegularPurchaseController.java`
- [ ] T052 [US1] Extend Order security/OpenAPI/query DTOs for the new authenticated command and additive purchase-source/stock fields in `services/order-service/src/main/java/com/philia/flashsale/order/security/OrderSecurityConfiguration.java` and `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/web/response/OrderDetailsResponse.java`
- [ ] T053 [US1] Publish regular `OrderCreatedV2`, existing `PaymentRequestedV1`, and confirm-hold command through stable Order outbox dispatchers in `services/order-service/src/main/java/com/philia/flashsale/order/outbox/application/usecase/OrderOutboxEventTypeDispatcher.java`
- [ ] T054 [US1] Consume and validate `RegularStockHoldConfirmedV1`, atomically complete Saga/Order, and publish `OrderConfirmedV2` in `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/adapter/in/messaging/kafka/RegularHoldResultKafkaConsumer.java`
- [ ] T055 [US1] Complete the `BuyNowPaid` local scenario through Gateway, Payment, test webhook, Kafka, Inventory, and Order while asserting Cart is unchanged in `infra/docker/smoke/feature-049-regular-purchase.ps1`
- [ ] T056 [US1] Run Product, Inventory, Order, Payment, Gateway, and contract module verification plus `BuyNowPaid`; record exact PASS evidence in `specs/049-regular-purchase-checkout/validation.md`

**Checkpoint**: US1 is an independently demonstrable MVP with real Payment convergence and no Cart
dependency.

---

## Phase 4: User Story 2 — Checkout My Current Cart (Priority: P1)

**Goal**: Checkout a reviewed multi-item Cart as one Order/Payment, reject the whole submission on
any Cart/Product/stock conflict, and remove only unchanged purchased intent after confirmation.

**Independent Test**: Create two items, submit exact revisions/prices, complete payment, verify one
multi-line Order and confirmed hold, edit one item before completion, and prove cleanup removes only
the unchanged item without exposing/changing another shopper's Cart.

### Tests for User Story 2

- [ ] T057 [P] [US2] Add Cart/cart-item monotonic revision and delete/re-add tests in `services/cart-service/src/test/java/com/philia/flashsale/cart/domain/CartCheckoutRevisionTests.java`
- [ ] T058 [P] [US2] Add Cart snapshot HTTP/application/owner-isolation tests for absent, empty, exact revisions, and no Product enrichment in `services/cart-service/src/test/java/com/philia/flashsale/cart/checkout/CartCheckoutSnapshotTests.java`
- [ ] T059 [P] [US2] Add Cart reconciliation integration tests for applied, partial no-op, quantity edit, remove/re-add, concurrent HTTP mutation, replay, conflict, wrong owner, and DLT in `services/cart-service/src/test/java/com/philia/flashsale/cart/checkout/CartReconciliationIntegrationTests.java`
- [ ] T060 [P] [US2] Add Order Cart checkout tests for exact snapshot comparison, empty/changed/foreign Cart, duplicate variants, mixed currency, price conflict payload, all-or-nothing stock rejection, one Order/Payment, and replay in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/CartCheckoutUseCaseTests.java`
- [ ] T061 [P] [US2] Add multi-item overlapping-hold concurrency tests proving atomic rejection and no unexplained partial hold in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/regularhold/integration/MultiItemRegularHoldConcurrencyTests.java`

### Cart snapshot and revision implementation

- [ ] T062 [US2] Extend Cart/CartItem domain and persistence mapping to allocate monotonic Cart/item revisions for CRUD and delete/re-add in `services/cart-service/src/main/java/com/philia/flashsale/cart/domain/model/Cart.java` and `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/out/persistence/jpa/CartPersistenceAdapter.java`
- [ ] T063 [US2] Add `cartVersion` and `itemVersion` to public Cart results/responses without changing existing CRUD semantics in `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartResponse.java` and `services/cart-service/src/main/java/com/philia/flashsale/cart/adapter/in/web/CartItemResponse.java`
- [ ] T064 [US2] Implement internal snapshot query/result/port/use case and owner-safe JPA adapter in `services/cart-service/src/main/java/com/philia/flashsale/cart/checkout/application/usecase/GetCartCheckoutSnapshotService.java`
- [ ] T065 [US2] Implement the exact-subject internal snapshot DTOs/controller/mapper in `services/cart-service/src/main/java/com/philia/flashsale/cart/checkout/adapter/in/web/CartCheckoutSnapshotController.java`

### Cart checkout orchestration

- [ ] T066 [US2] Implement the Cart snapshot Feign wire models/adapter and bounded error mapping in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/cart/CartCheckoutSnapshotClientAdapter.java`
- [ ] T067 [US2] Extend regular orchestration to canonicalize/compare the exact Cart snapshot, validate all Product prices, acquire one multi-item hold, and commit one Order/Payment in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/application/usecase/RegularPurchaseCheckoutService.java`
- [ ] T068 [US2] Add Cart checkout request validation and endpoint/replay behavior to `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/in/web/RegularPurchaseController.java`

### Confirmed Cart reconciliation

- [ ] T069 [US2] Implement Cart reconciliation domain policy, input port, command/result, and conditional cleanup use case in `services/cart-service/src/main/java/com/philia/flashsale/cart/checkout/application/usecase/ReconcilePurchasedCartSnapshotService.java`
- [ ] T070 [US2] Implement atomic conditional deletes plus inbox receipt and Cart version advancement in `services/cart-service/src/main/java/com/philia/flashsale/cart/checkout/adapter/out/persistence/jpa/CartReconciliationPersistenceAdapter.java`
- [ ] T071 [US2] Implement strict reconciliation Avro mapper/listener, manual acknowledgement, retry, and DLT in `services/cart-service/src/main/java/com/philia/flashsale/cart/checkout/adapter/in/messaging/kafka/CartReconciliationKafkaConsumer.java`
- [ ] T072 [US2] Emit one `ReconcilePurchasedCartSnapshotV1` outbox command only after a `CART` Order is durably confirmed in `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/adapter/out/persistence/jpa/RegularHoldConfirmationPersistenceAdapter.java`
- [ ] T073 [US2] Update public Gateway/Swagger/frontend handoff documentation for Cart versions, Cart checkout, price conflict, and Payment continuation in `docs/api/frontend-integration-guide.md` and `docs/api/endpoint-registry.md`
- [ ] T074 [US2] Complete `CartPaid`, `CartEditedWhilePaying`, `PriceChanged`, and `InsufficientStock` local scenarios in `infra/docker/smoke/feature-049-regular-purchase.ps1`
- [ ] T075 [US2] Run Cart, Product, Inventory, Order, Payment, Gateway, and contract module verification plus all US2 scenarios; record PASS evidence in `specs/049-regular-purchase-checkout/validation.md`

**Checkpoint**: US1 and US2 both work; Cart checkout is multi-line/all-or-nothing and later Cart
intent survives asynchronous confirmation.

---

## Phase 5: User Story 3 — Recover Safely Across Payment Outcomes (Priority: P1)

**Goal**: Accepted regular purchases converge safely through failure, expiry, duplicate/reordered
messages, transient outages, and late verified success without duplicate stock/Cart effects or an
invented refund.

**Independent Test**: Exercise terminal Payment failure, hold expiry, 100 request/event replays,
dependency outage/restart, and delayed higher-version success; verify exact Order/Saga/Payment/hold/
Cart outcomes and unchanged Flash Sale behavior.

### Tests for User Story 3

- [ ] T076 [P] [US3] Add Inventory release/expiry/current-state outcome tests covering deadline boundary, scheduler race, confirm-versus-release, late confirm, replay, and movement invariants in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/regularhold/integration/RegularHoldRecoveryIntegrationTests.java`
- [ ] T077 [P] [US3] Add Order Payment failure, hold released/expired, stale/reordered version, success-dominant late success, and manual-review tests in `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/RegularHoldRecoverySagaTests.java`
- [ ] T078 [P] [US3] Add intake crash-window/resume tests for failure before hold, ambiguous hold response, crash after hold/before Order commit, concurrent same key, and recovery worker lease in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/RegularPurchaseRecoveryIntegrationTests.java`
- [ ] T079 [P] [US3] Add consumer malformed/identity-conflict/retry/DLT tests in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/regularhold/integration/RegularHoldKafkaFailureTests.java`, `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/RegularHoldResultKafkaFailureTests.java`, and `services/cart-service/src/test/java/com/philia/flashsale/cart/checkout/CartReconciliationKafkaFailureTests.java`
- [ ] T080 [P] [US3] Add 100-replay and competing Buy Now/Cart final-unit load tests proving one semantic effect and no oversell in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/RegularPurchaseReplayLoadTests.java` and `services/inventory-service/src/test/java/com/philia/flashsale/inventory/regularhold/integration/RegularHoldLoadTests.java`

### Inventory release, expiry, and recoverable publication

- [ ] T081 [US3] Extend Inventory hold domain/application/persistence with idempotent release, expiry, current-state outcomes, and deterministic confirm/release/expiry locking in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/application/usecase/RegularStockHoldService.java`
- [ ] T082 [US3] Implement strict release command handling and all three stable outcome publishers in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/adapter/in/messaging/kafka/RegularHoldCommandKafkaConsumer.java` and `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/adapter/out/messaging/kafka/RegularHoldOutcomePublisher.java`
- [ ] T083 [US3] Implement bounded `SKIP LOCKED` hold expiry and outbox recovery scheduling with disabled-by-default runtime controls in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/regularhold/adapter/in/scheduling/RegularHoldExpiryJob.java`

### Order recovery and terminal convergence

- [ ] T084 [US3] Extend the Order Saga state/participant routing to release regular holds, consume released/expired outcomes, preserve monotonic versions, and terminalize `CANCELLED`/`EXPIRED` in `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/domain/model/PurchaseSaga.java`
- [ ] T085 [US3] Implement atomic regular hold release/expiry/late-success transitions and V2 terminal/manual-review outbox facts in `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/adapter/out/persistence/jpa/RegularHoldRecoveryPersistenceAdapter.java`
- [ ] T086 [US3] Extend strict Inventory result consumption, current-command causation checks, replay/conflict handling, retry, manual acknowledgement, and DLT in `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/adapter/in/messaging/kafka/RegularHoldResultKafkaConsumer.java`
- [ ] T087 [US3] Implement leased recovery of non-terminal regular intake checkpoints using the same idempotent clients/IDs in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/in/scheduling/RegularPurchaseRecoveryJob.java`
- [ ] T088 [US3] Prove the existing Payment deadline rejects new Checkout attempts while preserving provider reconciliation, without changing Payment production contracts or schemas, in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/domain/model/PaymentTests.java` and `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/CheckoutRecoveryIntegrationTests.java`

### Observability and aggregate recovery evidence

- [ ] T089 [P] [US3] Add bounded regular-intake/Saga/recovery/manual-review metrics and trace propagation without sensitive labels in `services/order-service/src/main/java/com/philia/flashsale/order/observability/OrderObservability.java`
- [ ] T090 [P] [US3] Add bounded active/expired/confirmed/released hold, outbox, DLT, and expiry metrics in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/observability/InventoryObservability.java`
- [ ] T091 [P] [US3] Add bounded reconciliation applied/no-op/replay/conflict/DLT metrics in `services/cart-service/src/main/java/com/philia/flashsale/cart/observability/CartObservability.java`
- [ ] T092 [US3] Complete `PaymentFailed`, `HoldExpired`, `Replay`, `Concurrency`, dependency-restart, and `LateSuccess` scenarios with sanitized diagnostics in `infra/docker/smoke/feature-049-regular-purchase.ps1`
- [ ] T093 [US3] Run existing Feature 044/Phase 22/Phase 24 Flash Sale and Payment regression scenarios without altering their contracts in `infra/docker/smoke/feature-044-purchase-saga.ps1` and record results in `specs/049-regular-purchase-checkout/validation.md`
- [ ] T094 [US3] Run all US3 module, Kafka/Registry/PostgreSQL, replay/concurrency, outage/restart, and late-success gates; record exact PASS evidence in `specs/049-regular-purchase-checkout/validation.md`

**Checkpoint**: All three stories converge under success/failure/replay/outage conditions and the
existing Flash Sale Saga remains green.

---

## Phase 6: Polish, Aggregate Validation, and One Cloud Release

**Purpose**: Complete G7/G8 without introducing another business behavior.

### Documentation and operations

- [ ] T095 [P] Update the complete endpoint registry, Swagger aggregation, frontend payload examples, and no-internal-route guidance in `docs/api/endpoint-registry.md`, `docs/api/frontend-integration-guide.md`, and `services/api-gateway/src/main/resources/application.yml`
- [ ] T096 [P] Add bounded Prometheus rules and Grafana panels for checkout rejection/latency, active/expired holds, Saga recovery, DLT/outbox lag, and Cart reconciliation in `infra/monitoring/prometheus/rules/regular-purchase-alerts.yml` and `infra/monitoring/grafana/dashboards/regular-purchase.json`
- [ ] T097 [P] Add troubleshooting, replay, expiry, orphan-hold, manual-review, and Secret-redaction procedures in `docs/runbooks/regular-purchase-checkout.md`
- [ ] T098 Audit comments/logs/error responses/metrics/traces for stale Flash Sale-only wording and sensitive/high-cardinality data in `services/order-service/src/main/java`, `services/inventory-service/src/main/java`, and `services/cart-service/src/main/java`

### Local aggregate gate

- [ ] T099 Complete the `All` orchestration, bounded process cleanup, PASS labels, and failure diagnostics in `infra/docker/smoke/feature-049-regular-purchase.ps1`
- [ ] T100 Run affected module verification for Authentication, Product, Cart, Inventory, Order, Payment, Gateway, and contracts; record exit codes in `specs/049-regular-purchase-checkout/validation.md`
- [ ] T101 Run `./mvnw clean verify` with a sufficient independent execution budget and record the successful exit code in `specs/049-regular-purchase-checkout/validation.md`
- [ ] T102 Run PowerShell syntax, Compose render, topic/schema validation-only, `git diff --check`, architecture tests, and `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`; record results in `specs/049-regular-purchase-checkout/validation.md`

### Migration, release, and rollback gates

- [ ] T103 Implement read-only/apply migration gate resolving exact `release-<develop SHA>` images and running Cart, Inventory, then Order Jobs sequentially in `infra/scripts/gitops/phase49-regular-purchase-migration-gate.ps1`
- [ ] T104 Implement read-only prior-image/schema/status compatibility rehearsal that never changes or prints business rows in `infra/scripts/gitops/phase49-regular-purchase-rollback-rehearsal.ps1`
- [ ] T105 Add the missing Cart ECR/workload ownership to the Terraform/Kustomize cloud delivery path and extend selective delivery mapping for affected Feature 049 service paths without rebuilding Payment unnecessarily in `.github/workflows/service-delivery.yml`
- [ ] T106 Provision Feature 049 topics/subjects with Payment safety flags respected and record the validation/apply evidence in `specs/049-regular-purchase-checkout/validation.md`
- [ ] T107 Run one selective immutable-image delivery, verify ECR digests, execute the migration gate, review one image-promotion PR, and record CI/PR references in `specs/049-regular-purchase-checkout/validation.md`
- [ ] T108 Merge image promotion, force Argo refresh, verify Synced/Healthy, Deployment image digests/readiness, consumers enabled and regular intake still disabled in `specs/049-regular-purchase-checkout/validation.md`
- [ ] T109 Enable regular purchase intake through the reviewed cloud ConfigMap change in `infra/k8s/overlays/cloud/config/order-service-runtime-config.yaml` only after T108 passes
- [ ] T110 Implement and run HTTPS Gateway Buy Now/Cart/Stripe/replay/failure cloud smoke with no Secret or Checkout URL output in `infra/scripts/gitops/phase49-regular-purchase-cloud.ps1`
- [ ] T111 Disable intake, drain/record durable work, run compatibility rehearsal, restore prior compatible immutable tags, and prove no schema/topic/business-row deletion in `specs/049-regular-purchase-checkout/validation.md`
- [ ] T112 Re-run the final enabled release after rehearsal, close every requirement/task with evidence, and record final Feature 049 PASS plus known operational limits in `specs/049-regular-purchase-checkout/validation.md`

---

## Dependencies and Execution Order

### Phase dependencies

```text
Phase 1 Setup
  -> Phase 2 contracts/migrations/trust (blocks all stories)
  -> Phase 3 US1 Buy Now MVP
  -> Phase 4 US2 Cart checkout
  -> Phase 5 US3 recovery
  -> Phase 6 aggregate validation and one cloud release
```

- Phase 1 starts immediately after this ledger is approved.
- Phase 2 must finish before production story code because schemas, migrations, scopes, and disabled
  configuration are shared blockers.
- US1 must finish before US2 because US2 reuses the regular Order/Payment/Inventory pipeline.
- US3 follows US1/US2 because it hardens both accepted workflows and terminal outcomes.
- Cloud tasks T106–T112 require every local gate T099–T102 and source PR CI to pass.
- Migration apply T107 occurs after immutable images exist but before the image-promotion PR merges.
- Intake enable T109 occurs only after consumer/image/migration/Argo checks T106–T108 pass.

### User story dependency graph

| Story | Depends on | Independently demonstrable result |
|---|---|---|
| US1 Buy Now | Foundation | One normal variant reaches confirmed Order/Payment/stock while Cart is unchanged. |
| US2 Cart checkout | Foundation + US1 regular pipeline | One multi-line Cart reaches one confirmed Order/Payment and conditionally reconciles only unchanged intent. |
| US3 Recovery | Foundation + US1 + US2 | Both entries converge under failure, expiry, replay, outage, and late success. |

### Critical implementation ordering

1. Avro/HTTP contracts before producers or consumers.
2. Expand migrations before new entity mappings or enum values are enabled.
3. Authentication client before internal callers are exercised.
4. Inventory hold persistence before Order can expose acceptance.
5. Consumers before producers; producers before public intake flag.
6. Focused scenario before aggregate runner; local gate before cloud.
7. Cloud topics and migrations before image promotion; image promotion before intake enablement.

## Parallel Opportunities

After Phase 1:

- T009–T012 Avro families can be authored in parallel, then T013 integrates them.
- T014 and T015 can run in parallel, then T016 integrates cloud provisioning.
- T017–T022 are service-owned migration/test pairs and can run in parallel across Cart, Inventory,
  and Order.
- T025–T027 are independent service security chains.

Within US1:

- Product T031/T037/T038 and Inventory T032/T033/T039–T044 can proceed in parallel after Foundation.
- Order test/model work T034–T036/T045–T047 can proceed while client adapters T048/T049 are built.
- T050 onward waits for Product and Inventory interfaces.

Within US2:

- Cart tests/implementation T057–T059/T062–T065 can proceed alongside Order Cart contract tests
  T060 and Inventory multi-item test T061.
- Reconciliation T069–T071 can proceed before Order emitter T072, then the E2E task integrates them.

Within US3:

- Inventory T076/T081–T083, Order T077/T084–T087, and cross-service Kafka failure tests T079 can
  proceed in parallel after US2.
- Metrics T089–T091 are independent by service after transition names are stable.

## Parallel Execution Examples

### US1

```text
Track A: T031 -> T037 -> T038
Track B: T032 + T033 -> T039 -> T040 -> T041 -> T042 -> T043 -> T044
Track C: T034 + T035 + T036 -> T045 -> T046 -> T047 -> T048 + T049
Join:    T050 -> T051 -> T052 -> T053 -> T054 -> T055 -> T056
```

### US2

```text
Track A: T057 + T058 + T059 -> T062 -> T063 -> T064 -> T065 -> T069 -> T070 -> T071
Track B: T060 -> T066 -> T067 -> T068
Track C: T061
Join:    T072 -> T073 -> T074 -> T075
```

### US3

```text
Track A: T076 -> T081 -> T082 -> T083
Track B: T077 + T078 -> T084 -> T085 -> T086 -> T087
Track C: T079 + T080
Track D: T089 + T090 + T091
Join:    T088 -> T092 -> T093 -> T094
```

## Implementation Strategy

### MVP first

1. Complete Setup and Foundation.
2. Complete US1 through `BuyNowPaid` and all module gates.
3. Stop and demonstrate normal Buy Now without Cart.
4. Continue US2 only after the MVP is green.

### Local-first single-release policy

- Commit coherent task groups locally, but do not trigger repeated cloud releases.
- Validate G1–G7 entirely locally and in source PR CI.
- Merge one reviewed source PR when all local evidence is green.
- Run one selective image build/promotion for the final source SHA.
- Use a separate small reviewed config PR only to enable intake after the disabled deployment is
  healthy; configuration activation is not an image rebuild.

### Completion discipline

- A checkbox is marked only with command/result evidence.
- Never mark Maven/build complete from partial compilation output; require exit code 0.
- Preserve existing Flash Sale V1 schemas and tests throughout every group.
- Stop and return to spec/plan approval if implementation exposes a new financial, stock, timeout,
  idempotency, Cart cleanup, retention, or refund rule.
- Do not stage unrelated `docs/api` or `.tmp-*` working-tree files unless the owner explicitly adds
  them to this feature.
