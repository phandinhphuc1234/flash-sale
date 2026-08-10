# Feature 017 Quickstart and Validation

**Status**: T017 baseline recorded — full Feature 017 implementation remains in progress
**Purpose**: Define the repeatable local validation flow that implementation tasks must make pass

## 1. Prerequisites

- Java 21;
- Docker Desktop/Compose;
- repository Maven wrapper;
- local `infra/docker/.env` copied from `.env.example` and never committed;
- a matching RSA public/private key pair mounted for Authentication Service;
- non-placeholder PostgreSQL, Redis, rate-limit HMAC, Auth throttle HMAC, Campaign client, and Flash
  Sale client secrets.

Feature 017 adds these local environment values to `.env.example` as placeholders:

```text
CAMPAIGN_LIQUIBASE_ENABLED=false
INTERNAL_JWT_AUDIENCE=flash-sale-internal-api
CAMPAIGN_CLIENT_ID=campaign-service
CAMPAIGN_CLIENT_SECRET=REPLACE_WITH_A_LONG_RANDOM_SECRET
FLASHSALE_CLIENT_ID=flashsale-service
FLASHSALE_CLIENT_SECRET=REPLACE_WITH_ANOTHER_LONG_RANDOM_SECRET
PRODUCT_SERVICE_URL=http://product-service:8080
INVENTORY_SERVICE_URL=http://inventory-service:8080
OAUTH_TOKEN_URI=http://authentication-service:8080/oauth2/token
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
SCHEMA_REGISTRY_URL=http://schema-registry:8081
KAFKA_AUTO_REGISTER_SCHEMAS=false
```

Authentication and the corresponding client container receive the same raw bootstrap secret through
separate environment injection. Authentication stores only an encoded value. Never paste an actual
secret into tracked YAML, Markdown, Java, SQL, Dockerfile, or image layer.

## 2. Build the affected modules

```powershell
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/product-service,services/inventory-service,services/campaign-service -am verify
```

Then run the full reactor:

```powershell
.\mvnw.cmd clean verify
```

### T017 baseline evidence (2026-07-30)

The foundational Campaign and Authentication context/migration baselines were run independently
before advancing to the next Feature 017 phase. Local Campaign datasource settings were injected
through the environment and are intentionally not recorded here.

Campaign baseline:

```powershell
.\mvnw.cmd -pl services/campaign-service -am clean test
```

Result: exit code `0`, `9` tests run, `0` failures, `0` errors. The Campaign Spring context loaded,
Liquibase reported the schema as up to date against the configured local PostgreSQL database, and
the Testcontainers migration checks passed.

Authentication baseline:

```powershell
.\mvnw.cmd -pl services/authentication-service -am clean test
```

Result: exit code `0`, `43` tests run, `0` failures, `0` errors. The Authentication Spring context,
refresh/concurrency persistence tests, and PostgreSQL Testcontainers Liquibase migration checks
passed.

## 3. Validate Compose configuration

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml `
  --profile apps config
```

This must not print real secret values in captured CI/PR evidence.

## 4. Start backing services

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka schema-registry
```

For an old PostgreSQL volume, verify `auth_db`, `product_db`, `campaign_db`, and `inventory_db` exist.
Create a missing logical database through an approved non-destructive administration command; do not
delete the volume merely to rerun bootstrap SQL.

## 5. Apply owning-service migrations

Build Auth and Campaign images:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml build authentication-service campaign-service
```

Apply Authentication's client-registry migration with a one-off non-web process:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  authentication-service --spring.main.web-application-type=none
```

Apply Campaign's migration the same way:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  campaign-service --spring.main.web-application-type=none
```

Normal replicas keep Liquibase disabled after the one-off migration completes.

## 6. Start the affected topology

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml `
  --profile apps up --build `
  api-gateway authentication-service product-service inventory-service campaign-service
```

The implementation must ensure local topic `campaign.lifecycle.v1` exists with three partitions and
replication factor one before the outbox publisher is expected to succeed. The Avro amendment also
requires Schema Registry at `http://schema-registry:8081`; controlled registration must complete
before a producer rollout because `auto.register.schemas=false`.

Debug endpoints:

```text
Gateway:        http://localhost:8080
Authentication:http://localhost:18081
Product:        http://localhost:18082
Campaign:       http://localhost:18083
Inventory:      http://localhost:18088
```

Only Gateway is the normal external ingress. Direct ports are for local diagnostics/contract tests.

## 7. Health and metrics smoke

Verify each affected service:

```text
/actuator/health/liveness
/actuator/health/readiness
/actuator/prometheus
```

Readiness must reflect required database connectivity. Kafka publication failure is visible through
outbox metrics/state and must not make durable Campaign state disappear.

## 8. Client Credentials smoke

Campaign client:

```powershell
curl.exe -u "$env:CAMPAIGN_CLIENT_ID`:$env:CAMPAIGN_CLIENT_SECRET" `
  -H "Content-Type: application/x-www-form-urlencoded" `
  -d "grant_type=client_credentials&scope=catalog.read" `
  http://localhost:18081/oauth2/token
