# Tasks: Order-Owned Purchase Saga Completion

**Input**: Design documents from `specs/044-order-purchase-saga/`

**Prerequisites**: Approved `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, and
`quickstart.md`

**Tests**: Required. Core financial/stock/Saga behavior follows test-first ordering: add the mapped
test, observe the expected failure, implement the smallest coherent behavior, and record the passing
gate.

**Delivery rule**: G1–G7 run locally on `codex/order-purchase-saga`. No EKS image promotion occurs
until the aggregate local gate and full reactor are green. Cart and Notification remain deferred.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes independent files and has no incomplete dependency.
- **[Story]**: Maps to a user story in `spec.md`.
- Checked tasks require validation evidence; a checkbox alone is not completion evidence.

## Phase 1: Setup — G1 Schema-First Contract Surface

**Purpose**: Approve and generate every wire contract before producer/consumer implementation.

- [X] T001 [P] Add `ConfirmPurchaseReservationV1.avsc` and `ReleasePurchaseReservationV1.avsc` under `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.commands.v1/` exactly as documented in `specs/044-order-purchase-saga/contracts/reservation-commands-kafka.md`
- [X] T002 [P] Add `PurchaseReservationConfirmedV1.avsc` and `PurchaseReservationReleasedV1.avsc` under `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/` without modifying `PurchaseAcceptedV1.avsc`
- [X] T003 [P] Add `OrderConfirmedV1.avsc`, `OrderCancelledV1.avsc`, `OrderExpiredV1.avsc`, and `OrderPaymentReviewRequiredV1.avsc` under `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.order.events.v1/` without modifying `OrderCreatedV1.avsc`
- [X] T004 [P] Add generated-record, exact-shape, logical-type, key-rule, and compatibility tests for reservation commands/results under `contracts/kafka-avro-contracts/src/test/java/com/philia/flashsale/contract/purchase/`
- [X] T005 [P] Add generated-record, exact-shape, logical-type, reason, correction-semantics, and compatibility tests for terminal/review-required Order events under `contracts/kafka-avro-contracts/src/test/java/com/philia/flashsale/contract/order/event/`
- [X] T006 Add `flashsale.purchase.commands.v1` and the three Feature 044 DLTs to `infra/docker/kafka/init-purchase-saga-topics.sh`, preserving idempotent create/describe/expand-only behavior
- [X] T007 Add controlled BACKWARD_TRANSITIVE registration for the eight main record subjects and six matching DLT subject bindings in `infra/docker/schema-registry/register-purchase-saga-schemas.ps1`
- [X] T008 [P] Update candidate-to-approved status, key transition, message counts, and DLT ownership in `docs/kafka/04-topic-message-catalog.md` and `docs/kafka/02-avro-data-contract-governance.md`
- [X] T009 Create the bounded `Contracts` selector shell in `infra/docker/smoke/feature-044-purchase-saga.ps1`, run `.\mvnw.cmd -pl contracts/kafka-avro-contracts -am verify`, provision local topics/subjects, record `FEATURE_044_CONTRACTS=PASS`, and add sanitized command/results to `specs/044-order-purchase-saga/validation.md`

**Checkpoint**: All new records compile and register; no application code references an unregistered schema.

---

## Phase 2: Foundational — Shared Durable and Publication Capabilities

**Purpose**: Establish migration/outbox/configuration primitives required by every Saga story.

**⚠️ CRITICAL**: No user-story production code begins until these tasks pass migration and
architecture gates.

- [ ] T010 [P] Add failing Order Liquibase migration/constraint tests for `purchase_sagas`, `purchase_saga_inbox`, generalized Order statuses, and generalized outbox constraints in `services/order-service/src/test/java/com/philia/flashsale/order/integration/OrderSchemaMigrationIntegrationTests.java`
- [ ] T011 [P] Add failing Flash Sale Liquibase migration/constraint tests for reservation statuses, reconciliation timestamps, command inbox, outbox causation identity/scoped uniqueness, and event types in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/integration/FlashSaleSchemaMigrationIntegrationTests.java`
- [ ] T012 Implement the Order schema changes in `services/order-service/src/main/resources/db/changelog/changes/002-add-purchase-saga.sql` and include them from `services/order-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- [ ] T013 Implement the Flash Sale schema changes, nullable outbox `causation_id`, legacy accepted-event uniqueness, and one partial unique `causation_id` across confirmed/released command outcomes in `services/flashsale-service/src/main/resources/db/changelog/changes/002-add-reservation-finalization.sql` and include them from `services/flashsale-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- [ ] T014 Add failing generalized Order outbox dispatch/lease/retry tests under `services/order-service/src/test/java/com/philia/flashsale/order/outbox/`, then refactor the application ports and event-type dispatcher under `services/order-service/src/main/java/com/philia/flashsale/order/outbox/` so all eight approved messages share existing semantics without exposing Avro or Kafka to the application layer
- [ ] T015 Add failing Flash Sale accepted/current-state-outcome command replay and outbox causation tests under `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/outbox/`, then refactor application ports and event-type dispatch under `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/outbox/` so accepted/confirmed/released records share stable retry and one result per command
- [ ] T016 [P] Extend ArchUnit tests under `services/order-service/src/test/java/com/philia/flashsale/order/architecture/` and `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/architecture/` to enforce `adapter -> application -> domain` and prohibit Avro/JPA/Redis/Spring leakage

