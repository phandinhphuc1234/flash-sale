# Tasks: Order Service Core MVP

**Status**: Approved for implementation — G1–G3 / T001–T029
**Input**: Design documents from `/specs/020-order-service-mvp/`
**Prerequisites**: Approved `spec.md`, plan, tasks, and contracts for G1–G3; later groups remain
gated by their checkpoints and required project-owner/architecture/security review.

**Tests**: All domain, architecture, PostgreSQL, Kafka/Schema Registry, consumer redelivery/
concurrency, outbox recovery, HTTP/security, failure-matrix, Compose smoke, performance, module, and
full-reactor validations required by the specification and plan are mandatory.

**Organization**: Tasks are grouped into coherent implementation groups and user-story phases.
Within a story, contract/test tasks precede the production behavior they protect. A checked task is
a completion ledger entry only after the required evidence is recorded in `validation.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it owns different files and has no incomplete dependency.
- **[US1]**, **[US2]**, **[US3]**: Maps to the approved user story in `spec.md`.
- Every task contains the exact repository path it owns.

## Phase 1 / G1: Module, Contract, and Test Foundation

**Purpose**: Establish approved dependencies, protocol generation, configuration, and architecture
guards before business implementation.

- [x] T001 Update `services/order-service/pom.xml` with the plan-approved `common-web`, `kafka-avro-contracts`, Validation, JPA, Security/Resource Server, Spring Kafka, Confluent serializer, OTel tracing bridge, PostgreSQL runtime, Security/Kafka/Testcontainers, and ArchUnit dependencies while adding no Redis, Feign, Payment SDK, MapStruct, or distributed-lock dependency
- [x] T002 [P] Add `OrderCreatedV1` schema at `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.order.events.v1/OrderCreatedV1.avsc` exactly matching `contracts/order-created-kafka.md`
- [x] T003 Add generated-record, logical-type, forbidden-field, subject-strategy, and backward-compatibility tests at `contracts/kafka-avro-contracts/src/test/java/com/philia/flashsale/contract/order/event/OrderCreatedSchemaTests.java`
- [x] T004 Add typed runtime properties for inbound Kafka, outbox relay, JWT trust, and runtime switches in `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderKafkaProperties.java`, `OrderOutboxProperties.java`, `OrderJwtProperties.java`, and `OrderRuntimeProperties.java`
- [x] T005 Configure PostgreSQL/JPA validation, Kafka Avro consumer/producer settings, retry/DLT names, JWT trust values, Actuator endpoints, bounded percentiles, and feature properties in `services/order-service/src/main/resources/application.yml`
- [x] T006 [P] Add shared PostgreSQL/Kafka integration-test support and opt-in Registry conditions in `services/order-service/src/test/java/com/philia/flashsale/order/support/PostgreSqlIntegrationTestSupport.java`, `KafkaIntegrationTestSupport.java`, and `OrderIntegrationTestCondition.java`
- [x] T007 Add inward-dependency and boundary-model rules in `services/order-service/src/test/java/com/philia/flashsale/order/architecture/OrderArchitectureTests.java`, including bans on Avro/Kafka/JPA/Spring imports in domain/application and adapter-to-adapter implementation coupling
- [x] T008 Create the validation ledger with planned commands and empty evidence slots at `specs/020-order-service-mvp/validation.md`

**Checkpoint**: The Order module compiles against generated contracts, typed configuration loads,
and architecture tests can guard all subsequent groups.

---

## Phase 2 / G2: PostgreSQL and Local Transaction Foundation

**Purpose**: Create the service-owned durable schema and prove migration/constraint correctness.

**⚠️ CRITICAL**: User-story persistence work does not begin until this group is complete.

- [x] T009 Implement the immutable Liquibase SQL changeset for `orders`, `order_lines`, `order_consumer_inbox`, `order_outbox_events`, constraints, indexes, and development rollback in `services/order-service/src/main/resources/db/changelog/changes/001-create-order-core-schema.sql`
- [x] T010 Include only the Order Core changeset in `services/order-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- [x] T011 Verify clean migration, Hibernate validation, exact numeric/time types, check/unique/FK/index behavior, and development rollback against PostgreSQL in `services/order-service/src/test/java/com/philia/flashsale/order/integration/OrderSchemaMigrationIntegrationTests.java`
- [x] T012 [P] Add deterministic PostgreSQL advisory-lock key derivation with collision-domain separation in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/PostgreSqlOrderIdentityLockKey.java` and verify it in `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/PostgreSqlOrderIdentityLockKeyTests.java`
- [x] T013 [P] Verify property constraints, unsupported blank/negative settings, and default values in `services/order-service/src/test/java/com/philia/flashsale/order/configuration/OrderPropertiesTests.java`
- [x] T014 Run the contract/module compile plus migration/architecture foundation tests and record command, scope, exit status, and test counts in `specs/020-order-service-mvp/validation.md`

**Checkpoint**: `order_db` schema and service foundations are independently verifiable; no Kafka
consumer or public endpoint is active yet.

---

## Phase 3 / G3: User Story 1 — Create One Durable Order (Priority: P1) 🎯 MVP

**Goal**: A valid accepted purchase establishes exactly one `PENDING_PAYMENT` Order, one line, one
inbox identity, and one stable Order-created outbox identity in one PostgreSQL transaction.

**Independent Test**: Invoke the application/persistence boundary with valid, repeated, concurrent,
and contradictory accepted-purchase commands and reconcile the four durable components without
starting Kafka or HTTP.

### Tests for User Story 1

- [x] T015 [P] [US1] Add Order invariant and exact-money tests for positive quantity/price, scale-4 arithmetic, one line, immutable snapshot, `PENDING_PAYMENT`, and reservation temporal ordering in `services/order-service/src/test/java/com/philia/flashsale/order/order/domain/OrderDomainTests.java`
- [x] T016 [P] [US1] Add canonical fingerprint tests covering field order, UUID/instant/decimal normalization, changed business fields, and excluded trace/Kafka-position fields in `services/order-service/src/test/java/com/philia/flashsale/order/order/application/AcceptedPurchaseFingerprintTests.java`
- [x] T017 [P] [US1] Add application use-case tests for created, event replay, business replay, conflict, retryable persistence failure, stable identities, and no adapter/framework leakage in `services/order-service/src/test/java/com/philia/flashsale/order/order/application/CreateOrderFromAcceptedPurchaseServiceTests.java`

### Domain and Application

- [x] T018 [P] [US1] Implement exact money and Order status values in `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/valueobject/Money.java` and `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/OrderStatus.java`
- [x] T019 [US1] Implement the `Order` Aggregate, one contained `OrderLine`, creation factory, immutable snapshot rules, and meaningful invariant failure in `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/Order.java`, `OrderLine.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/exception/InvalidOrderException.java`
- [x] T020 [P] [US1] Define accepted-purchase command, persistence candidate, and created/replayed/conflict result models in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/command/CreateOrderFromAcceptedPurchaseCommand.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/model/OrderCreationCandidate.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/application/result/OrderCreationResult.java`
- [x] T021 [P] [US1] Define business-named input/output boundaries in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/in/CreateOrderFromAcceptedPurchaseUseCase.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/out/PersistOrderCreationPort.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/out/GenerateOrderIdentityPort.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/out/GenerateOrderNumberPort.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/out/CurrentTimePort.java`
- [x] T022 [US1] Implement canonical SHA-256 business fingerprinting and Order creation orchestration in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/usecase/AcceptedPurchaseFingerprintService.java` and `CreateOrderFromAcceptedPurchaseService.java`

