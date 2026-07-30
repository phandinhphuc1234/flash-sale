# Tasks: Campaign Management MVP

**Input**: Design documents from `/specs/017-campaign-management-mvp/`

**Prerequisites**: Approved `spec.md`, approved `plan.md`, `research.md`, `data-model.md`,
`quickstart.md`, approved contracts, ADR 0012, and ADR 0013

**Tests**: Unit, PostgreSQL integration, HTTP/OAuth2 contract, security substitution, concurrency,
Kafka-compatible integration, observability, Compose, and reactor validation are required by the
approved plan. In each story, create the mapped tests before the corresponding production behavior
and record the expected failing result before implementation.

**Organization**: Tasks are grouped by user story. Setup and Foundation establish only the shared
prerequisites that block every story. Production implementation remains prohibited until this task
ledger is explicitly approved.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel because it changes different files and has no unmet dependency
- **[Story]**: Maps the task to an approved Feature 017 user story
- Every task names the repository path it owns

---

## Phase 1: Setup (Build and Runtime Baseline)

**Purpose**: Add only the dependencies and declarative configuration approved by the plan while
keeping all affected service contexts buildable.

- [x] T001 Add the approved MVC, validation, JPA, Resource Server, OAuth2 Client, MapStruct, PostgreSQL, Kafka, Testcontainers, and test dependencies to `services/campaign-service/pom.xml`
- [x] T002 Add the approved Spring Authorization Server and PostgreSQL Testcontainers dependencies for durable Client Credentials support to `services/authentication-service/pom.xml`
- [x] T003 [P] Configure Campaign virtual threads, datasource/JPA, normal-replica-disabled Liquibase, public/internal JWT trust values, OAuth2 registrations, downstream URLs, scheduler/outbox settings, Kafka producer, and declarative Actuator endpoints in `services/campaign-service/src/main/resources/application.yml`
- [x] T004 [P] Configure service-token audience, maximum 300-second TTL, and fixed Campaign/Flash Sale client provisioning inputs in `services/authentication-service/src/main/resources/application.yml`
- [x] T005 [P] Add internal-token issuer/audience configuration without changing existing public-token behavior in `services/product-service/src/main/resources/application.yml` and `services/inventory-service/src/main/resources/application.yml`

**Checkpoint**: All affected modules resolve their approved dependencies and load their configuration properties without business behavior.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish Campaign/Auth schemas, persistence boundaries, security separation, error
contracts, trace context, and architecture rules required by every user story.

**CRITICAL**: No user-story implementation begins until this phase is complete and green.

- [x] T006 [P] Add PostgreSQL migration and constraint tests for Campaign, item, schedule-operation, and outbox tables in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/integration/CampaignSchemaMigrationIntegrationTests.java`
- [x] T007 [P] Add PostgreSQL migration, uniqueness, scope, status, and hashed-secret constraint tests for OAuth clients in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/serviceclient/integration/ServiceClientSchemaMigrationIntegrationTests.java`
- [x] T008 Create the four Campaign-owned tables, indexes, checks, unique operation/idempotency constraints, and rollback metadata in `services/campaign-service/src/main/resources/db/changelog/changes/001-create-campaign-schema.sql` and include it from `services/campaign-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- [x] T009 Create durable `oauth_clients` and `oauth_client_scopes` tables without plaintext secrets in `services/authentication-service/src/main/resources/db/changelog/changes/003-create-oauth-client-schema.sql` and include it from `services/authentication-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- [x] T010 [P] Implement framework-free Campaign identity, money, item, status, and lifecycle invariants in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/domain/model/Campaign.java`, `CampaignItem.java`, `CampaignStatus.java`, and `CampaignMoney.java`
- [x] T011 [P] Implement typed Campaign policies and domain failures in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/domain/policy/CampaignLifecyclePolicy.java` and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/domain/exception/CampaignDomainException.java`
- [x] T012 Implement Campaign and item JPA entities, Spring Data repository, MapStruct persistence mapper, and persistence adapter in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/out/persistence/jpa/`
- [x] T013 [P] Implement schedule-operation and outbox JPA entity/repository foundations in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/scheduleoperation/adapter/out/persistence/jpa/` and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/adapter/out/persistence/jpa/`
- [x] T014 Implement separate ordered public-admin and internal-service JWT security chains with issuer/audience validation in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/security/configuration/CampaignSecurityConfiguration.java` and `CampaignJwtTrustConfiguration.java`
- [ ] T015 [P] Implement bounded `X-Trace-Id` request context, response propagation, and credential-safe Campaign error contract in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/websupport/context/CampaignRequestContext.java`, `websupport/filter/CampaignTraceIdFilter.java`, and `websupport/error/`
- [ ] T016 [P] Add package-by-feature dependency rules for Campaign domain/application/adapters and remove obsolete empty scaffold markers in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/architecture/CampaignArchitectureTests.java` and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/`
- [ ] T017 Run the Campaign and Authentication context plus migration test baseline and record command/results in `specs/017-campaign-management-mvp/quickstart.md`