**Checkpoint**: Both migrations and architecture tests pass; outbox capabilities can dispatch by
approved event type but no new Saga producer is active.

---

## Phase 3: User Story 1 — Order Durably Requests Payment (Priority: P1) — G2 MVP

**Goal**: One accepted reservation creates one durable Order/Saga and one semantic Payment request.

**Independent Test**: Repeatedly deliver the same `PurchaseAcceptedV1`; verify one Order, one Saga,
one Payment command identity, exact deadline, and one Payment-owned acceptance.

### Tests for User Story 1

- [ ] T017 [P] [US1] Add failing deadline, creation, invariant, and replay transition tests for `PurchaseSaga` under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/domain/`
- [ ] T018 [P] [US1] Add failing atomic creation/rollback/100-duplicate concurrency tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/integration/PurchaseSagaStartIntegrationTests.java`
- [ ] T019 [P] [US1] Add failing PaymentRequested mapping, `orderId` key, headers, Registry, retry, and stable-ID tests under `services/order-service/src/test/java/com/philia/flashsale/order/outbox/adapter/out/messaging/kafka/PaymentRequestedPublisherTests.java`

### Implementation for User Story 1

- [ ] T020 [P] [US1] Implement `PurchaseSaga`, `PurchaseSagaStatus`, deadline policy, and domain failures under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/domain/`
- [ ] T021 [P] [US1] Implement framework-free start commands/results and input/output ports under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/application/`
- [ ] T022 [US1] Extend accepted-purchase creation in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/usecase/CreateOrderFromAcceptedPurchaseService.java` and its atomic persistence capability so Order, line, accepted inbox, Saga, OrderCreated outbox, and PaymentRequested outbox commit together
- [ ] T023 [US1] Implement Saga JPA entities/repositories/mapping and the atomic start adapter under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/adapter/out/persistence/jpa/`
- [ ] T024 [US1] Implement PaymentRequested Avro mapping/publication under `services/order-service/src/main/java/com/philia/flashsale/order/outbox/adapter/out/messaging/kafka/` using the existing accepted contract unchanged
- [ ] T025 [US1] Add Saga/outbox topic/runtime properties and wiring in `services/order-service/src/main/java/com/philia/flashsale/order/configuration/` and `services/order-service/src/main/resources/application.yml`
- [ ] T026 [US1] Implement the `Start` selector and bounded real-interface assertions in `infra/docker/smoke/feature-044-purchase-saga.ps1`, then record Order module verify and `FEATURE_044_START=PASS` in `specs/044-order-purchase-saga/validation.md`

**Checkpoint**: The previous Phase 24 blocker is removed locally: a real accepted purchase causes
Payment Service to own one durable Payment request.