### Atomic Persistence

- [x] T023 [P] [US1] Implement separate Order, line, inbox, and creation-outbox JPA representations in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/entity/OrderJpaEntity.java`, `OrderLineJpaEntity.java`, `OrderConsumerInboxJpaEntity.java`, and `OrderCreationOutboxJpaEntity.java`
- [x] T024 [US1] Implement Spring Data repositories and persistence mapper in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/repository/OrderJpaRepository.java`, `OrderConsumerInboxJpaRepository.java`, `OrderCreationOutboxJpaRepository.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/mapper/OrderPersistenceMapper.java`
- [x] T025 [US1] Implement deterministic PostgreSQL transaction-scoped purchase/reservation identity arbitration, equivalence reload, unique backstops, and atomic Order+line+inbox+outbox commit in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/OrderCreationJpaAdapter.java`
- [x] T026 [US1] Implement deterministic clock, UUID, and collision-safe Order-number adapters and wire them with the Order creation use case and atomic persistence capability without adapter-to-adapter calls in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/identity/OrderIdentityAdapter.java`, `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderFoundationConfiguration.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderCreationConfiguration.java`
- [x] T027 [US1] Verify exact snapshot persistence, full rollback, event replay, different-event equivalent replay, contradictory identities, Order-number collision recovery, and outbox identity stability against PostgreSQL in `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/AcceptedPurchasePersistenceIntegrationTests.java`
- [x] T028 [US1] Prove 100 concurrent same/different-event-ID equivalent deliveries across multiple workers create one Order, line, and logical outbox fact, while contradictory races never mutate the winner in `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/AcceptedPurchaseConcurrencyIntegrationTests.java`
- [x] T029 [US1] Run the US1 domain/application/PostgreSQL suite and record identities, row counts, concurrency result, commands, and exit status in `specs/020-order-service-mvp/validation.md`

