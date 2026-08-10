# Tasks: Flash Sale Service MVP

**Status**: Approved — confirmed by project owner on 2026-08-10
**Input**: Approved design documents from `/specs/019-flash-sale-service-mvp/`  
**Prerequisites**: Approved `spec.md`, approved `plan.md`, accepted ADR 0017, `research.md`,
`data-model.md`, `contracts/`, and `quickstart.md`

**Tests**: All unit, architecture, web/security, Redis/PostgreSQL concurrency, Kafka/Schema Registry,
failure-recovery, Compose smoke, load, module, and full-reactor validations required by the approved
plan are mandatory.

**Organization**: Tasks are grouped by user story. Because US1–US3 are all P1 but have real data
dependencies, execution order is US3 Campaign projection -> US1 atomic quota admission -> US2
durable acceptance/publication -> US4 owner query.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: May run in parallel because it owns different files and has no dependency on an
  incomplete task in the same phase.
- **[Story]**: Maps the task to a Feature 019 user story.
- Every completion must record its validation command/result in
  `specs/019-flash-sale-service-mvp/validation.md`; a checked task is not evidence by itself.
- Production implementation must proceed as one approved task or one coherent task group.

## Phase 1: Setup and Module Baseline

**Purpose**: Turn the existing Flash Sale skeleton into a configured, independently buildable module
without implementing business behavior.

- [x] T001 Update `services/flashsale-service/pom.xml` with every approved production and test-only dependency from `plan.md`, MapStruct processing, and no excluded dependency such as Redisson, Kafka Connect, Resilience4j, gRPC, or OpenAPI
- [x] T002 [P] Define environment-backed JPA/Liquibase, Redis, Kafka/Schema Registry, JWT/JWKS, OAuth2 Client Credentials, virtual-thread, Actuator, Prometheus, and tracing configuration in `services/flashsale-service/src/main/resources/application.yml` and safe test overrides in `services/flashsale-service/src/test/resources/application.yml`
- [x] T003 [P] Enable only the planned scheduling, configuration-properties scanning, and OpenFeign capabilities in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/FlashsaleServiceApplication.java`
- [x] T004 [P] Add fail-fast typed configuration in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/configuration/FlashSaleProperties.java`, `RedisHotPathProperties.java`, `CampaignProjectionProperties.java`, and `OutboxProperties.java`
- [x] T005 Add configuration binding/validation coverage in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/configuration/FlashSaleConfigurationPropertiesTests.java` and keep `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/FlashsaleServiceApplicationTests.java` green

**Checkpoint**: The service skeleton resolves approved dependencies and rejects missing/invalid
runtime settings before any story implementation begins.

---

## Phase 2: Foundational Boundaries (Blocking)

**Purpose**: Establish service-owned schema, security, standardized HTTP failures, trace boundary,
and architecture enforcement used by every story.

**CRITICAL**: No user-story implementation begins until this phase passes its checkpoint.

- [ ] T006 [P] Create the Liquibase formatted-SQL schema, constraints, indexes, and explicit development rollback in `services/flashsale-service/src/main/resources/db/changelog/changes/001-create-flash-sale-mvp-schema.sql` and include it from `services/flashsale-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- [ ] T007 Validate forward migration, rollback guidance, Hibernate `ddl-auto=validate`, PostgreSQL-specific constraints, and absence of `schema.sql`/`data.sql` in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/integration/FlashSaleSchemaMigrationIntegrationTests.java`
- [ ] T008 [P] Implement independent shopper JWT validation and sanitized 401/403 handling in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/security/FlashSaleSecurityConfiguration.java`, `FlashSaleJwtTrustConfiguration.java`, `FlashSaleAuthenticationEntryPoint.java`, and `FlashSaleAccessDeniedHandler.java`
- [ ] T009 Verify issuer, `flash-sale-api` audience, signature, expiry, token type, JWT-subject identity, and sanitized failures in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/security/FlashSaleJwtTrustConfigurationTests.java` and `FlashSalePublicSecurityTests.java`
- [ ] T010 [P] Create the Flash Sale-owned error catalog and shared-envelope translation in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/websupport/error/FlashSaleErrorCode.java`, `FlashSaleExceptionClassifier.java`, and `FlashSaleHttpExceptionHandler.java`
- [ ] T011 Verify the complete HTTP status/error-code matrix, shared `ApiErrorResponse`, validation `FieldViolation`, no internal-detail leakage, and header-only trace identity in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/websupport/error/FlashSaleHttpExceptionHandlerTests.java`
- [ ] T012 [P] Establish W3C/MDC request context and bounded observation names in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/observability/FlashSaleRequestContext.java`, `FlashSaleTraceHeaderFilter.java`, and `FlashSaleObservationNames.java`
- [ ] T013 Enforce package-by-feature and Clean/Hexagonal import direction, including Avro/Feign/Redis/JPA isolation, in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/architecture/FlashSaleArchitectureTests.java`
- [ ] T014 Create `specs/019-flash-sale-service-mvp/validation.md` and record the Phase 1–2 module command, scope, exit status, and any failure before checking this foundation complete

**Checkpoint**: Liquibase, configuration, security, error, trace, and architecture foundations are
ready. User-story work may begin in the dependency order below.

---

## Phase 3: User Story 3 — Project Campaign Availability (Priority: P1)

**Goal**: Build a safe Redis Campaign projection from scheduled/activated Avro facts and recover a
missing or stale projection through the approved Campaign snapshot endpoint without opening the
shopper hot path.

**Independent Test**: Deliver scheduled, duplicate scheduled, activated, stale, activation-before-
schedule, and older/incomplete recovery snapshots; verify exact snapshot data, version monotonicity,
no quota reset, fail-closed state, and recovery scheduling.

### Domain and Application

- [ ] T015 [P] [US3] Implement Campaign projection domain language and invariants in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/campaignprojection/domain/model/CampaignSaleProjection.java`, `CampaignItemProjection.java`, `CampaignProjectionState.java`, and `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/campaignprojection/domain/exception/InvalidCampaignProjectionException.java`
- [ ] T016 [US3] Define scheduled/activated/recovery commands, inbound use cases, and Redis/snapshot output ports in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/campaignprojection/application/command/ApplyCampaignScheduledCommand.java`, `ApplyCampaignActivatedCommand.java`, `RecoverCampaignProjectionCommand.java`, `application/port/in/ProjectCampaignScheduleUseCase.java`, `ProjectCampaignActivationUseCase.java`, `RecoverCampaignProjectionUseCase.java`, `application/port/out/StoreCampaignProjectionPort.java`, `LoadCampaignSnapshotPort.java`, `QueueCampaignRecoveryPort.java`, `application/usecase/CampaignProjectionService.java`, and `CampaignProjectionRecoveryService.java`
- [ ] T017 [US3] Cover projection version/state invariants and recovery validation in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/campaignprojection/domain/CampaignSaleProjectionTests.java` and `campaignprojection/application/CampaignProjectionServiceTests.java`