**Checkpoint**: PostgreSQL owns durable Campaign/Auth state; Campaign public and internal security are isolated; adapters depend inward.

---

## Phase 3: User Story 1 - Prepare a Draft Campaign (Priority: P1) MVP Increment

**Goal**: An authorized Campaign administrator can create, revise, replace the single item of, and
retrieve a durable `DRAFT` without contacting Product, Inventory, Authentication Client Credentials,
or Kafka.

**Independent Test**: Create a draft, update metadata with `If-Match`, replace its item twice, and
retrieve the latest detail while downstream call spies remain untouched; stale/non-draft mutations
leave stored data unchanged.

### Tests for User Story 1

- [ ] T018 [P] [US1] Add unit tests for code normalization, time range, one-item replacement, price/quantity/limit validation, draft-only mutation, and version advancement in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/campaign/domain/CampaignDraftDomainTests.java`
- [ ] T019 [P] [US1] Add application tests for create, metadata replacement, item replacement, detail lookup, duplicate code, active-operation blocking, and no downstream calls in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/campaign/application/CampaignDraftUseCaseTests.java`
- [ ] T020 [P] [US1] Add PostgreSQL tests for case-insensitive code uniqueness, optimistic version conflicts, atomic replacement, and audit actor/time persistence in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/integration/CampaignDraftPersistenceIntegrationTests.java`
- [ ] T021 [P] [US1] Add MockMvc contract tests for all four draft endpoints, validation errors, direct DTO bodies, `Location`, `ETag`, and `X-Trace-Id` in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/contract/CampaignAdminDraftHttpContractTests.java`
- [ ] T022 [P] [US1] Add Campaign admin JWT tests for missing/invalid tokens, wrong audience, and missing/present `SCOPE_CAMPAIGN_ADMIN` in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/security/CampaignAdminSecurityTests.java`
- [ ] T023 [P] [US1] Add Gateway route, method/path/query/body/header forwarding, public-audience validation, and Campaign authority tests in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/CampaignAdminGatewayRouteTests.java`

### Implementation for User Story 1

