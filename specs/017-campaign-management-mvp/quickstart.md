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
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d postgres redis kafka
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
replication factor one before the outbox publisher is expected to succeed.

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
- Kafka receives the approved envelope with Campaign ID key;
- repeated identical schedule returns/resumes the same result;
- changed request with the same key returns 409 without another allocation;
- Product/Inventory request capture contains a Campaign service token, never the administrator token;
- `X-Trace-Id` appears across Gateway, Campaign, downstream calls, outbox, Kafka, and logs.

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
- stop Kafka -> outbox remains durable and retries with backoff;
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
