# Validation ledger: Order-Owned Purchase Saga Completion

Evidence is appended by coherent task group. Secret values, authorization headers, provider
payloads, webhook signatures, JWTs, and Checkout URLs must never be recorded here.

## G1 — Schema-first contract surface — 2026-08-24

| Check | Result | Evidence / boundary |
|---|---|---|
| Approved artifacts | PASS | `spec.md` and `plan.md` record owner approval; `tasks.md` defines the dependency-ordered implementation groups. |
| Avro generated records and contract tests | PASS | `.\mvnw.cmd --batch-mode --no-transfer-progress -pl contracts/kafka-avro-contracts -am verify`: 21 tests, 0 failures, 0 errors, 0 skipped; reactor `BUILD SUCCESS`. |
| Local topic provisioning | PASS | `init-purchase-saga-topics.sh` verified `flashsale.purchase.commands.v1` plus three approved consumer DLTs at 3 partitions and replication factor 1. Repeated execution remained expand-only and non-destructive. |
| Schema Registry provisioning | PASS | Eight main topic/record subjects plus six DLT topic/record bindings registered and re-verified at `BACKWARD_TRANSITIVE`; repeated execution retained version 1 and exact latest Git schema identity. |
| Bounded G1 runner | PASS | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-044-purchase-saga.ps1 -Scenario Contracts` exited 0 and emitted `FEATURE_044_CONTRACTS=PASS`. |
| Kafka Compose readiness | PASS | Broker probe measured 13.43 seconds on Docker Desktop; Kafka-only health timeout was raised from 5 to 20 seconds. Broker and Schema Registry became healthy without deleting volumes or topic data. |
| Contract governance docs | PASS | Topic catalog and Avro governance document the approved records, key rules, 23-event/7-command totals, DLT ownership, 14 Registry subject bindings, and late-success correction path. |

G1 performs no application image build, ECR push, EKS rollout, or cloud mutation. Those gates remain
deferred until the full local implementation is green as required by the approved plan.

## G2 — Durable schema and outbox capabilities — 2026-08-24

| Check | Result | Evidence / boundary |
|---|---|---|
| Order migration and constraints | PASS | `OrderSchemaMigrationIntegrationTests`: 7 tests, 0 failures, 0 errors; Liquibase applied two changesets and rollback of the Saga changeset restored the G1 schema. Verified Saga identity/deadline/status, inbox source-position deduplication, generalized Order statuses, and event/aggregate/key constraints. |
| Flash Sale migration and constraints | PASS | `FlashSaleSchemaMigrationIntegrationTests`: 3 tests, 0 failures, 0 errors; Liquibase applied nine changesets. Verified reservation finalization columns/statuses, `reservation_command_inbox`, typed outcome events, legacy accepted uniqueness, and partial causation uniqueness. |
| Generalized Order outbox dispatch | PASS | `OrderOutboxPublicationServiceTests`, `OrderOutboxEventTypeDispatcherTests`, and `OrderArchitectureTests`: 8 tests, 0 failures, 0 errors. Lease, retry, sanitized error, stable identity, and event-type routing remain application-port concerns; only the existing OrderCreated Kafka adapter is registered. |
| Generalized Flash Sale outbox dispatch and causation model | PASS | `FlashSaleOutboxPublisherTests` and `FlashSaleArchitectureTests`: 7 tests, 0 failures, 0 errors. Accepted publication retains existing behavior, event dispatcher rejects unregistered outcome types, and `causation_id` is carried through the durable outbox model/adapter. |
| G2 boundary | PASS | No Payment/Saga producer, consumer, Redis command, application image build, ECR push, EKS rollout, or cloud mutation was performed. New event publishers remain disabled until the user-story groups wire them. |

## G3 — Order-owned Saga start and Payment request — 2026-08-25

| Check | Result | Evidence / boundary |
|---|---|---|
| PurchaseSaga domain invariants | PASS | `PurchaseSagaDomainTests`: 3 tests, 0 failures, 0 errors, 0 skipped. Verified the 30-second reservation safety margin, future deadline requirement, Order/Saga identity separation, and initial `PAYMENT_PENDING` state. |
| Atomic persistence, rollback, replay | PASS | `AcceptedPurchasePersistenceIntegrationTests`: 5 tests, 0 failures, 0 errors, 0 skipped. Testcontainers PostgreSQL verified one Order, one line, one accepted inbox row, one Saga row, one `OrderCreated` outbox row, and one `PaymentRequested` outbox row in one transaction; replay and rollback remain idempotent. |
| Duplicate/concurrent accepted delivery | PASS | `AcceptedPurchaseConcurrencyIntegrationTests`: 2 tests, 0 failures, 0 errors, 0 skipped. 100 equivalent deliveries and 100 contradictory deliveries converge to one Order/Saga and two durable outbox rows; the bounded run used an extended local datasource connection timeout only. |
| PaymentRequested contract mapping/publication | PASS | `PaymentRequestedPublisherTests`: 2 tests, 0 failures, 0 errors, 0 skipped. Mapping validates the DB envelope, keeps `PaymentRequestedV1` wire shape unchanged, publishes to `flashsale.payment.commands.v1` with `orderId` key, and propagates trace headers. |
| Order module Start gate | PASS | `pwsh -NoLogo -NoProfile -File .\\infra\\docker\\smoke\\feature-044-purchase-saga.ps1 -Scenario Start -TimeoutSeconds 1500`: Order module `verify` completed with 98 tests, 0 failures, 0 errors, 8 intentional skips; Maven reactor `BUILD SUCCESS`; runner emitted `FEATURE_044_START=PASS`. |
| Static hygiene | PASS | PowerShell parser check passed and `git diff --check` found no whitespace errors. Git emitted only the existing user-config ignore permission/LF-to-CRLF warnings. |

G3 establishes the Order-owned durable start boundary. An accepted reservation now atomically creates
the Order, `PurchaseSaga(PAYMENT_PENDING)`, accepted inbox, `OrderCreated` outbox, and
`PaymentRequested` outbox; the Order outbox relay can publish the latter through Schema Registry to
`flashsale.payment.commands.v1`. Payment Service already owns the corresponding consumer and inbox
boundary, but the paid, failed, replay, late-success, cloud, and image-promotion slices remain
deferred to later task groups. No Payment/Flash Sale result transition, ECR image build, EKS rollout,
cloud mutation, or ignored `.env` change was performed by G3.

## G4a — Order PaymentSucceeded boundary — 2026-08-25

| Check | Result | Evidence / boundary |
|---|---|---|
| PaymentSucceeded domain transition | PASS | `PurchaseSagaDomainTests`: 4 tests, 0 failures, 0 errors. A verified payment records payment identity/version, increments Saga version, and opens `CONFIRMING_RESERVATION` with a deterministic confirm-command identity. |
| PaymentSucceeded Avro boundary | PASS | `PaymentSucceededAvroMapperTests`: 2 tests, 0 failures, 0 errors. Envelope, producer/type/version, amount/currency, order key, and stable fingerprint checks pass. |
| Order Kafka property binding | PASS | `OrderPropertiesTests`: 3 tests, 0 failures, 0 errors. Payment result topic/group/DLT and purchase-command topic defaults bind successfully. |
| Order module compile | PASS | `mvnw.cmd -pl services/order-service -am -DskipTests compile`: reactor `BUILD SUCCESS`. |

This G4a slice adds the Order-side PaymentSucceeded consumer boundary, durable Saga inbox
representation, atomic confirm-command outbox adapter, and Schema Registry publisher wiring. The
Flash Sale confirm participant and terminal Order finalization are intentionally not marked complete
yet; they are the next dependent slices of the approved Paid path. No ECR/EKS/cloud mutation or
ignored `.env` change was performed.

## G5 — Flash Sale confirm participant — 2026-08-25

| Check | Result | Evidence / boundary |
|---|---|---|
| Confirm command boundary | PASS | `ConfirmReservationAvroMapperTests`: 2 tests, 0 failures; validates topic, key, envelope, Saga identity, producer, version, and SHA-256 fingerprint before entering the use case. |
| Durable confirmation | PASS | `ReservationConfirmationPersistenceTests`: 3 tests, 0 failures; PostgreSQL adapter locks the reservation, deduplicates the command inbox, transitions only `RESERVED` to `CONFIRMED`, and writes the confirmed outbox intent in the same transaction. |
| Confirmed outcome publication | PASS | Confirmed outbox mapping/publisher routes `PurchaseReservationConfirmedV1` to the approved purchase-events topic with `orderId` key, causation ID, and trace headers. Existing accepted publication remains registered. |
| Redis confirmation | PASS | `confirm-reservation.lua` is idempotent: `RESERVED` becomes `CONFIRMED`, the expiry index entry is removed, and replay returns `ALREADY_CONFIRMED`; Redis failures bubble before Kafka acknowledgement. |
| Schema/JPA compatibility | PASS | Added Liquibase changeset `003-normalize-reservation-inbox-fingerprint.sql` to convert the previously committed `CHAR(64)` fingerprint column to `VARCHAR(64)` expected by JPA. |
| Flash Sale module verify | PASS | `\.\mvnw.cmd -pl services/flashsale-service -am verify`: 107 tests, 0 failures, 0 errors, 1 intentional skip; reactor `BUILD SUCCESS`. |
| Targeted persistence regression | PASS | Outbox concurrency, durable acceptance persistence, and owned-reservation integration tests: 8 tests, 0 failures, 0 errors. |
| Contract boundary | PASS | Consumer DLT uses the approved `flashsale.flash-sale.purchase-command.dlt.v1`; no new topic/schema provisioning, ECR push, EKS rollout, cloud mutation, or ignored `.env` change was performed. |

G5 closes the Flash Sale confirmation participant only. Order terminal finalization, Payment failure
release, late-success/reordering convergence, and the dedicated Redis reconciliation worker remain
deferred to the following task groups.

## G6 — Order terminal finalization — 2026-08-25

| Check | Result | Evidence / boundary |
|---|---|---|
| PurchaseReservationConfirmed contract boundary | PASS | `PurchaseReservationConfirmedAvroMapperTests`: 3 tests, 0 failures; validates topic/key, producer/type, aggregate/version, Saga identity, and SHA-256 replay fingerprint before entering the use case. |
| OrderConfirmed publication boundary | PASS | `OrderConfirmedAvroMapperTests`: 2 tests, 0 failures; maps the durable outbox envelope to `flashsale.order.events.v1` with `orderId` key and the approved terminal payload. |
| Domain terminal transitions | PASS | `PurchaseSagaDomainTests` and `OrderDomainTests`: confirmation transition tests pass; Saga moves `CONFIRMING_RESERVATION → COMPLETED`, while Order moves `PENDING_PAYMENT → CONFIRMED` without losing its snapshot. |
| Atomic terminalization and replay | PASS | `PurchaseReservationConfirmationPersistenceIntegrationTests`: 1 test, 0 failures; Testcontainers PostgreSQL verified `Order.CONFIRMED`, `PurchaseSaga.COMPLETED`, one Saga inbox row, one `OrderConfirmed` outbox row, and same-event replay as a no-op. |
| Order consumer wiring/regression | PASS WITH INFRA NOTE | Targeted Spring contexts: 5/5 pass; `AcceptedPurchaseConcurrencyIntegrationTests`: 2/2 pass on rerun. One full-suite attempt ran 108 tests with 2 transient Testcontainers JDBC connection errors; rerunning that class passed 2/2. |
| Static hygiene | PASS | `git diff --check` passed. No ECR push, EKS rollout, cloud mutation, or ignored `.env` change was performed. |

G6 closes the successful paid-path terminalization boundary: an authenticated
`PurchaseReservationConfirmedV1` result is deduplicated by the Order Saga inbox and atomically
confirms the Order, completes the Saga, and queues `OrderConfirmedV1` for the outbox relay. Payment
failure/release, replay/reordering recovery, and cloud promotion remain deferred.

## G6/T037 — Local Paid gate — 2026-08-25

| Check | Result | Evidence / boundary |
|---|---|---|
| Paid runner selector | PASS | `pwsh -NoLogo -NoProfile -File .\\infra\\docker\\smoke\\feature-044-purchase-saga.ps1 -Scenario Paid -TimeoutSeconds 1800` runs the affected Order and Flash Sale module verification in one Maven reactor and emits `FEATURE_044_PAID=PASS`. |
| Affected module verification | PASS | Order + Flash Sale `verify`: 216 tests, 0 failures, 0 errors, 9 intentional skips; reactor `BUILD SUCCESS`. |
| Paid-path behavior covered | PASS | Order PaymentSucceeded/confirmation/outbox tests and Flash Sale confirm/inbox/Redis/outcome tests prove the approved `PaymentSucceeded → ConfirmPurchaseReservation → PurchaseReservationConfirmed → OrderConfirmed` boundary. |
| Safety boundary | PASS | The runner invokes service-owned tests only; it does not write another service's database, print secrets, mutate `.env`, build/push images, or change cloud state. |

T037 closes the local paid checkpoint. Failure/release, replay/reordering, late-success recovery,
and aggregate `All` validation remain deferred to their approved task groups.

## G7 — Terminal Payment failure and reservation release — 2026-08-25

| Check | Result | Evidence / boundary |
|---|---|---|
| PaymentFailed contract boundary | PASS | `PaymentFailedConsumerTests`: 4 tests, 0 failures, 0 errors. The three approved terminal reasons map to the owner-approved cancellation/expiry policy; duplicate/version/conflict and non-retryable record validation remain bounded at the Kafka adapter. |
| Atomic failure transition | PASS | `PaymentFailureTransitionIntegrationTests`: 2 tests, 0 failures, 0 errors. Testcontainers PostgreSQL verified PaymentFailed inbox deduplication, Saga `RELEASING_RESERVATION`, one release command, replay no-op, and deadline-driven `EXPIRED` terminalization. |
| Reservation release persistence | PASS | `ReservationReleasePersistenceTests`: 4 tests, 0 failures, 0 errors. PostgreSQL adapter behavior verifies reserved release, already-expired mapping, command replay, and confirmed-reservation protection. |
| Redis exact-once release | PASS | `ReservationReleaseRedisIntegrationTests`: 1 test, 0 failures, 0 errors. Real Redis 7 executes `release-reservation.lua`; stock/user quota is restored once, expiry index is removed, and replay reports the existing release result. |
| Full failure smoke | PASS | `pwsh -NoLogo -NoProfile -File .\\infra\\docker\\smoke\\feature-044-purchase-saga.ps1 -Scenario Failed -TimeoutSeconds 1800` exited 0 and emitted `FEATURE_044_FAILED=PASS`. Affected reactor verification completed with Flash Sale 116 tests plus Order 120 tests: 236 tests, 0 failures, 0 errors, 9 intentional skips; `BUILD SUCCESS`. |
| Failure-flow boundary | PASS | Payment failure is persisted before the release command; Flash Sale acknowledges release through `PurchaseReservationReleasedV1`; Order then atomically terminalizes to `CANCELLED` or `EXPIRED`, marks Saga `COMPENSATED`, and queues the matching terminal event. No ECR push, EKS rollout, cloud mutation, or ignored `.env` change was performed. |
| Static hygiene | PASS | `git diff --check` passed. Existing Git user-config permission and line-ending warnings do not represent source/test failures. |

G7 closes T038–T046 locally. The failure path is now durable and replay-safe; replay/reordering,
late-success/manual-review correction, aggregate `All`, and cloud promotion remain deferred to the
next approved groups.

## G8 — Replay, reordering, late success, and Redis recovery — 2026-08-25

| Check | Result | Evidence / boundary |
|---|---|---|
| Fingerprint and conflict guards | PASS | Existing Order/Flash Sale inbox tests plus the affected-module suite cover duplicate delivery, canonical fingerprints, same-event/different-payload conflicts, stale participant versions, and non-retryable identity conflicts. |
| Concurrency/crash-window safety | PASS | `AcceptedPurchaseConcurrencyIntegrationTests`, `AcceptedPurchasePersistenceIntegrationTests`, `ReservationIdempotencyConcurrencyIntegrationTests`, and Redis failure/recovery tests pass; physical replay remains one semantic effect. |
| Compensated late success | PASS | `LatePaymentCorrectionIntegrationTests.lateSuccessReopensTerminalOrderAndWritesOneStableReviewCorrection`: Order `CANCELLED` reopens to `PENDING_PAYMENT`, Saga enters `MANUAL_REVIEW`, and one `OrderPaymentReviewRequired` outbox fact remains after replay. |
| Release-in-flight late success | PASS | `LatePaymentCorrectionIntegrationTests.releaseResultAfterLateSuccessInFlightMovesSagaToManualReview`: a higher-version success opens confirmation while release is in flight; the later released result preserves `PENDING_PAYMENT`, records manual review, and does not emit a second correction. |
| Correction contract boundary | PASS | `OrderPaymentReviewRequiredMapperTests` validates `OrderPaymentReviewRequiredV1`, fixed `LATE_PAYMENT_RESERVATION_UNAVAILABLE` reason, previous terminal state, stable event identity, and `flashsale.order.events.v1` routing. |
| Redis reconciliation backlog | PASS | `ReservationReconciliationServiceTests` verifies bounded retry with failed rows left pending; `ReservationReconciliationIntegrationTests` verifies CONFIRMED/RELEASED/EXPIRED Lua repair and a restarted worker observing no pending marker. |
| Reconciliation scheduling/metrics | PASS | The scheduled adapter uses a bounded batch, single-process in-flight lease, safe exception logging, and the existing low-cardinality `FlashSaleObservability.REDIS_RECONCILIATION` timer/observation; PostgreSQL `redis_reconciled_at` remains the durable completion marker. |
| Replay runner | PASS | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-044-purchase-saga.ps1 -Scenario Replay -TimeoutSeconds 1800` exited 0 and emitted `FEATURE_044_REPLAY=PASS`; affected Order/Flash Sale focused tests passed with no secrets or cloud mutation. |
| Late-success runner | PASS | `pwsh -NoLogo -NoProfile -File .\infra\docker\smoke\feature-044-purchase-saga.ps1 -Scenario LateSuccess -TimeoutSeconds 1200` exited 0 and emitted `FEATURE_044_LATE_SUCCESS=PASS`; 5 selected tests passed, 0 failures, 0 errors. |
| Static hygiene | PASS | PowerShell parser validation and `git diff --check` passed. No `.env`, ECR, EKS, Argo, or cloud state was changed. |

