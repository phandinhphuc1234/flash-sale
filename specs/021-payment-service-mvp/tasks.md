# Tasks: Payment Service MVP with Stripe Checkout

**Status**: Approved task ledger — implementation may begin only after `speckit-analyze` reports no
blocking finding
**Input**: Approved design artifacts from `/specs/021-payment-service-mvp/`
**Prerequisites**: Approved `spec.md`, `plan.md`, five contracts, and Accepted ADR 0018

**Tests**: Unit, architecture, PostgreSQL, Kafka/Schema Registry, HTTP/security, provider-adapter,
webhook, reconciliation, outbox, failure-matrix, smoke, Stripe test-mode, load, module, and full
reactor validation are mandatory because Payment is a money-bearing `CORE_DOMAIN` feature.

**Organization**: Work is grouped into G1–G10. User-story tasks retain `[US1]`–`[US5]` labels;
setup, shared foundation, and cross-cutting evidence tasks intentionally have no story label.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: safe to execute in parallel after the group's prerequisites because it owns different files.
- **[Story]**: maps directly to the approved user story in `spec.md`.
- Every task names its implementation or evidence path.
- Required tests are written before or with their implementation and must be observed failing for
  the intended reason before the production change is considered complete.

---

## Phase 1 / G1: Module, Versioned Contracts, and Build Foundation

**Purpose**: Establish approved dependencies and generated wire contracts before implementing any
business behavior.

