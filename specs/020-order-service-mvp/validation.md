# Feature 020 Validation Ledger

**Feature**: Order Service Core MVP
**Scope for this branch**: G6 / T047-T060
**Status**: G6 complete

This ledger records commands, scope, exit status, and evidence for each approved task group. A
checked task is not complete until its evidence is recorded here.

## G1 Foundation

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T001 | `./mvnw -pl services/order-service -am test` | Approved dependency set and module compilation | PASS | Reactor: common-web 9 tests, Kafka contracts 9 tests, Order 3 tests; BUILD SUCCESS (2026-08-15) |
| T002-T003 | `./mvnw -pl services/order-service -am test` | Generated `OrderCreatedV1`, logical types, forbidden fields, subject and compatibility tests | PASS | `OrderCreatedSchemaTests`: 3/3; contract module: 9/9 tests (2026-08-15) |
| T004-T005 | `./mvnw -pl services/order-service -am test` | Typed properties and baseline runtime configuration load | PASS | `OrderServiceApplicationTests`: 1/1; `@ConfigurationPropertiesScan` context started (2026-08-15) |
| T006 | `./mvnw -pl services/order-service -am test` | Testcontainers support compiles; containers remain opt-in outside integration profile | PASS | PostgreSQL/Kafka support and Registry condition compiled; no container started by baseline suite (2026-08-15) |
| T007 | `./mvnw -pl services/order-service -am test` | Inward dependency and adapter boundary rules | PASS | `OrderArchitectureTests`: 2/2 (2026-08-15) |
| T008 | `git diff --check` and this ledger review | Documentation and evidence slot integrity | PASS | Whitespace and ledger review passed (2026-08-15) |

## Group Checkpoint

- [x] Order module compiles against generated contracts.
- [x] Typed runtime properties bind from `application.yml`.
- [x] Architecture guard tests pass with no domain/application framework leakage.
- [x] Contract and module validation commands have recorded exit status.

## G2 PostgreSQL and Local Transaction Foundation

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T009-T010 | `./mvnw -pl services/order-service -am test -Dtest=OrderSchemaMigrationIntegrationTests` | Liquibase changeset and master include | PASS | PostgreSQL 17 Testcontainers created all four Order tables and one Liquibase changeset; 6/6 tests passed (2026-08-15) |
| T011 | `./mvnw -pl services/order-service -am test -Dtest=OrderSchemaMigrationIntegrationTests` | Clean migration, exact types, constraints, indexes, rollback | PASS | Exact NUMERIC(19,4), TIMESTAMPTZ, JSONB, identity/status/FK/check/index assertions and reverse rollback passed; 6/6 (2026-08-15) |
| T012 | `./mvnw -pl services/order-service -am test -Dtest=PostgreSqlOrderIdentityLockKeyTests` | Deterministic, domain-separated advisory-lock keys | PASS | 3/3 tests passed; same identity is stable and purchase/reservation namespaces are separated (2026-08-15) |
| T013 | `./mvnw -pl services/order-service -am test -Dtest=OrderPropertiesTests` | Property validation and supported defaults | PASS | 3/3 tests passed for required values, bounded batch/page settings, retry defaults, and invalid input (2026-08-15) |
| T014 | `./mvnw -pl services/order-service -am verify` | Order module, contract, architecture, and G2 foundation suite | PASS | Reactor common-web 9, Kafka contracts 9, Order 15 tests; BUILD SUCCESS (2026-08-15) |

## Command Results

- `./mvnw -pl services/order-service -am test`: PASS; common-web 9 tests, Kafka contracts 9 tests,
  Order 3 tests, 0 failures.
- `./mvnw -pl services/order-service -am verify`: PASS; all four reactor projects succeeded.
- Production dependency scan: PASS; no Redis, Feign, MapStruct, Payment SDK, or distributed-lock
  dependency introduced.
- `git diff --check`: PASS; no whitespace errors in the G1 diff.

## G2 Checkpoint

- [x] `order_db` Liquibase migration creates the four service-owned durable tables.
- [x] PostgreSQL constraints, exact types, indexes, and development rollback are verified.
- [x] Advisory-lock identity keys are deterministic and collision-domain separated.
- [x] Typed property constraints and supported defaults are verified.
- [x] No Kafka consumer or public endpoint was activated in G2.