G8 closes T047–T054 locally. `Replay` and `LateSuccess` are now selectable runner scenarios; the
`All` selector is implemented but has not been executed in this turn because it intentionally runs
the full reactor and is the final pre-cloud gate. The one-time cloud release remains deferred until
the user explicitly runs/approves `-Scenario All`, then promotes the affected images and executes the
existing Phase 20/21/24 cloud gates.

## US5 aggregate local gate — 2026-08-26

| Check | Result | Evidence / boundary |
|---|---|---|
| Aggregate Feature 044 runner | PASS | `pwsh -NoLogo -NoProfile -File .\\infra\\docker\\smoke\\feature-044-purchase-saga.ps1 -Scenario All -TimeoutSeconds 3600` completed with `FEATURE_044_CONTRACTS`, `FEATURE_044_START`, `FEATURE_044_PAID`, `FEATURE_044_FAILED`, `FEATURE_044_REPLAY`, `FEATURE_044_LATE_SUCCESS`, `FEATURE_044_MODULES`, `FEATURE_044_MONOREPO`, and `FEATURE_044_LOCAL_GATE` all `PASS`. The aggregate timeout was increased only for the full local reactor and remains bounded. |
| Full monorepo verification | PASS | Maven `clean verify` completed through all reactor modules. Recent Surefire reports cover 855 tests across 262 suites with 0 failures, 0 errors, and 16 intentional skips. |
| Replay performance regression | PASS | `CheckoutReplayConcurrencyIntegrationTests`: 3 tests, 0 failures, 0 errors; the replay benchmark passed after a bounded warm-up and the idempotent persistence fast path removed redundant locking/update work. |
| Local environment restoration | PASS | Five high-load local containers were stopped only while measuring the benchmark and were started again after the aggregate run. No `.env`, ECR, EKS, Argo, or cloud state was changed. |