- [X] T001 Add the approved Payment production/test dependencies and pin Stripe Java `33.2.0` in `pom.xml` and `services/payment-service/pom.xml`
- [X] T002 [P] Add `PaymentRequestedV1`, `PaymentSucceededV1`, and `PaymentFailedV1` Avro schemas with exact envelope, logical types, defaults, namespaces, and documentation in `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.commands.v1/PaymentRequestedV1.avsc` and `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.events.v1/PaymentSucceededV1.avsc`, `PaymentFailedV1.avsc` (FR-002, FR-003, FR-033)
- [X] T003 Add exact-shape, decimal/timestamp logical-type, forbidden-field, and backward-compatibility tests for all three records in `contracts/kafka-avro-contracts/src/test/java/com/philia/flashsale/contract/payment/PaymentContractSchemaTests.java` (NFR-COMPAT-001)
- [X] T004 [P] Create the package-by-feature skeleton and dependency-direction ArchUnit rules in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/package-info.java` and `services/payment-service/src/test/java/com/philia/flashsale/payment/architecture/PaymentArchitectureTests.java`
- [X] T005 [P] Define validated typed properties for provider, expected Stripe webhook API version, webhook signature, Kafka consumer/outbox, recovery, and public security settings in `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentProperties.java`, `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/StripeCheckoutProperties.java`, `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentKafkaProperties.java`, and `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentRecoveryProperties.java`
- [X] T006 Configure PostgreSQL, Liquibase, JPA validation, Kafka/Schema Registry, JWT trust, Actuator, tracing, redacted Stripe inputs, and disabled-by-property workers in `services/payment-service/src/main/resources/application.yml` (FR-039, FR-040, FR-042)
- [X] T007 Add idempotent local command/event/DLT topic provisioning plus controlled BACKWARD_TRANSITIVE Payment subject registration/check-only workflows in `infra/docker/kafka/init-payment-topics.sh` and `infra/docker/schema-registry/register-payment-schemas.ps1`
- [X] T008 Create the implementation evidence ledger, run contract generation/schema compatibility plus the empty Payment application context, and record approved artifact/ADR/schema/topic baselines, commands, generated records, test counts, and exit status in `specs/021-payment-service-mvp/validation.md`

**Checkpoint**: Payment compiles with the approved dependencies and exact generated contracts; its
topics/subjects can be provisioned before live Kafka tests, but no command, HTTP route, provider
call, or worker is enabled.

---

## Phase 2 / G2: Shared Domain and PostgreSQL Foundation

**Purpose**: Build the invariant-bearing aggregate and durable records that block all user stories.

**⚠️ CRITICAL**: G2 must complete before any inbound adapter or Stripe call is implemented.

- [X] T009 [P] Add pure value-object tests for exact `Money`, VND zero-decimal conversion preconditions, IDs, deadlines, failure reasons, and status values in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/domain/model/PaymentValueObjectTests.java`
- [X] T010 [P] Add Payment/PaymentAttempt state-machine tests for immutability, one unresolved attempt, maximum three attempts, success dominance, and no terminal inference from retry exhaustion in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/domain/model/PaymentTests.java` and `PaymentAttemptTests.java` (FR-007, FR-027, FR-028)
- [X] T011 Implement Payment aggregate, PaymentAttempt entity, value objects, status/failure enums, and domain exceptions without Spring/JPA/Stripe imports in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/domain/model/Payment.java`, `PaymentAttempt.java`, `Money.java`, `PaymentStatus.java`, `PaymentAttemptStatus.java`, and `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/domain/exception/`
- [X] T012 Add Liquibase changesets for `payments`, `payment_attempts`, command inbox, client idempotency, provider receipts, recovery work, outbox, constraints, partial unique indexes, claim indexes, and prohibited-data comments under `services/payment-service/src/main/resources/db/changelog/` and include them from `db.changelog-master.yaml` (FR-040, FR-041)
- [X] T013 [P] Implement JPA entities and explicit persistence mappers for Payment and attempts in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/entity/PaymentJpaEntity.java`, `PaymentAttemptJpaEntity.java`, and `PaymentPersistenceMapper.java`
- [X] T014 [P] Implement JPA entities for command inbox/client idempotency in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/entity/PaymentCommandInboxJpaEntity.java` and `PaymentClientIdempotencyJpaEntity.java`
- [X] T015 [P] Implement JPA entities for provider receipts/recovery/outbox in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/entity/PaymentProviderEventReceiptJpaEntity.java`, `PaymentRecoveryWorkJpaEntity.java`, and `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/adapter/out/persistence/jpa/PaymentOutboxEventJpaEntity.java`
- [X] T016 Define Spring Data repositories plus adapter-local native `FOR UPDATE SKIP LOCKED` claim queries under `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/repository/` and `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/adapter/out/persistence/jpa/repository/`
- [X] T017 Define application-owned clock/identity/transaction, aggregate load/save, inbox, idempotency, provider-receipt, recovery, and outbox ports under `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/port/out/` and `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/application/port/`
- [X] T018 Implement JPA persistence adapters and explicit mapper boundaries under `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/` and `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/adapter/out/persistence/jpa/`
- [X] T019 Prove Liquibase/JPA agreement, all checks/unique/partial indexes, decimal precision, no URL/raw-webhook columns, and rollback atomicity using PostgreSQL Testcontainers in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentSchemaMigrationIntegrationTests.java`
- [X] T020 Prove multi-worker aggregate locks, one-unresolved-attempt constraint, work leases, stale claim recovery, and outbox claims using PostgreSQL in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentPersistenceConcurrencyIntegrationTests.java`
- [X] T021 Wire shared foundation beans without component scanning business implementations in `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentFoundationConfiguration.java` and rerun `PaymentArchitectureTests`

**Checkpoint**: Pure domain invariants and all durable identities/queues are proven against real
PostgreSQL; no provider call is reachable.

---

## Phase 3 / G3: User Story 1 — Atomic Payment Acceptance (Priority: P1) 🎯 MVP Core

**Goal**: Convert one trusted command snapshot into one durable Payment without calling Stripe.

**Independent Test**: Invoke the application use case with valid, expired, equivalent, contradictory,
and 100 concurrent commands and reconcile one Payment/inbox/terminal outbox result as applicable.

### Tests for User Story 1

- [X] T022 [P] [US1] Add application tests for new/equivalent/expired/conflicting commands, immutable snapshot mapping, stable identity, no provider port, and deadline-failure outbox behavior in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/application/AcceptPaymentRequestServiceTests.java` (FR-004–FR-007)
- [X] T023 [US1] Add PostgreSQL tests for atomic Payment+inbox commit, different-event equivalent replay, contradictory fingerprint visibility, rollback, and exact expired-command result in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentCommandAcceptanceIntegrationTests.java`
- [X] T024 [US1] Prove 100 concurrent same/different-event-ID equivalent commands create one logical Payment and contradictory races never mutate it in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentCommandConcurrencyIntegrationTests.java` (SC-001)

### Implementation for User Story 1