---

## Phase 4: User Story 2 — Successful Payment Confirms Purchase (Priority: P1) — G3/G4/G5 Paid Slice

**Goal**: Verified Payment success confirms the reservation and then confirms the Order exactly once.

**Independent Test**: Deliver valid Payment success, observe one confirm command/result, and verify
reservation `CONFIRMED`, Saga `COMPLETED`, Order `CONFIRMED`, and one terminal event.

### Tests for User Story 2

- [ ] T027 [P] [US2] Add failing PaymentSucceeded Avro validation, identity, amount/currency, stale-version, duplicate, DLT, and trace tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/adapter/in/messaging/kafka/PaymentSucceededConsumerTests.java`
- [ ] T028 [P] [US2] Add failing `PAYMENT_PENDING -> CONFIRMING_RESERVATION` atomic Saga/inbox/confirm-outbox tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/integration/PaymentSuccessTransitionIntegrationTests.java`
- [ ] T029 [P] [US2] Add failing reservation confirm domain, command replay/conflict, expiry race, and PostgreSQL transaction tests under `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/`
- [ ] T030 [P] [US2] Add failing confirm/reconciliation Lua exact-once and Redis outage/recovery tests under `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/adapter/out/redis/`
- [ ] T031 [P] [US2] Add failing PurchaseReservationConfirmed consumer and Order terminal-outbox tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/integration/PurchaseConfirmationIntegrationTests.java`, plus authenticated public `CONFIRMED` response coverage in `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/in/web/OrderQueryControllerTests.java`

### Implementation for User Story 2

- [ ] T032 [US2] Implement PaymentSucceeded application mapping/use case and atomic inbox/Saga/confirm-command persistence under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/`
- [ ] T033 [US2] Implement strict PaymentSucceeded Kafka adapter, listener retry/DLT configuration, and trace extraction under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/adapter/in/messaging/kafka/` and `services/order-service/src/main/java/com/philia/flashsale/order/configuration/`
- [ ] T034 [US2] Implement Flash Sale confirm command mapping/use case and atomic reservation/inbox/confirmed-outbox adapter under `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/`
- [ ] T035 [US2] Implement `confirm-reservation.lua`, Redis adapter, and reconciliation worker under `services/flashsale-service/src/main/resources/redis/reservation/` and `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/out/redis/`
- [ ] T036 [US2] Implement PurchaseReservationConfirmed publication/consumption and atomic `Order.CONFIRMED + Saga.COMPLETED + OrderConfirmed` persistence across the matching Order/Flash Sale messaging adapters and Order Saga adapter
- [ ] T037 [US2] Implement `Paid` in `infra/docker/smoke/feature-044-purchase-saga.ps1` and record affected module verify plus `FEATURE_044_PAID=PASS` in `specs/044-order-purchase-saga/validation.md`

**Checkpoint**: Paid happy path is complete locally without Cart, Notification, direct SQL fixture
insertion, or cloud deployment.

---

## Phase 5: User Story 3 — Terminal Payment Failure Releases Stock (Priority: P1) — G3/G4/G5 Failure Slice

**Goal**: Each approved terminal Payment reason releases the reservation and closes Order with the
owner-approved state.

**Independent Test**: Exercise all three failure reasons and verify exact release idempotency plus
`EXPIRED`/`CANCELLED` mapping and terminal Order events.

### Tests for User Story 3

- [ ] T038 [P] [US3] Add failing PaymentFailed reason validation, status mapping, duplicate/version, and DLT tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/adapter/in/messaging/kafka/PaymentFailedConsumerTests.java`
- [ ] T039 [P] [US3] Add failing atomic release-command creation and desired-terminal-state tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/integration/PaymentFailureTransitionIntegrationTests.java`
- [ ] T040 [P] [US3] Add failing Flash Sale release, already-expired, competing-confirm, command replay, and exact-once quota tests under `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/`
- [ ] T041 [P] [US3] Add failing PurchaseReservationReleased consumer, OrderCancelled/OrderExpired outbox, and authenticated public Order-status response tests in `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/integration/PurchaseReleaseIntegrationTests.java` and `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/in/web/OrderQueryControllerTests.java`

### Implementation for User Story 3

- [ ] T042 [US3] Implement PaymentFailed mapping/policy/use case and atomic Saga inbox/release-command transition under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/`
- [ ] T043 [US3] Implement Flash Sale release command consumer/application/persistence and `PurchaseReservationReleasedV1` outbox mapping under `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/` and `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/outbox/`
- [ ] T044 [US3] Implement `release-reservation.lua`, exact-once stock/user-quota restoration, expiry-index removal, and durable reconciliation completion under `services/flashsale-service/src/main/resources/redis/reservation/` and the reservation Redis adapter package
- [ ] T045 [US3] Implement released-result handling and atomic Order `CANCELLED`/`EXPIRED`, Saga `COMPENSATED`, and terminal event outbox transitions under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/` and `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/`
- [ ] T046 [US3] Implement all reason branches in the `Failed` scenario of `infra/docker/smoke/feature-044-purchase-saga.ps1` and record `FEATURE_044_FAILED=PASS` in `specs/044-order-purchase-saga/validation.md`

**Checkpoint**: No unpaid terminal workflow strands a durable reservation or emits a terminal Order
event before Flash Sale acknowledgement.

---

## Phase 6: User Story 4 — Replay, Reordering, and Late Success Safety (Priority: P1) — G5 Recovery

**Goal**: At-least-once duplicates, stale/reordered outcomes, and late verified success converge
without double effects or automatic refund.

**Independent Test**: Replay every message, inject lower/higher participant versions, restart each
consumer, and drive a paid-but-unconfirmable workflow to durable manual review.

### Tests for User Story 4

- [ ] T047 [P] [US4] Add failing canonical fingerprint and same-ID/different-payload conflict tests for Order Saga and Flash Sale command inboxes under both services' `src/test/java/.../application/` packages
- [ ] T048 [P] [US4] Add failing 100-delivery concurrency and process-crash window tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/integration/PurchaseSagaConcurrencyIntegrationTests.java`
- [ ] T049 [P] [US4] Add failing higher-version-success-after-failure, release-in-flight, compensated-late-success, active-command same-version current-state result, `OrderPaymentReviewRequiredV1`, and manual-review tests under `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/domain/PurchaseSagaLateSuccessTests.java` and `services/order-service/src/test/java/com/philia/flashsale/order/purchasesaga/integration/LatePaymentCorrectionIntegrationTests.java`
- [ ] T050 [P] [US4] Add failing confirmed/released/expired Redis reconciliation backlog and restart tests under `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/ReservationReconciliationIntegrationTests.java`

