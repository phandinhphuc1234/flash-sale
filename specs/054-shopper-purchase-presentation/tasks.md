# Tasks: Shopper Purchase Presentation

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/order-item-display-http.md](contracts/order-item-display-http.md)

**Status**: Approved — owner approved plan and tasks on 2026-09-23

**Tests**: Order unit, integration, migration and HTTP contract tests; frontend Node tests, production build and local desktop/mobile review; affected-module and full Maven verification.

## Phase 1: Approval and contract gate

**Purpose**: Keep implementation tied to the resolved behavior and the existing HTTP/Kafka boundaries.

- [x] T001 Confirm the owner-approved feature pointer and complete spec/plan/tasks review in `.specify/feature.json`, `specs/054-shopper-purchase-presentation/spec.md`, `specs/054-shopper-purchase-presentation/plan.md`, and `specs/054-shopper-purchase-presentation/tasks.md` before editing production code.
- [x] T002 Confirm the additive nullable Order response in `specs/054-shopper-purchase-presentation/contracts/order-item-display-http.md` and the unchanged private Product HTTP and `PurchaseAcceptedV1` Kafka contracts in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/client/product/ProductPurchaseQuoteFeignClient.java` and `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc`.

**Checkpoint**: Approved artifacts and contract boundaries are verified; no production task begins before this gate.

## Phase 2: User Story 1 — Recognize purchased items (P1) 🎯 MVP

**Goal**: New regular and Flash Sale Orders retain available names at creation; old and best-effort-unnamed Orders remain readable without changing purchase semantics.

**Independent test**: A regular Order survives recovery with the original quote names; a Flash Sale Order is created with or without Product lookup; replay never overwrites its initial names; the owner-only Order API returns named or nullable unnamed lines.

### Tests

- [x] T003 [P] [US1] Add regular-purchase name/fingerprint and recovery tests in `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/domain/RegularPurchaseRequestDomainTests.java`, `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/application/usecase/RegularPurchaseCheckoutServiceTests.java`, and `services/order-service/src/test/java/com/philia/flashsale/order/regularpurchase/integration/RegularPurchaseRecoveryIntegrationTests.java`.
- [x] T004 [P] [US1] Add old-row and legacy-JSON migration coverage in `services/order-service/src/test/java/com/philia/flashsale/order/integration/OrderSchemaMigrationIntegrationTests.java` and `services/order-service/src/test/java/com/philia/flashsale/order/integration/RegularPurchaseMigrationIntegrationTests.java`.
- [x] T005 [P] [US1] Add Flash Sale Product-found/missing/failure tests and purchase-event replay assertions in `services/order-service/src/test/java/com/philia/flashsale/order/order/application/CreateOrderFromAcceptedPurchaseServiceTests.java`, `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/out/client/product/OrderLineNameLookupAdapterTests.java`, `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/PurchaseAcceptedConsumerIntegrationTests.java`, and `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/AcceptedPurchasePersistenceIntegrationTests.java`.
- [x] T006 [P] [US1] Add named/null Order API, monetary-field and owner-boundary contract assertions in `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/in/web/OrderQueryControllerTests.java` and `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/OwnedOrderQueryPersistenceIntegrationTests.java`.

### Implementation

- [x] T007 [US1] Add optional quote names to the durable regular-purchase line snapshot without changing browser-field fingerprints in `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/domain/model/RegularPurchaseLine.java`, `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/domain/model/RegularPurchaseRequest.java`, `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/adapter/out/persistence/jpa/mapper/RegularPurchasePersistenceMapper.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/regularpurchase/application/usecase/RegularPurchaseCheckoutService.java`.
- [x] T008 [US1] Add nullable Product/Variant names to Order line domain and JPA mapping, preserving replay and existing money fields, in `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/OrderLine.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/entity/OrderLineJpaEntity.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/OrderCreationJpaAdapter.java`.
- [x] T009 [US1] Add the backward-compatible nullable-column migration in `services/order-service/src/main/resources/db/changelog/changes/007-add-order-line-name-snapshots.sql` and register it in `services/order-service/src/main/resources/db/changelog/db.changelog-master.yaml`; do not backfill or drop pre-existing columns.
- [x] T010 [US1] Add an Order-owned optional name lookup port and adapter using the existing secured Product purchase-quote Feign transport in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/out/LookupOrderLineNamesPort.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/model/OrderLineNames.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/client/product/OrderLineNameLookupAdapter.java`; classify lookup failures without logging customer data or credentials.
- [x] T011 [US1] Wire best-effort names only into Flash Sale Order creation, preserving the original persisted snapshot on replay, in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/usecase/CreateOrderFromAcceptedPurchaseService.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/OrderCreationJpaAdapter.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderCreationConfiguration.java`; do not swallow persistence or saga errors.
- [x] T012 [US1] Return nullable names through the existing owner-only Order detail response in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/OwnedOrderQueryJpaAdapter.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/result/OrderItemResult.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/web/OrderWebMapper.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/web/response/OrderItemResponse.java`.
- [x] T013 [US1] Render stored product/variant names and an explicit neutral label plus secondary variant identifier when absent in `flash-sale frontend/QuickCart/app/orders/[id]/page.jsx`, with fallback coverage in `flash-sale frontend/QuickCart/tests/purchasePresentation.test.mjs`; never query the current catalog for historical Order names.
- [x] T014 [US1] Run `./mvnw.cmd -pl services/order-service -am verify` and record command, scope, result and any environment limitation in `specs/054-shopper-purchase-presentation/validation.md` before marking US1 complete.