- [ ] T024 [P] [US1] Define draft commands, admin-detail query, results, and input ports in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/application/command/`, `query/`, `result/`, and `port/in/`
- [ ] T025 [P] [US1] Define Campaign load/save, uniqueness, active-operation check, and clock/actor output ports in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/application/port/out/`
- [ ] T026 [US1] Implement transactional create, replace-metadata, replace-item, and admin-detail use cases in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/application/usecase/`
- [ ] T027 [US1] Implement version-aware persistence, code uniqueness, active-operation guard, and atomic one-item replacement in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/out/persistence/jpa/CampaignPersistenceAdapter.java`
- [ ] T028 [P] [US1] Implement Jakarta-validated admin request/response DTOs and MapStruct web mapping in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/in/web/admin/request/`, `response/`, and `mapper/CampaignAdminWebMapper.java`
- [ ] T029 [US1] Implement the separated OpenAPI contract and controller for create, metadata update, item replacement, and detail in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/in/web/admin/CampaignAdminApi.java` and `CampaignAdminController.java`
- [ ] T030 [US1] Map validation, not-found, duplicate-code, stale-version, invalid-status, and operation-in-progress failures to the approved body/status contract in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/websupport/error/CampaignHttpExceptionHandler.java`
- [ ] T031 [P] [US1] Add `CAMPAIGN_ADMIN` to administrator authority issuance and its compatibility test in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/domain/AccountRole.java` and `services/authentication-service/src/test/java/com/philia/flashsale/authentication/integration/JwtTrustCompatibilityIntegrationTests.java`
- [ ] T032 [US1] Add only the `/api/v1/admin/campaigns/**` Gateway route and `SCOPE_CAMPAIGN_ADMIN` rule while keeping `/internal/**` unexposed in `services/api-gateway/src/main/resources/application.yml` and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`
- [ ] T033 [US1] Run the US1 Campaign/Auth/Gateway tests and record the independent draft acceptance result in `specs/017-campaign-management-mvp/quickstart.md`

**Checkpoint**: US1 is independently usable through Gateway and performs no Product, Inventory, Client Credentials, or Kafka work.

---

## Phase 4: User Story 2 - Schedule a Validated, Stock-Backed Campaign (Priority: P1)

**Goal**: Schedule one complete draft exactly once using Campaign's own narrow service identity,
authoritative Product validation, complete Inventory allocation, stable operation identities, and an
atomic `CampaignScheduled.v1` outbox record.

**Independent Test**: Against controlled Product/Inventory/Auth contracts, schedule a valid draft;
then verify insufficient stock, key conflict, concurrent commands, ambiguous Inventory timeout,
crash-after-allocation recovery, background recovery, and absence of admin-token relay.

### Tests for User Story 2

- [ ] T034 [P] [US2] Add OAuth2 token endpoint contract tests for valid Campaign credentials plus unknown, inactive, wrong-secret, disallowed-grant, disallowed-scope, TTL, claims, and no-refresh-token cases in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/serviceclient/contract/ClientCredentialsTokenEndpointTests.java`
- [ ] T035 [P] [US2] Add PostgreSQL tests for hashed-secret persistence, allowed-scope loading, status handling, and fixed-client provisioning idempotency in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/serviceclient/integration/ServiceClientPersistenceIntegrationTests.java`
- [ ] T036 [P] [US2] Add regression tests proving existing administrator tokens remain `flash-sale-api` tokens and service tokens are RS256, internal-audience, scope-bound, and at most 300 seconds in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/security/ServiceTokenClaimCompatibilityTests.java`
- [ ] T037 [P] [US2] Add Product campaign-validation domain, HTTP, persistence-query, and token-substitution tests in `services/product-service/src/test/java/com/philia/flashsale/product/campaignvalidation/ProductCampaignValidationTests.java`
- [ ] T038 [P] [US2] Add Inventory allocation compatibility tests for the narrow scope, `campaign-service` subject, public/broad-token denial, stable error codes, and unchanged success envelope in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/allocation/CampaignAllocationCompatibilityTests.java`
- [ ] T039 [P] [US2] Add Campaign RestClient contract tests for token acquisition/cache/renewal, trace propagation, Product/Inventory response mapping, response identity checks, and no administrator-token relay in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/contract/CampaignDownstreamClientContractTests.java`
- [ ] T040 [P] [US2] Add schedule-policy unit tests for future start, complete item, price below base price, currency match, complete allocation, frozen snapshot, and retry decisions in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/campaign/domain/CampaignSchedulePolicyTests.java`
- [ ] T041 [P] [US2] Add application tests for TX-A/remote-call/TX-B sequencing, stable fingerprint/request ID, same-key replay, different-hash conflict, dependency failures, and resumable failed operations in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/scheduleoperation/application/ScheduleCampaignUseCaseTests.java`
- [ ] T042 [US2] Add real PostgreSQL concurrency tests proving one active operation, allocation identity, scheduled transition, and outbox row for concurrent scheduling in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/integration/ScheduleCampaignConcurrencyIntegrationTests.java`
- [ ] T043 [US2] Add crash-window and ambiguous Inventory timeout recovery tests that reuse the same operation and Inventory request IDs in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/integration/ScheduleCampaignRecoveryIntegrationTests.java`
- [ ] T044 [P] [US2] Add schedule endpoint contract tests for `If-Match`, `Idempotency-Key`, `{}`, replay, approved errors, `ETag`, and trace response in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/contract/CampaignScheduleHttpContractTests.java`

### Implementation for User Story 2

- [ ] T045 [P] [US2] Implement durable service-client models, failures, use cases, and ports in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/serviceclient/domain/` and `serviceclient/application/`
- [ ] T046 [US2] Implement OAuth client/scope JPA entities, repositories, mappers, and persistence adapter in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/serviceclient/adapter/out/persistence/jpa/`
- [ ] T047 [US2] Implement the durable Spring Authorization Server `RegisteredClientRepository`, fixed secret-hashed Campaign provisioning, ordered token-endpoint chain, and RS256 service-token claim customization in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/serviceclient/adapter/in/oauth/` and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/ServiceClientAuthorizationConfiguration.java`
- [ ] T048 [P] [US2] Implement Product-owned sellability policy, validation query/result, ports, and use case in `services/product-service/src/main/java/com/philia/flashsale/product/campaignvalidation/domain/` and `campaignvalidation/application/`
- [ ] T049 [US2] Implement the Product internal projection query adapter without sharing Product JPA/domain types in `services/product-service/src/main/java/com/philia/flashsale/product/campaignvalidation/adapter/out/persistence/ProductCampaignValidationPersistenceAdapter.java`
- [ ] T050 [US2] Implement Product validation request/response mapping, internal controller, exact internal JWT audience/subject/scope chain, and stable errors in `services/product-service/src/main/java/com/philia/flashsale/product/campaignvalidation/adapter/in/web/` and `services/product-service/src/main/java/com/philia/flashsale/product/configuration/ProductInternalSecurityConfiguration.java`
- [ ] T051 [P] [US2] Introduce typed insufficient-stock and allocation-request-conflict failures and stable HTTP mappings without parsing messages in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/allocation/domain/exception/` and `services/inventory-service/src/main/java/com/philia/flashsale/inventory/websupport/error/InventoryExceptionHandler.java`
- [ ] T052 [US2] Narrow only `POST /internal/v1/campaign-stock-allocations` to internal audience, `campaign-service`, and `SCOPE_inventory.campaign.allocate` while preserving other Inventory authorization in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/configuration/InventorySecurityConfiguration.java` and `InventoryJwtTrustConfiguration.java`
- [ ] T053 [P] [US2] Implement scope-specific in-memory service-token acquisition and renewal with `AuthorizedClientServiceOAuth2AuthorizedClientManager` in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/security/serviceidentity/CampaignServiceTokenManager.java` and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/configuration/CampaignOAuth2ClientConfiguration.java`
- [ ] T054 [P] [US2] Implement Product validation DTOs, MapStruct mapping, HTTP/status/error mapping, and `X-Trace-Id` propagation in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/out/client/product/`
- [ ] T055 [P] [US2] Implement Inventory allocation DTOs, common-envelope parsing, identity/result verification, stable-error mapping, and `X-Trace-Id` propagation in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/out/client/inventory/`
- [ ] T056 [P] [US2] Implement schedule-operation status/model/fingerprint rules and application ports in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/scheduleoperation/domain/` and `scheduleoperation/application/port/`
- [ ] T057 [US2] Implement short-transaction schedule-operation creation/loading, indefinite key retention, stable Inventory request ID, same-config reopen, and conflict detection in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/scheduleoperation/application/usecase/PrepareScheduleOperationService.java` and `scheduleoperation/adapter/out/persistence/jpa/ScheduleOperationPersistenceAdapter.java`
- [ ] T058 [US2] Implement the schedule orchestrator with no database transaction around token/Product/Inventory HTTP calls in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/application/usecase/ScheduleCampaignService.java`
- [ ] T059 [US2] Implement version/config revalidation, immutable snapshot freeze, `DRAFT -> SCHEDULED`, operation completion, and one `CampaignScheduled.v1` outbox insert in one final transaction in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/out/persistence/jpa/CampaignSchedulingPersistenceAdapter.java`
- [ ] T060 [US2] Add canonical scheduled-event envelope/payload creation without broker publication in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/domain/event/CampaignScheduled.java` and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/application/model/CampaignOutboxEvent.java`
- [ ] T061 [US2] Expose the approved schedule command through the existing Campaign admin API/controller and error mappings in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/in/web/admin/CampaignAdminApi.java`, `CampaignAdminController.java`, and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/websupport/error/CampaignHttpExceptionHandler.java`
- [ ] T062 [US2] Implement batch recovery of resumable schedule operations using Campaign's service identity and stable Inventory request identity in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/scheduleoperation/adapter/in/scheduling/ScheduleOperationRecoveryJob.java`
- [ ] T063 [US2] Run affected Auth/Product/Inventory/Campaign schedule tests and record the independent US2 acceptance result in `specs/017-campaign-management-mvp/quickstart.md`