- [X] T025 [P] [US1] Define `AcceptPaymentRequestCommand`, semantic result, conflict details, and `AcceptPaymentRequestUseCase` in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/model/AcceptPaymentRequestCommand.java`, `AcceptPaymentRequestResult.java`, and `application/port/in/AcceptPaymentRequestUseCase.java`
- [X] T026 [US1] Implement canonical command fingerprinting and atomic inbox/Payment/expired-outbox orchestration in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/service/AcceptPaymentRequestService.java`
- [X] T027 [US1] Implement inbox conflict persistence and same-Order equivalence lookup without exposing JPA types in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/PaymentCommandInboxPersistenceAdapter.java`
- [X] T028 [US1] Wire command acceptance and disabled-by-default Kafka-facing capability in `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentAcceptanceConfiguration.java`
- [X] T029 [US1] Run G3 domain/application/PostgreSQL tests and record row identities/counts, 100-delivery convergence, rollback, expired result, commands, and exit status in `specs/021-payment-service-mvp/validation.md`

**Checkpoint**: US1 works through its application port with one durable Payment and zero Stripe
calls; first-processing-after-deadline creates the one approved terminal fact.

---

## Phase 4 / G4: User Story 1 — PaymentRequested Kafka Boundary (Priority: P1)

**Goal**: Drive G3 from the explicit Order-owned command with safe Avro mapping, retry, DLT, trace,
and acknowledgement-after-commit semantics.

**Independent Test**: Deliver generated records through Kafka/Schema Registry and prove key/envelope,
duplicates, poison input, crash/redelivery, retry/DLT, and database convergence.

- [X] T030 [P] [US1] Add Avro-to-command mapper tests for every field/logical type, key/envelope mismatch, amount/deadline validation, W3C headers, and forbidden data in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/adapter/in/messaging/kafka/PaymentRequestedAvroMapperTests.java`
- [X] T031 [P] [US1] Add listener tests for created/replayed acknowledgement, conflict/poison classification, retryable storage failure, safe diagnostics, and no acknowledgement before commit in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/adapter/in/messaging/kafka/PaymentRequestedKafkaConsumerTests.java`
- [ ] T032 [US1] After T007 provisioning is available, add real Kafka/Registry integration tests for group/key/headers, 100 duplicates, commit-before-ack crash replay, 1/3/10-second retry, command-specific DLT, and operator replay in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentRequestedConsumerIntegrationTests.java`
- [X] T033 [P] [US1] Implement generated-record validation/mapping and typed poison/conflict failures in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/messaging/kafka/PaymentRequestedAvroMapper.java`, `PaymentRequestedRecordException.java`, and `PaymentRequestedConflictException.java`
- [X] T034 [US1] Implement `PaymentRequestedKafkaConsumer` with trace restoration, key/envelope validation, use-case invocation, and manual post-commit acknowledgement in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/messaging/kafka/PaymentRequestedKafkaConsumer.java`
- [X] T035 [US1] Configure SpecificRecord deserialization, consumer group, manual acknowledgement, classified retries, safe `flashsale.payment.payment-requested.dlt.v1` recovery, and runtime enablement in `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentKafkaConsumerConfiguration.java`
- [ ] T036 [US1] Add DLT record/replay documentation and safe diagnostic header assertions in `infra/docker/kafka/README-payment-dlt.md` and `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentRequestedDltIntegrationTests.java`
- [ ] T037 [US1] Run the live G4 Kafka/Registry suite and record topic/group/key, retry/DLT/replay, Payment/inbox/outbox counts, commands, and exit status in `specs/021-payment-service-mvp/validation.md`

**Checkpoint**: US1 is operationally consumable from only `PaymentRequested.v1`; `OrderCreated.v1`
cannot create Payment state.

---

## Phase 5 / G5: User Story 2 — Durable Hosted Checkout Start/Resume (Priority: P1)

**Goal**: Let an authenticated owner safely create/resume one Stripe-hosted, card-only Checkout
workflow with durable client/provider idempotency and no card-data boundary expansion.

**Independent Test**: Use the deterministic provider fake and PostgreSQL to verify new/replay/
conflict/foreign/deadline/limit/100-concurrent/response-loss cases through MVC.

### Tests for User Story 2