**Checkpoint**: Both purchase paths produce stable, truthful Order labels, and US1 tests pass independently.

## Phase 3: User Story 2 — Understand purchase progress (P1)

**Goal**: Translate canonical Order, Payment and Reservation states to understandable shopper text without changing permitted actions.

**Independent test**: Each documented state has a reviewed label and guidance; an unknown state is neutral; payment buttons still follow backend status.

- [x] T015 [US2] Add focused label/action tests in `flash-sale frontend/QuickCart/tests/purchasePresentation.test.mjs` for Order, Payment and Reservation states, unknown fallback and unchanged payment eligibility.
- [x] T016 [US2] Implement a shared canonical-status-to-shopper-text helper in `flash-sale frontend/QuickCart/lib/purchasePresentation.mjs`; keep status values and action decisions separate from display labels.
- [x] T017 [US2] Apply the helper to `flash-sale frontend/QuickCart/app/my-orders/page.jsx`, `flash-sale frontend/QuickCart/app/orders/[id]/page.jsx`, `flash-sale frontend/QuickCart/app/payments/success/page.jsx`, and `flash-sale frontend/QuickCart/app/reservations/[id]/page.jsx` without altering request payloads or payment action guards.
- [x] T018 [US2] Run `node --test 'flash-sale frontend/QuickCart/tests/purchasePresentation.test.mjs'` and record result in `specs/054-shopper-purchase-presentation/validation.md`.

**Checkpoint**: Shopper progress screens are readable, and no unknown state falsely implies success.

## Phase 4: User Story 3 — See truthful offer pricing (P2)

**Goal**: Avoid arbitrary list-card prices and unsupported Flash Sale eligibility claims while retaining selected-variant pricing.

**Independent test**: Multi-variant cards say “From” with the minimum displayable catalog price; selected variant changes detail price; product detail makes no unverified sale claim and identifies manual campaign entry.

- [x] T019 [US3] Add price-selection and fallback tests in `flash-sale frontend/QuickCart/tests/purchasePresentation.test.mjs` for single, multiple, unavailable and malformed-price variants.
- [x] T020 [US3] Implement testable card-price selection in `flash-sale frontend/QuickCart/lib/purchasePresentation.mjs` and render accurate single/starting-price text in `flash-sale frontend/QuickCart/components/ProductCard.jsx`.
- [x] T021 [US3] Remove the universal eligibility badge, retain selected-variant catalog price, and label manual campaign-ID entry clearly in `flash-sale frontend/QuickCart/app/product/[id]/page.jsx`.
- [x] T022 [US3] Run `node --test 'flash-sale frontend/QuickCart/tests/purchasePresentation.test.mjs'` and `npm.cmd --prefix 'flash-sale frontend/QuickCart' run build`; record results in `specs/054-shopper-purchase-presentation/validation.md`.

**Checkpoint**: Pricing/eligibility copy is truthful in the tested fixtures.

## Phase 5: Cross-cutting validation and handoff

- [x] T023 [P] Update the Order detail response example, nullable-name behavior and existing security semantics in `docs/api/frontend-integration-guide.md` to match `specs/054-shopper-purchase-presentation/contracts/order-item-display-http.md`.
- [ ] T024 Review Order detail, Order list, Payment/Reservation progress, product cards and product detail in local desktop and mobile layouts per `specs/054-shopper-purchase-presentation/quickstart.md`; record observed scope and screenshots or limitations in `specs/054-shopper-purchase-presentation/validation.md`.
- [ ] T025 Run `./mvnw.cmd clean verify`, `npm.cmd --prefix 'flash-sale frontend/QuickCart' run build`, relevant Node tests, and `git diff --check`; record commands, exit results and any unrun gate in `specs/054-shopper-purchase-presentation/validation.md`.
- [x] T026 Audit Order migration rollback compatibility, no new Kafka/schema/topic changes, no exposed private route, and no change to stock/payment/idempotency behavior against `specs/054-shopper-purchase-presentation/plan.md`; record findings in `specs/054-shopper-purchase-presentation/validation.md`.

## Dependencies and execution order

- T001–T002 are the approval/contract gate. T003–T006 are independent test files and can start after approval.
- T007 precedes T008 for regular name propagation; T008 and T009 precede T012; T010 precedes T011. T011 relies on the existing replay/inbox behavior verified in T005.
- T012 precedes T013. T014 closes US1. T015 precedes T016–T017; T019 precedes T020–T021. US2 and US3 can proceed independently after the gate, but shared `purchasePresentation.mjs` edits must be coordinated serially.
- T023 may proceed with other late-stage tests once T012 is implemented. T024–T026 close the feature and do not authorize cloud rollout.

## Implementation discipline

Work on one task or one coherent group at a time and commit logical groups only after their required validation. A checked task records completion; `validation.md` supplies the evidence. Stop and revise the approved artifacts if implementation reveals an unapproved business, security, stock, consistency, or API behavior.