**Checkpoint**: US1 works without Kafka or HTTP: one accepted-purchase command becomes one durable,
idempotent Order result and pending publication identity.

---

## Phase 4 / G4: User Story 1 — PurchaseAccepted Kafka Boundary (Priority: P1)

**Goal**: Safely drive US1 from the implemented Feature 019 Kafka contract with bounded retry,
consumer-specific DLT, trace restoration, and acknowledgement after local commit.

**Independent Test**: Deliver generated `PurchaseAcceptedV1` records through a real broker and prove
mapping, key/envelope validation, commit-before-ack replay, retry/DLT routing, and database
reconciliation.

### Tests for the Kafka Boundary

- [ ] T030 [P] [US1] Add Avro-to-command mapping tests for all fields, decimal/instant normalization, key/envelope mismatch, invalid values, trace headers, and forbidden infrastructure leakage in `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/in/messaging/kafka/PurchaseAcceptedAvroMapperTests.java`
- [ ] T031 [P] [US1] Add listener behavior tests for created/replayed acknowledgement, conflict classification, retryable storage failure, poison input, safe logging, and no acknowledgement before commit in `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/in/messaging/kafka/PurchaseAcceptedKafkaConsumerTests.java`
- [ ] T032 [US1] Add real Kafka/Registry integration tests for group/key/header handling, duplicate redelivery, process-after-commit-before-ack recovery, three transient retries, non-retryable/exhausted DLT delivery, and operator replay in `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/PurchaseAcceptedConsumerIntegrationTests.java` and `PurchaseAcceptedRetryDltIntegrationTests.java`

### Implementation for the Kafka Boundary

- [ ] T033 [P] [US1] Implement the inbound Avro/Kafka boundary mapper and typed record failures in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/messaging/kafka/PurchaseAcceptedAvroMapper.java`, `PurchaseAcceptedRecordException.java`, and `PurchaseAcceptedConflictException.java`
- [ ] T034 [US1] Implement `PurchaseAcceptedKafkaConsumer` with key/envelope validation, trace context restoration, use-case invocation, and post-commit manual acknowledgement in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/messaging/kafka/PurchaseAcceptedKafkaConsumer.java`
- [ ] T035 [US1] Configure Avro deserialization, `order-purchase-accepted-v1`, manual acknowledgement, classified 1/3/10-second retries, safe DLT recoverer, and disabled-by-property test/runtime controls in `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderKafkaConsumerConfiguration.java`
- [ ] T036 [US1] Run the US1 live Kafka/Registry suite and record topic/group/key, retry/DLT outcomes, Order/inbox/outbox reconciliation, commands, and exit status in `specs/020-order-service-mvp/validation.md`

**Checkpoint**: US1 is an independently operational MVP: Feature 019's implemented event creates
one durable Order under duplicate, crash, and poison-record conditions.

---

## Phase 5 / G5: User Story 2 — Publish the Committed Order Fact (Priority: P1)

**Goal**: Reliably publish the stable `OrderCreatedV1` fact after Order commit without coupling Order
durability to Kafka availability.

