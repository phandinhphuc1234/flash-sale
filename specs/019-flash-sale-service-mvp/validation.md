# Feature 019 Validation Evidence

## G1 — Setup and Module Baseline

**Date**: 2026-08-10  
**Scope**: T001–T005; Flash Sale module dependency/configuration/bootstrap baseline  
**Command**: `./mvnw -pl services/flashsale-service -am verify`  
**Result**: PASS — exit status 0  

Summary:

- `common-web` and `kafka-avro-contracts` reactor prerequisites passed.
- Flash Sale Maven dependency resolution and MapStruct compiler configuration passed.
- `FlashSaleConfigurationPropertiesTests`: 2 tests passed, including invalid idempotency-key bound.
- `FlashsaleServiceApplicationTests`: 1 context test passed.
- No production business behavior, schema, Kafka consumer, Redis script, or public endpoint was
  implemented in G1.
- Maven emitted Mockito dynamic-agent and SLF4J provider warnings; no test failure occurred.

**Rerun**: After aligning default URLs with the shared Docker Compose service names, the same module
verification was rerun and passed with exit status 0.

Future task groups append their command, scope, exit status, and failure details below this section.

## G2 — Foundational Boundaries

**Date**: 2026-08-10
**Scope**: T006–T014; Liquibase schema, independent JWT trust, HTTP error envelope, W3C/MDC
context, and Clean/Hexagonal architecture checks
**Command**: `./mvnw -pl services/flashsale-service -am verify`
**Result**: PASS — exit status 0

Summary:

- Liquibase formatted SQL defines the four service-owned durable tables, PostgreSQL checks/indexes,
  unique business identities, and an explicit development rollback order; a real PostgreSQL 16
  Testcontainers run applied all eight changesets successfully.
- JWT trust validates issuer, `flash-sale-api` audience, `typ=at+jwt`, expiry/signature through the
  Nimbus decoder, and UUID `sub`; security failures use sanitized shared 401/403 envelopes.
- Flash Sale errors use the shared `ApiErrorResponse`/`FieldViolation` shape with `X-Trace-Id` and
  `Cache-Control: no-store`; trace IDs are not copied into JSON bodies.
- W3C `traceparent` extraction and MDC request context are bounded, and observation names are
  stable low-cardinality constants.
- Architecture tests reject provider imports in domain/application and keep Redis/Kafka at adapter
  or configuration boundaries.
- Flash Sale tests: 14 passed in the module verification, including 3 migration integration tests
  against PostgreSQL 16; `common-web` (9) and Kafka contract (3) reactor tests also passed.
- No user-story behavior, Redis Lua script, Kafka consumer, outbox worker, or public reservation
  endpoint was implemented in G2.
- Maven emitted only existing Mockito dynamic-agent and SLF4J provider warnings; no test failed.

## G3 — Campaign Projection

**Date**: 2026-08-11
**Scope**: T015–T019; Campaign projection domain/application core and atomic Redis projection
scripts/adapter
**Command**: `./mvnw -pl services/flashsale-service -am test`
**Result**: PASS — exit status 0

Summary:

- Campaign projection domain validates positive aggregate versions, complete item snapshots,
  exact four-decimal prices, uppercase currency, positive allocation/limit, and valid sale windows.
- Application ports keep scheduled, activation, and recovery orchestration independent of Kafka,
  Redis, and HTTP clients.
- Redis Lua applies version guards atomically. Duplicate/lower versions return `NOOP_STALE`;
  activation without a prepared projection returns `RECOVERY_REQUIRED` and does not create quota.
- Recovery can replace a recovery marker at the same version and preserves an already usable stock
  counter instead of resetting consumed quota.
- Real Redis 7 Testcontainers tests covered duplicate/stale scheduled facts, activation-before-
  schedule, recovery repair, activation window mismatch, active-state quota preservation, and
  application recovery queue behavior.
