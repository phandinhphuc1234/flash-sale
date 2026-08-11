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
