# Quickstart and Validation: Order Service Core MVP

**Feature**: [Order Service Core MVP](./spec.md)
**Purpose**: Reproducible local evidence after implementation
**Platform shown**: PowerShell on Windows; Maven Wrapper remains canonical

Do not treat this guide as implementation approval. Run it only after the relevant tasks are
implemented, and record command, scope, exit status, counts, and CI/PR reference in
`validation.md`.

## 1. Prerequisites

- Java 21
- Docker Desktop with Compose
- k6 for the optional query-latency profile
- Local `infra/docker/.env` created from `.env.example` with non-committed secrets
- Ports required by the root Compose developer overlay available

Never print or commit JWT signing keys, OAuth client secrets, PostgreSQL passwords, raw access
tokens, or generated token files.

## 2. Verify the Contract Module and Order Module

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl contracts/kafka-avro-contracts,services/order-service,services/api-gateway `
  -am verify
```

Expected:

- `PurchaseAcceptedV1` remains unchanged and compatible.
- `OrderCreatedV1` is generated as a `SpecificRecord` with the approved logical types.
- Order domain, architecture, persistence, Kafka, web, security, and observability tests pass.
- Gateway Order route tests pass.
- Hibernate validates the Liquibase-owned Order schema rather than creating it.

## 3. Start Shared Local Infrastructure

```powershell
docker compose `
  --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml `
  up -d postgres kafka schema-registry authentication-service api-gateway flashsale-service order-service
```

Inspect readiness:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml ps
```

Expected PostgreSQL, Kafka, Schema Registry, Authentication, Gateway, Flash Sale, and Order
containers to be healthy before smoke validation continues.

## 4. Provision the Order Topic and Schema

Provisioning is idempotent and root-owned:

```powershell
$kafkaContainer = docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml ps -q kafka

if ([string]::IsNullOrWhiteSpace($kafkaContainer)) {
  throw "Kafka container is not running"
}

docker cp infra/docker/kafka/init-order-topics.sh "${kafkaContainer}:/tmp/init-order-topics.sh"
docker exec $kafkaContainer bash /tmp/init-order-topics.sh

.\infra\docker\schema-registry\register-order-schemas.ps1 `
  -SchemaRegistryUrl http://localhost:8081
```

Expected:

```text
flashsale.order.events.v1: 3 partitions, replication factor 1
flashsale.order.purchase-accepted.dlt.v1: provisioned operational DLT
OrderCreatedV1 subject: BACKWARD_TRANSITIVE
OrderCreatedV1 exact generated schema: registered and readable
```

The application runs with schema auto-registration disabled; failure to pre-register is expected to
leave the outbox retryable rather than mutate Registry policy at startup.

## 5. Run Focused PostgreSQL and Kafka Integration Tests

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/order-service -am `
  "-Dtest=OrderSchemaMigrationIntegrationTests,AcceptedPurchasePersistenceIntegrationTests,AcceptedPurchaseConcurrencyIntegrationTests,OrderOutboxConcurrencyIntegrationTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Expected evidence:

- one Order and line for a valid accepted purchase;
- one inbox and one logical outbox transition;
- 100 same/different-event-ID equivalent deliveries remain one logical result;
- contradictory duplicate creates no mutation;
- transaction rollback leaves no partial rows;
- multiple relay workers do not claim the same outbox row concurrently.

Run the opt-in live Kafka/Registry tests:

```powershell
$env:RUN_ORDER_KAFKA_INTEGRATION_TESTS = 'true'
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:29092'
$env:SCHEMA_REGISTRY_URL = 'http://localhost:8081'