- Flash Sale tests: 21 passed; `common-web` (9) and Kafka contract (3) reactor tests also passed.
- No Kafka consumer, OpenFeign recovery client, scheduler, reservation admission, or public API was
  implemented in G3; those remain in G4 and later groups.

## G4 — Campaign Kafka Consumer and Control-Plane Recovery

**Date**: 2026-08-11
**Scope**: T020–T027; Avro Campaign lifecycle consumer, bounded manual acknowledgement, recovery-only
OpenFeign client, OAuth2 service identity, five-second recovery scheduler, and Redis recovery evidence
**Commands**:

- `./mvnw -pl services/flashsale-service -am '-Dtest=CampaignProjectionKafkaConfigurationTests,CampaignLifecycleConsumerContractTests,CampaignSnapshotClientAdapterTests,FlashSaleServiceTokenManagerTests,CampaignProjectionRecoveryIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test`
- `./mvnw -pl services/flashsale-service -am clean verify`
- `git diff --check`

**Result**: PASS — targeted G4 tests exit status 0; clean module verification exit status 0; diff check
reported no whitespace errors.

The final Feign child-context condition was also rerun with
`./mvnw -pl services/flashsale-service -am verify` and remained green with 38 Flash Sale tests.

Summary:

- `CampaignLifecycleKafkaConsumer` accepts only generated `CampaignScheduledV1` and
  `CampaignActivatedV1` SpecificRecords, validates key/type/version/aggregate identity, propagates
  W3C context, and acknowledges only after the Redis projection use case succeeds.
- Kafka configuration uses `campaign.lifecycle.v1`, group
  `flashsale-campaign-projection-v1`, manual acknowledgement, three total deliveries with 250/500 ms
  retry delays, no DLT, and an uncommitted offset when the bounded listener handling stops.
- Recovery uses a Redis sorted-set queue and a five-second scheduler. The recovery-only OpenFeign
  boundary calls Campaign with 500 ms connect and 1,000 ms read timeouts, no Feign retry, and maps
  HTTP 401/403/404/409/429/5xx into sanitized recovery outcomes.
- OAuth2 Client Credentials is cached by Spring's authorized-client manager for subject
  `flashsale-service`, scope `campaign.snapshot.read`, audience `flash-sale-internal-api`, and a
  maximum token lifetime of 300 seconds. The recovery client is outside the shopper reservation path.
- Real Redis 7 Testcontainers tests covered scheduled projection, activation-before-schedule
  recovery, stale/incomplete snapshot rejection, and Redis-loss fail-closed/no-ack behavior.
- Flash Sale module verification passed 38 tests; the focused G4 suite passed 17 tests. The run also
  passed the `common-web` (9) and Kafka Avro contract (3) reactor tests. Test output contained only
  existing Mockito dynamic-agent and SLF4J provider warnings.
- This evidence does not claim a live Kafka broker or Schema Registry end-to-end run; those remain
  part of the later operational validation group.

## G5 — Reservation Domain and Atomic Redis Admission

**Date**: 2026-08-11
**Scope**: T028–T033; reservation lifecycle model, atomic application boundary, SHA-256
fingerprints/stable identities, Redis Lua admission, expiry index, and Stream handoff
**Commands**:

- `./mvnw -pl services/flashsale-service -am -Dtest=ReservationDomainTests,ReserveCampaignQuotaServiceTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `./mvnw -pl services/flashsale-service -am -Dtest=AtomicReservationRedisIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `git diff --check`

**Result**: PASS — focused unit tests passed 6 tests; real Redis 7 Testcontainers passed 5 tests;
diff check reported no whitespace errors.

Summary:

- Reservation domain protects the `RESERVED -> EXPIRED` lifecycle, exact four-decimal money
  snapshot, positive quantity, stable identities, and the non-resurrectable purchase outcome.
- The application service hashes the raw case-sensitive idempotency key and the canonical
  `(userId, campaignId, variantId, quantity)` request, creates purchase/reservation/event IDs before
  Lua, and calculates the approved five-minute expiry and Campaign-end-plus-24-hour retention.