```

Verify the JWT has:

```text
sub=campaign-service
aud=flash-sale-internal-api
scope=catalog.read
exp-iat <= 300 seconds
typ=at+jwt
```

Repeat with `inventory.campaign.allocate`. A disallowed scope, wrong secret, inactive client, or
wrong grant must fail with the standard OAuth error response and must not return an access token or
refresh token.

Flash Sale client obtains only `campaign.snapshot.read`. It cannot validate Product or allocate
Inventory.

## 9. Campaign business smoke sequence

Prepare an admin access token through the existing login flow, then use Gateway only:

```text
1. POST /api/v1/admin/campaigns
2. PUT  /api/v1/admin/campaigns/{id}/item with If-Match
3. GET  /api/v1/admin/campaigns/{id}
4. POST /api/v1/admin/campaigns/{id}/schedule with If-Match + Idempotency-Key
```

Before scheduling, the chosen Variant and Inventory item must exist through their owning-service
admin APIs. Do not seed Product/Inventory by writing their databases directly.

Verify after schedule:

- one Inventory allocation exists for the stable request ID;
- Campaign is SCHEDULED and immutable;
- snapshot/allocation quantity is complete;
- exactly one `CampaignScheduled.v1` outbox row exists;
- Kafka receives the approved Avro SpecificRecord with Campaign ID key and W3C trace headers;
- repeated identical schedule returns/resumes the same result;
- changed request with the same key returns 409 without another allocation;
- Product/Inventory request capture contains a Campaign service token, never the administrator token;
- `X-Trace-Id` appears across HTTP boundaries; W3C `traceparent`/`tracestate` appears in Kafka
  headers, outbox relay spans, and logs.

## 10. Lifecycle and snapshot smoke

Use a short future time window in a controlled local fixture:

- at start, Campaign becomes ACTIVE exactly once and publishes one `CampaignActivated.v1` after the
  Scheduled event;
- at end, Campaign becomes ENDED exactly once and emits no ended event;
- early manual activation returns 409;
- Flash Sale client token with `campaign.snapshot.read` receives the snapshot;
- admin, Campaign service, wrong-audience, wrong-subject, and missing-scope tokens are denied.

## 11. Failure/recovery smoke

Exercise at least:

- stop Product before schedule -> 503 and Campaign remains DRAFT;
- stop Inventory before/around allocation -> resumable operation with unchanged request ID;
- simulate crash after Inventory success -> retry completes one Campaign/one event;
- stop Kafka or Schema Registry -> outbox remains durable and retries with backoff;
- reach ten publication failures -> FAILED;
- authorized requeue -> same event ID returns PENDING and later publishes;
- expire publisher lease -> another instance reclaims without changing event identity;
- run two schedule/activation workers -> one business outcome.

## 12. Final evidence

Record command, scope, exit code/result, and relevant CI/PR reference for:

```powershell
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/product-service,services/inventory-service,services/campaign-service -am verify
.\mvnw.cmd clean verify
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
curl.exe http://localhost:8081/subjects
```

Do not report Feature 017 complete while a required test, migration, contract, concurrency, Kafka,
security, observability, or smoke check is failing.

## T022 Campaign admin security evidence

Validated on 2026-08-02:

```text
./mvnw -pl services/campaign-service -am verify
BUILD SUCCESS
Campaign module tests: 33 passed, 0 failures, 0 errors
```

`CampaignAdminSecurityTests` covers missing/invalid/wrong-audience tokens (`401`), missing
`SCOPE_CAMPAIGN_ADMIN` (`403`), and a scoped administrator reaching controller validation.

## 11. T023 Gateway contract-test baseline

Validated on 2026-08-02 before the T032 Gateway production wiring:

```powershell
.\mvnw.cmd -pl services/api-gateway -am "-Dtest=CampaignAdminGatewayRouteTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD FAILURE (expected red baseline for test-first workflow)
```

The new `CampaignAdminGatewayRouteTests` covers route registration, unauthenticated access,
wrong-audience rejection, missing `SCOPE_CAMPAIGN_ADMIN`, and method/path/query/body plus
`Authorization`, `X-Trace-Id`, and `Idempotency-Key` forwarding. Two assertions fail as expected
because T032 has not yet added the `campaign-admin` route and authority rule; the security-only
assertions pass. Re-run this class after T032 and require a green result before completing US1.

## 12. T030 Campaign HTTP error mapping evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/campaign-service -am verify
BUILD SUCCESS
Campaign module tests: 33 passed, 0 failures, 0 errors
```

`CampaignHttpExceptionHandler` now translates application failures for missing Campaigns,
duplicate codes, stale versions, and active operations to the approved `404`/`409` error codes;
request arguments map to `CAMPAIGN_VALIDATION_FAILED` (`400`), and lifecycle state failures map to
`CAMPAIGN_INVALID_STATUS` (`409`). The handler continues to use shared `ApiErrorResponse`, field
violations, `X-Trace-Id`, and `Cache-Control: no-store` without exposing exception details.