### Implementation for User Story 4

- [ ] T051 [US4] Implement canonical fingerprints, per-command stable result identities, active-command causation matching for unchanged current-state outcomes, participant-version guards, and non-retryable conflicts under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/` and `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/`
- [ ] T052 [US4] Implement success-dominant forward recovery and atomic `Order.PENDING_PAYMENT + Saga.MANUAL_REVIEW + OrderPaymentReviewRequired` correction without automatic refund under `services/order-service/src/main/java/com/philia/flashsale/order/purchasesaga/` and the generalized Order outbox adapter
- [ ] T053 [US4] Add bounded reconciliation scheduling, leases/metrics, and safe restart behavior in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/in/scheduling/` and its application ports/use cases
- [ ] T054 [US4] Implement `Replay` and `LateSuccess` in `infra/docker/smoke/feature-044-purchase-saga.ps1`, assert one review-required correction fact for terminal-to-manual-review recovery, and record `FEATURE_044_REPLAY=PASS` plus `FEATURE_044_LATE_SUCCESS=PASS` in `specs/044-order-purchase-saga/validation.md`

**Checkpoint**: Every physical duplicate remains one semantic effect; late paid truth ends confirmed
or durably visible for manual review.

---

## Phase 7: User Story 5 — Local-First Release Evidence (Priority: P1) — G6/G7/G8