T064 is complete. Cloud promotion and live Phase 20/21/24 evidence remain separate release-gate tasks.

## T065–T066 — Documentation and static audit — 2026-08-26

| Check | Result | Evidence / boundary |
|---|---|---|
| Architecture documentation | PASS | Updated the end-to-end guide with the implemented Feature 044 Purchase Saga, approved deadline/terminal/late-success rules, deferred Cart/Notification boundary, and troubleshooting table. |
| Messaging reliability documentation | PASS | Updated topic statuses so Feature 044 Purchase/Payment/Order topics are approved while Campaign end/cancellation and other candidate families remain explicitly deferred; documented `MANUAL_REVIEW` and DLT boundaries. |
| Kafka catalog | PASS | Recorded Feature 044's five approved main topics, three consumer DLTs, 14 Registry-subject inventory, and the local-gate release boundary. |
| Java package/comment audit | PASS | Changed Order/Flash Sale Java files remain under service-owned feature/configuration/adapter/observability packages; no TODO/FIXME/HACK/XXX markers were introduced. |
| Log/error/redaction audit | PASS | No production `System.out`, stack-trace, Authorization/Bearer, provider-secret, or password logging pattern was found in changed Java files. The only matches are a deterministic test webhook fixture and a non-sensitive replay benchmark metric. |
| Formatting hygiene | PASS | `git diff --check` completed with no whitespace errors. Git's existing user-config permission and LF/CRLF warnings are environmental only. |