### Redis Projection

- [ ] T018 [US3] Implement version-guarded scheduled, activated, and recovered projection scripts in `services/flashsale-service/src/main/resources/redis/campaign/apply-campaign-scheduled.lua`, `apply-campaign-activated.lua`, and `apply-campaign-recovered.lua`, then expose them through `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/campaignprojection/adapter/out/redis/CampaignProjectionRedisKeys.java`, `CampaignProjectionRedisAdapter.java`, and `CampaignProjectionRedisResultMapper.java`
- [ ] T019 [US3] Prove real-Redis duplicate/stale no-op behavior, exact price/allocation/limit projection, activation-without-schedule fail-closed behavior, and no quota reset in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/campaignprojection/integration/CampaignProjectionRedisIntegrationTests.java`

### Kafka Consumer and Recovery HTTP

- [ ] T020 [P] [US3] Map only generated `CampaignScheduledV1` and `CampaignActivatedV1` records into application commands and consume `campaign.lifecycle.v1` in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/campaignprojection/adapter/in/messaging/kafka/CampaignLifecycleAvroMapper.java` and `CampaignLifecycleKafkaConsumer.java`
- [ ] T021 [US3] Configure group `flashsale-campaign-projection-v1`, manual acknowledgement, three total deliveries with 250/500 ms delays, listener stop with uncommitted offset, and no DLT in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/configuration/CampaignProjectionKafkaConfiguration.java`
- [ ] T022 [US3] Verify accepted record types, key/version handling, duplicate redelivery, poison/deserialization failure, acknowledgement boundary, stopped-listener outcome, and W3C header propagation in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/campaignprojection/contract/CampaignLifecycleConsumerContractTests.java`
- [ ] T023 [P] [US3] Implement the recovery-only OpenFeign boundary in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/campaignprojection/adapter/out/client/campaign/CampaignSnapshotFeignClient.java`, `CampaignSnapshotClientResponse.java`, `CampaignSnapshotClientMapper.java`, `CampaignSnapshotErrorDecoder.java`, and `CampaignSnapshotClientAdapter.java`
- [ ] T024 [P] [US3] Implement cached OAuth2 Client Credentials authorization for subject `flashsale-service`, audience `flash-sale-internal-api`, scope `campaign.snapshot.read`, and token TTL at most 300 seconds in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/security/serviceidentity/FlashSaleServiceTokenManager.java` and `CampaignServiceAuthorizationInterceptor.java`
- [ ] T025 [US3] Verify 500 ms connect/1,000 ms read timeout, `Retryer.NEVER_RETRY`, OAuth2 token reuse/refresh, scope/audience, HTTP error mapping, and absence of calls inside database transactions in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/campaignprojection/adapter/out/client/campaign/CampaignSnapshotClientAdapterTests.java` and `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/security/serviceidentity/FlashSaleServiceTokenManagerTests.java`
- [ ] T026 [US3] Run five-second projection recovery outside the shopper path in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/campaignprojection/adapter/in/scheduling/CampaignProjectionRecoveryJob.java` and wire the feature through `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/configuration/CampaignProjectionConfiguration.java`
- [ ] T027 [US3] Validate Kafka-to-Redis projection, activation-before-schedule recovery, stale/incomplete snapshot rejection, Redis-loss fail-closed behavior, and no synchronous recovery call from a reservation request in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/campaignprojection/integration/CampaignProjectionRecoveryIntegrationTests.java`, then record the US3 checkpoint in `specs/019-flash-sale-service-mvp/validation.md`

**Checkpoint**: Campaign projection is independently operational and safe under duplicate,
out-of-order, missing, and recovery inputs; purchases are still closed until the next phases.

---

## Phase 4: User Story 1 — Reserve Campaign Quota Safely (Priority: P1)

**Goal**: Make one Redis Lua operation select winners, enforce Campaign/user constraints, establish
stable IDs and idempotency, index expiry, and append the recoverable Stream handoff without oversell.

**Independent Test**: From 100 units, run at least 1,000 concurrent valid attempts and accept no more
than 100 units; run 100 concurrent identical retries and observe one logical winner and one quota
decrement.

### Domain and Application

- [ ] T028 [P] [US1] Implement reservation decision language and lifecycle invariants in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/domain/model/Reservation.java`, `ReservationStatus.java`, `PurchaseRequest.java`, `PurchaseOutcome.java`, `AcceptedReservationSnapshot.java`, and `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/domain/exception/InvalidReservationStateException.java`
- [ ] T029 [US1] Define the atomic command/result and use-case boundary in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/application/command/ReserveCampaignQuotaCommand.java`, `application/result/ReservationDecisionResult.java`, `application/port/in/ReserveCampaignQuotaUseCase.java`, `application/port/out/ExecuteAtomicReservationPort.java`, `PersistAcceptedPurchasePort.java`, `AcknowledgeReservationHandoffPort.java`, and `application/usecase/ReserveCampaignQuotaService.java`
- [ ] T030 [P] [US1] Implement case-sensitive SHA-256 key hashing and the canonical user/Campaign/Variant/quantity fingerprint in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/application/usecase/ReservationFingerprintService.java` and stable pre-Lua identity creation in `ReservationIdentityFactory.java`
- [ ] T031 [US1] Verify positive quantity, stable IDs, five-minute expiry, Campaign-end-plus-24-hour retention, canonical fingerprint exclusions, same-request replay, changed-request conflict, and no Java duplicate of Lua quota rules in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/domain/ReservationDomainTests.java` and `reservation/application/ReserveCampaignQuotaServiceTests.java`

### Atomic Redis Admission

- [ ] T032 [US1] Implement the all-or-nothing admission plus `XADD` script in `services/flashsale-service/src/main/resources/redis/reservation/reserve-campaign-quota.lua` and the adapter boundary in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/out/redis/ReservationRedisKeys.java`, `ReservationLuaResultMapper.java`, and `AtomicReservationRedisAdapter.java`
- [ ] T033 [US1] Verify every typed Lua result, exact money snapshot, window/Variant/sold-out/user-limit rejection, arithmetic safety, atomic expiry index, atomic handoff creation, and unchanged counters on failure against real Redis in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/AtomicReservationRedisIntegrationTests.java`
- [ ] T034 [US1] Demonstrate at least 1,000 concurrent attempts against 100 units with no negative quota, no user-limit violation, and correct reconciliation in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/ReservationOversellConcurrencyIntegrationTests.java`
- [ ] T035 [US1] Demonstrate 100 concurrent identical retries reuse one purchase request/reservation/event identity, create one Stream entry, and decrement quota once while a changed request conflicts in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/ReservationIdempotencyConcurrencyIntegrationTests.java`
- [ ] T036 [US1] Verify Redis-unavailable fail-closed behavior, no PostgreSQL/Inventory fallback, protected business keys, and `NOSCRIPT` reload without non-atomic fallback in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/ReservationRedisFailureIntegrationTests.java`
- [ ] T037 [US1] Validate the reservation application flow with deterministic fake durable/ack ports so durable success, acceptance-pending, terminal expiry, and acknowledgement ordering are independently testable in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/application/ReserveCampaignQuotaFlowTests.java`
- [ ] T038 [US1] Run the US1 Redis/domain/application suite and record winner count, remaining quota, one-key replay identity, commands, and exit status in `specs/019-flash-sale-service-mvp/validation.md`

**Checkpoint**: Atomic winner selection and recoverable handoff are correct under concurrency, but a
public 202 is not enabled until US2 provides the real durable persistence/publication boundary.

---

## Phase 5: User Story 2 — Establish a Durable Accepted Purchase (Priority: P1)

**Goal**: Persist the Redis winner and publication intent atomically, recover Stream work, arbitrate
expiry, expose the POST contract only after commit, and publish stable Avro through the outbox.

**Independent Test**: Stop/fail each boundary from Lua winner through PostgreSQL, Stream ACK, Kafka,
and Schema Registry; recover to one durable reservation and one stable event identity, or terminal
expiry, without a second quota decrement or false 202.

### Durable Acceptance and Expiry

- [ ] T039 [P] [US2] Implement JPA entities and embedded idempotency identity matching the Liquibase schema in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/out/persistence/jpa/entity/PurchaseRequestJpaEntity.java`, `FlashSaleReservationJpaEntity.java`, `PurchaseIdempotencyJpaEntity.java`, `PurchaseIdempotencyJpaId.java`, and `PurchaseEventOutboxJpaEntity.java`
- [ ] T040 [US2] Implement Spring Data repositories, persistence mapper, and the one transactional acceptance adapter that writes all four tables without calling another adapter in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/out/persistence/jpa/repository/PurchaseRequestJpaRepository.java`, `FlashSaleReservationJpaRepository.java`, `PurchaseIdempotencyJpaRepository.java`, `PurchaseEventOutboxJpaRepository.java`, `mapper/ReservationPersistenceMapper.java`, and `DurableAcceptanceJpaAdapter.java`
- [ ] T041 [US2] Verify atomic purchase/reservation/idempotency/outbox commit, unique-conflict reload, same-hash replay, changed-hash conflict, exact snapshot persistence, and full rollback against PostgreSQL in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/DurableAcceptancePersistenceIntegrationTests.java`
- [ ] T042 [US2] Prove concurrent request-thread/Stream-worker persistence and `ACCEPTED` versus `EXPIRED` PostgreSQL arbitration cannot create duplicate rows, resurrect expiry, or create a late accepted event in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/DurableAcceptanceConcurrencyIntegrationTests.java`
- [ ] T043 [US2] Define expiration ports/policy/use case in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/domain/policy/ReservationExpiryPolicy.java`, `reservation/application/port/in/ExpireReservationsUseCase.java`, `application/port/out/FindDueReservationPort.java`, `PersistReservationExpiryPort.java`, `ReleaseExpiredQuotaPort.java`, and `application/usecase/ExpireReservationsService.java`
- [ ] T044 [US2] Implement PostgreSQL-first expiry scheduling and idempotent Redis release in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/in/scheduling/ReservationExpiryJob.java`, `reservation/adapter/out/persistence/jpa/ReservationExpiryJpaAdapter.java`, `reservation/adapter/out/redis/ReservationExpiryRedisAdapter.java`, and `services/flashsale-service/src/main/resources/redis/reservation/release-expired-reservation.lua`
- [ ] T045 [US2] Verify five-minute eligibility, PostgreSQL-outage retry, repeated release, accepted/expired races, exactly-once quota/user restoration, and absence of an expiry event in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/ReservationExpiryIntegrationTests.java`
- [ ] T046 [US2] Implement bounded Campaign-end-plus-24-hour PostgreSQL idempotency cleanup through `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/application/port/out/DeleteExpiredIdempotencyPort.java`, `application/usecase/IdempotencyCleanupService.java`, `reservation/adapter/in/scheduling/IdempotencyCleanupJob.java`, and `reservation/adapter/out/persistence/jpa/IdempotencyCleanupJpaAdapter.java`; keep Redis replay keys on their exact retained-until TTL and never delete durable purchases/reservations/outbox history
- [ ] T047 [US2] Verify pre-retention replay, post-cleanup key reuse as a new command, bounded batches, and independent durable audit retention in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/IdempotencyRetentionIntegrationTests.java`

### Redis Stream Recovery

- [ ] T048 [US2] Implement stable Stream field mapping, idempotent consumer-group creation, new-entry consumption, and application-use-case invocation in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/in/messaging/redis/ReservationHandoffMessageMapper.java`, `ReservationHandoffConsumerGroupInitializer.java`, and `ReservationHandoffStreamConsumer.java`
- [ ] T049 [US2] Implement terminal-outcome-only atomic `XACK` plus `XDEL`, pending reclaim, and configured 1-second/100-entry/30-second boundaries in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/out/redis/ReservationHandoffRedisAdapter.java` and `services/flashsale-service/src/main/resources/redis/reservation/ack-delete-handoff.lua`
- [ ] T050 [US2] Verify crash after Lua, crash after read, crash after PostgreSQL commit, duplicate request-thread/consumer execution, no pending-entry trimming, and stable replay in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/ReservationHandoffRecoveryIntegrationTests.java`
- [ ] T051 [US2] Verify `XAUTOCLAIM` transfers only entries idle at least 30 seconds and preserves immutable IDs/snapshot/trace fields in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/ReservationHandoffAutoClaimIntegrationTests.java`

### Avro Contract and Transactional Outbox

- [ ] T052 [P] [US2] Add the exact schema-first contract in `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc` without JWT, raw idempotency, JPA, or Redis fields
- [ ] T053 [US2] Verify Avro syntax, generated `SpecificRecord`, decimal/timestamp/UUID logical types, TopicRecordNameStrategy subject, BACKWARD_TRANSITIVE compatibility, and prohibited-field absence in `contracts/kafka-avro-contracts/src/test/java/com/philia/flashsale/contract/purchase/event/PurchaseAcceptedSchemaTests.java`
- [ ] T054 [P] [US2] Implement outbox domain model, ports, and exponential retry policy capped at 60 seconds in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/outbox/application/model/OutboxEvent.java`, `application/port/ClaimOutboxEventsPort.java`, `PublishPurchaseAcceptedPort.java`, `UpdateOutboxPublicationPort.java`, `application/usecase/OutboxRetryPolicy.java`, and `OutboxPublicationService.java`
- [ ] T055 [US2] Implement the leased outbox claim/update adapter with PostgreSQL JDBC/native SQL isolated at the outbound boundary, batch 100, `FOR UPDATE SKIP LOCKED`, 30-second claims, sanitized errors, indefinite durable retry, and no DLT/admin API in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/outbox/adapter/out/persistence/jpa/FlashSaleOutboxPersistenceAdapter.java` without importing reservation adapter entities/repositories
- [ ] T056 [US2] Prove multiple outbox workers cannot claim one live lease, expired leases are reclaimable, publish acknowledgement is idempotent, and rollback leaves rows recoverable in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/outbox/integration/FlashSaleOutboxConcurrencyIntegrationTests.java`
- [ ] T057 [US2] Map immutable outbox JSONB only at the adapter boundary and publish keyed Avro with `acks=all`, producer idempotence, W3C headers, and `auto.register.schemas=false` in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/outbox/adapter/out/messaging/kafka/PurchaseAcceptedAvroMapper.java` and `KafkaPurchaseAcceptedPublisher.java`
- [ ] T058 [US2] Poll every 500 ms and publish outside the claim transaction in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/outbox/adapter/in/scheduling/FlashSaleOutboxPublisherJob.java` and wire outbox dependencies in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/configuration/FlashSaleOutboxConfiguration.java`
- [ ] T059 [US2] Verify stable key/event identity/payload/occurredAt across retries, retry timing, header propagation, sanitized failure storage, and no direct Kafka call in the acceptance transaction in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/outbox/FlashSaleOutboxPublisherTests.java`
- [ ] T060 [US2] Validate real Kafka and Confluent Schema Registry serialization, controlled registration compatibility, duplicate delivery, broker outage, Registry outage, and recovery of the original event identity in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/outbox/integration/PurchaseAcceptedKafkaIntegrationTests.java`

### Public Submit Contract and Gateway

- [ ] T061 [US2] Implement POST request/accepted response records, MapStruct boundary mapper, and `ResponseEntity<ApiResponse<...>>` with 202/Location only after commit in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/in/web/request/ReserveCampaignQuotaRequest.java`, `response/ReservationAcceptedResponse.java`, `mapper/ReservationWebMapper.java`, and `FlashSaleReservationController.java`
- [ ] T062 [US2] Verify validation, shared success/error envelopes, required 1–128 character key, identical 202 replay/Location, changed-key conflict, no-store, Redis fail-closed, terminal expiry, and 503 acceptance-pending with `Retry-After: 1` in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/contract/FlashSaleReservationSubmitContractTests.java`
- [ ] T063 [US2] Add authenticated `/api/v1/flash-sales/**` routing and header/body/path/query pass-through in `services/api-gateway/src/main/resources/application.yml`, then verify Authorization, Idempotency-Key, W3C context, X-Trace-Id, downstream response, and no Gateway business interpretation in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/FlashSaleGatewayRouteTests.java`
- [ ] T064 [US2] Prove the end-to-end service path creates one durable result/outbox before 202, makes no synchronous Campaign/Product/Inventory/Order/Payment/Authentication call, and recovers PostgreSQL-pending outcomes in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/FlashSaleDurableAcceptanceIntegrationTests.java`, then record the US2 checkpoint in `specs/019-flash-sale-service-mvp/validation.md`

**Checkpoint**: A real Redis winner becomes one durable reservation plus one stable retryable Avro
publication intent; public submit is now truthful and recoverable.

---

## Phase 6: User Story 4 — Query an Owned Reservation (Priority: P2)

**Goal**: Let a shopper recover their durable reservation status without exposing another user's
reservation existence.

**Independent Test**: Retrieve a durable reservation as its owner, then query the same ID as another
user and an unknown ID; the latter two outcomes must be indistinguishable 404 responses.

- [ ] T065 [P] [US4] Define owner-scoped query/result boundaries in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/application/query/GetOwnedReservationQuery.java`, `application/result/ReservationDetailsResult.java`, `application/port/in/GetOwnedReservationUseCase.java`, `application/port/out/LoadOwnedReservationPort.java`, and `application/usecase/GetOwnedReservationService.java`
- [ ] T066 [US4] Implement one owner-filtered durable query without foreign-row fallback in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/out/persistence/jpa/OwnedReservationQueryJpaAdapter.java` and `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/out/persistence/jpa/repository/FlashSaleReservationJpaRepository.java`
- [ ] T067 [US4] Add `ReservationResponse` and the authenticated GET mapping to `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/reservation/adapter/in/web/response/ReservationResponse.java`, `mapper/ReservationWebMapper.java`, and `FlashSaleReservationController.java`
- [ ] T068 [US4] Verify exact price/currency/status/expiry response, shared envelope, no-store, JWT-sub ownership, and identical unknown/foreign 404 contracts in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/contract/FlashSaleReservationQueryContractTests.java`
- [ ] T069 [US4] Validate owner query against real PostgreSQL and through Gateway, then record the US4 checkpoint in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/reservation/integration/OwnedReservationQueryIntegrationTests.java` and `specs/019-flash-sale-service-mvp/validation.md`

**Checkpoint**: Every Feature 019 user story is functional and independently evidenced.

---

## Phase 7: Observability, Local Topology, Failure and Portfolio Evidence

**Purpose**: Finish cross-cutting operational behavior and prove the full approved topology without
adding Kubernetes assets or another infrastructure service.

- [ ] T070 [P] Add low-cardinality Micrometer observations/metrics and dependency-aware readiness for HTTP admission, Campaign projection/recovery, Redis Lua/Stream, PostgreSQL acceptance/expiry, and outbox publication in `services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/observability/FlashSaleObservability.java` and `FlashSaleReadinessHealthIndicator.java`
- [ ] T071 Verify liveness independence, Redis/PostgreSQL readiness failure, Kafka/Registry backlog-only behavior, p50/p95/p99-capable timer output, W3C HTTP/Kafka linkage, MDC, bounded tags, and secret/high-cardinality absence in `services/flashsale-service/src/test/java/com/philia/flashsale/flashsale/observability/FlashSaleObservabilityTests.java`
- [ ] T072 [P] Add `flashsale-service`, its one-off Liquibase migration, safe environment wiring, authenticated Redis/AOF settings, and no committed secrets to `infra/docker/compose.yml`, `infra/docker/compose.dev.yml`, and `infra/docker/.env.example`
- [ ] T073 [P] Provision `flashsale.purchase.events.v1` with three partitions/replication one and register/check `PurchaseAcceptedV1` subjects under controlled compatibility in `infra/docker/kafka/init-flashsale-topics.sh` and `infra/docker/schema-registry/register-flashsale-schemas.ps1`
- [ ] T074 Implement the real Authentication -> Gateway -> Campaign projection -> Flash Sale -> Redis/PostgreSQL -> Kafka/Schema Registry -> owner-query smoke in `infra/docker/smoke/feature-019-flashsale.ps1`
- [ ] T075 Execute and record Redis-down, PostgreSQL-after-winner, process-after-Lua, process-after-DB-before-ACK, Kafka-down, Registry-down, activation-before-schedule, wrong-audience JWT, and foreign-owner failure evidence in `specs/019-flash-sale-service-mvp/validation.md`
- [ ] T076 [P] Create the documented concurrency/load scenario with correctness counters and no hardware-independent threshold in `load-tests/flashsale-service/reservation.js`
- [ ] T077 Run the k6 scenario and record date/commit, hardware, container resources, replicas/JVM, topology, traffic model, throughput, p50/p95/p99, error rate, winner count, and saturation signals in `specs/019-flash-sale-service-mvp/validation.md`
- [ ] T078 Run `.\mvnw.cmd -pl services/flashsale-service -am verify` and record command, scope, exit status, and failing-test details if any in `specs/019-flash-sale-service-mvp/validation.md`
- [ ] T079 Run `.\mvnw.cmd clean verify` for the cross-module Gateway/contracts/infra change and record command, scope, exit status, and failing-test details if any in `specs/019-flash-sale-service-mvp/validation.md`
- [ ] T080 Reconcile `specs/019-flash-sale-service-mvp/spec.md`, `plan.md`, `contracts/`, `quickstart.md`, and `tasks.md` against implemented behavior, run `git diff --check`, confirm no Kubernetes manifest was introduced, and update artifact status/history only when every required validation is green

**Final Checkpoint**: Feature 019 is not complete while any required validation, correctness
invariant, failure scenario, or full-reactor check is failing or unrecorded.

---

## Dependencies and Execution Order

### Phase Dependencies

```text
Phase 1 Setup
  -> Phase 2 Foundation
     -> Phase 3 US3 Campaign projection
        -> Phase 4 US1 Atomic reservation
           -> Phase 5 US2 Durable acceptance/publication
              -> Phase 6 US4 Owner query
                 -> Phase 7 Full operational evidence
```

- Phase 1 has no implementation dependency.
- Phase 2 depends on the configured module and blocks all stories.
- US3 precedes US1 because admission requires a valid local Campaign snapshot.
- US1 precedes US2 because durable recovery persists the stable winner created by the Lua decision.
- US4 depends on the durable reservation created in US2, not on Kafka publication success.
- Phase 7 depends on all desired story phases and owns the full-topology/failure/load evidence.

### User Story Independence

- **US3**: Independently testable using Campaign Avro fixtures, real Redis, and a stub/real snapshot
  server; it never requires shopper reservation traffic.
- **US1**: Independently testable at domain/application/real-Redis boundaries using deterministic
  durable ports; it proves winner correctness without claiming public durable acceptance.
- **US2**: Integrates US1's stable winner with PostgreSQL/Stream/outbox and enables the public POST;
  it does not require an implemented Order consumer.
- **US4**: Reads only Flash Sale-owned PostgreSQL state and hides ownership; it does not depend on
  Redis, Kafka, or Schema Registry availability after acceptance.

### Parallel Opportunities

- T002, T003, and T004 may run in parallel after T001 defines dependencies.
- T006, T008, T010, and T012 own separate foundation files and may run in parallel.
- Within US3, T020 and T023/T024 may run in parallel after T015–T016 establish application types.
- Within US1, domain/fingerprint work can proceed in parallel before the Lua adapter is integrated.
- Within US2, T052–T054 may run in parallel with initial JPA work; Kafka publication waits for both
  the generated Avro type and outbox model.
- T070, T072, T073, and T076 own separate cross-cutting files and may run in parallel after all
  story contracts stabilize.

---

## Parallel Examples

### US3

```text
Task T020: Campaign Avro mapper/consumer
Task T023: Campaign snapshot Feign adapter
Task T024: OAuth2 service-identity token/interceptor
```

### US1

```text
Task T028: Reservation domain model
Task T030: Idempotency fingerprint and stable identity factory
```

### US2

```text
Task T039: Durable JPA entities
Task T052: PurchaseAcceptedV1 Avro schema
Task T054: Outbox application model and retry policy
```

### Cross-Cutting

```text
Task T070: Observability/readiness
Task T072: Compose/environment wiring
Task T073: Topic/schema bootstrap
Task T076: k6 scenario
```

---

## Implementation Strategy

### Correctness MVP First

The smallest safe demonstration is not US1 alone. Complete:

1. Phase 1 setup.
2. Phase 2 foundation.
3. US3 Campaign projection.
4. US1 atomic winner selection.
5. US2 durable acceptance and purchase-event outbox.
6. Stop and validate all P1 correctness/failure gates before adding US4.

This produces a truthful reservation submit path and Kafka fact without requiring Order, Payment,
or Inventory calls on the hot path.

### Complete Feature 019

1. Add US4 owner query after the correctness MVP.
2. Complete observability and local infrastructure wiring.
3. Run the full Compose failure matrix and k6 evidence.
4. Pass module and full-reactor validation.
5. Reconcile artifacts and request feature verification.

### Coherent Task Groups

Recommended approval/execution groups that preserve context without becoming oversized:

| Group | Tasks | Outcome |
|---|---|---|
| G1 | T001–T005 | Module/dependency/configuration baseline |
| G2 | T006–T014 | Schema, security, errors, trace, architecture foundation |
| G3 | T015–T019 | Campaign domain/application/Redis projection |
| G4 | T020–T027 | Campaign Kafka consumer and Feign recovery |
| G5 | T028–T033 | Reservation core and atomic Lua adapter |
| G6 | T034–T038 | Hot-path concurrency/idempotency/failure evidence |
| G7 | T039–T047 | Durable PostgreSQL acceptance, expiry, retention |
| G8 | T048–T051 | Redis Stream recovery and reclaim |
| G9 | T052–T060 | Avro and transactional outbox publication |
| G10 | T061–T064 | Public POST and Gateway integration |
| G11 | T065–T069 | Owner query |
| G12 | T070–T080 | Observability, Compose, failure/load/build evidence |

## Notes

- Tests are mandatory because Feature 019 affects stock correctness, concurrency, TTL,
  idempotency, distributed recovery, security, and Kafka contracts.
- Do not create empty package scaffolding; create a package only when its task creates a real class.
- Do not add an Order consumer, expiry Kafka event, DLT, admin replay API, Redis Cluster design,
  Kafka Connect/Debezium, purchase-specific Gateway rate limit, or Kubernetes manifests.
- Do not mark a task complete by weakening assertions or skipping a required test.
- Commit after each task or coherent group and stop at every checkpoint for review.