- [X] T038 [P] [US2] Add provider-neutral request/result and VND exact-conversion tests in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/application/HostedCheckoutProviderContractTests.java` (FR-011, FR-014–FR-016)
- [X] T039 [P] [US2] Add Stripe adapter tests for pinned `StripeClient`/API-version compatibility, hosted `payment` mode, card-only automatic capture, trusted amount/metadata/URLs, earliest valid provider expiry, stable key, timeout classification, retrieve/expire mapping, and redaction in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/adapter/out/provider/stripe/StripeHostedCheckoutAdapterTests.java`
- [X] T040 [P] [US2] Add application tests for owner/new/replay/conflict/deadline/status/three-attempt/unknown outcomes and Transaction-A-before-provider/Transaction-B-after-provider ordering in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/application/StartCheckoutServiceTests.java`
- [X] T041 [US2] Prove client-key uniqueness, raw-key non-storage, one unresolved attempt, 100 different-key requests, rollback, and response-loss recovery records against PostgreSQL in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/CheckoutAttemptConcurrencyIntegrationTests.java` (SC-002)
- [X] T042 [P] [US2] Add MVC contract tests for `201`/`200`/`202`, all stable `PAYMENT_*` errors, owner masking, empty request body, `Location`, `Retry-After`, `X-Trace-Id`, and `Cache-Control: no-store` in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/adapter/in/web/CheckoutControllerTests.java`
- [X] T043 [P] [US2] Add JWT issuer/audience/type/subject/signature/expiry, owner access, and Checkout route tests in `services/payment-service/src/test/java/com/philia/flashsale/payment/security/PaymentJwtTrustConfigurationTests.java` and `PaymentPublicSecurityTests.java`

### Implementation for User Story 2

- [X] T044 [P] [US2] Define `HostedCheckoutProviderPort`, safe create/retrieve/expire commands, normalized results, and provider error categories in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/port/out/HostedCheckoutProviderPort.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/model/provider/`
- [X] T045 [P] [US2] Implement exact money/metadata/request mapping and Stripe SDK response/exception redaction in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/provider/stripe/StripeCheckoutMapper.java` and `StripeFailureClassifier.java`
- [X] T046 [US2] Implement injected `StripeClient` create/retrieve/expire behavior with pinned SDK/API-version configuration, configured retries/timeouts, and no SDK leakage in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/provider/stripe/StripeHostedCheckoutAdapter.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/StripeClientConfiguration.java`
- [X] T047 [P] [US2] Define start/resume command/result and `StartCheckoutUseCase` in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/model/StartCheckoutCommand.java`, `StartCheckoutResult.java`, and `application/port/in/StartCheckoutUseCase.java`
- [X] T048 [US2] Implement durable Transaction A allocation, out-of-transaction provider call, Transaction B convergence, SHA-256 client-key conflict checks, and `202` recovery semantics in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/service/StartCheckoutService.java` and `CheckoutPersistenceService.java`
- [X] T049 [P] [US2] Implement HTTP response DTOs and a Checkout-only mapper that keeps Checkout URL ephemeral in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/web/response/CheckoutSessionResponse.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/web/CheckoutWebMapper.java`
- [X] T050 [US2] Implement owner-only no-body start/resume endpoint with required idempotency header and no-store response handling in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/web/CheckoutController.java`
- [X] T051 [US2] Implement stable Payment error codes, exception classifier/advice, authentication entry point, and access-denied handler in `services/payment-service/src/main/java/com/philia/flashsale/payment/websupport/error/PaymentErrorCode.java`, `PaymentExceptionClassifier.java`, `PaymentHttpExceptionHandler.java`, `PaymentAuthenticationEntryPoint.java`, and `PaymentAccessDeniedHandler.java`
- [X] T052 [US2] Implement local JWT validation and owner/webhook route authorization in `services/payment-service/src/main/java/com/philia/flashsale/payment/security/PaymentJwtTrustConfiguration.java` and `PaymentSecurityConfiguration.java`
- [X] T053 [US2] Wire Checkout/provider/idempotency capabilities behind validated runtime flags in `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentCheckoutConfiguration.java` and record the G5 suite/concurrency results in `specs/021-payment-service-mvp/validation.md`

**Checkpoint**: US2 returns a hosted URL only to the owner and only in a no-store success response;
ambiguous create returns durable `202` and never opens a blind second attempt.

---

## Phase 6 / G6: User Story 3 — Verified Webhook Receipt and Outcome Convergence (Priority: P1)

**Goal**: Verify exact Stripe bytes, acknowledge only a durable receipt, and apply duplicate-safe,
out-of-order-safe provider truth to Payment.

**Independent Test**: Send signed/invalid/duplicate/concurrent/out-of-order events and prove receipt
semantics plus one success-dominant Payment transition without depending on Kafka availability.

- [X] T054 [P] [US3] Add exact raw-body signature, timestamp tolerance, wrong secret/live-mode/expected-API-version, malformed/unsupported event, and redaction tests in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/adapter/in/webhook/StripeWebhookVerifierTests.java` (FR-019, NFR-SEC-002)
- [X] T055 [P] [US3] Add webhook MVC tests for empty `204`, duplicate `204`, invalid `400`, persistence-unavailable `503`, no user envelope, and no sensitive output in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/adapter/in/webhook/StripeWebhookControllerTests.java`
- [X] T056 [US3] Prove receipt-before-ack, unique provider event ID, raw-body non-storage, early event pending, worker lease/crash recovery, and database rollback in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/StripeWebhookReceiptIntegrationTests.java` (FR-020, FR-037)
- [X] T057 [P] [US3] Add outcome-policy tests for paid/unpaid/processing/unknown, duplicate/different-event same outcome, contradictory retrieval, success dominance, and browser no-op in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/application/ApplyProviderOutcomeServiceTests.java`
- [X] T058 [US3] Prove concurrent/out-of-order receipts create one semantic transition/stable fact and never regress success in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/StripeProviderOutcomeIntegrationTests.java` (SC-003)
- [X] T059 [P] [US3] Define verified receipt command/result and provider-outcome application models/ports in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/model/webhook/`, `application/port/in/AcceptProviderEventUseCase.java`, and `ProcessProviderEventUseCase.java`
- [X] T060 [P] [US3] Implement official-library signature verification and allowlisted event extraction without raw-body persistence in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/webhook/StripeWebhookVerifier.java` and `StripeWebhookMapper.java`
- [X] T061 [US3] Implement raw-byte webhook controller and short durable receipt use case in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/webhook/StripeWebhookController.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/service/AcceptProviderEventService.java`
- [X] T062 [US3] Implement receipt claim/processing orchestration, provider retrieval for insufficient/conflicting signals, and atomic success-dominant transition/outbox insertion in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/service/ProcessProviderEventService.java`
- [X] T063 [US3] Implement leased asynchronous receipt driver and configuration in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/scheduling/ProviderEventProcessingJob.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/ProviderEventProcessingConfiguration.java`
- [X] T064 [US3] Add the exact unauthenticated POST-only webhook Gateway route with raw-body/signature pass-through and default-deny coverage in `services/api-gateway/src/main/resources/application.yml` and `services/api-gateway/src/test/java/com/philia/flashsale/gateway/PaymentWebhookGatewayRouteTests.java` (FR-018)
- [X] T065 [US3] Run the G6 webhook/receipt/outcome/Gateway suite and record signature, receipt, duplicate/order, state/outbox counts, commands, and exit status in `specs/021-payment-service-mvp/validation.md`