## 13. T031 administrator authority evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/authentication-service -am "-Dtest=JwtTrustCompatibilityIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD SUCCESS

.\mvnw.cmd -pl services/authentication-service -am verify
BUILD SUCCESS
Authentication module tests: 44 passed, 0 failures, 0 errors
```

`ROLE_ADMIN` access tokens now carry `CAMPAIGN_ADMIN` alongside the existing administrator
authorities. The compatibility test verifies the complete canonical claim list and JWT trust
headers/audience remain unchanged.

## 14. T032 Gateway route and authority evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/api-gateway -am "-Dtest=CampaignAdminGatewayRouteTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD SUCCESS
Campaign Gateway contract tests: 6 passed, 0 failures, 0 errors

.\mvnw.cmd -pl services/api-gateway -am verify
BUILD SUCCESS
```

Gateway now forwards `/api/v1/admin/campaigns/**` to `${CAMPAIGN_SERVICE_URL}` and requires
`SCOPE_CAMPAIGN_ADMIN`. The route contract verifies method/path/query/body and required headers,
wrong-audience rejection, missing-scope denial, and that `/internal/**` remains unexposed.

## 15. T033 US1 independent draft acceptance evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/campaign-service -am verify
BUILD SUCCESS
```

Surefire totals for the US1 service set:

| Module | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| API Gateway | 183 | 0 | 0 | 0 |
| Authentication Service | 44 | 0 | 0 | 0 |
| Campaign Service | 33 | 0 | 0 | 0 |

The independent draft acceptance path is green: Campaign draft contract/security/persistence
tests pass, administrator JWT issuance includes the Campaign authority, and Gateway route/security
tests pass while internal Campaign endpoints remain denied. This validates the US1 boundary only;
Campaign scheduling, Product/Inventory calls, service identities, Kafka, and lifecycle workers
remain in later tasks.

## 16. T034 Client Credentials contract baseline

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/authentication-service -am "-Dtest=ClientCredentialsTokenEndpointTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD FAILURE (expected red baseline for test-first workflow)
```

`ClientCredentialsTokenEndpointTests` now provisions the approved OAuth client schema in a real
PostgreSQL Testcontainer and specifies the success contract (internal audience, client subject,
requested scopes, maximum 300-second TTL, RS256/JWKS-compatible token, and no refresh token) plus
unknown-client, inactive-client, wrong-secret, disallowed-grant, and disallowed-scope failures.
The current red result is expected because the `/oauth2/token` Authorization Server endpoint and
its durable client registry adapter are not wired yet; later US2 implementation tasks must make
this suite green.

## 17. T035 service-client persistence evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/authentication-service -am "-Dtest=ServiceClientPersistenceIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD SUCCESS
Service-client persistence tests: 5 passed, 0 failures, 0 errors
```

The PostgreSQL contract verifies Argon2-only secret storage and verification, client-scoped
allowed-scope loading, inactive status preservation for authorization checks, and idempotent fixed
client provisioning without overwriting an existing secret or scope set. The tests use a real
Liquibase-created schema and remain independent of future Spring Authorization Server adapter code.

## 18. T036 JWT claim-boundary evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/authentication-service -am "-Dtest=ServiceTokenClaimCompatibilityTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD FAILURE (expected red baseline for test-first workflow)
```

The new regression suite proves the existing administrator issuer keeps the public
`flash-sale-api` audience and administrator authority boundary. It also defines the executable
service-token contract: RS256/`at+jwt`, issuer `http://authentication-service:8080`, subject
`campaign-service`, audience `flash-sale-internal-api`, requested scope, and a maximum five-minute
TTL. The service-token assertion currently receives HTTP 401 because the OAuth2 client-credentials
endpoint is not implemented yet; T047 must make this test green without changing administrator
token claims.

## 19. T037 Product campaign-validation evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/product-service -am clean "-Dtest=ProductCampaignValidationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD FAILURE (expected red baseline for test-first workflow)
```

The PostgreSQL projection test passes against a Liquibase-created `product_db` and verifies that
only an ACTIVE, published Product with an ACTIVE positive-price VND Variant is sellable. The
token-substitution denial test passes: a public administrator audience cannot use the internal
boundary. The valid Campaign-service HTTP contract currently receives HTTP 403 because the
`/internal/v1/catalog/variants/campaign-validation` endpoint and its dedicated internal JWT chain
are not implemented yet; T048-T050 must make that assertion green while preserving the direct
snapshot response contract.

## 20. T038 Inventory allocation compatibility evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/inventory-service -am clean "-Dtest=CampaignAllocationCompatibilityTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD FAILURE (expected red baseline for test-first workflow)
```

The suite runs against a real Liquibase-created `inventory_db` and confirms the existing success
envelope and same-request replay behavior. The narrow `inventory.campaign.allocate` token currently
receives HTTP 403 because Inventory still requires the broad `SCOPE_INVENTORY_WRITE`. Conversely,
the broad/public token substitution tests currently receive HTTP 200, and insufficient-stock and
request-id-conflict scenarios still return `INVENTORY_OPERATION_REJECTED`. T051-T052 must introduce
the endpoint-specific identity/scope chain and stable machine-readable error codes while keeping
the replay and success envelope unchanged.