T065 and T066 are complete. T067–T068 are the next required quality gates.

## T067–T068 — Local quality gates — 2026-08-26

| Check | Result | Evidence / boundary |
|---|---|---|
| Affected-module reactor verification (T067) | PASS | `./mvnw.cmd --batch-mode --no-transfer-progress -pl contracts/kafka-avro-contracts,services/order-service,services/flashsale-service -am verify` completed successfully: common-web 9 tests, Kafka contracts 21, Flash Sale 118 (1 intentional skip), and Order 125 (8 intentional skips); 273 tests total, 0 failures, 0 errors, 9 skips; reactor `BUILD SUCCESS`. |
| Full monorepo clean verification (T068) | PASS | `./mvnw.cmd --batch-mode --no-transfer-progress clean verify` completed all 13 reactor modules in 40:57: 855 tests, 0 failures, 0 errors, 16 intentional skips; reactor `BUILD SUCCESS`. Module counts: common-web 9, Kafka contracts 21, API Gateway 194, Authentication 64, Product 35, Cart 1, Campaign 111 (1 skip), Flash Sale 118 (1 skip), Order 125 (8 skips), Payment 147 (6 skips), Notification 1, Inventory 29. |
| PowerShell syntax/tests gate (T068) | PASS | PowerShell parser checked all 46 `infra/**/*.ps1` files with 0 parser errors; `Invoke-Pester -Path .\\infra\\scripts\\gitops\\tests -PassThru` completed with exit code 0 and all four static safety scripts emitted `PASS`. The Phase 23 test was aligned with the current `api-gateway-https-service.yaml` patch name. Existing Git user-config permission and line-ending warnings are environmental only. |
| Kubernetes cloud manifest gate (T068) | PASS | `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` rendered namespace, 8 service ConfigMaps, platform services, 8 Deployments, Schema Registry, and Postgres/Redis/Kafka StatefulSets successfully. No live Kubernetes resource was changed. |
| Secret/cloud boundary | PASS | No `.env` file was read or changed, and no ECR push, Argo sync, EKS rollout, or other cloud mutation was executed by these gates. |