**Checkpoint**: A Stripe request receives `204` only after durable deduplication; asynchronous
provider truth is monotonic and independent of Kafka availability.

---

## Phase 7 / G7: User Story 3 — Stable Payment Result Outbox (Priority: P1)

**Goal**: Publish every committed Payment result eventually with the exact v1 facts and stable
identity despite Kafka/Schema Registry failures.

**Independent Test**: Seed outcome outbox rows, interrupt broker/Registry/mark-published boundaries,
and prove exact key/schema/headers/identity plus duplicate-safe replay.

- [X] T066 [P] [US3] Add outbox retry, stable snapshot, capped backoff, lease recovery, and sanitized-error tests in `services/payment-service/src/test/java/com/philia/flashsale/payment/outbox/application/PaymentOutboxPublicationServiceTests.java`
- [X] T067 [P] [US3] Add exact outbox-to-`PaymentSucceededV1`/`PaymentFailedV1` mapper tests for envelope, data, decimals, nullable defaults, key, versions, causation/correlation, W3C headers, and forbidden fields in `services/payment-service/src/test/java/com/philia/flashsale/payment/outbox/adapter/out/messaging/kafka/PaymentResultAvroMapperTests.java`
- [X] T068 [US3] Prove multi-worker claim, expired lease, send-before-mark duplicate identity, and requeue after failure in `services/payment-service/src/test/java/com/philia/flashsale/payment/outbox/integration/PaymentOutboxConcurrencyIntegrationTests.java`
- [X] T069 [US3] Verify live subjects, `auto.register.schemas=false`, Kafka order key, headers, both records, broker/Registry outage recovery, and duplicate physical identity in `services/payment-service/src/test/java/com/philia/flashsale/payment/outbox/integration/PaymentResultKafkaIntegrationTests.java` (SC-005)
- [X] T070 [P] [US3] Define immutable outbox application record, claim/update/publish ports, and capped retry policy in `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/application/model/PaymentOutboxEvent.java`, `application/port/`, and `application/service/PaymentOutboxRetryPolicy.java`
- [X] T071 [US3] Implement sequential claim/publish/mark/requeue orchestration in `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/application/service/PaymentOutboxPublicationService.java`
- [X] T072 [P] [US3] Implement immutable snapshot-to-Avro mapping and order-keyed publication in `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/adapter/out/messaging/kafka/PaymentResultAvroMapper.java` and `KafkaPaymentResultPublisher.java`
- [X] T073 [US3] Implement scheduled publisher and validated Kafka producer configuration with idempotent producer settings in `services/payment-service/src/main/java/com/philia/flashsale/payment/outbox/adapter/in/scheduling/PaymentOutboxPublisherJob.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentOutboxConfiguration.java`
- [X] T074 [US3] Run the G7 PostgreSQL/Kafka/Registry suite and record event IDs/versions/keys/subjects, outage recovery, duplicate identity, commands, and exit status in `specs/021-payment-service-mvp/validation.md`

**Checkpoint**: Every established result survives messaging outages and is eventually published as
one stable fact identity; publication never changes Payment truth.

---

## Phase 8 / G8: User Story 4 — Reconciliation, Deadline, and Manual Review (Priority: P1)

**Goal**: Converge ambiguous/stale/deadline-bound money from provider truth without duplicate charges
or guessed failure.

**Independent Test**: Inject create-response loss, webhook delay, provider outage, worker crash,
deadline/expire/paid races, and safe-replay exhaustion and verify one outcome or durable manual review.