- Redis Lua evaluates projection state/window, Variant snapshot, sold-out and per-user limits,
  idempotency replay/conflict, quota/user counters, immutable reservation snapshot, expiry ZSET, and
  `XADD` handoff as one operation. No Java quota fallback is used.
- Real Redis tests covered accepted snapshots, exact quota/user-counter changes, stable replay
  identity with one Stream entry, changed-request conflict, sold-out rejection, purchase-limit
  rejection, and unchanged counters on rejected requests.
- Full `./mvnw -pl services/flashsale-service -am verify` then passed 49 Flash Sale tests, along
  with the common-web (9) and Kafka Avro contract (3) reactor tests; only existing Mockito agent
  and SLF4J provider warnings were emitted.
- Durable PostgreSQL persistence, Stream consumer recovery, expiry release, public HTTP, and Kafka
  publication remain intentionally unimplemented for G6+ and later approved task groups.

## G6 — Reservation Concurrency, Failure, and Application Flow

**Date**: 2026-08-11
**Scope**: T034–T038; Redis oversell concurrency, idempotency concurrency, Redis failure/NOSCRIPT
behavior, and the deterministic durable-acceptance application flow
**Commands**:

- `./mvnw -pl services/flashsale-service -am -Dtest=ReservationDomainTests,ReserveCampaignQuotaServiceTests,AtomicReservationRedisIntegrationTests,ReservationOversellConcurrencyIntegrationTests,ReservationIdempotencyConcurrencyIntegrationTests,ReservationRedisFailureIntegrationTests,ReserveCampaignQuotaFlowTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `./mvnw -pl services/flashsale-service -am verify`
- `git diff --check`

**Result**: PASS — focused G6 suite exit status 0; full flashsale-service reactor verification exit
status 0; diff check reported no whitespace errors.

Summary:

- The oversell integration test coordinated 1,000 concurrent attempts against 100 units. Exactly
  100 reservations were accepted, the Redis stock counter reached zero, no per-user limit was
  violated, and the reconciled user quantities totaled 100.
- The idempotency integration test coordinated 100 identical retries. All callers observed the same
  logical acceptance identity, exactly one Stream entry was created, quota decremented once, and a
  changed request under the same key was rejected without changing stock.
- Redis failure coverage verifies fail-closed behavior with no PostgreSQL/Inventory fallback,
  hash-tagged/protected business keys, and `NOSCRIPT` script reload before the operation proceeds.
  The expected Lettuce reconnect warnings after deliberately stopping the Testcontainer are not
  test failures.
- `ReservationAcceptanceFlow` is tested with deterministic fake durable and acknowledgement ports:
  durable success acknowledges the handoff, persistence failure returns `ACCEPTANCE_PENDING` without
  acknowledgement, expired reservations are terminal, and acknowledgement occurs only after durable
  persistence succeeds.
- The focused suite passed 20 tests. Full module verification passed 58 Flash Sale tests, plus
  `common-web` (9) and Kafka Avro contract (3) reactor tests. Maven emitted only existing Mockito
  dynamic-agent and SLF4J provider warnings.
- This group still does not claim real PostgreSQL durable acceptance, Stream recovery, public HTTP,
  or Kafka publication; those remain in the later approved groups.

## G7 — Durable PostgreSQL Acceptance, Expiry, and Retention

**Date**: 2026-08-11
**Scope**: T039–T047; durable acceptance/outbox transaction, PostgreSQL arbitration, durable-first
expiry, idempotent Redis quota release, and bounded idempotency cleanup.
**Commands**:

- `./mvnw -pl services/flashsale-service -am -Dtest=DurableAcceptancePersistenceIntegrationTests,DurableAcceptanceConcurrencyIntegrationTests,ReservationExpiryIntegrationTests,IdempotencyRetentionIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `./mvnw -pl services/flashsale-service -am -Dtest=AtomicReservationRedisIntegrationTests,ReservationIdempotencyConcurrencyIntegrationTests,ReservationRedisFailureIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `./mvnw -pl services/flashsale-service -am verify`
- `git diff --check`

**Result**: PASS — focused G7 validation passed, PostgreSQL concurrency accepted one durable result,
the repaired Redis time fixtures passed, and full module verification exited 0 with 65 Flash Sale
tests, 9 `common-web` tests, and 3 Kafka Avro contract tests.

Summary:

- Acceptance commits the purchase request, immutable reservation snapshot, scoped hashed
  idempotency row, and `PURCHASE_REQUEST` outbox intent together. A PostgreSQL advisory transaction
  lock serializes same-key request-thread and Stream-worker races; an after-expiry writer persists an
  `EXPIRED` tombstone and never creates a reservation or accepted outbox event.
- The expiry path locks PostgreSQL first, then invokes an idempotent Lua release with a one-way
  `quotaReleased` marker after commit. It emits no expiry event.
- Cleanup deletes only bounded batches of expired idempotency rows; purchases, reservations, and
  outbox audit history remain untouched. Redis replay fixtures now use a future clock so retained
  keys cannot expire during tests.

## G8 — Redis Stream Recovery and Reclaim

**Date**: 2026-08-12
**Scope**: T048–T051; durable reservation handoff consumer-group recovery, pending reclaim, and
terminal acknowledgement cleanup.
**Commands**:

- `./mvnw -pl services/flashsale-service -am -Dtest=ReservationHandoffAutoClaimIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `./mvnw -pl services/flashsale-service -am -Dtest=ReservationHandoffRecoveryIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `./mvnw -pl services/flashsale-service -am verify`
- `git diff --check`

**Result**: PASS — focused auto-claim test passed 1 test; focused recovery tests passed 3 tests;
full module verification exited 0 with 69 Flash Sale tests, 9 `common-web` tests, and 3 Kafka
Avro contract tests.

Summary:

- Redis Stream fields are mapped into the immutable accepted-reservation snapshot, and the
  consumer group is created idempotently with `MKSTREAM`.
- New entries and reclaimed pending entries invoke the same `ReservationAcceptanceFlow` used by
  the request path. Redis failures leave entries pending for retry.
- `XAUTOCLAIM` uses the configured 30-second idle threshold and 100-entry batch; the integration
  test confirms only the old pending entry transfers and its identity, snapshot, and trace fields
  remain unchanged.
- Terminal handling runs the Lua `XACK` + `XDEL` sequence atomically. No stream trimming is used,
  and replay after a post-commit acknowledgement failure reuses the durable identity safely.
- Testcontainers used Redis 7 for all focused recovery tests. Maven emitted only existing Mockito
  dynamic-agent and SLF4J provider warnings; no test failed.

## G9 — Avro and Transactional Outbox Publication

**Date**: 2026-08-12
**Scope**: T052–T059; schema-first `PurchaseAcceptedV1`, leased PostgreSQL outbox relay,
adapter-boundary Avro mapping, retry/backoff, and scheduled publication.
**Commands**:

- `./mvnw -pl contracts/kafka-avro-contracts -am test`
- `./mvnw -pl services/flashsale-service -am -Dtest=FlashSaleOutboxPublisherTests,FlashSaleOutboxConcurrencyIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test`
- `./mvnw -pl services/flashsale-service -am clean verify`
- `git diff --check`

**Result**: PASS — Avro contract module passed 6 tests; focused outbox tests passed 7 tests;
full clean module verification exited 0 with 76 Flash Sale tests, 9 `common-web` tests, and 6
Kafka Avro contract tests. The opt-in `PurchaseAcceptedKafkaIntegrationTests` compiles and is
enabled with `RUN_KAFKA_INTEGRATION_TESTS=true` against the local Kafka + Confluent Registry
stack; it is skipped by default when that external stack is not running.

Summary:

- `PurchaseAcceptedV1` uses UUID/timestamp/decimal logical types and excludes JWT, idempotency,
  JPA, and Redis internals. Contract tests assert the record identity and compatibility policy.
- PostgreSQL claims due rows with `FOR UPDATE SKIP LOCKED`, leases for 30 seconds, increments
  attempts, and requeues failures indefinitely with capped exponential backoff and sanitized
  diagnostics.
- The relay publishes outside the claim transaction, preserves the outbox event identity and
  key, propagates W3C trace headers, and acknowledges publication idempotently.
- Application context wiring is conditional on JDBC availability, so lightweight context tests
  remain bootable without a configured datasource while PostgreSQL integration tests import the
  adapter directly.

### T060 — Live Kafka and Schema Registry validation

**Date**: 2026-08-13
**Scope**: Strengthen the opt-in black-box test for real Avro serialization, controlled subject
compatibility, the approved three-partition topic, duplicate delivery, broker/Registry outages,
and stable event identity/payload recovery.
**Commands**:

- `.\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/flashsale-service -am "-DskipTests" test-compile`
- `$env:RUN_KAFKA_INTEGRATION_TESTS='false'; .\\mvnw.cmd --batch-mode --no-transfer-progress -pl services/flashsale-service -am "-Dtest=PurchaseAcceptedKafkaIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `$env:CAMPAIGN_CLIENT_SECRET='local-test-placeholder'; docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d kafka schema-registry`
- `$env:RUN_KAFKA_INTEGRATION_TESTS='true'; $env:KAFKA_BOOTSTRAP_SERVERS='localhost:29092'; $env:SCHEMA_REGISTRY_URL='http://localhost:8081'; .\mvnw.cmd --batch-mode --no-transfer-progress -pl services/flashsale-service -am "-Dtest=PurchaseAcceptedKafkaIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `git diff --check`

**Result**: PASS — the live run executed 1 test with 0 failures, errors, or skips. It verified the
`BACKWARD_TRANSITIVE` subject compatibility, the approved three-partition topic, duplicate delivery,
broker and Schema Registry outage/recovery, and stable original event identity/payload. The Maven
reactor (`common-web`, Avro contracts, and `flashsale-service`) completed successfully.

## G10 — Public POST and Gateway integration

**Date**: 2026-08-13
**Scope**: T061–T064; public reservation submit boundary, durable acceptance/pending recovery,
shared envelopes, authenticated Gateway routing, and request pass-through.
**Commands**:

- `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/flashsale-service -am "-Dtest=ReservationSubmissionServiceTests,FlashSaleReservationSubmitContractTests,FlashSaleDurableAcceptanceIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/api-gateway -am "-Dtest=FlashSaleGatewayRouteTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/flashsale-service -am -DskipTests verify`
- `.\mvnw.cmd --batch-mode --no-transfer-progress -pl services/api-gateway -am -DskipTests verify`
- `git diff --check`

**Result**: PASS — Flash Sale focused G10 tests passed 13 tests; Gateway focused G10 tests passed
3 tests. Both affected Maven reactors completed package verification with `-DskipTests`, and the
diff check passed. The combined Gateway regression run also passed the new route, proxy pass-through,
and product-admin tests; one pre-existing Campaign admin scope test timed out during the local
combined run and again when isolated, while its other five scenarios passed.

Summary:

- `POST /api/v1/flash-sales/{campaignId}/reservations` binds shopper identity only from JWT `sub`,
  validates the body and 1–128 character idempotency header, and returns shared `ApiResponse` with
  `202`, stable `Location`, `X-Trace-Id`, and `Cache-Control: no-store` only after durable acceptance.
- The application flow reads only the projected Campaign end boundary, executes Redis admission,
  persists through the existing PostgreSQL acceptance port, and returns `503` with `Retry-After: 1`
  when the recoverable Redis Stream winner is not durable yet.
- API Gateway authenticates `/api/v1/flash-sales/**` and forwards Authorization, Idempotency-Key,
  W3C trace headers, X-Trace-Id, path, query, body, and downstream status/body without business
  interpretation.
