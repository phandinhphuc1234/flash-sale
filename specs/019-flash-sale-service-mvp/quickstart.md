# Feature 019 Quickstart and Validation Guide

**Status**: Planned commands — executable after Feature 019 implementation  
**Purpose**: Reproduce migration, contract, integration, smoke, failure, and load evidence

## Prerequisites

- Java 21
- Docker Desktop / Docker Engine with Compose v2
- PowerShell 7 on Windows for the repository smoke script
- k6 for the optional load evidence
- Real local secrets copied into `infra/docker/.env`; never commit this file

Start from repository root:

```powershell
Set-Location C:\Users\MSi\flash-sale
Copy-Item infra\docker\.env.example infra\docker\.env
```

Populate at least PostgreSQL, Redis, JWT/OAuth2 client, rate-limit HMAC, and Schema Registry/Kafka
settings with non-default local values. Do not paste secrets into test reports or issue comments.

## 1. Validate Generated Contracts and Module

```powershell
.\mvnw.cmd -pl contracts/kafka-avro-contracts -am clean verify
.\mvnw.cmd -pl services/flashsale-service -am clean verify
```

Expected:

- `PurchaseAcceptedV1` is generated as an Avro `SpecificRecord`;
- architecture, unit, web/security, PostgreSQL, Redis, and Kafka tests pass;
- Hibernate validates the Liquibase-owned schema rather than creating it.

## 2. Start Shared Local Infrastructure

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml up -d `
  postgres redis kafka schema-registry
```

Wait until health checks pass:

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml ps
```

Run the controlled topic/schema bootstrap added by Feature 019. Stable environments must keep
`auto.register.schemas=false`; application startup is not the schema owner.

## 3. Run the Flash Sale Migration

Use the one-off migration profile/container owned by root Compose. It runs
`services/flashsale-service/src/main/resources/db/changelog/db.changelog-master.yaml`, acquires the
Liquibase lock, applies pending changes, and exits before application replicas start.

Verify `DATABASECHANGELOG` contains `001-create-flash-sale-mvp-schema`. Do not use Hibernate
`ddl-auto=update`, `schema.sql`, or `data.sql`.

## 4. Start the Required Service Topology

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml up -d `
  authentication-service api-gateway campaign-service flashsale-service
```

Product and Inventory are required only to prepare a Campaign through its normal administrative
flow; the shopper reservation hot path itself calls neither service.

Health checks:

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/prometheus
```

Use the service's configured management port/path through the local environment. Do not publicly
expose all Actuator endpoints on a VPS.

## 5. Prepare and Activate a Campaign

Use a real admin token through Gateway to create/prepare/activate a Campaign, or use approved fixture
events in the integration profile. Verify:

1. `CampaignScheduledV1` is consumed into Redis with the correct allocation/price/limit snapshot.
2. `CampaignActivatedV1` transitions only a known scheduled projection.
3. Duplicate and stale events do not reset remaining quota.
4. Missing scheduled state remains closed until the internal snapshot recovery succeeds.

## 6. Complete End-to-End Shopper Smoke

The automated smoke script obtains a real shopper access token, reserves through Gateway, waits for
the durable result, queries it as the owner, and consumes the Kafka fact:

```powershell
infra\docker\smoke\feature-019-flashsale.ps1
```

Expected flow:

```text
Shopper -> Gateway -> Flash Sale -> Redis Lua/XADD
                               -> PostgreSQL + outbox
Outbox -> Schema Registry/Kafka -> PurchaseAcceptedV1
Shopper -> Gateway -> Flash Sale -> owned reservation 200
```

Evidence must show:

- submit returns 202 only after database commit;
- identical replay returns the same purchase/reservation IDs;
- `Location` resolves to the owner query;
- shared success/error envelopes and `X-Trace-Id` are present;
- one Avro event with the stable event/key is readable from Kafka.

## 7. Failure Smoke Matrix

Run failures one at a time and restore the dependency before the next case.

| Failure | Expected evidence |
|---|---|
| Redis stopped before admission | 503, no durable purchase/outbox, readiness not ready. |
| PostgreSQL stopped after Redis winner | 503 acceptance-pending + `Retry-After: 1`; Stream retains work; same-key retry later returns stable 202 or terminal expiry. |
| Flash Sale killed after Lua | New instance claims/reclaims Stream entry and persists the same IDs. |
| Kafka stopped | Accepted request remains 202 after DB commit; outbox backlog grows; original event publishes after recovery. |
| Schema Registry stopped | Same behavior as broker failure; no new event identity. |
| Campaign activation before schedule | Admission fails closed; snapshot recovery is attempted outside the request. |
| JWT wrong audience/expired | 401 without internal/token details. |
| Foreign reservation query | Same 404 body as unknown reservation. |

After each case, check sanitized logs, readiness, Prometheus metrics, Stream pending count, and outbox
age. No secret, JWT, raw idempotency key, or full payload may appear in logs.

## 8. Concurrency Acceptance

Redis integration evidence must include:

```text
Initial quantity: 100
Concurrent attempts: >= 1,000
Expected accepted winners: exactly 100
Expected remaining quantity: 0
Expected negative counters: none
```

Idempotency evidence:

```text
Concurrent identical retries: 100
Expected logical purchaseRequestId: 1
Expected logical reservationId: 1
Expected PurchaseAccepted eventId: 1
Expected quota decrement: once
```

## 9. Load Evidence

After the functional/concurrency gates pass:

```powershell
k6 run load-tests\flashsale-service\reservation.js
```

Record with the result:

- date and commit;
- machine/VPS CPU, memory, OS, Docker resources;
- service replicas and JVM settings;
- Redis/PostgreSQL/Kafka topology;
- virtual users, duration, Campaign quota, and idempotency mix;
- throughput, p50, p95, p99, error rate, winner count, and any saturation signal.

Do not convert one local result into a production SLA.

## 10. Final Reactor Gate

```powershell
.\mvnw.cmd clean verify
git diff --check
```

Feature tasks may be checked only when the required command, scope, exit status, and relevant smoke/
load evidence are recorded in the task ledger.