- [ ] T075 [P] [US4] Add deterministic recovery-policy tests for 1/3/10/30/60-second backoff, leases, bounded attempts, 23-hour create replay horizon, unknown states, and manual-review escalation in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/application/PaymentRecoveryPolicyTests.java`
- [ ] T076 [P] [US4] Add application tests for same-key create recovery, Session refresh, open-at-deadline expire/retrieve, missing webhook, pre-deadline terminal attempt reopening, and maximum three attempts in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/application/ReconcilePaymentServiceTests.java`
- [ ] T077 [US4] Prove provider-response-loss and process-crash windows converge with one attempt/Session/provider key using PostgreSQL and the deterministic provider in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/CheckoutRecoveryIntegrationTests.java` (FR-029, FR-030)
- [ ] T078 [US4] Prove expiry/payment races, late verified success after failure, higher aggregate version, no regression, no auto-refund, and one stable fact per transition in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentLateSuccessIntegrationTests.java` (FR-025–FR-027)
- [ ] T079 [US4] Prove multi-instance work claiming, stale lease recovery, provider outage, unrecognized state, safe-window exhaustion, and manual-review visibility in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/PaymentRecoveryWorkIntegrationTests.java`
- [ ] T080 [P] [US4] Define recovery commands/results, policy, and `ReconcilePaymentUseCase` in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/model/recovery/`, `application/service/PaymentRecoveryPolicy.java`, and `application/port/in/ReconcilePaymentUseCase.java`
- [ ] T081 [US4] Implement claim-call-converge recovery orchestration with provider calls outside transactions and the same transition/outbox policy as webhook processing in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/service/ReconcilePaymentService.java`
- [ ] T082 [US4] Implement due/stale/deadline work persistence, unique active work, leases, and manual-review state in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/PaymentRecoveryPersistenceAdapter.java`
- [ ] T083 [US4] Implement recovery/deadline scheduled drivers with batch 100, poll 1 second, lease 30 seconds, instance-safe claiming, and disabled-by-property controls in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/scheduling/PaymentRecoveryJob.java` and `PaymentDeadlineJob.java`
- [ ] T084 [US4] Emit bounded recovery/manual-review age, attempt, outcome, and queue metrics from `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/observability/PaymentRecoveryObservability.java` and verify low-cardinality labels in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/observability/PaymentRecoveryObservabilityTests.java`
- [ ] T085 [US4] Run the G8 response-loss/deadline/late-success/multi-worker/manual-review suite and record identities, timelines, outcomes, commands, and exit status in `specs/021-payment-service-mvp/validation.md`

**Checkpoint**: Every injected ambiguity either converges from provider truth or remains durably
actionable; retry exhaustion never invents a financial outcome.

---

## Phase 9 / G9: User Story 5 — Owner Payment Queries (Priority: P2)

**Goal**: Expose durable Payment status by Payment or Order identity to only the owner through Gateway,
without depending on Stripe/Kafka and without leaking Checkout/provider data.

**Independent Test**: Seed two owners, query both routes through MVC/Gateway during provider/broker
outage, and prove owner success, foreign/absent equivalence, safe envelopes, and p95 target readiness.