**Checkpoint**: US2 schedules exactly once, persists one immutable snapshot/allocation/outbox identity, and never relays an administrator token.

---

## Phase 5: User Story 3 - Progress the Campaign Lifecycle Safely (Priority: P1)

**Goal**: Due campaigns activate once, active campaigns end once, manual activation obeys the same
window rules, and the activated event follows the scheduled event.

**Independent Test**: Advance a controllable clock while two workers race and verify monotonic
`DRAFT -> SCHEDULED -> ACTIVE -> ENDED`, one activation event, no early/manual invalid transition,
and no ended event.

### Tests for User Story 3

- [ ] T064 [P] [US3] Add domain tests for activation window, complete snapshot/allocation, monotonic statuses, terminal `ENDED`, and no early/expired activation in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/campaign/domain/CampaignLifecyclePolicyTests.java`
- [ ] T065 [US3] Add PostgreSQL two-worker activation/end race tests with a controllable clock in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/integration/CampaignLifecycleConcurrencyIntegrationTests.java`
- [ ] T066 [P] [US3] Add manual activation HTTP contract tests for authorization, `If-Match`, window/status conflicts, `ETag`, and successful response in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/contract/CampaignManualActivationHttpContractTests.java`
- [ ] T067 [P] [US3] Add persistence tests proving scheduled-before-activated aggregate ordering, exactly one activated outbox row, and no ended event in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/outbox/CampaignLifecycleOutboxOrderingTests.java`