**Goal**: Prove all Saga slices locally, then promote affected images once and complete Phase 24.

**Independent Test**: Run `-Scenario All` from documented prerequisites, obtain sanitized PASS
labels, then verify one immutable cloud release and final Stripe/Order result.

### Tests for User Story 5

- [ ] T055 [P] [US5] Add PSScriptAnalyzer/parser, timeout, redaction, cleanup, and scenario-dispatch tests for `infra/docker/smoke/feature-044-purchase-saga.ps1` under `infra/scripts/tests/`
- [ ] T056 [P] [US5] Add observability/readiness tests for Saga states, DLT/outbox lag, manual review, and Redis reconciliation under both affected services' `src/test/java/.../observability/` packages
- [ ] T057 [P] [US5] Add cloud manifest/config contract tests for all new runtime flags and internal topic names under `infra/k8s/overlays/cloud/` and the existing infrastructure test location

### Implementation for User Story 5

- [ ] T058 [US5] Complete `Contracts|Start|Paid|Failed|Replay|LateSuccess|All` orchestration, memory-only credentials, bounded native processes, safe diagnostics, and cleanup in `infra/docker/smoke/feature-044-purchase-saga.ps1`
- [ ] T059 [US5] Add local Compose runtime flags/topic wiring for Order and Flash Sale in `infra/docker/compose.yml`, `infra/docker/compose.dev.yml`, and `infra/docker/.env.example` without reading or modifying ignored `infra/docker/.env`
- [ ] T060 [US5] Add declarative metrics/readiness wiring and low-cardinality observations in both services' `configuration/`, `observability/`, and `application.yml`; update alert rules under `infra/monitoring/prometheus/rules/`
- [ ] T061 [US5] Extend `infra/scripts/gitops/phase20-kafka-contracts.ps1` to validate/provision the Feature 044 topic, DLTs, and subjects idempotently without delete or auto-creation
- [ ] T062 [US5] Extend cloud ConfigMaps under `infra/k8s/overlays/cloud/config/order-service-runtime-config.yaml` and `infra/k8s/overlays/cloud/config/flash-sale-service-runtime-config.yaml` with reviewed Saga consumer/producer/reconciliation flags
- [ ] T063 [US5] Extend `infra/scripts/gitops/phase24-stripe-cloud.ps1` and its delegated runner so the real Order-owned Payment request drives Checkout/webhook/replay/reservation/final-Order assertions without fabricating Payment state
- [ ] T064 [US5] Run `feature-044-purchase-saga.ps1 -Scenario All`, affected module verifies, and full monorepo verify; record all PASS labels and exit statuses in `specs/044-order-purchase-saga/validation.md`

**Checkpoint**: `FEATURE_044_LOCAL_GATE=PASS`; only now may the cloud tasks below run.

---

## Phase 8: Polish, Repository Convergence, and Single Cloud Release

**Purpose**: Final quality gates, one affected-service promotion, Phase 24 proof, and rollback evidence.

- [ ] T065 [P] Update Saga lifecycle, topic counts/status, troubleshooting, and deferred Cart/Notification notes in `docs/architecture/flash-sale-end-to-end-flow.md`, `docs/architecture/saga-messaging-reliability.md`, and `docs/kafka/04-topic-message-catalog.md`
- [ ] T066 [P] Audit comments, error classification, log redaction, trace propagation, and package placement across changed Order/Flash Sale Java files; run `git diff --check`
- [ ] T067 Run `mvnw.cmd -pl contracts/kafka-avro-contracts,services/order-service,services/flashsale-service -am verify` and record test counts/results in `specs/044-order-purchase-saga/validation.md`
- [ ] T068 Run `mvnw.cmd clean verify`, PowerShell syntax/tests, and `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`; record results in `specs/044-order-purchase-saga/validation.md`
- [ ] T069 After implementation PR merge and local PASS, run Phase 20 validation/apply for the new EKS topic/DLTs/subjects and record sanitized inventory in `specs/044-order-purchase-saga/validation.md`
- [ ] T070 Trigger one selective GitHub Actions delivery for the affected services, review/merge one immutable image-promotion PR, and record workflow/PR/SHA/ECR tags in `specs/044-order-purchase-saga/validation.md`
- [ ] T071 Verify Argo `Synced/Healthy`, affected Deployment images/rollouts, Phase 21 release gate, and Phase 24 HTTPS/Stripe Checkout/webhook/replay/final-Order PASS; record sanitized live evidence in `specs/044-order-purchase-saga/validation.md`
- [ ] T072 Rehearse rollback by disabling Order command production first and restoring prior immutable tags while preserving topics, schemas, PVCs, Saga/inbox/outbox/payment/reservation rows; record recovery evidence in `specs/044-order-purchase-saga/validation.md`