T067 and T068 close the local quality gates. The remaining T069–T072 tasks are sequential cloud
release, promotion, live verification, and rollback evidence and must not be marked complete from
these local results.

## T055–T063 — Release assets, observability, and cloud-contract closure — 2026-08-26

| Check | Result | Evidence / boundary |
|---|---|---|
| Local runner safety contract (T055/T058) | PASS | `infra/scripts/tests/feature-044-purchase-saga.tests.ps1` parsed the runner and verified scenario dispatch, the bounded 60–3600 second timeout, bounded native processes/diagnostics, cleanup, redaction, and forbidden cloud/secret mutations. `-Scenario Contracts` additionally verified the local Kafka/Registry contract inventory and emitted `FEATURE_044_CONTRACTS=PASS`; the previously recorded aggregate `All` gate remains PASS. |
| Observability/readiness contract (T056/T060) | PASS | Targeted Order/Flash Sale observability tests completed 15 tests with 0 failures/errors. The affected-module `verify` then completed Flash Sale 120 tests (1 intentional skip) and Order 127 tests (8 intentional skips), 0 failures/errors, reactor `BUILD SUCCESS` in 6:50. Metrics use bounded Saga state/outcome tags; readiness exposes manual-review, outbox, DLT, and Redis-reconciliation diagnostics without making Kafka a false hard dependency. |
| Local Compose configuration (T059) | PASS | `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml config --quiet` exited 0. Order and Flash Sale Feature 044 topics, consumer groups, DLTs, retries, and explicit local enable flags render without reading or changing ignored `infra/docker/.env`. |
| Phase 20 inventory (T061) | PASS | PowerShell parsing passed. The Phase 20 inventory now contains 11 topics and 23 subjects overall; the Feature 044 subset is 4 reviewed topics/DLTs and 14 exact topic-record subjects with `BACKWARD_TRANSITIVE` compatibility. Local `Contracts` validation found partitions=3, replication factor=1, and registered/verified all 14 subjects without topic deletion or broker auto-creation. |
| Cloud ConfigMap contract (T057/T062) | PASS | `phase44-purchase-saga-cloud.tests.ps1` verified canonical Order/Flash Sale topic names, all reviewed runtime/consumer/reconciliation flags, absence of the deprecated Payment-result DLT name, Phase 20/schema-script linkage, and a successful local Kustomize render. No EKS, Kafka, Registry, database, Redis, Stripe, or Secret state changed. |
| Phase 24 continuation (T063) | PASS | `phase24-stripe-cloud.tests.ps1` passed. The delegated real fixture path now polls the owner-scoped reservation and Order APIs after Checkout/webhook/replay, requires matching `purchaseRequestId`, reservation `CONFIRMED`, and final Order `CONFIRMED`, and does not fabricate Payment state or query another service's database. Live Stripe/cloud proof remains correctly owned by T071. |
| Canonical DLT/config consistency | PASS | Order defaults, local Compose, cloud ConfigMaps, Phase 20, and static tests consistently use `flashsale.order.payment-result.dlt.v1`; the obsolete `flashsale.order.payment-events.dlt.v1` name is absent. |
| Static hygiene and mutation boundary | PASS | `git diff --check` passed. No ignored `.env`, Secret value, ECR image, Argo Application, EKS workload, cloud topic/schema, or live database was read or mutated by this task group. |

T055–T063 are complete. Together with the previously green T064–T068 local gates, the next allowed
work is the sequential post-merge cloud path T069–T072. Database migration ordering, immutable image
promotion, and rollback compatibility must be resolved and evidenced at that release boundary rather
than inferred from local tests.

## T073 — Paid-success slice convergence — 2026-08-26

| Check | Result | Evidence / boundary |
|---|---|---|
| PaymentSucceeded adapter and DLT boundary (T027/T033) | PASS | `PaymentSucceededConsumerTests` validates trace propagation, acknowledgement only after successful handling, validation of amount/currency, and non-retryable conflict behaviour. `OrderPaymentEventsConsumerConfigurationTests` verifies the actual non-retryable error handler publishes to the configured `flashsale.order.payment-result.dlt.v1` topic. |
| Atomic Saga/inbox/confirm command (T028/T032) | PASS | `PaymentSuccessTransitionIntegrationTests` uses PostgreSQL + Liquibase and proves `PAYMENT_PENDING -> CONFIRMING_RESERVATION`, one inbox row and one confirm command; replay and lower versions add no command; invalid amount rolls back all three durable writes. |
| Confirm state and expiry race (T029/T034) | PASS | `ReservationConfirmationPersistenceTests` covers normal confirmation, command replay, confirmation after release with the durable payment-failure reason, and the expiry boundary with stable `RESERVATION_EXPIRED`. A distinct confirm command after a terminal release/expiry now writes a causation-correlated existing `PurchaseReservationReleasedV1` result instead of throwing; this gives Order a durable result that can lead to `MANUAL_REVIEW`. |
| Redis exact-once and recovery boundary (T030/T035) | PASS | `ReservationConfirmationRedisIntegrationTests` proves one successful confirmation removes the expiry marker while keeping stock/quota correct; replay is idempotent. `ConfirmReservationServiceTests` proves Redis confirmation Lua is invoked only for `CONFIRMED`/`ALREADY_CONFIRMED`, never for released/expired current-state results. Existing reconciliation and Redis-failure integration coverage remains green in the affected-module suite. |
| Order confirmation and public state (T031) | PASS | Existing `PurchaseReservationConfirmationPersistenceIntegrationTests` verifies Order confirmation persistence; updated `OrderQueryControllerTests` includes authenticated terminal `CONFIRMED` responses. |
| Focused tests | PASS | Focused Flash Sale test command completed with 8 tests, 0 failures/errors. Focused Order adapter/Saga/query command completed with 9 tests, 0 failures/errors; the focused DLT configuration command completed with 1 test, 0 failures/errors. |
| Affected-module release gate | PASS | `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/order-service,services/flashsale-service -am verify` completed in 10:28 with reactor `BUILD SUCCESS`: Flash Sale 126 tests, 0 failures/errors, 1 intentional skip; Order 134 tests, 0 failures/errors, 8 intentional skips. |
| Contract/schema and mutation boundary | PASS | The fix emits the already versioned `PurchaseReservationReleasedV1` current-state fact, so no Kafka topic, Avro schema, or database migration changed. `git diff --check` passed before ledger update; no `.env`, Secret, ECR, EKS, Argo, Kafka broker, Registry, Redis, or database state was changed. |