.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/order-service -am `
  "-Dtest=PurchaseAcceptedConsumerIntegrationTests,OrderCreatedKafkaIntegrationTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Clear temporary environment values after the run:

```powershell
Remove-Item Env:RUN_ORDER_KAFKA_INTEGRATION_TESTS -ErrorAction SilentlyContinue
Remove-Item Env:KAFKA_BOOTSTRAP_SERVERS -ErrorAction SilentlyContinue
Remove-Item Env:SCHEMA_REGISTRY_URL -ErrorAction SilentlyContinue
```

## 6. Run the Feature 020 Compose Smoke

```powershell
.\infra\docker\smoke\feature-020-order.ps1
```

Expected flow:

```text
Authentication -> shopper JWT
Feature 019 fixture -> PurchaseAcceptedV1
Order consumer -> Order + line + inbox + outbox COMMIT
Order relay -> OrderCreatedV1
Shopper -> Gateway -> owner Order detail/list
Foreign shopper -> same safe 404 as unknown Order
```

Expected final marker:

```text
FEATURE_020_SMOKE=PASS
```

The script must reconcile PostgreSQL counts and event identity, not merely assert HTTP 200.

## 7. Failure Matrix

Run the smoke script's failure mode:

```powershell
.\infra\docker\smoke\feature-020-order.ps1 -RunFailureMatrix
```

Required scenarios:

| Failure window | Required evidence |
|----------------|-------------------|
| PostgreSQL unavailable before commit | No partial Order/inbox/outbox; input remains retryable or reaches replayable DLT after bounded delivery |
| Process stops after DB commit before offset ack | Redelivery resolves to the same Order and no second logical event |
| Kafka unavailable after Order commit | Query works; outbox remains pending and later publishes same identity |
| Schema Registry unavailable | Order remains durable; outbox retries; no runtime schema registration |
| Publisher sends then stops before marking published | Duplicate physical event keeps the same `eventId` and key |
| Unsupported/malformed input | No Order mutation; consumer-specific DLT contains safe diagnostic context |
| Same identity with contradictory content | No mutation; conflict metric/log/DLT outcome is visible |
| Wrong audience / foreign owner | No Order disclosure and no internal detail |

Expected final marker:

```text
FEATURE_020_FAILURE_MATRIX=PASS
```

## 8. Query Performance Profile

Create a documented fixture through the Feature 020 smoke script, then run:

```powershell
k6 run `
  -e ORDER_BASE_URL=http://localhost:18080 `
  -e ORDER_QUERY_TOKEN_FILE=<temporary-token-file> `
  -e ORDER_QUERY_VUS=25 `
  -e ORDER_QUERY_DURATION=30s `
  .\load-tests\order-service\order-query.js
```

Record:

```text
environment and fixture size
VUs, duration, and HTTP request rate
p50, p95, p99
error/authorization rate
database connection-pool state
```

Required nominal threshold:

```text
owned Order detail p95 < 200 ms
unexpected error rate = 0
foreign/unknown non-enumeration checks = 100% pass
```

The token file is generated test data, must be ignored by Git, and is deleted after the run.

## 9. Event-to-Commit Performance Evidence

Run the opt-in consumer profile that publishes a documented batch of unique valid
`PurchaseAcceptedV1` records and measures consumer-receipt-to-Order-commit observations:

```powershell
$env:RUN_ORDER_PERFORMANCE_TESTS = 'true'

.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/order-service -am `
  "-Dtest=OrderConsumerPerformanceIntegrationTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test

Remove-Item Env:RUN_ORDER_PERFORMANCE_TESTS -ErrorAction SilentlyContinue
```

Record batch size, unique identities, p50/p95/p99, error count, and Order/inbox/outbox reconciliation.
At least 95% of delivered valid events must commit within one second in the documented nominal
local profile.

## 10. Full Reactor and Static Validation

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
git diff --check
```

If Kubernetes assets are added by a later approved task, also run client-side dry-run for every
affected overlay. Feature 020 currently adds no Kubernetes manifest.

## 11. Completion Evidence

Record at least:

```text
command
scope
date/environment
exit status
test counts
database reconciliation counts
Kafka topic/key/event IDs
performance percentiles
failure-recovery result
CI/PR reference
```

Do not mark Feature 020 verified while any required check is skipped without plan rationale or any
required test/build is failing.
