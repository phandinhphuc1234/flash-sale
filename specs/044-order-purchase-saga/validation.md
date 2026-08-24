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