T027–T035 and T073 are complete. T074 (cloud migration release gate) and T075 (rollback compatibility rehearsal) remain separate, intentionally unchecked follow-up tasks.

## T074 — Feature 044 cloud migration release gate — 2026-08-26

| Check | Result | Evidence / boundary |
|---|---|---|
| Test-first static contract | PASS | The new `phase44-purchase-saga-migration-gate.tests.ps1` initially failed because the gate assets did not exist, then passed after the gate, two-Job overlay, promotion-PR handoff, parser checks, and Kustomize render were implemented. It verifies that only Order and Flash Sale are selected and that the gate has explicit `-Apply`, immutable release-tag, dry-run, no-delete, and redaction boundaries. |
| Manifest scope | PASS | `kubectl kustomize infra/k8s/overlays/cloud-migrations/feature-044` and `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud-migrations/feature-044` rendered exactly `migrate-order-purchase-saga` and `migrate-flash-sale-reservation-finalization`; no other service migration Job is present. |
| Release-gate behavior | PASS | Default mode resolves the current remote `develop` SHA, checks ECR digests for exactly `flash-sale/order-service` and `flash-sale/flash-sale-service`, validates EKS prerequisites, writes the resolved image manifest only to an OS temporary directory, and performs a client dry-run. `-Apply` is the sole mutation opt-in and waits Order then Flash Sale with bounded status-only diagnostics; it never deletes/reruns Jobs or reads Secret data. |
| Promotion handoff | PASS | `service-delivery.yml` detects the two Feature 044 Liquibase change paths and injects an explicit pre-merge gate instruction into the generated promotion PR. PR Markdown is now supplied through `gh pr create --body-file`, avoiding Bash command-substitution from Markdown backticks. |
| Live precondition probe | EXPECTED BLOCK | The read-only gate reached AWS identity successfully, then rejected the current remote `develop` tag because the two new `release-<develop SHA>` images are not in ECR yet. This is the intended stop condition before delivery; no Kubernetes Job, database migration, ECR image, Argo state, or Secret was changed. Run the two documented commands after the selective delivery workflow builds the images and before merging its promotion PR. |
| Static hygiene | PASS | Gate static test, parser check, Kustomize render/client dry-run, and `git diff --check` passed. No ignored `.env` file or Secret value was read or changed. |

T074 implements the required release gate. Its positive live execution is intentionally part of the later T070 promotion sequence, because the required immutable images do not exist until that workflow has completed.

## T075 — Non-destructive rollback compatibility rehearsal — 2026-08-26

| Check | Result | Evidence / boundary |
|---|---|---|
| Test-first static contract | PASS | `infra/scripts/gitops/tests/phase44-purchase-saga-rollback-rehearsal.tests.ps1` first failed because the rehearsal asset was absent, then passed after the read-only gate was added and parsed. It checks the immutable prior-release input, matching historical Order/Flash Sale enums, read-only PostgreSQL transaction, expanded-schema checks, terminal-row checks, redaction, and explicit incompatible-state stop. |
| Rollback safety design | PASS | `phase44-purchase-saga-rollback-rehearsal.ps1` has no `-Apply` mode and contains no Kubernetes apply/delete/patch, Git revert, Liquibase history change, or data-write command. It resolves only `release-<SHA>` ECR images, compares their source enums with grouped terminal states, and stops before any rollback mutation when an old image cannot read a persisted state. |
| Schema/data boundary | PASS | The rehearsal proves the additive Feature 044 tables/columns are present and queries only aggregate terminal status/count values inside `BEGIN TRANSACTION READ ONLY`; it does not print row IDs, customer data, credentials, Secret values, or full SQL payloads. An incompatible result explicitly preserves all Saga, inbox, outbox, reservation, topic, schema, receipt, and PVC data and returns to spec/plan approval. |
| Operator handoff | PASS | `quickstart.md` now documents how to identify the prior common immutable SHA, run the compatibility prerequisite, interpret `PASS` versus `Incompatible persisted terminal state`, and continue the approved T072 GitOps rollback order. |
| Live invocation | DEFERRED | A meaningful run requires the Feature 044 Order/Flash Sale images to be promoted and at least one representative terminal Order and Reservation row in EKS. The release has not been promoted in this implementation turn, so no ECR, EKS, Argo, PostgreSQL, Kafka, Schema Registry, Redis, or Secret state was contacted or changed. |

T075 completes the rollback-compatibility asset and static evidence. Its read-only live result is a
mandatory prerequisite of T072, not authorization to invent a destructive database rollback.

## T069 — Cloud Kafka and Schema Registry provisioning — 2026-08-27

| Check | Result | Evidence / boundary |
|---|---|---|
| Payment safety precondition | PASS | `phase20-kafka-contracts.ps1` confirmed the live cloud ConfigMap has `7/7` Payment runtime flags disabled during provisioning. Stripe credentials remained in `payment-secrets` and were not read or changed. |
| Kafka topic inventory | PASS | Phase 20 `-Apply` created the four missing Feature 044 topics/DLTs: `flashsale.purchase.commands.v1`, `flashsale.order.payment-result.dlt.v1`, `flashsale.flash-sale.purchase-command.dlt.v1`, and `flashsale.order.purchase-reservation-result.dlt.v1`. Existing approved topics were verified at partitions=3 and replication factor=1; broker automatic topic creation remained disabled. |
| Schema Registry inventory | PASS (initial inventory) | The initial Feature 044 Registry gate registered and verified 14 topic/record subjects with `BACKWARD_TRANSITIVE` compatibility. A later Order consumer log audit found that the shared purchase-events source also requires three cross-boundary DLT subjects; the corrected inventory is 17 subjects and is recorded below for re-provisioning. The overall cloud inventory was 11 topics and 23 subjects before that correction. |
| Argo and workload gate | PASS | Argo `flash-sale-cloud` reconciled at `0876104f7c917b65254870c69227f8a891898cb0` with `Synced|Healthy`; Schema Registry rollout completed successfully. |
| Failure/retry evidence | PASS | The first read-only attempt encountered Kafka pod restart and `kubectl` exit 137; after Kafka returned `Running/Ready`, the rerun completed validation and the explicit `-Apply` completed successfully. No topic, schema, database, PVC, image, or Secret was deleted. |
| Command boundary | PASS | `pwsh -NoLogo -NoProfile -File .\\infra\\scripts\\gitops\\phase20-kafka-contracts.ps1 -Apply` completed with `Phase 20 complete`; no Secret values or provider payloads were printed. |