### Implementation for User Story 3

- [ ] T068 [P] [US3] Define lifecycle commands/results and activation/end input/output ports in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/application/command/`, `result/`, and `port/`
- [ ] T069 [US3] Implement conditional status/version/time persistence for one-winner activation and ending in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/out/persistence/jpa/CampaignLifecyclePersistenceAdapter.java`
- [ ] T070 [US3] Implement shared automatic/manual activation and ending use cases with a configurable `Clock` in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/application/usecase/CampaignLifecycleService.java`
- [ ] T071 [US3] Implement the two-second batch-100 due lifecycle scanner with scheduler trace identity in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/in/scheduling/CampaignLifecycleScheduler.java`
- [ ] T072 [US3] Add atomic `CampaignActivated.v1` creation to the winning activation transaction and omit any ended event in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/domain/event/CampaignActivated.java` and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/out/persistence/jpa/CampaignLifecyclePersistenceAdapter.java`
- [ ] T073 [US3] Expose manual recovery activation through the Campaign admin API/controller and approved errors in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/in/web/admin/CampaignAdminApi.java`, `CampaignAdminController.java`, and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/websupport/error/CampaignHttpExceptionHandler.java`
- [ ] T074 [US3] Run lifecycle unit/contract/concurrency/order tests and record the independent US3 acceptance result in `specs/017-campaign-management-mvp/quickstart.md`

**Checkpoint**: Lifecycle progression is monotonic and multi-instance safe, with one activated event and no ended event.

---

## Phase 6: User Story 4 - Recover Downstream Runtime State (Priority: P2)

**Goal**: Flash Sale Service can securely rebuild from a stable internal snapshot and from reliable,
ordered, retryable lifecycle records without introducing a purchase-path dependency.

**Independent Test**: A correctly scoped `flashsale-service` token reads a non-draft snapshot;
identity substitutions fail; a real Kafka-compatible broker receives ordered stable-ID lifecycle
events; retry, lease reclaim, terminal failure, and authorized requeue preserve the event identity.

### Tests for User Story 4