---

## Dependencies and Execution Order

### Group dependency graph

```text
G1 contracts
  -> Foundation migrations/outbox
     -> US1 / G2 Saga start
        -> US2 + US3 / G3-G5 result and participant slices
           -> US4 / replay and late-success recovery
              -> US5 / aggregate local gate
                 -> repository convergence
                    -> one cloud image promotion
                       -> Phase 24 + rollback evidence
```

### User story dependencies

- **US1** depends on G1/Foundation and is the MVP that unblocks a real Payment request.
- **US2** depends on US1 plus Payment success consumption and Flash Sale confirm capability.
- **US3** depends on US1 plus Payment failure consumption and Flash Sale release capability; its
  tests can be developed alongside US2 after shared adapters/migrations stabilize.
- **US4** depends on US2/US3 state transitions because it tests their replays and contradictions.
- **US5** depends on all behavioral stories and is the only path to cloud promotion.

### Parallel opportunities

- T001–T005 and T008 can run in parallel by contract/document family.
- T010/T011 and later T012/T013 can run in parallel by service.
- Within US2, Order Payment-success tests and Flash Sale confirm tests are parallel until contract
  integration.
- Within US3, Order failure-policy tests and Flash Sale release/Lua tests are parallel.
- Observability, script tests, and cloud manifest tests T055–T057 are parallel after behavior names
  stabilize.
- Cloud mutation tasks T069–T072 are deliberately sequential.

## Parallel Examples

### US2 paid slice

```text
Task T027: Order PaymentSucceeded adapter tests
Task T029: Flash Sale confirm domain/persistence tests
Task T030: Redis confirm/reconciliation tests
```

Join them at T036 for the complete confirmed outcome.

### US3 failure slice

```text
Task T038: Order PaymentFailed mapping/reason tests
Task T040: Flash Sale release/expiry/concurrency tests
Task T041: Order released-result terminalization tests
```

Join them at T046 for the `Failed` local scenario.

## Implementation Strategy

### MVP first

1. Complete G1 contracts and Foundation.
2. Complete US1/G2.
3. Stop and run the `Start` gate until it proves one real Payment acceptance.
4. Do not deploy; proceed locally to paid/failure slices.

### Local incremental delivery

1. G1 → `Contracts` PASS.
2. G2 → `Start` PASS.
3. G3/G4/G5 paid slice → `Paid` PASS.
4. G3/G4/G5 failure slice → `Failed` PASS.
5. Recovery → `Replay` and `LateSuccess` PASS.
6. Aggregate → `All`, affected modules, and full reactor PASS.

### Cloud delivery

Only after all local steps:

1. provision EKS contracts;
2. merge the implementation PR;
3. build/push affected images once;
4. merge one image-promotion PR;
5. verify Argo/Deployments and run Phase 24;
6. rehearse rollback.

## Notes

- Commit after each coherent G group, but keep the feature on one branch/PR until all local gates
  pass unless the owner explicitly approves another integration strategy.
- Do not mark T069–T072 complete from local evidence.
- Do not create Cart/Notification code, topics, Deployments, or completion claims in Feature 044.
- Any discovered change to financial, stock, timeout, refund, or public-status semantics stops the
  affected task and returns to spec/plan approval.