The initial T069 provisioning run is recorded above. Payment runtime restoration is intentionally
handled by a separate reviewed GitOps change; the corrected shared-DLT subjects must be applied
before treating the cloud contract inventory as current and before the Feature 044 migration gate
and Stripe smoke.

## Shared purchase-event DLT correction — 2026-08-27

The first cloud run exposed a Schema Registry `40401 Subject not found` while Order attempted to
dead-letter a `PurchaseReservationReleasedV1` record read from the shared
`flashsale.purchase.events.v1` topic. This was an operational registration gap, not a new business
event or a data-loss condition. The Order DLT must be able to serialize every SpecificRecord that
can arrive from that shared source: `PurchaseAcceptedV1`, `PurchaseReservationConfirmedV1`, and
`PurchaseReservationReleasedV1`. The corrected Phase 20 inventory and the idempotent local Order
schema script contain all three bindings; the reservation-results DLT retains its
`PurchaseAcceptedV1` binding for the symmetric poison-record case.

| Check | Result | Evidence / boundary |
|---|---|---|
| Static contract regression | PASS | `phase44-purchase-saga-cloud.tests.ps1` passed after asserting both missing Order DLT subjects, the three bindings in `register-order-schemas.ps1`, and the shared-source documentation. |
| PowerShell syntax | PASS | Parser checked the corrected Phase 20 script, Order Schema Registry script, and Feature 044 cloud contract test with zero errors. |
| Cloud re-provisioning | PASS | The reviewed pause/apply/restore sequence completed: Phase 20 registered the corrected subjects idempotently while Payment was `7/7 disabled`; a second reviewed GitOps change restored `7/7 enabled`, and the final Phase 24 flow passed. No topic, schema, receipt, database row, PVC, or Secret was deleted. |

## T070 — Immutable service delivery and promotion — 2026-08-28

| Check | Result | Evidence / boundary |
|---|---|---|
| Feature release workflow | PASS | GitHub Actions Eight-Service GitOps Delivery run `33058503531` completed successfully at source SHA `f5a11de7dd40da24bb4c12e70fb2051b589fc066`; the reviewed shared-change run verified and published the eight deployable services, including the affected Order and Flash Sale images. |
| Base image promotion | PASS | Promotion PR `#99` merged commit `c74051d6a48a6de1e883f148c6742d6f0b35f289`; the desired-state change used immutable `release-f5a11de7dd40da24bb4c12e70fb2051b589fc066` tags. |
| Selective corrective delivery | PASS | The final Flash Sale correlation correction was detected as the only changed deployable service. Run `33130893478` completed at source SHA `486c6b110c843831a3c86a8f3c4f47af1e5adb61`, and promotion PR `#114` merged commit `b208bc8296e1cd19024f930175fe6c0176587b22`. |
| Live immutable artifacts | PASS | Phase 21 resolved the running Order image `release-f5a11de7dd40da24bb4c12e70fb2051b589fc066` to ECR digest `sha256:af7153081ff2e0877b547000fd9acb8a5ffee5012d806e131e598fb699477e3a` and the running Flash Sale hotfix `release-486c6b110c843831a3c86a8f3c4f47af1e5adb61` to digest `sha256:17f5e7fd47c72e903d09487997ec87b8c3bd32fb6c4505a801fa4d06e151252e`; ready Pod image IDs matched those ECR digests. |
| Release shape | PASS | The baseline shared release was followed by reviewed single-service promotions. Mixed immutable per-service SHAs are expected for selective delivery; no mutable tag is present. |

T070 is complete. Workflow and PR links are retained by GitHub; the ledger records immutable IDs
only and contains no credential, Secret, Checkout URL, customer identity, or provider payload.

## T071 — Argo, release, and Stripe runtime verification — 2026-08-28

| Check | Result | Evidence / boundary |
|---|---|---|
| Argo ownership and health | PASS | `flash-sale-cloud` reported `Synced/Healthy`, target `develop`, revision `a35d69a029144d226ad48c27a86cb3cac9826cb1`. |
| Eight artifact gate | PASS | `phase21-cloud-release-verify.ps1 -PaymentRuntimeState enabled` verified 8/8 available Deployments, immutable ECR tags, and exact running Pod digests. Payment runtime reported `7/7 enabled`. |
| Gateway boundary | PASS | Phase 18 selected the current HTTPS-only Service port `443` for its localhost-only port-forward and returned readiness `200`, catalog `200`, anonymous admin `401`; Phase 21 ended with `PASS`. |
| Purchase and Checkout | PASS | The approved Phase 24 runner created an authenticated shopper and owned fixtures, accepted and replayed one reservation, produced one Order in `PENDING_PAYMENT`, found the Payment aggregate in `PENDING`, and received Checkout `201` plus idempotent replay `200`. Identities and Checkout URL were not copied into this ledger. |
| Stripe webhook and replay | PASS | Stripe test Checkout completed; Payment reached `SUCCEEDED`; the first valid signed webhook and duplicate delivery were both acknowledged with HTTP `204`, proving durable provider receipt/idempotent processing without recording signature or payload. |
| Kafka and Saga completion | PASS | The Payment event topic aggregate offset advanced, Order reached `CONFIRMED`, the reservation reached `CONFIRMED`, and the runner emitted `Phase 24 Stripe runtime smoke: PASS`. |
| Secret/data boundary | PASS | No Secret value, JWT, Authorization header, Checkout URL, Stripe identifier/signature/body, customer identity, or fixture UUID is recorded here. |