- [ ] T075 [P] [US4] Add internal snapshot HTTP/security contract tests for allowed statuses, missing/incomplete/draft data, exact Flash Sale subject/audience/scope, and token substitution denial in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/contract/InternalCampaignSnapshotContractTests.java`
- [ ] T076 [P] [US4] Add exact JSON envelope/payload serialization, additive-compatibility, secret absence, trace, and stable event-ID tests for both v1 events in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/contract/CampaignLifecycleEventContractTests.java`
- [ ] T077 [US4] Add real Kafka-compatible integration tests for topic, Campaign ID key, partition ordering, Scheduled-before-Activated, duplicate identity, and independent aggregates in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/integration/CampaignLifecycleKafkaIntegrationTests.java`
- [ ] T078 [US4] Add PostgreSQL outbox tests for batch claim/lease, expired-lease reclaim, earliest aggregate version, exponential backoff, tenth-attempt failure, predecessor blocking, and same-event requeue audit in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/integration/CampaignOutboxRecoveryIntegrationTests.java`
- [ ] T079 [P] [US4] Add Authentication tests for separate `flashsale-service` credentials/scopes and denial of Campaign scopes in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/serviceclient/contract/FlashSaleServiceClientCredentialsTests.java`

### Implementation for User Story 4

- [ ] T080 [P] [US4] Define snapshot query/result/input/output ports and implement non-draft complete-snapshot lookup in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/application/query/`, `result/`, `port/`, and `usecase/GetCampaignSnapshotService.java`
- [ ] T081 [US4] Implement internal snapshot response mapping, separated OpenAPI contract, and controller in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/campaign/adapter/in/web/internal/`
- [ ] T082 [US4] Enforce `flashsale-service`, `flash-sale-internal-api`, and `SCOPE_campaign.snapshot.read` only on the snapshot endpoint in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/security/configuration/CampaignSecurityConfiguration.java`
- [ ] T083 [P] [US4] Add a separate hashed `flashsale-service` fixed-client registration limited to `campaign.snapshot.read` in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/serviceclient/adapter/in/oauth/ServiceClientBootstrapper.java`
- [ ] T084 [P] [US4] Define outbox claim, publish, retry, terminal-failure, and requeue ports/models/use cases in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/application/`
- [ ] T085 [US4] Implement multi-instance-safe due-row claim/lease, aggregate ordering, retry state, sanitized failure, and same-event requeue persistence in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/adapter/out/persistence/jpa/OutboxPersistenceAdapter.java`
- [ ] T086 [US4] Configure idempotent `acks=all` Kafka production and publish canonical v1 records keyed by Campaign ID in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/configuration/CampaignKafkaProducerConfiguration.java` and `outbox/adapter/out/messaging/kafka/KafkaCampaignLifecyclePublisher.java`
- [ ] T087 [US4] Implement the 500-ms batch-100 outbox publisher, retry/backoff, lease recovery, terminal failure, and trace restoration in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/adapter/in/scheduling/CampaignOutboxPublisherJob.java`
- [ ] T088 [US4] Implement authenticated same-event requeue use case, response, OpenAPI operation, controller endpoint, and error mappings in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/application/usecase/RequeueCampaignOutboxEventService.java` and `outbox/adapter/in/web/admin/`
- [ ] T089 [US4] Run snapshot/Auth/Kafka/outbox tests and record the independent US4 acceptance result in `specs/017-campaign-management-mvp/quickstart.md`

**Checkpoint**: Runtime recovery is secure and reliable; no Flash Sale purchase logic or per-purchase Campaign lookup has been introduced.

---

## Phase 7: Polish and Cross-Cutting Validation

**Purpose**: Complete observability, local topology, secret-safe configuration, operational recovery,
and the required module/reactor evidence without expanding Feature 017 scope.