- [ ] T086 [P] [US5] Add application query tests for owner Payment/Order lookup, foreign/absent equivalence, all public statuses, and provider/Kafka-independent reads in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/application/PaymentQueryServiceTests.java`
- [ ] T087 [P] [US5] Add PostgreSQL owner-projection tests for `(paymentId,userId)` and `(orderId,userId)`, attempt count, safe failure mapping, and no provider/internal-field exposure in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/integration/OwnedPaymentQueryIntegrationTests.java`
- [ ] T088 [P] [US5] Add MVC tests for both GET routes, `ApiResponse`, stable errors, `X-Trace-Id`, owner masking, status mapping, and absence of URL/provider/secrets in `services/payment-service/src/test/java/com/philia/flashsale/payment/payment/adapter/in/web/PaymentQueryControllerTests.java`
- [ ] T089 [P] [US5] Add Gateway authenticated Payment-route tests for forwarding, method/path authorization, trace/error pass-through, webhook separation, and default deny in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/PaymentGatewayRouteConfigurationTests.java`
- [ ] T090 [P] [US5] Define owner query/result models and ports in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/model/query/`, `application/port/in/GetOwnedPaymentUseCase.java`, and `application/port/out/LoadOwnedPaymentPort.java`
- [ ] T091 [US5] Implement non-enumerating owner query orchestration in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/application/service/PaymentQueryService.java`
- [ ] T092 [US5] Implement owner-scoped JPA projections/repository mapping in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/out/persistence/jpa/OwnedPaymentQueryJpaAdapter.java` and `repository/OwnedPaymentQueryJpaRepository.java`
- [ ] T093 [P] [US5] Implement safe Payment detail DTO and query-only mapper in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/web/response/PaymentDetailsResponse.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/web/PaymentQueryWebMapper.java`
- [ ] T094 [US5] Implement GET-by-Payment and GET-by-Order endpoints in `services/payment-service/src/main/java/com/philia/flashsale/payment/payment/adapter/in/web/PaymentQueryController.java`
- [ ] T095 [US5] After T064, add authenticated `/api/v1/payments/**` Gateway routing and target configuration in `services/api-gateway/src/main/resources/application.yml` while preserving the exact webhook exception
- [ ] T096 [US5] Wire query capability in `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentQueryConfiguration.java`
- [ ] T097 [US5] Run the G9 application/PostgreSQL/MVC/security/Gateway suite during simulated Kafka/Stripe outage and record owner/foreign/absent outcomes, fields, headers, commands, and exit status in `specs/021-payment-service-mvp/validation.md` (SC-006)

**Checkpoint**: US5 returns only PostgreSQL-backed owner state through Gateway; Stripe/browser/Kafka
availability cannot make a healthy owner query lie or leak another user's Payment.

---

## Phase 10 / G10: Observability, Root Infrastructure, End-to-End Evidence, and Completion

**Purpose**: Make the full feature secure, diagnosable, reproducible, performance-checked, and ready
for the later Order Saga integration without silently enabling that integration.

- [ ] T098 [P] Implement bounded observation names, timers/counters/gauges, trace/MDC lifecycle, safe structured context, and redaction helpers in `services/payment-service/src/main/java/com/philia/flashsale/payment/observability/PaymentObservability.java`, `PaymentObservationNames.java`, `PaymentTraceContext.java`, and `services/payment-service/src/main/java/com/philia/flashsale/payment/websupport/context/PaymentRequestContext.java` (NFR-OBS-001)
- [ ] T099 [P] Implement PostgreSQL/config-gated readiness plus non-gating Kafka/Schema Registry/Stripe/recovery/outbox component health in `services/payment-service/src/main/java/com/philia/flashsale/payment/observability/PaymentReadinessHealthIndicator.java` and `services/payment-service/src/main/java/com/philia/flashsale/payment/configuration/PaymentReadinessConfiguration.java` (FR-042)
- [ ] T100 Verify bounded labels, trace propagation, readiness during broker/provider outage, liveness, no manual Prometheus registry, and zero sensitive telemetry in `services/payment-service/src/test/java/com/philia/flashsale/payment/observability/PaymentObservabilityTests.java`, `PaymentReadinessIntegrationTests.java`, and `PaymentTelemetryRedactionTests.java`
- [ ] T101 Add manual-review/recovery-age/outbox-lag alert rules only after T084 and T098–T100 metrics exist, plus operator actions and safe links in `infra/monitoring/prometheus/rules/payment-service-alerts.yml` and `docs/runbooks/payment-service-recovery.md`
- [ ] T102 Add test-first verification for the configured Stripe webhook API-version contract and Payment-container JVM DNS cache TTL of 60 seconds in `services/payment-service/src/test/java/com/philia/flashsale/payment/integration/StripeRuntimePlatformContractTests.java`
- [ ] T103 Add Payment database migration/runtime, Kafka/Registry, JWT, expected Stripe webhook API version, Stripe test-secret placeholders, service-specific JVM DNS cache TTL 60 seconds, health, worker, and developer-port wiring to `services/payment-service/src/main/resources/application.yml`, `infra/docker/compose.yml`, `infra/docker/compose.dev.yml`, and non-secret `infra/docker/.env.example`; notify the project owner of required variable names and never inspect or mutate `infra/docker/.env` (FR-043)
- [ ] T104 Add PowerShell parser/rendered Compose/topic/schema/security ownership tests in `services/payment-service/src/test/java/com/philia/flashsale/payment/integration/PaymentInfrastructureContractTests.java`
- [ ] T105 Implement owner/foreign/command/Checkout/webhook/PostgreSQL/Kafka reconciliation smoke and bounded failure matrix in `infra/docker/smoke/feature-021-payment.ps1`, emitting only verified `FEATURE_021_SMOKE=PASS` and `FEATURE_021_FAILURE_MATRIX=PASS`
- [ ] T106 [P] Add warm-up plus staged owner-query/service-local Checkout k6 profiles with p50/p95/p99, p95 thresholds, zero unexpected errors, non-enumeration checks, and token-file hygiene in `load-tests/payment-service/feature-021-payment.js` (NFR-PERF-001, NFR-PERF-002)
- [ ] T107 Notify the project owner of every missing secret variable and pause until confirmation, then verify the Stripe test webhook endpoint uses the SDK-compatible configured API version and execute the 100-concurrent identity profile, full failure matrix, Redis-unavailable proof, staged k6 profiles, and bounded Stripe test-mode/CLI E2E through Gateway without reading or rendering `infra/docker/.env`; record variable names but never values plus counts, percentiles, recovery/manual-review, and redacted evidence in `specs/021-payment-service-mvp/validation.md` (SC-002, SC-004, SC-007, SC-008, SC-010)
- [ ] T108 Run secret/card/raw-body/Checkout-URL scans over tracked APIs, persistence, events, fixtures, logs, metrics, traces, Git diff, and evidence without opening `infra/docker/.env`; verify the file remains ignored/untracked and record zero prohibited findings in `specs/021-payment-service-mvp/validation.md` (FR-038, FR-039, FR-043, NFR-SEC-003, NFR-SEC-004)
- [ ] T109 Run `./mvnw -pl contracts/kafka-avro-contracts,services/payment-service,services/api-gateway -am verify` and record module/test counts and exit status in `specs/021-payment-service-mvp/validation.md`
- [ ] T110 Run `./mvnw clean verify`, `git diff --check`, Markdown-link validation, feature-pointer validation, ignored/untracked `.env` path validation without reading the file, and root/service infrastructure ownership audit; reconcile every UC/AC, FR, NFR, and SC and mark Feature 021 Verified only after all gates pass in `specs/021-payment-service-mvp/spec.md`, `plan.md`, `tasks.md`, and `validation.md` (SC-009, SC-010)

**Checkpoint**: Feature 021 is complete only when all required evidence is green. This does not mark
the later Order command producer/result consumer/reservation Saga work complete.

---

## Dependencies and Execution Order

### Group dependency graph

```text
G1 Contracts/module
  -> G2 Domain/PostgreSQL foundation
    -> G3 US1 atomic acceptance
      -> G4 US1 Kafka ingress
      -> G5 US2 hosted Checkout
        -> G6 US3 webhook/outcome convergence
          -> G7 US3 outbox publication
        -> G8 US4 reconciliation/deadline
      -> G9 US5 owner queries
        -> G10 observability/infra/end-to-end evidence
```

- G4 requires G3 but can run in parallel with G5 after G3.
- T032 additionally requires the G1 T007 topic/schema provisioning workflow.
- G5 requires G2 and a seeded Payment; its application tests need not wait for live Kafka G4.
- G6 requires G5's provider correlation and G3's Payment aggregate.
- G7 requires the outcome/outbox insertion boundary from G6.
- G8 requires G5 provider operations and shares the G6 outcome policy; it may begin after those
  specific boundaries are stable and can proceed alongside G7.
- G9 requires the G2 projection and security foundation, not a live provider/broker; its application,
  persistence, and Payment MVC work may proceed alongside G4–G8 after G2, while Gateway task T095
  waits for T064 because both intentionally update the shared Gateway configuration.
- G10 integrates every selected story and is the only completion-evidence group.
- T101 alert/runbook work waits for the recovery and service-wide metrics in T084 and T098–T100;
  T102 is the failing platform contract implemented by T103.

### User story dependencies

- **US1 (P1)**: G1 → G2 → G3 → G4. Minimum durable Payment consumer; no Stripe call.
- **US2 (P1)**: G1 → G2 → G3 → G5. Requires a seeded/accepted Payment but not live command ingress.
- **US3 (P1)**: G1 → G2 → G3 → G5 → G6 → G7. Establishes and publishes provider truth.
- **US4 (P1)**: G1 → G2 → G3 → G5 → G6 → G8. Reuses the exact outcome policy.
- **US5 (P2)**: G1 → G2 → G9. Independently testable from seeded PostgreSQL state.

### Parallel opportunities

- G1 schema work, architecture skeleton, and property design use independent files after T001.
- G2 domain tests, migration work, and three JPA entity families can proceed independently before
  repository/adaptor integration.
- After G3, G4 Kafka ingress, G5 Checkout, and G9 application/persistence/query adapters can proceed
  in parallel; T095 is serialized after T064 for the shared Gateway YAML.
- G6 signature/controller tests and outcome-policy tests are independent before orchestration.
- After G6, G7 publication and G8 recovery own separate scheduled drivers and can proceed in parallel.
- G10 observability, infrastructure scripts, and load profile can be prepared independently, but
  execution/evidence tasks wait for the complete runtime.

## Parallel Examples

### After G3

```text
Track A: T030–T037 — PaymentRequested Kafka ingress
Track B: T038–T053 — owner Checkout and Stripe adapter
Track C: T086–T097 — owner query projection and Gateway route
```

### After G6

```text
Track A: T066–T074 — Payment result outbox publication
Track B: T075–T085 — reconciliation, deadline, and manual review
```

## Implementation Strategy

### Safe MVP progression

1. Complete G1–G2 and validate the architecture/database foundation.
2. Complete G3 independently; one command input at the application port creates one Payment.
3. Complete G4; this is the first operational Kafka-driven Payment increment.
4. Complete G5; Checkout is safe but no browser redirect can establish success.
5. Complete G6–G8; provider truth, publication, and recovery make the money flow correct.
6. Complete G9 for UI-safe reads.
7. Complete G10 and mark Verified only from recorded evidence.

### Commit boundaries

Prefer one branch/commit/PR per completed group G1–G10. Never check a task or group merely because
code exists: required tests and `validation.md` evidence must pass. Rebase each new group from the
latest `develop` after the previous group merges.

## Notes

- Domain/application code must not import Spring MVC, JPA, Kafka, Avro, Stripe SDK, or Prometheus
  registry implementation types.
- Provider calls and Kafka publication never occur inside a long-running aggregate transaction.
- Do not add refund, capture command, delayed payment method, retention cleanup, Redis correctness,
  global Saga, Order mutation, or reservation mutation behavior.
- Do not enable Order's future `PaymentRequested.v1` producer until the separate approved Purchase
  Saga integration feature is implemented and validated.
- Stop and update approved artifacts if implementation reveals an absent financial, security,
  deadline, retry, idempotency, or compensation rule.
