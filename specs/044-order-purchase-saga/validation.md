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