**Independent Test**: Commit a prepared outbox row, interrupt Registry/Kafka and publisher state
updates, recover dependencies/workers, and prove publication preserves event identity, key, payload,
correlation, causation, and trace headers.

### Tests for User Story 2

- [ ] T037 [P] [US2] Add outbox retry, stable snapshot, lease recovery, and sanitized-error unit tests in `services/order-service/src/test/java/com/philia/flashsale/order/outbox/application/OrderOutboxPublicationServiceTests.java` and `OrderOutboxRetryPolicyTests.java`
- [ ] T038 [P] [US2] Add outbox-to-`OrderCreatedV1` mapper tests for exact envelope/data/logical types, one item, forbidden fields, key, causation/correlation, and W3C headers in `services/order-service/src/test/java/com/philia/flashsale/order/outbox/adapter/out/messaging/kafka/OrderCreatedAvroMapperTests.java`
- [ ] T039 [US2] Prove multiple workers claim each row once, expired leases recover, send-before-mark duplicates retain identity, and failures return to due retry against PostgreSQL in `services/order-service/src/test/java/com/philia/flashsale/order/outbox/integration/OrderOutboxConcurrencyIntegrationTests.java`
- [ ] T040 [US2] Verify live Registry subject compatibility, `auto.register.schemas=false`, Kafka key/headers/record, Kafka-down and Registry-down retry, and duplicate physical publication identity in `services/order-service/src/test/java/com/philia/flashsale/order/outbox/integration/OrderCreatedKafkaIntegrationTests.java`

### Implementation for User Story 2

- [ ] T041 [P] [US2] Define outbox application record, claim/update/publish ports, and capped retry policy in `services/order-service/src/main/java/com/philia/flashsale/order/outbox/application/model/OrderOutboxEvent.java`, `services/order-service/src/main/java/com/philia/flashsale/order/outbox/application/port/ClaimOrderOutboxEventsPort.java`, `UpdateOrderOutboxPublicationPort.java`, `PublishOrderCreatedPort.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/outbox/application/usecase/OrderOutboxRetryPolicy.java`
- [ ] T042 [US2] Implement outbox orchestration that claims due events, publishes sequentially, marks success, and requeues sanitized failures without discarding committed facts in `services/order-service/src/main/java/com/philia/flashsale/order/outbox/application/usecase/OrderOutboxPublicationService.java`
- [ ] T043 [P] [US2] Implement leased `FOR UPDATE SKIP LOCKED` claim/update mapping in `services/order-service/src/main/java/com/philia/flashsale/order/outbox/adapter/out/persistence/OrderOutboxPersistenceAdapter.java`
- [ ] T044 [P] [US2] Implement immutable snapshot-to-Avro mapping and keyed Kafka publication in `services/order-service/src/main/java/com/philia/flashsale/order/outbox/adapter/out/messaging/kafka/OrderCreatedAvroMapper.java` and `KafkaOrderCreatedPublisher.java`
- [ ] T045 [US2] Implement the scheduled driving adapter and wire 500-ms polling, batch 100, 30-second leases, worker identity, and runtime enablement in `services/order-service/src/main/java/com/philia/flashsale/order/outbox/adapter/in/scheduling/OrderOutboxPublisherJob.java` and `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderOutboxConfiguration.java`
- [ ] T046 [US2] Run the US2 PostgreSQL/Kafka/Registry suite and record event IDs, keys, subject/version, retry/recovery evidence, commands, and exit status in `specs/020-order-service-mvp/validation.md`

**Checkpoint**: US2 publishes every committed Order-created fact eventually; Kafka/Registry outage
does not remove the Order or change its publication identity.

---

## Phase 6 / G6: User Story 3 — Query Only My Orders (Priority: P2)

**Goal**: Authenticated shoppers retrieve/list only their committed Orders through Gateway using the
shared HTTP contract and non-enumerating authorization.

**Independent Test**: Seed Orders for two users and verify owner detail/list, deterministic bounded
pagination, foreign/unknown equivalence, JWT trust failures, trace/no-store headers, and absent
mutation routes through controller and Gateway tests.

### Tests for User Story 3