## G3 User Story 1 — Durable Order creation

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T015-T017 | `./mvnw -pl services/order-service -am test -Dtest=OrderDomainTests,AcceptedPurchaseFingerprintTests,CreateOrderFromAcceptedPurchaseServiceTests -Dsurefire.failIfNoSpecifiedTests=false` | Domain invariants, exact scale-4 money, canonical fingerprint, use-case outcomes, and framework-free application boundary | PASS | 9/9 tests passed; BUILD SUCCESS (2026-08-15) |
| T018-T022 | `./mvnw -pl services/order-service -am test -Dtest=OrderDomainTests,AcceptedPurchaseFingerprintTests,CreateOrderFromAcceptedPurchaseServiceTests -Dsurefire.failIfNoSpecifiedTests=false` | Domain models, command/result/ports, fingerprinting, and Order creation orchestration | PASS | Production and test compilation succeeded; 9/9 focused tests passed (2026-08-15) |
| T023-T026 | `./mvnw -pl services/order-service -am test -Dtest=AcceptedPurchasePersistenceIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | JPA entities, repositories, mapper, advisory-lock adapter, identity/clock wiring, and four-row transaction | PASS | PostgreSQL 17 Testcontainers; 5/5 tests passed, including full rollback and Order-number collision recovery (2026-08-15) |
| T027 | `./mvnw -pl services/order-service -am test -Dtest=AcceptedPurchasePersistenceIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | Exact snapshot, event/business replay, contradiction, rollback, and stable outbox identity | PASS | 5/5 PostgreSQL integration tests passed; one Order, line, inbox, and outbox row on the create path (2026-08-15) |
| T028 | `./mvnw -pl services/order-service -am test -Dtest=AcceptedPurchaseConcurrencyIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | 100 equivalent deliveries and 100 contradictory concurrent deliveries | PASS | 2/2 tests passed; each scenario leaves exactly one Order/line/outbox fact and conflicting deliveries do not mutate the winner (2026-08-15) |
| T029 | `./mvnw -pl services/order-service -am verify` | Full G3 module validation and reactor dependencies | PASS | Common Web 9, Kafka contracts 9, Order 31 tests; all reactor projects succeeded and the Order JAR was packaged (2026-08-15) |

## G3 Checkpoint

- [x] A valid accepted purchase creates one `PENDING_PAYMENT` Order, one line, one inbox receipt,
  and one stable `OrderCreated` outbox identity in one PostgreSQL transaction.
- [x] Same event and equivalent different-event replays are no-ops; contradictory identities return
  `CONFLICT` without changing the established Order.
- [x] 100-way equivalent and contradictory concurrency evidence passes against PostgreSQL.
- [x] No Kafka consumer or HTTP endpoint was activated in G3; those remain G4/G6 work.

## G4 User Story 1 — PurchaseAccepted Kafka Boundary

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T030 | `./mvnw -pl services/order-service -am test -Dtest=PurchaseAcceptedAvroMapperTests -Dsurefire.failIfNoSpecifiedTests=false` | Avro-to-command mapping, envelope/key validation, decimal/instant normalization, W3C header propagation, and adapter boundary | PASS | 4/4 tests passed; no infrastructure types leak into the application command (2026-08-15) |
| T031 | `./mvnw -pl services/order-service -am test -Dtest=PurchaseAcceptedKafkaConsumerTests -Dsurefire.failIfNoSpecifiedTests=false` | Post-commit acknowledgement, replay outcomes, conflict/retry classification, poison input, trace restoration, and sanitized logging | PASS | 6/6 tests passed (2026-08-15) |
| T032 | `./mvnw -pl services/order-service -am test -Dtest=PurchaseAcceptedConsumerIntegrationTests,PurchaseAcceptedRetryDltIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | Opt-in live Kafka/Schema Registry integration fixtures | PASS | Default suite skips live tests; opt-in live runs below passed 5/5 tests (2026-08-15) |
| T033-T035 | `./mvnw -pl services/order-service -am verify` | Mapper, typed failures, listener, manual-immediate ack, Avro consumer factory, 1/3/10-second retry backoff, DLT recoverer, and runtime switch | PASS | Order reactor: 48 tests, 0 failures, 0 errors, 5 opt-in live tests skipped by default; BUILD SUCCESS (2026-08-15) |
| T036 | `./mvnw -pl services/order-service -am test -Dtest=PurchaseAcceptedConsumerIntegrationTests,PurchaseAcceptedRetryDltIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false -DargLine="-Dorder.schema-registry.enabled=true -DSCHEMA_REGISTRY_URL=http://localhost:8081"` | Real broker/Registry delivery, topic/group/key/header handling, duplicate redelivery, transient retry recovery, exhausted retries, and poison DLT routing | PASS | Consumer integration 2/2 and retry/DLT integration 3/3 passed (5/5 total). The exhausted case invoked the use case 4 times (initial delivery plus 3 retries) before routing to `flashsale.order.purchase-accepted.dlt.v1`. Topic `flashsale.purchase.events.v1`, group `order-purchase-accepted-v1`; records use `purchaseRequestId` keys and trace headers. DLT schema subject is explicitly provisioned by the opt-in fixture; production provisioning remains an infrastructure task for a later group (2026-08-15) |