T071 is complete. Phase 21 originally assumed all Payment flags remained disabled and Phase 18
assumed Service port 8080. The gates now explicitly support the reviewed post-Phase-24 state while
remaining read-only; static regression tests and the live rerun both passed.

## T072 — rollback compatibility, safe stop, and recovery — 2026-08-28

| Check | Result | Evidence / boundary |
|---|---|---|
| Prior common release | PASS | The predecessor reviewed common Order/Flash Sale target is `release-f5a11de7dd40da24bb4c12e70fb2051b589fc066`; both ECR digests resolved. |
| Active release shape | PASS | The rehearsal accepted reviewed immutable per-service active tags after targeted Flash Sale hotfixes; it does not require unrelated services to be rebuilt solely to manufacture one common current SHA. |
| Enum/schema compatibility | PASS | Prior Order and Reservation enum surfaces each contained four states; the additive Feature 044 Order and Flash Sale schema was present. |
| Representative terminal data | PASS | Read-only aggregate queries found two Order terminal-state groups and two reservation terminal-state groups; no row identity or SQL payload was printed. All observed terminal values are readable by the prior images. |
| Mutation boundary | PASS | The compatibility rehearsal changed no Deployment, ConfigMap, Git ref, database row, Kafka topic, Registry subject, Redis key, ECR artifact, PVC, or Secret. |
| Intake-pause desired state | PREPARED | The reviewed GitOps candidate sets only `ORDER_ACCEPTED_PURCHASE_CONSUMER_ENABLED=false`; Payment-result and reservation-result consumers plus the Order outbox publisher stay enabled so already accepted Sagas can drain. This row is not live evidence until the PR is merged and Argo reconciles it. |
| Intake pause live | PASS | PR `#117` merged as `564a088bf5945362c233f4bb60fc981527276f02`; Argo reconciled `Synced/Healthy`. The ConfigMap and restarted Order pod both reported `ORDER_ACCEPTED_PURCHASE_CONSUMER_ENABLED=false`; Order remained `1/1` available. |
| Drain snapshot | PASS WITH RECORDED DURABLE WORK | Order, Flash Sale, and Payment unpublished outbox counts were all zero; terminal Redis reconciliation backlog was zero. Two Saga rows remained in `CONFIRMING_RESERVATION` and were preserved without inspection or mutation. |
| Kafka drain | PASS | Payment-result and reservation-result consumer groups reported aggregate lag zero. The paused PurchaseAccepted group had no active member and lag zero, proving no accepted message was stranded when intake stopped. |
| Image-revert desired state | PREPARED | Order already uses the prior common `release-f5a11de7dd40da24bb4c12e70fb2051b589fc066` image. The second reviewed GitOps candidate changes only Flash Sale from its targeted hotfix tag back to that same prior immutable release; it does not touch schemas, data, topics, PVCs, or Secret references. |
| Image rollback execution | SAFE STOP | PR `#118` merged as `852ec32cd0530437ecf9d0c81ce5d9d378d0923e`. Argo accepted the desired rollback revision, but the prior Flash Sale image failed Spring startup because `ConfirmReservationAvroMapper` had no default constructor. Kubernetes kept the existing hotfix pod available while the rollback pod entered `CrashLoopBackOff`; no database, Kafka, Registry, Redis, PVC, or Secret state was changed. |
| Recovery desired state | PREPARED | The reviewed recovery candidate restores only Flash Sale to the previously verified immutable hotfix `release-486c6b110c843831a3c86a8f3c4f47af1e5adb61`. Order purchase intake remains paused until Argo reports `Synced/Healthy` and the preserved aggregate/outbox state is rechecked. |
| Recovery live | PASS | PR `#119` merged as `a1073e71b4bbab4fb6fc7712dab0af6bba828707`; Argo reported `Synced/Healthy` and Flash Sale returned to `1/1` on the verified hotfix image. The failed rollback pod was removed by the rolling Deployment without deleting or rewriting durable state. |
| Post-recovery drain recheck | PASS WITH RECORDED DURABLE WORK | Aggregate counts matched the pre-rollback snapshot: Order and Flash Sale unpublished outboxes remained zero, Payment unpublished outbox remained zero, terminal Redis backlog remained zero, and two `CONFIRMING_RESERVATION` Saga rows remained preserved. Payment-result and reservation-result groups had lag zero; paused PurchaseAccepted had no active member and lag zero. |
| Intake-resume desired state | PREPARED | A separate reviewed GitOps candidate restores only `ORDER_ACCEPTED_PURCHASE_CONSUMER_ENABLED=true`. No image, schema, topic, database row, Redis key, PVC, or Secret reference changes with this candidate. |
| Intake resume live | PASS | PR `#120` merged as `4f189d1fcc9a7a92c50bf8cbf371a398094d8b69`; Argo reported `Synced/Healthy`. After the required rolling restart, the ready Order pod reported `ORDER_ACCEPTED_PURCHASE_CONSUMER_ENABLED=true`, the Deployment was `1/1`, and `order-purchase-accepted-v1` had an active member with lag zero on all three partitions. |
| Final outcome | PASS WITH SAFE ROLLBACK REJECTION | T072 proved the operational order: pause intake, drain and record durable work, attempt the reviewed immutable rollback, let Kubernetes retain the healthy pod when the prior image failed startup, recover through GitOps, recheck preserved state, and resume intake. The failed prior image was not reported as compatible; no durable data, topic, schema, PVC, Redis state, ECR artifact, or Secret was deleted or rewritten. |

T072 is complete. The rehearsal demonstrated both rollback safety and recovery behavior: an
application-incompatible prior image was rejected without downtime or destructive data rollback,
and normal PurchaseAccepted processing was restored through reviewed GitOps desired state.