- [ ] T047 [P] [US3] Add application query tests for owner detail, absent/foreign equivalence, page bounds, empty pages, deterministic sort, and Kafka-independent reads in `services/order-service/src/test/java/com/philia/flashsale/order/order/application/OrderQueryServiceTests.java`
- [ ] T048 [P] [US3] Add PostgreSQL owner projection tests for `id+userId`, user-scoped pagination, stable tie-breaking, line mapping, and no inbox/outbox exposure in `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/OwnedOrderQueryPersistenceIntegrationTests.java`
- [ ] T049 [P] [US3] Add MVC contract tests for `ApiResponse`, `PageResponse`, errors, validation, 404 non-enumeration, no-store, `X-Trace-Id`, forbidden `userId` input, and absent mutation methods in `services/order-service/src/test/java/com/philia/flashsale/order/order/adapter/in/web/OrderQueryControllerTests.java`
- [ ] T050 [P] [US3] Add JWT issuer/audience/type/subject/signature/expiry and owner-access tests in `services/order-service/src/test/java/com/philia/flashsale/order/security/OrderJwtTrustConfigurationTests.java` and `OrderPublicSecurityTests.java`

### Application, Persistence, and Web

- [ ] T051 [P] [US3] Define owner detail/list queries, results, and input/output ports in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/query/GetOwnedOrderQuery.java`, `ListOwnedOrdersQuery.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/result/OrderDetailsResult.java`, `OrderSummaryResult.java`, `OrderPageResult.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/in/GetOwnedOrderUseCase.java`, `ListOwnedOrdersUseCase.java`, `services/order-service/src/main/java/com/philia/flashsale/order/order/application/port/out/LoadOwnedOrderPort.java`, and `ListOwnedOrdersPort.java`
- [ ] T052 [US3] Implement owner query orchestration and application not-found semantics in `services/order-service/src/main/java/com/philia/flashsale/order/order/application/usecase/OrderQueryService.java` and `services/order-service/src/main/java/com/philia/flashsale/order/order/application/exception/OrderNotFoundException.java`
- [ ] T053 [US3] Implement owner-scoped JPA detail/page projections and mapping in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/OwnedOrderQueryJpaAdapter.java` and `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/out/persistence/jpa/repository/OwnedOrderQueryJpaRepository.java`
- [ ] T054 [P] [US3] Implement Order detail/summary/item HTTP responses and boundary mapping in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/web/response/OrderDetailsResponse.java`, `OrderSummaryResponse.java`, `OrderItemResponse.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/web/OrderWebMapper.java`
- [ ] T055 [US3] Implement GET-only owner detail/list endpoints with bounded pagination, shared envelopes, no-store, and trace headers in `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/web/OrderQueryController.java`
- [ ] T056 [US3] Implement stable Order error codes, exception classification, MVC advice, authentication entry point, and access-denied handler in `services/order-service/src/main/java/com/philia/flashsale/order/websupport/error/OrderErrorCode.java`, `OrderExceptionClassifier.java`, `OrderHttpExceptionHandler.java`, `OrderAuthenticationEntryPoint.java`, and `OrderAccessDeniedHandler.java`
- [ ] T057 [P] [US3] Implement local public JWT decoder validation and GET-only route authorization in `services/order-service/src/main/java/com/philia/flashsale/order/security/OrderJwtTrustConfiguration.java` and `OrderSecurityConfiguration.java`
- [ ] T058 [US3] Wire owner query application/persistence/web capabilities in `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderQueryConfiguration.java`
- [ ] T059 [US3] Add the authenticated `order-public` GET route and target URL in `services/api-gateway/src/main/resources/application.yml`, then verify forwarding, auth, methods, trace, and upstream error preservation in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/OrderGatewayRouteTests.java`
- [ ] T060 [US3] Run the US3 application/PostgreSQL/MVC/security/Gateway suite and record owner/foreign/unknown outcomes, page metadata, headers, commands, and exit status in `specs/020-order-service-mvp/validation.md`

**Checkpoint**: US3 is independently demonstrable against seeded committed Orders even while Kafka
or Schema Registry is unavailable.

---

## Phase 7 / G7: Observability, Readiness, and Root Infrastructure

**Purpose**: Make the three stories diagnosable and runnable in the shared local topology without
moving service-owned artifacts into root infrastructure.