### G4 Checkpoint

- [x] `PurchaseAcceptedV1` is validated at the Kafka boundary before entering the use case.
- [x] Manual acknowledgement occurs only after the local use case returns a committed/replayed outcome.
- [x] Equivalent re-delivery is acknowledged safely; contradictory identity and poison records are classified without mutating Order state.
- [x] Transient persistence failures use bounded 1/3/10-second retries and non-retryable/exhausted records route to the consumer-specific DLT.
- [x] Trace context is restored or safely generated and propagated through the command; raw payloads and secrets are not logged.

## G5 User Story 2 — Durable Order-created outbox publication

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T037 | `./mvnw -pl services/order-service -am test -Dtest=OrderOutboxRetryPolicyTests,OrderOutboxPublicationServiceTests,OrderOutboxPublisherJobTests -Dsurefire.failIfNoSpecifiedTests=false` | Capped exponential retry, sequential publication, worker ownership, sanitized failure, and scheduled UTC invocation | PASS | 6/6 tests passed; failure stores only exception class, requeues the same event for a bounded delay, and a recovered dependency republishes the same identity (2026-08-15) |
| T038 | `./mvnw -pl services/order-service -am test -Dtest=OrderCreatedAvroMapperTests -Dsurefire.failIfNoSpecifiedTests=false` | Immutable snapshot-to-Avro envelope/data mapping, UUID/decimal/time validation, one-item rule, aggregate/key identity, and infrastructure-field exclusion | PASS | 4/4 tests passed; generated `OrderCreatedV1` remains outside the application model (2026-08-15) |
| T039 | `./mvnw -pl services/order-service -am test -Dtest=OrderOutboxConcurrencyIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | PostgreSQL lease claim, `FOR UPDATE SKIP LOCKED`, expired-lease recovery, stale-worker fencing, failed-publication requeue, and stable event identity | PASS | PostgreSQL 17 Testcontainers: 4/4 tests passed. Two workers claim one due row once; a recovered worker increments the attempt; stale workers are fenced; failures return the same identity to `PENDING` with a due retry time (2026-08-15) |
| T040 | `./mvnw -pl services/order-service -am test -Dtest=OrderCreatedKafkaIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false -DargLine="-Dorder.schema-registry.enabled=true -DSCHEMA_REGISTRY_URL=http://localhost:8081"` | Live Kafka/Registry publication with controlled subject, `TopicRecordNameStrategy`, `auto.register.schemas=false`, key, record identity, trace headers, and duplicate delivery | PASS | 2/2 live tests passed. Subject `flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderCreatedV1` was pre-registered with `BACKWARD_TRANSITIVE`; the same event ID/key/payload was observed twice. Publisher failures are requeued by the T037 application test (2026-08-15) |
| T041-T045 | `./mvnw -pl services/order-service -am verify` | Outbox record/ports, retry policy, orchestration, PostgreSQL adapter, Avro/Kafka adapter, scheduler, configuration, and architecture boundaries | PASS | Order reactor: 64 tests, 0 failures, 0 errors, 7 opt-in live tests skipped by default; JAR packaged and all four reactor projects succeeded (2026-08-15) |
| T046 | `git diff --check` plus the focused PostgreSQL and live Kafka/Registry commands above | Recorded commands, identity/key/subject/recovery evidence, and clean patch formatting | PASS | `git diff --check` passed; G5 evidence is captured in this ledger with command scope and exit status (2026-08-15) |

### G5 Checkpoint

- [x] A committed outbox row is leased by one worker at a time with PostgreSQL row locking and
  expires/re-enters the retry path after a worker interruption.
- [x] Kafka publication uses the stable event identity and `orderId` key, preserves trace headers,
  and can produce duplicate physical records without changing the logical fact.
- [x] Schema registration is controlled outside bootstrap; live publication uses the approved
  record-name subject and `BACKWARD_TRANSITIVE` compatibility.
- [x] Kafka/Registry publication failures are retryable application failures; they do not delete or
  roll back the committed Order/outbox fact.

## G6 User Story 3 — Query Only My Orders

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T047 | `./mvnw -pl services/order-service -am test -Dtest=OrderQueryServiceTests -Dsurefire.failIfNoSpecifiedTests=false` | Owner detail/list orchestration, foreign/unknown equivalence, bounded page size, and Kafka-independent application ports | PASS | 3/3 unit tests passed; no Kafka or persistence framework type enters the application use case (2026-08-16) |
| T048 | `./mvnw -pl services/order-service -am test -Dtest=OwnedOrderQueryPersistenceIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | PostgreSQL owner predicate, line snapshot mapping, owner-only list, deterministic `createdAt DESC, id DESC` ordering | PASS | PostgreSQL 17 Testcontainers; 2/2 integration tests passed; foreign owner returns empty and inbox/outbox tables are not queried (2026-08-16) |
| T049 | `./mvnw -pl services/order-service -am test -Dtest=OrderQueryControllerTests -Dsurefire.failIfNoSpecifiedTests=false` | Shared success/page/error envelopes, validation, forbidden `userId`, malformed UUID, no-store and `X-Trace-Id` | PASS | MVC MockMvc contract suite: 3/3 tests passed (2026-08-16) |
| T050 | `./mvnw -pl services/order-service -am test -Dtest=OrderJwtTrustConfigurationTests,OrderPublicSecurityTests -Dsurefire.failIfNoSpecifiedTests=false` | Issuer, audience, `typ`, nonblank subject, expiry, authentication failure sanitization, bearer challenge, and route security boundary | PASS | JWT validator tests 2/2 and public security tests 2/2 passed (2026-08-16) |
| T051-T053 | `./mvnw -pl services/order-service -am verify` | Application ports/results, owner-scoped JPA repositories/adapter, not-found semantics and deterministic pagination | PASS | Order module full reactor verification passed; JPA context started against PostgreSQL and query integration passed (2026-08-16) |
| T054-T058 | `./mvnw -pl services/order-service -am verify` | HTTP mapper/responses, GET endpoints, error/advice/security handlers, JWT decoder, configuration wiring, and architecture boundaries | PASS | Order reactor: 76 tests, 0 failures, 0 errors, 7 opt-in live tests skipped; JAR packaged; BUILD SUCCESS (2026-08-16) |
| T059 | `./mvnw -pl services/api-gateway -am verify` | `order-public` route, GET predicate, target URL, gateway security registration, and existing gateway regression suite | PASS | Gateway reactor: 188 tests, 0 failures, 0 errors; route configuration test found `order-public` targeting `http://order-service:8080`; BUILD SUCCESS (2026-08-16) |
| T060 | `git diff --check` plus all focused/module/Gateway commands above | Evidence integrity and completion checkpoint | PASS | Whitespace check passed; owner/foreign/unknown, pagination metadata, trace/no-store, JWT failures, and Gateway route evidence recorded (2026-08-16) |

### G6 Checkpoint

- [x] An authenticated shopper can retrieve only their committed Orders; unknown and foreign Orders
  return the same `ORDER_NOT_FOUND` outcome.
- [x] PostgreSQL is the durable source for owner queries; Kafka, Schema Registry, inbox, and outbox
  are not dependencies of the read path.
- [x] Detail/list responses use the approved shared envelopes, bounded pagination, deterministic
  ordering, `Cache-Control: no-store`, and an `X-Trace-Id` response header.
- [x] JWT trust is independently enforced at Order Service and Gateway; invalid credentials are
  sanitized as `AUTHENTICATION_REQUIRED` without token or SQL disclosure.
- [x] Gateway exposes only the authenticated `GET /api/v1/orders/**` route to Order Service.

## Later Evidence Slots

The following sections will be expanded by the corresponding approved groups:

- G2: Liquibase migration, PostgreSQL schema, constraints, rollback, and persistence foundation.
- G3-G4: accepted-purchase idempotency, atomic Order creation, Kafka consumer retry/DLT, and replay.
- G7-G8: readiness, Compose smoke, failure matrix, concurrency, performance, module, and full build.