## 21. T039-T044 grouped US2 schedule-test evidence

Validated on 2026-08-02:

```powershell
.\mvnw.cmd -pl services/campaign-service -am clean "-Dtest=CampaignDownstreamClientContractTests,ScheduleCampaignUseCaseTests,CampaignSchedulePolicyTests,ScheduleCampaignConcurrencyIntegrationTests,ScheduleCampaignRecoveryIntegrationTests,CampaignScheduleHttpContractTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
BUILD FAILURE (expected red baseline for test-first workflow)
```

| Test class | Tests | Passed | Failures | Errors |
|---|---:|---:|---:|---:|
| `CampaignDownstreamClientContractTests` | 2 | 0 | 2 | 0 |
| `CampaignSchedulePolicyTests` | 7 | 7 | 0 | 0 |
| `ScheduleCampaignUseCaseTests` | 2 | 0 | 2 | 0 |
| `ScheduleCampaignConcurrencyIntegrationTests` | 2 | 2 | 0 | 0 |
| `ScheduleCampaignRecoveryIntegrationTests` | 2 | 2 | 0 | 0 |
| `CampaignScheduleHttpContractTests` | 3 | 0 | 3 | 0 |
| **Total** | **18** | **11** | **7** | **0** |

The PostgreSQL concurrency tests prove that the partial unique active-operation index leaves one
winner and that one winner can persist the allocation snapshot, `SCHEDULED` transition, and one
scheduled outbox row. Recovery tests preserve the operation and Inventory request identities.
Remaining red tests are intentional: downstream client/service-token adapters, schedule application
services, and the schedule HTTP endpoint are not implemented yet. T045-T062 must make these
contracts green without changing the approved identity, idempotency, transaction, or trace rules.

## 22. T045-T047 Authentication service-client implementation evidence

Implemented on 2026-08-02:

- Durable `ServiceClient` domain rules and application ports/use cases.
- JPA entities/repositories/mappers for `oauth_clients` and `oauth_client_scopes`.
- Read-only durable `RegisteredClientRepository`, Argon2 client-secret verification, and fixed
  client provisioning that never replaces an existing secret.
- Ordered Spring Authorization Server client-credentials chain with RS256, `at+jwt`, internal
  audience, client subject, requested scopes, and a maximum 300-second token TTL.

Validation command:

```powershell
.\mvnw.cmd -pl services/authentication-service -am "-Dtest=ClientCredentialsTokenEndpointTests,ServiceTokenClaimCompatibilityTests,ServiceClientPersistenceIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: the three targeted classes completed with **13 tests, 13 passed, 0 failures, 0 errors**.
The final Maven process exceeded the command timeout after reports were written, so the report files
were used to confirm the completed test totals; module compilation also completed successfully with
`-DskipTests compile`.

## 23. T048-T050 Product campaign-validation implementation evidence

Implemented on 2026-08-02:

- Product-owned sellability policy and application use case for campaign variant validation.
- JDBC projection adapter that reads Product tables without sharing Product JPA entities with
  Campaign.
- Direct internal HTTP response at `POST /internal/v1/catalog/variants/campaign-validation`.
- Dedicated JWT decoder/security chain requiring the configured issuer, `at+jwt`, internal audience,
  subject `campaign-service`, and `SCOPE_catalog.read`.
- Stable `404 PRODUCT_VARIANT_NOT_FOUND`, `409 PRODUCT_VARIANT_NOT_SELLABLE`, and validation error
  responses with trace-header preservation.

Validation command:

```powershell
.\mvnw.cmd -pl services/product-service -am "-Dtest=ProductCampaignValidationTests,ProductJwtTrustConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **7 tests, 7 passed, 0 failures, 0 errors**.

## 24. T051-T052 Inventory allocation compatibility implementation evidence

Implemented on 2026-08-02:

- Typed `InsufficientStockException` and `AllocationRequestConflictException` now drive stable
  `INVENTORY_INSUFFICIENT_STOCK` and `INVENTORY_ALLOCATION_REQUEST_CONFLICT` responses without
  parsing exception messages.
- `POST /internal/v1/campaign-stock-allocations` now has a dedicated internal JWT boundary requiring
  the configured internal audience, subject `campaign-service`, and
  `SCOPE_inventory.campaign.allocate`.
- The existing broad Inventory authorization chain remains responsible for the other internal
  endpoints, including release and reconciliation operations.

Validation command:

```powershell
.\mvnw.cmd -pl services/inventory-service -am "-Dtest=CampaignAllocationCompatibilityTests,InventoryJwtTrustConfigurationTests,InventorySecurityConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **9 tests, 9 passed, 0 failures, 0 errors**.

## 25. T001/T053-T055 Campaign OpenFeign client implementation evidence

Implemented on 2026-08-03:

- Accepted ADR 0015 and added the BOM-managed OpenFeign dependency to Campaign Service.
- Added an in-memory `AuthorizedClientServiceOAuth2AuthorizedClientManager` and a capability-limited
  token manager for `campaign-product` and `campaign-inventory-allocation` registrations.
- Added separate Product and Inventory Feign interfaces, transport DTOs, MapStruct boundary mappers,
  application output ports/results, stable error translation, `X-Trace-Id`, and response identity
  verification.
- Configured Product at 500-ms connect / 1,200-ms read and Inventory at 500-ms connect / 1,000-ms
  read; both clients use `Retryer.NEVER_RETRY` so the durable schedule workflow remains retry owner.
- Added architecture checks that keep Feign imports out of Campaign application/domain packages.

Focused validation command:

```powershell
.\mvnw.cmd --% -pl services/campaign-service -am -Dtest=CampaignDownstreamClientContractTests,CampaignServiceTokenManagerTests,CampaignProductValidationClientTests,CampaignInventoryAllocationClientTests,CampaignArchitectureTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Result: **13 tests, 13 passed, 0 failures, 0 errors**.

Spring wiring validation command:

```powershell
.\mvnw.cmd --% -pl services/campaign-service -am -Dtest=CampaignServiceApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test
```

Result: **1 test, 1 passed, 0 failures, 0 errors**. The application context created the OAuth2
manager and both Feign proxies against the real Testcontainers PostgreSQL/Liquibase baseline.

A broader clean Campaign test run compiled all 79 production sources but remains intentionally red
on five test-first assertions owned by unimplemented T056-T061: two schedule-operation/orchestrator
type markers and three schedule HTTP cases. No T053-T055 test failed; T063 remains the later US2
aggregate validation gate.

## 26. T056 Schedule-operation identity model evidence

Implemented on 2026-08-03:

- Added framework-free `ScheduleOperationStatus` transitions for the approved
  `STARTED -> INVENTORY_ALLOCATED -> COMPLETED`, `STARTED -> FAILED`, and `FAILED -> STARTED`
  retry paths.
- Added a deterministic SHA-256 `ScheduleOperationFingerprint` value object for the canonical
  command/configuration identity.
- Added the `ScheduleOperation` domain model, including stable idempotency and Inventory request
  identities, retained-key matching, failure recording, and same-operation retry rules.
- Added capability-oriented load/save application ports without coupling the application boundary
  to JPA or Spring.

Focused validation command:

```powershell
.\mvnw.cmd -pl services/campaign-service -am "-Dtest=ScheduleOperationDomainTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **4 tests, 4 passed, 0 failures, 0 errors**. T057 and later schedule orchestration work
remain intentionally unchecked.

## 27. T057 Schedule-operation preparation evidence

Implemented on 2026-08-03:

- Added a transactional `PrepareScheduleOperationService` that performs only the local
  create/load/replay/reopen decision and never wraps downstream HTTP calls.
- Added stable operation and Inventory request IDs for newly-created commands; retained records are
  loaded by `(campaignId, idempotencyKey)` and never expire.
- Added same-fingerprint/version replay and FAILED-operation reopen behavior, while rejecting a
  reused key with a different request identity or a different active operation for the Campaign.
- Added a JPA persistence adapter with pessimistic locking for existing identity lookups and
  `saveAndFlush` mapping at the outbound boundary.

Focused validation command:

```powershell
.\mvnw.cmd -pl services/campaign-service -am clean "-Dtest=PrepareScheduleOperationServiceTests,ScheduleOperationDomainTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **8 tests, 8 passed, 0 failures, 0 errors**. A separate full Spring context check remains
blocked by the pre-existing missing `CampaignPersistenceMapper` bean; it is unrelated to the T057
operation tests and must be repaired before the broader Campaign baseline is green.

The focused architecture check was also run with the T056/T057 tests: **11 tests, 11 passed, 0
failures, 0 errors**.

## 28. Avro SpecificRecord foundation validation

The Feature 017 Avro amendment and ADR 0016 are approved. The protocol-only module now generates
typed SpecificRecord classes from the checked-in schemas. Runtime Confluent serializer wiring and
live Registry publication remain in T101/T103.

```powershell
.\mvnw.cmd -pl contracts/kafka-avro-contracts -am verify
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d kafka schema-registry
curl.exe http://localhost:8081/subjects
```

The focused contract module validation passed with exit code `0`; 3 tests passed, 0 failures, and 0
errors. It generated `CampaignScheduledV1` and `CampaignActivatedV1` as typed Avro
`SpecificRecord` classes, verified UUID/timestamp/decimal logical types, and checked additive
backward compatibility versus breaking schema changes. The approved policy is
`TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE`, and `auto.register.schemas=false` outside local
experiments. Live Registry and serializer/deserializer smoke evidence remains in T101/T103.

## 29. T058-T063 schedule orchestration acceptance

Validated on 2026-08-03 with Docker available:

```powershell
.\mvnw.cmd -pl services/campaign-service -am "-Dtest=ScheduleCampaignUseCaseTests,CampaignSchedulePolicyTests,ScheduleCampaignRecoveryIntegrationTests,CampaignScheduleHttpContractTests,PrepareScheduleOperationServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **18 tests, 18 passed, 0 failures, 0 errors**.

The acceptance scope covers the no-remote-call transaction boundary, stable idempotency and
Inventory request identities, version and request-conflict checks, immutable scheduling snapshot,
`DRAFT -> SCHEDULED` finalization, one transactional outbox row, admin HTTP headers/error contract,
and resumable operation recovery. The HTTP contract uses a real PostgreSQL Testcontainer and mocked
downstream ports; it does not claim a live Product/Inventory network smoke test.

## 30. B3 protected snapshot and Flash Sale identity evidence

Implemented on 2026-08-03:

- Added an application snapshot query boundary that returns only stable Campaign, Product snapshot,
  and Inventory allocation fields. Draft, missing, and incomplete aggregates resolve to the approved
  `CAMPAIGN_SNAPSHOT_NOT_FOUND` contract.
- Added a private `GET /internal/v1/campaigns/{campaignId}/snapshot` HTTP adapter and separated
  transport contract. The response is intentionally a direct snapshot document, not the public
  administrator `ApiResponse` envelope.
- Restricted the endpoint to the dedicated `flashsale-service` subject, internal audience
  `flash-sale-internal-api`, and `SCOPE_campaign.snapshot.read`; the endpoint is not routed through
  the API Gateway.
- Moved fixed service-client provisioning into the OAuth adapter boundary. The Flash Sale client is
  provisioned with an Argon2-hashed secret and only its approved snapshot scope; Campaign scopes are
  denied.

Focused validation commands:

```powershell
.\mvnw.cmd -pl services/campaign-service -am "-Dtest=InternalCampaignSnapshotContractTests,GetCampaignSnapshotServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
# BUILD SUCCESS — 8 tests, 0 failures, 0 errors

.\mvnw.cmd -pl services/authentication-service -am "-Dtest=FlashSaleServiceClientCredentialsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
# BUILD SUCCESS — 2 tests, 0 failures, 0 errors
```

These focused checks use real PostgreSQL Testcontainers. Kafka, outbox recovery, and full topology
smoke validation remain in T076-T078 and T084-T103.

## 31. T064-T074 Campaign lifecycle acceptance

Implemented and validated on 2026-08-04:

- Added framework-free activation/ending policy tests and monotonic lifecycle rules.
- Added conditional PostgreSQL status/version/time updates so concurrent workers have one winner.
- Added the two-second, batch-100 scheduler with a generated scheduler trace identity.
- Added manual `POST /api/v1/admin/campaigns/{campaignId}/activate` with quoted `If-Match`,
  administrator scope, window/status validation, next `ETag`, and shared error handling.
- Added atomic `CampaignActivated.v1` outbox persistence after the winning transition. The MVP does
  not emit `CampaignEnded`.
- Kept cross-feature persistence behind application ports; feature adapters no longer import another
  feature's adapter directly.

Focused validation command:

```powershell
.\mvnw.cmd -pl services/campaign-service -am "-Dtest=CampaignArchitectureTests,CampaignLifecyclePolicyTests,CampaignLifecycleSchedulerTests,CampaignManualActivationHttpContractTests,CampaignLifecycleConcurrencyIntegrationTests,CampaignLifecycleOutboxOrderingTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **15 tests, 15 passed, 0 failures, 0 errors**. The PostgreSQL integration tests used real
Testcontainers and verified one-winner activation/ending races, scheduled-before-activated event
ordering, exactly one activation outbox row, and no ended event. An additional
`.\mvnw.cmd -pl services/campaign-service -am verify` completed with exit code `0`; the broader
reactor and topology evidence are recorded in T095-T097 below.

## B4 — Durable outbox recovery foundation

Implemented the B4 recovery boundary without Kafka publication:

- `T078`: PostgreSQL Testcontainers coverage for concurrent claim/lease, expired lease reclaim,
  aggregate ordering/predecessor blocking, bounded exponential backoff, tenth-attempt terminal
  failure, sanitized failure text, and same-event requeue audit.
- `T084–T085`: application-owned outbox ports/models plus PostgreSQL `FOR UPDATE SKIP LOCKED`
  claim, lease ownership checks, retry state, terminal failure, and requeue persistence.
- `T088`: authenticated admin requeue HTTP contract at
  `POST /api/v1/admin/campaigns/{campaignId}/outbox-events/{eventId}/requeue`, shared
  `ApiResponse`, OpenAPI metadata, trace header, and Campaign-owned error mappings.

Validation:

```text
.\mvnw.cmd clean test -pl services/campaign-service -am "-Dtest=CampaignOutboxRecoveryIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false"
BUILD SUCCESS — 3 PostgreSQL recovery tests passed (2026-08-04)
```

The scheduled outbox publisher remains intentionally deferred to T087.

## 32. T086 Campaign Avro publisher boundary

Implemented on 2026-08-09:

- Added a Campaign-only Kafka producer factory that enforces `acks=all`, idempotence, the approved
  Schema Registry URL from `spring.kafka.producer.properties`, `TopicRecordNameStrategy`, and
  `auto.register.schemas=false`.
- Added an outbox messaging adapter that maps canonical `CampaignScheduled` and
  `CampaignActivated` JSON payloads to generated Avro `SpecificRecord` values, uses the Campaign ID
  as the Kafka key, and preserves the stable outbox event ID in routing headers.
- Added `eventId`, `eventType`, `eventVersion`, `contentType=application/avro`, and a valid W3C
  `traceparent` Kafka header. No trace ID, credential, or secret is added to the Avro business body.

Focused validation:

```powershell
.\mvnw.cmd -pl services/campaign-service -am test "-Dtest=CampaignLifecycleKafkaPublisherTests" "-Dsurefire.failIfNoSpecifiedTests=false"
# BUILD SUCCESS — 5 tests, 0 failures, 0 errors
```

## 33. B6 — Avro contract tests and scheduled outbox relay

Implemented on 2026-08-09:

- `T076`: Added generated-SpecificRecord binary round-trip tests for both lifecycle records,
  UUID/timestamp/decimal logical-type assertions, secret/credential absence checks, W3C
  `traceparent` shape checks, and `BACKWARD_TRANSITIVE` additive/breaking compatibility fixtures.
- `T087`: Added the 500-ms outbox scheduler. It claims at most 100 rows with a 30-second reclaim
  lease, publishes outside the database transaction, marks only the current lease owner as
  published, records failures through the approved retry policy, and restores the stored trace
  identity in the logging context.
- `T101`: Runtime YAML and Campaign producer configuration provide the Registry URL,
  `KafkaAvroSerializer`, `TopicRecordNameStrategy`, controlled registration, idempotent producer
  settings, and W3C header propagation.
- Added an explicitly opt-in live test profile at
  `CampaignLifecycleKafkaIntegrationTests`. It requires `campaign.kafka.integration=true`,
  `campaign.kafka.bootstrap`, and `campaign.schema-registry.url`; the normal module build does not
  contact external infrastructure.

Focused validation:

```powershell
.\mvnw.cmd -pl services/campaign-service -am test "-Dtest=CampaignLifecycleEventContractTests,CampaignOutboxPublisherJobTests,CampaignLifecycleKafkaPublisherTests,CampaignArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false"
# BUILD SUCCESS — 18 tests, 0 failures, 0 errors

.\mvnw.cmd -pl services/campaign-service -am test "-Dtest=CampaignLifecycleKafkaIntegrationTests,CampaignLifecycleEventContractTests,CampaignOutboxPublisherJobTests" "-Dsurefire.failIfNoSpecifiedTests=false"
# BUILD SUCCESS — 11 tests, 0 failures, 0 errors; live Kafka test skipped because the opt-in property was not set
```

## 34. T077/T089/T103 — live Registry and US4 acceptance evidence

Validated on 2026-08-09 against the existing local `flash-sale-vps` Kafka and Schema Registry
containers. The Registry was configured to `BACKWARD_TRANSITIVE` and the two approved
TopicRecordNameStrategy subjects were registered through the Registry REST API with
`auto.register.schemas=false`; no application startup registration was used.

```powershell
.\mvnw.cmd -pl contracts/kafka-avro-contracts -am verify
# BUILD SUCCESS — 3 contract generation/compatibility tests

.\mvnw.cmd -pl services/campaign-service -am test `
  "-Dtest=InternalCampaignSnapshotContractTests,CampaignLifecycleEventContractTests,CampaignOutboxRecoveryIntegrationTests,CampaignLifecycleKafkaIntegrationTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-Dcampaign.kafka.integration=true" `
  "-Dcampaign.kafka.bootstrap=localhost:29092" `
  "-Dcampaign.schema-registry.url=http://localhost:8081"
# BUILD SUCCESS — 15 Campaign acceptance tests; live Kafka/Registry test passed

.\mvnw.cmd -pl services/authentication-service -am test `
  "-Dtest=FlashSaleServiceClientCredentialsTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
# BUILD SUCCESS — 2 Auth client-credentials tests
```

The live test verified the three-partition topic, Campaign ID key, same-aggregate partition
ordering, Scheduled-before-Activated ordering, duplicate event identity, independent aggregate
keys, W3C `traceparent`, and Registry compatibility. T077, T089, and T103 are complete.

## 35. B7 — observability and local messaging operations

Implemented on 2026-08-09:

- `T090–T091`: Added Campaign low-cardinality Micrometer observations for operation, command,
  downstream, lifecycle, outbox, and requeue outcomes. Trace restoration is scoped to the outbox
  relay and restores the previous MDC value; identifiers, exception text, credentials, and tokens
  are not metric dimensions. The configuration uses the Actuator-provided registry and a
  non-Prometheus `SimpleMeterRegistry` fallback only for minimal contexts that do not provide a
  registry.