- [ ] T061 [P] Implement bounded observation names, counters/timers/gauges, W3C consumer/request context, and structured-log helpers in `services/order-service/src/main/java/com/philia/flashsale/order/observability/OrderObservationNames.java`, `OrderObservability.java`, `OrderTraceContext.java`, `OrderTraceHeaderFilter.java`, and `services/order-service/src/main/java/com/philia/flashsale/order/websupport/context/OrderRequestContext.java`
- [ ] T062 [P] Implement PostgreSQL readiness plus consumer/outbox health signals that keep broker outages from disabling committed queries in `services/order-service/src/main/java/com/philia/flashsale/order/observability/OrderReadinessHealthIndicator.java` and `services/order-service/src/main/java/com/philia/flashsale/order/configuration/OrderReadinessConfiguration.java`
- [ ] T063 Verify bounded labels, trace/MDC lifecycle, Kafka-to-outbox propagation, liveness/readiness semantics, query availability during Kafka outage, and absence of a manual Prometheus registry in `services/order-service/src/test/java/com/philia/flashsale/order/observability/OrderObservabilityTests.java` and `OrderReadinessIntegrationTests.java`
- [ ] T064 Add idempotent provisioning for `flashsale.order.events.v1` and `flashsale.order.purchase-accepted.dlt.v1` in `infra/docker/kafka/init-order-topics.sh`, including exact local partition/replication verification
- [ ] T065 Add controlled `OrderCreatedV1` subject compatibility/registration/check-only workflow in `infra/docker/schema-registry/register-order-schemas.ps1`
- [ ] T066 Add `order_db`, Kafka/Registry, JWT, runtime, health, migration, and dependency wiring for `order-service` plus one-off `order-migration` in `infra/docker/compose.yml`, developer port wiring in `infra/docker/compose.dev.yml`, and documented non-secret defaults in `infra/docker/.env.example`
- [ ] T067 Add PowerShell parser and rendered Compose configuration tests for the new scripts/topology in `services/order-service/src/test/java/com/philia/flashsale/order/integration/OrderInfrastructureContractTests.java`

**Checkpoint**: Order exposes bounded runtime signals and the shared Compose topology can migrate,
start, provision, and diagnose it reproducibly.

---

## Phase 8 / G8: End-to-End, Failure, Performance, and Completion Evidence

**Purpose**: Prove the complete Feature 019 -> Order -> query/event flow and close every required
verification gate.

- [ ] T068 Implement the end-to-end owner/foreign/wrong-audience fixture and PostgreSQL/Kafka reconciliation in `infra/docker/smoke/feature-020-order.ps1`, ending only with `FEATURE_020_SMOKE=PASS` after OrderCreated and owner-query evidence pass
- [ ] T069 Extend `infra/docker/smoke/feature-020-order.ps1` with bounded PostgreSQL/process/Kafka/Registry/publisher/poison/conflict recovery scenarios and the `FEATURE_020_FAILURE_MATRIX=PASS` marker
- [ ] T070 [P] Add the owner detail-query k6 profile with token-file hygiene, p50/p95/p99, p95-under-200-ms threshold, zero unexpected errors, and owner/non-enumeration checks in `load-tests/order-service/order-query.js`
- [ ] T071 [P] Add the opt-in delivered-event-to-commit nominal profile and database reconciliation in `services/order-service/src/test/java/com/philia/flashsale/order/order/integration/OrderConsumerPerformanceIntegrationTests.java`
- [ ] T072 Execute the Feature 020 Compose smoke, failure matrix, duplicate/concurrency profile, query k6 profile, and event-to-commit profile, then record environment, commands, exit statuses, row/event identities, counts, p50/p95/p99, and recovery results in `specs/020-order-service-mvp/validation.md`
- [ ] T073 Run `./mvnw -pl services/order-service -am verify` and affected Gateway/contract module checks, then record module/test counts and exit status in `specs/020-order-service-mvp/validation.md`
- [ ] T074 Run `./mvnw clean verify` for the full reactor and record all-module result plus any intentional opt-in skip in `specs/020-order-service-mvp/validation.md`
- [ ] T075 Run `git diff --check`, validate Markdown relative links, verify `.specify/feature.json` still targets Feature 020, audit root/service infrastructure ownership, and record results in `specs/020-order-service-mvp/validation.md`
- [ ] T076 Reconcile every UC/AC, FR, NFR, and SC against tests/contracts/evidence; update Feature 020 status/history only after all required gates pass in `specs/020-order-service-mvp/spec.md`, `plan.md`, `tasks.md`, and `validation.md`