- [ ] T090 [P] Add low-cardinality metrics plus health/Prometheus/trace/redaction tests for Campaign commands, downstream calls, lifecycle, outbox, and requeue in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/observability/CampaignObservabilityTests.java`
- [ ] T091 Implement low-cardinality Micrometer observations and trace/log propagation without a manual Prometheus registry in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/observability/CampaignObservability.java` and `services/campaign-service/src/main/java/com/philia/flashsale/campaign/configuration/CampaignObservabilityConfiguration.java`
- [ ] T092 [P] Add Campaign database/application wiring, OAuth secret placeholders, service URLs, and one-off migration dependencies to `infra/docker/compose.yml`, `infra/docker/compose.dev.yml`, and `infra/docker/.env.example` without committing real credentials
- [ ] T093 [P] Provision `campaign.lifecycle.v1` with three partitions and replication factor one through root-owned local tooling in `infra/docker/kafka/init-campaign-topics.sh`
- [ ] T094 Validate the merged Compose application profile and record the exact command/result in `specs/017-campaign-management-mvp/quickstart.md`
- [ ] T095 Execute the full Gateway -> Authentication -> Campaign -> Product -> Inventory -> PostgreSQL/Kafka smoke and failure/restart/requeue flow, recording sanitized evidence in `specs/017-campaign-management-mvp/quickstart.md`
- [ ] T096 Run `./mvnw -pl services/api-gateway,services/authentication-service,services/product-service,services/inventory-service,services/campaign-service -am verify` and record exit status in `specs/017-campaign-management-mvp/quickstart.md`
- [ ] T097 Run `./mvnw clean verify`, confirm no required test is failing, and record exit status in `specs/017-campaign-management-mvp/quickstart.md`

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: Starts after task-ledger approval.
- **Phase 2 — Foundational**: Depends on Phase 1 and blocks all user stories.
- **Phase 3 — US1**: Depends on Foundation; establishes editable Campaign state and admin ingress.
- **Phase 4 — US2**: Depends on US1's draft aggregate/API and Foundation's durable operation/outbox schema.
- **Phase 5 — US3**: Depends on US2 because only a scheduled, snapshotted Campaign can activate.
- **Phase 6 — US4**: Snapshot work depends on US2; activated-event ordering depends on US3; outbox publication can be developed after US2 event durability exists.
- **Phase 7 — Polish**: Depends on every story selected for the release.

### Critical Cross-Service Rollout

```text
Authentication Client Credentials capability
  -> Product/Inventory internal token acceptance
  -> Campaign downstream clients and scheduling
  -> Gateway Campaign administration exposure
  -> Flash Sale snapshot compatibility
```

### Within Each User Story

1. Create mapped tests and record their expected failure.
2. Implement domain rules and application ports/use cases.
3. Implement persistence and remote adapters.
4. Implement security and HTTP/messaging adapters.
5. Run the independent story acceptance tests before advancing.

### Parallel Opportunities

- T003, T004, and T005 configure different modules and can run in parallel after dependency updates.
- T006 and T007 are independent schema-test tracks; T010 and T011 are framework-free Campaign tracks.
- In US1, domain/application/persistence/HTTP/Gateway tests marked `[P]` target different files.
- In US2, Auth, Product, Inventory, Campaign-client, and schedule-policy test tracks can proceed in parallel after Foundation.
- Product implementation T048–T050 and Inventory implementation T051–T052 can proceed in parallel with Auth T045–T047.
- Campaign Product and Inventory clients T054–T055 can proceed in parallel after their contracts are test-pinned.
- In US4, snapshot, Auth Flash Sale client, and outbox application work can proceed in parallel before integration.
- T092 and T093 own separate root-infrastructure files and can proceed in parallel.

---

## Implementation Strategy

### Smallest Demonstrable Increment

Complete Phases 1–3, then stop and validate US1. This produces a secure, durable draft-management
slice through Gateway without coupling the demonstration to Product, Inventory, OAuth Client
Credentials, or Kafka.

### Core Campaign MVP

Complete Phases 1–5. This adds the actual stock-backed scheduling and lifecycle behavior needed
before Flash Sale runtime work can begin.

### Full Feature 017

Complete Phases 1–7. This adds secure snapshot recovery, reliable Kafka lifecycle publication,
local topology, and required verification evidence.

## Notes

- Do not relay, persist, or log administrator bearer tokens or service credentials.
- Do not open database transactions across OAuth2, Product, Inventory, or Kafka network calls.
- Do not regenerate schedule operation IDs, Inventory request IDs, or outbox event IDs on retry.
- Do not introduce Redis, gRPC, release/cancellation, ended events, DLT, Debezium, or Flash Sale purchase logic.
- A checked task is not evidence; record the commands and sanitized outcomes required by the task.
- If a test exposes behavior absent from the approved spec/contracts, stop and amend/approve artifacts before implementation.