- `T092`: Wired Campaign's `campaign_db`, Kafka, Schema Registry, OAuth2 client placeholders, JWT
  trust settings, and Product/Inventory URLs in the root Compose application profile. Real secrets
  remain required in the ignored `infra/docker/.env`; `.env.example` contains placeholders only.
- `T093`: Added the root-owned `infra/docker/kafka/init-campaign-topics.sh` provisioning tool. It
  creates `campaign.lifecycle.v1` idempotently and verifies three partitions and replication
  factor one. The repository-owned script was executed successfully with the installed Git Bash;
  a PowerShell host without Bash can run the equivalent `docker compose exec` command shown below.
- `T094`: The merged Compose application profile rendered successfully with placeholder settings.
- `T102`: Updated the Feature 017 Avro contract and repository Kafka documentation with exact
  `TopicRecordNameStrategy` subjects, controlled registration, consumer-first rollout, and
  Kafka/Registry outage recovery semantics.

Focused validation:

```powershell
.\mvnw.cmd -pl services/campaign-service -am test "-Dtest=CampaignObservabilityTests,CampaignOutboxPublisherJobTests,CampaignLifecycleEventContractTests,CampaignLifecycleKafkaPublisherTests,CampaignArchitectureTests" "-Dsurefire.failIfNoSpecifiedTests=false"
# BUILD SUCCESS — 21 tests, 0 failures, 0 errors

.\mvnw.cmd -pl services/campaign-service -am test "-Dtest=CampaignServiceApplicationTests" "-Dsurefire.failIfNoSpecifiedTests=false"
# BUILD SUCCESS — 1 context test passed; scheduled DB warnings occur during Testcontainer shutdown

.\mvnw.cmd -pl services/campaign-service -am verify
# BUILD SUCCESS — Campaign module and its upstream modules verified

docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml --profile apps config
# exit code 0
```

The local broker was already running under the developer's `flash-sale-vps` Compose project. The
topic was created and described without deleting volumes:

```powershell
& 'C:\Program Files\Git\bin\bash.exe' -lc 'cd /c/Users/MSi/flash-sale/infra/docker; ./kafka/init-campaign-topics.sh'
# Provisioned campaign.lifecycle.v1 (partitions=3, replication-factor=1)

docker compose --env-file infra/docker/.env -f infra/docker/compose.yml exec -T kafka `
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists `
  --topic campaign.lifecycle.v1 --partitions 3 --replication-factor 1
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml exec -T kafka `
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --describe --topic campaign.lifecycle.v1
# PartitionCount: 3, ReplicationFactor: 1, partitions 0/1/2 led by broker 1
```

The full Gateway-to-Registry smoke and business recovery flow remain tracked by `T095`; the
affected-module and full-reactor build gates are recorded below as `T096` and `T097`.

## 36. T095–T097 — final local smoke and build evidence

The non-mutating portion of the local topology smoke was run against the existing `flash-sale-vps`
Compose project on 2026-08-09. Campaign was rebuilt from the current monorepo source, its Dockerfile
was corrected to copy the root `contracts/` Maven module, and the Authentication Compose wiring was
corrected so the runtime Campaign client secret is injected into both Authentication and Campaign
without being committed or printed.

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps ps
# Authentication, Product, Inventory, Campaign, Gateway, PostgreSQL, Redis, Kafka, and Registry up

# From inside the Compose network:
# authentication-service={"status":"UP","groups":["liveness","readiness"]}
# product-service={"status":"UP","groups":["liveness","readiness"]}
# inventory-service={"status":"UP","groups":["liveness","readiness"]}
# campaign-service={"status":"UP","groups":["liveness","readiness"]}

Invoke-WebRequest http://127.0.0.1:18080/api/v1/catalog/products?size=1
# HTTP 200 — Gateway public catalog route forwarded to Product

docker exec flash-sale-vps-kafka-1 /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 --describe --topic campaign.lifecycle.v1
# PartitionCount: 3, ReplicationFactor: 1

Invoke-RestMethod http://127.0.0.1:8081/subjects
# 2 controlled Campaign TopicRecordNameStrategy subjects

docker compose --env-file infra/docker/.env -f infra/docker/compose.yml restart campaign-service
# Campaign restarted; internal health returned {"status":"UP"}
```

T095 is intentionally still open. The database currently contains zero Product variants and zero
Inventory items, and the local Compose environment has no approved administrator fixture for the
Campaign write path. Therefore the authenticated Gateway → Campaign → Product → Inventory prepare,
outbox requeue, and business failure/recovery path was not fabricated or marked complete. Completing
T095 requires an approved disposable fixture/credential procedure (or seeded local data) before the
next validation run.

Affected-module verification (T096):

```powershell
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/product-service,services/inventory-service,services/campaign-service -am verify
# BUILD SUCCESS — exit code 0; all selected modules and upstream modules passed
```

Full reactor verification (T097):

```powershell
.\mvnw.cmd clean verify
# BUILD SUCCESS — exit code 0; all 13 reactor modules passed with no required test failures
```