**Checkpoint**: Feature 020 is complete only when smoke, failure, performance, module, and full
reactor evidence pass; a checked task without evidence does not satisfy this checkpoint.

---

## Dependencies and Execution Order

### Group Dependencies

```text
G1 Module/contracts/test foundation
  -> G2 PostgreSQL foundation
    -> G3 US1 domain + atomic persistence
      -> G4 US1 Kafka consumer
      -> G5 US2 outbox publication
      -> G6 US3 owner queries
        -> G7 observability + root infrastructure
          -> G8 end-to-end completion evidence
```

- G4 requires the G3 persistence/use-case boundary.
- G5 requires the G3 creation outbox schema/snapshot but not the G4 listener; it may proceed in
  parallel with G4 after G3.
- G6 requires the G3 Order persistence model but does not require Kafka or outbox runtime; it may
  proceed in parallel with G4/G5 after G3.
- G7 integrates all selected stories and blocks full Compose evidence.
- G8 runs only after G4, G5, G6, and G7 checkpoints pass.

### User Story Dependencies

- **US1 (P1)**: G1 -> G2 -> G3 -> G4. This is the minimum viable Order consumer.
- **US2 (P1)**: G1 -> G2 -> G3 -> G5. It independently proves reliable downstream publication.
- **US3 (P2)**: G1 -> G2 -> G3 -> G6. It independently works against seeded committed Orders and
  does not require Kafka availability.

### Parallel Opportunities

- T002 and T006 may proceed in parallel after T001 coordination; T003 follows generated contract T002.
- T015/T016/T017 are independent test-first tasks; T018 and T020/T021 own separate core files.
- After G3, G4 consumer work, G5 outbox work, and G6 query work own separate files and may proceed in
  parallel.
- T037/T038, T043/T044, T047–T050, T054/T057, T061/T062, and T070/T071 are explicit parallel pairs
  or groups.
- Shared files `services/order-service/pom.xml`, `application.yml`, Gateway `application.yml`,
  Compose files, `validation.md`, and feature status artifacts must be changed serially.

## Parallel Examples

### After G3

```text
Developer A / G4: PurchaseAccepted mapper, listener, retry/DLT, live consumer tests
Developer B / G5: outbox application, claim adapter, Avro publisher, relay tests
Developer C / G6: owner query use cases, persistence projections, HTTP/security/Gateway tests
```

### Final Evidence

```text
T070: k6 owner-query profile
T071: Kafka event-to-commit profile
```

They may be prepared in parallel, but T072 records one reconciled environment and runs them in a
controlled sequence.

## Implementation Strategy

### MVP First

1. Approve plan and tasks.
2. Complete G1 and G2.
3. Complete G3 and validate atomic Order creation.
4. Complete G4 and validate Feature 019 -> Order through Kafka.
5. Stop at the US1 checkpoint for review before adding outbound publication and public queries.

### Incremental Delivery

1. **US1**: durable idempotent Order consumer.
2. **US2**: reliable `OrderCreatedV1` fact.
3. **US3**: owner-only detail/list APIs through Gateway.
4. **G7/G8**: production readiness, smoke, failure, performance, and full evidence.

### Explicit Deferred Work

Do not add `PaymentRequestedV1`, payment-result consumers, reservation confirm/release, Order
terminal states, payment deadlines, refund/compensation, Inventory settlement, status filtering, or
retention cleanup while executing these tasks. Discovery of a need for any item stops the affected
task and returns to specification review.

## Notes

- Commit after a completed task or coherent group; do not mix unrelated pre-existing worktree
  changes into a Feature 020 commit.
- `[P]` means file/dependency independence, not permission to ignore the group checkpoint.
- Generated Avro types, HTTP DTOs, JPA entities, and domain/application models remain separate.
- G1 production tasks are authorized by the project owner; later groups remain blocked until their
  required approval and preceding checkpoint are complete.
