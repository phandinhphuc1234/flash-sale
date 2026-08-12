# Implementation Plan: Flash Sale Service MVP

**Status**: Approved — confirmed by project owner on 2026-08-10  
**Branch**: `019-flash-sale-service-mvp` | **Date**: 2026-08-10 | **Spec**: [spec.md](./spec.md)  
**Input**: Approved Feature 019 specification at `/specs/019-flash-sale-service-mvp/spec.md`

## Summary

Build the first sellable Flash Sale hot path. An authenticated shopper submits a reservation through
API Gateway; one Redis Lua script atomically validates the Campaign projection, decrements quota,
enforces the per-user limit, establishes stable identifiers, records idempotency state, and appends a
recoverable handoff entry to Redis Stream. The request is reported as accepted only after the same
purchase, reservation, idempotency result, and `PurchaseAccepted.v1` outbox row commit in the
Flash Sale PostgreSQL database. An outbox relay publishes the Avro `SpecificRecord` through Confluent
Schema Registry to Kafka with at-least-once delivery.

Campaign availability is projected from the approved `CampaignScheduledV1` and
`CampaignActivatedV1` events. An OpenFeign snapshot call is restricted to projection recovery and is
never used by the ordinary purchase path. The module is organized package-by-feature with Clean and
Hexagonal boundaries; Redis, JPA, Feign, HTTP, Kafka, and Avro remain adapters.

## Technical Context

**Language/Version**: Java 21  
**Framework**: Spring Boot 3.5.16, Spring Cloud 2025.0.3  
**Primary Dependencies**: Spring MVC, Validation, Security OAuth2 Resource Server/Client, Spring
Data JPA, Spring Data Redis/Lettuce, Spring Kafka, Spring Cloud OpenFeign, Liquibase, MapStruct,
Micrometer Tracing bridge for OpenTelemetry, Actuator, runtime Prometheus registry, Confluent Avro
serializer, `common-web`, and `kafka-avro-contracts`  
**Storage**: Redis 7.4 for atomic admission/projection/recovery handoff; service-owned PostgreSQL 17
for durable purchase, reservation, idempotency, and outbox state  
**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers PostgreSQL/Kafka/Redis,
Spring Kafka Test, ArchUnit 1.4.2, Maven Surefire/Failsafe, and k6  
**Target Platform**: Linux containers for local Docker Compose and future Kubernetes deployment  
**Project Type**: Independently deployable Spring Boot microservice in a Maven monorepo  
**Performance Goals**: Demonstrate exactly 100 winners from 1,000 concurrent attempts for 100 units;
record throughput and p50/p95/p99; no fixed latency promise before a measured baseline  
**Constraints**: No oversell; one accepted identity per idempotency scope; five-minute reservation
TTL; no synchronous downstream call in the ordinary purchase path; 202 only after PostgreSQL commit;
Kafka at-least-once; one Redis and one Kafka broker in local MVP  
**Scale/Scope**: One Flash Sale service instance is sufficient for the first local/VPS deployment,
while Redis Lua, consumer groups, `SKIP LOCKED`, leases, and idempotency permit safe horizontal
scaling later. Redis Cluster, multi-region operation, Order processing, payment, and reservation
confirmation/release are outside Feature 019.

## Constitution Check

*GATE: Passed before Phase 0 research and re-checked after Phase 1 design.*

| Gate | Result | Design evidence |
|---|---|---|
| Specification traceability | PASS | All observable behavior comes from approved FR-001–FR-026 and NFR-001–NFR-007; no unresolved clarification remains. |
| Service ownership | PASS | Flash Sale owns only `flashsale_db`, its Liquibase changelog, Redis projection keys, reservations, and outbox records. No foreign database is accessed. |
| Architecture | PASS | Package-by-feature plus inward dependency direction. Domain/application do not import HTTP, Redis, JPA, Kafka, Avro, Feign, or Schema Registry types. |
| External ingress | PASS | Shopper endpoints are routed through API Gateway; internal snapshot recovery is not exposed through Gateway. |
| Synchronous communication | PASS | Campaign snapshot recovery uses the approved internal HTTP contract and OpenFeign with OAuth2 Client Credentials. The hot path makes no downstream HTTP call. |
| Asynchronous communication | PASS | Campaign v1 events are consumed and `PurchaseAcceptedV1` is added as an Avro schema-first, versioned Kafka contract. |
| Correctness and durability | PASS | Redis Lua + Stream prevents a lost winner; PostgreSQL arbitrates accepted versus expired; transactional outbox prevents a lost accepted event. |
| Infrastructure ownership | PASS | Compose/topic/smoke assets stay in `infra/docker`; service migrations and runtime configuration stay in `services/flashsale-service`. |
| Observability | PASS | Declarative Actuator endpoints, Prometheus runtime registry, low-cardinality Micrometer observations, W3C trace propagation, and structured logs are planned. |
| Contract/dependency control | PASS | Every new production dependency and public/internal/Kafka contract is listed below and in `contracts/`. |
| Validation | PASS | Unit, architecture, web/security, PostgreSQL, Redis concurrency, Kafka/Registry, recovery, Compose, and load validation are planned. |

### Architecture Decision Gate

ADR [0017](../../docs/adr/0017-redis-stream-durable-acceptance-handoff.md) is **Accepted** with this
plan. The architecture gate for the Redis Stream durability boundary is satisfied, so `tasks.md` may
now be generated without another design decision.

## Project Structure

### Documentation (this feature)

```text
specs/019-flash-sale-service-mvp/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── public-reservation-http.md
│   ├── campaign-projection-input.md
│   ├── campaign-snapshot-recovery-http.md
│   ├── purchase-accepted-kafka.md
│   └── redis-hot-path-and-handoff.md
└── tasks.md                         # Created only after plan/ADR approval
```

### Source Code (repository root)

Only packages that receive real classes are created. The tree expresses ownership; it does not
authorize empty scaffolding.

```text
services/flashsale-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/philia/flashsale/flashsale/
    │   │   ├── FlashsaleServiceApplication.java
    │   │   ├── campaignprojection/
    │   │   │   ├── domain/{model,exception}/
    │   │   │   ├── application/{command,port/in,port/out,usecase}/
    │   │   │   └── adapter/
    │   │   │       ├── in/messaging/kafka/
    │   │   │       └── out/{redis,client/campaign}/
    │   │   ├── reservation/
    │   │   │   ├── domain/{model,policy,exception}/
    │   │   │   ├── application/{command,query,result,port/in,port/out,usecase}/
    │   │   │   └── adapter/
    │   │   │       ├── in/{web,scheduling,messaging/redis}/
    │   │   │       └── out/{redis,persistence/jpa}/
    │   │   ├── outbox/
    │   │   │   ├── application/{model,port,usecase}/
    │   │   │   └── adapter/{in/scheduling,out/persistence/jpa,out/messaging/kafka}/
    │   │   ├── configuration/
    │   │   ├── security/
    │   │   ├── observability/
    │   │   └── websupport/error/
    │   └── resources/
    │       ├── application.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/001-create-flash-sale-mvp-schema.sql
    └── test/java/com/philia/flashsale/flashsale/
        ├── architecture/
        ├── campaignprojection/
        ├── reservation/
        ├── outbox/
        └── support/

contracts/kafka-avro-contracts/src/main/avro/topics/
└── flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc

infra/docker/
├── compose.yml
├── compose.dev.yml
├── .env.example
├── kafka/                         # Controlled topic/schema initialization
└── smoke/feature-019-flashsale.ps1

load-tests/flashsale-service/
└── reservation.js
```

**Structure Decision**: `flashsale-service` is a core-domain service, so package-by-feature is used
at the top level. Each feature receives only the Clean/Hexagonal sublayers its implemented behavior
needs. Shared service-level configuration, security, observability, and HTTP exception translation
remain thin technical support packages; no service-local replacement for `common-web` is created.

## Dependency and Boundary Rules

```text
adapter/in  -> application/port/in -> application/usecase -> domain
adapter/out -> application/port/out <- application/usecase
configuration -> adapters + application wiring
```

- Web request/response records live beside the reservation web adapter and use MapStruct to map to
  application commands/results.
- JPA entities/repositories and persistence mappers stay inside the owning outbound adapter.
- Generated Avro `SpecificRecord` types appear only in Kafka adapters and the contract module.
- Feign DTOs, OAuth2 interceptors, timeout/error decoding, and generated Feign proxies stay in the
  Campaign client adapter.
- Redis keys, Lua arguments/results, Stream records, and serialization stay in Redis adapters.
- Business exceptions are typed by feature; `websupport/error` translates them into the shared
  `ApiErrorResponse` without leaking infrastructure details.

Architecture tests enforce the rules and prevent adapters from depending on one another through
implementation types.

## Phase 0: Research Decisions

The resolved alternatives and evidence are recorded in [research.md](./research.md). The principal
decisions are:

1. One Redis Lua script owns the atomic hot-path decision and appends the recovery handoff.
2. One Redis Stream consumer group persists winners; `XAUTOCLAIM` recovers abandoned pending work.
3. PostgreSQL uniquely arbitrates accepted versus expired so a late retry cannot resurrect an
   expired reservation.
4. Accepted durable changes use a PostgreSQL transactional outbox and an at-least-once relay using
   `FOR UPDATE SKIP LOCKED`.
5. Kafka payloads are Avro schema-first generated `SpecificRecord` values registered under
   `TopicRecordNameStrategy` with `BACKWARD_TRANSITIVE` compatibility.
6. Campaign projection recovery uses OpenFeign + OAuth2 Client Credentials only outside the hot
   path.
7. W3C tracing is implemented through Micrometer's OpenTelemetry bridge; external OTLP export and
   centralized log shipping remain a later observability feature.

## Phase 1: Design Outputs

- [Data model](./data-model.md): Redis keys/state machines and PostgreSQL schema/invariants.
- [Public HTTP contract](./contracts/public-reservation-http.md): reservation submit/query,
  envelopes, status codes, headers, and ownership behavior.
- [Campaign input contract](./contracts/campaign-projection-input.md): scheduled/activated consumer,
  ordering, recovery, and failure policy.
- [Snapshot recovery contract](./contracts/campaign-snapshot-recovery-http.md): approved Campaign
  internal endpoint, OAuth2 identity, and timeout boundaries.
- [Purchase event contract](./contracts/purchase-accepted-kafka.md): topic, key, Avro schema fields,
  headers, compatibility, and delivery guarantees.
- [Redis contract](./contracts/redis-hot-path-and-handoff.md): keys, Lua outcomes, Stream lifecycle,
  expiration, and crash recovery.
- [Quickstart](./quickstart.md): validation and local smoke sequence.

## Dependency Changes

### Production dependencies added to `flashsale-service`

| Dependency | Purpose and boundary |
|---|---|
| `libs/common-web` | Shared HTTP success/error and pagination contracts only. |
| `contracts/kafka-avro-contracts` | Generated protocol-only Avro types used by Kafka adapters. |
| `spring-boot-starter-security` + OAuth2 resource server | Validate shopper JWT issuer/audience/signature and authorize public endpoints. |
| `spring-boot-starter-oauth2-client` | Campaign snapshot recovery service identity. |
| `spring-cloud-starter-openfeign` | Typed internal Campaign snapshot client outside the hot path. |
| `spring-boot-starter-data-jpa` | Service-owned durable records and outbox. |
| `spring-boot-starter-data-redis` | Redis Lua, projection, expiration index, and Stream handoff via Lettuce. |
| `spring-kafka` | Campaign consumer and PurchaseAccepted producer. |
| `kafka-avro-serializer` | Confluent Schema Registry wire serialization. |
| `mapstruct` | Boundary mappings without reflection-based domain coupling. |
| `micrometer-tracing-bridge-otel` | W3C trace/MDC/observation integration using OpenTelemetry semantics. |
| PostgreSQL runtime driver and Liquibase | Service-owned database and forward-only migrations. |

Existing Actuator and runtime Prometheus dependencies remain. No `PrometheusMeterRegistry` bean is
created. No Redisson, Debezium/Kafka Connect, Kafka transactions, Resilience4j, Spring Modulith,
gRPC, or OpenAPI dependency is introduced by this feature.

### Test-only dependencies

Spring Security Test, Spring Kafka Test, Spring Boot Testcontainers, Testcontainers JUnit/PostgreSQL/
Kafka, a Redis `GenericContainer`, and ArchUnit 1.4.2. These do not enter the runtime artifact.

## Runtime Design

### Successful reservation

```text
Client -> Gateway -> Flash Sale HTTP
  -> validate JWT, request, and idempotency header
  -> Redis Lua: validate + decrement + record ids + XADD handoff
  -> idempotent PostgreSQL transaction: purchase + reservation + key + outbox
  -> Redis XACK/XDEL handoff
  <- 202 ApiResponse + Location

Outbox scheduler -> Kafka + Schema Registry -> PurchaseAcceptedV1
```

### PostgreSQL unavailable after Redis wins

The recoverable Stream entry remains pending. The caller receives `503
FLASH_SALE_ACCEPTANCE_PENDING` plus `Retry-After: 1`, never 202. The same request/key may retry the
same persistence use case while the consumer recovers it. Both paths converge on unique PostgreSQL
identities. If expiry becomes the terminal PostgreSQL outcome first, no accepted outbox row may be
created later.

### Reservation expiry

At five minutes the reservation is no longer eligible for purchase. The expiry worker first writes
or observes the terminal PostgreSQL outcome, then an idempotent Redis Lua script restores quota and
removes the expiration entry. If PostgreSQL is unavailable, physical quota restoration waits for a
durable terminal record; the worker retries and never invents an accepted event.

## Persistence and Migration Plan

- Use Liquibase formatted PostgreSQL SQL because JSONB, partial/compound indexes, unique constraints,
  and check constraints are clearer in exact DDL.
- Add a new immutable changeset `001-create-flash-sale-mvp-schema` and include explicit development
  rollback statements in reverse dependency order.
- Normal replicas use `spring.jpa.hibernate.ddl-auto=validate`; Liquibase runs through the existing
  one-off migration flow in Compose/future Kubernetes.
- Validate the migration against PostgreSQL Testcontainers. Do not use `schema.sql`, `data.sql`, or
  Hibernate DDL update.
- No data retention/deletion rule is invented for durable purchases, reservations, or published
  outbox rows. Only idempotency keys have the approved `campaign end + 24 hours` cleanup rule.

## Kafka and Schema Governance

- Input: `campaign.lifecycle.v1`, key `campaignId`, group
  `flashsale-campaign-projection-v1`, existing scheduled/activated `SpecificRecord` schemas.
- Output: `flashsale.purchase.events.v1`, key `purchaseRequestId`, three local partitions,
  replication factor one, `PurchaseAcceptedV1`.
- Use `TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE`, and `auto.register.schemas=false` in stable
  environments. Registration/compatibility is a controlled setup/CI action.
- Producer uses `acks=all` and idempotence; outbox still owns end-to-end durability and consumers
  must tolerate duplicate deliveries.
- Campaign projection consumer acknowledges only after its idempotent Redis mutation succeeds.
  After three bounded delivery attempts it stops the affected listener with the offset uncommitted;
  there is no DLT in this MVP.

## Security and HTTP Design

- API Gateway route `/api/v1/flash-sales/**` requires authentication and forwards JWT, idempotency,
  and trace headers.
- Flash Sale validates JWT independently using issuer, audience `flash-sale-api`, signature,
  expiration, and token type; `sub` is the only shopper identity source.
- Campaign snapshot recovery obtains a short-lived token as `flashsale-service`, audience
  `flash-sale-internal-api`, scope `campaign.snapshot.read`.
- Missing/foreign reservations are both 404 to avoid ownership disclosure.
- Successes use shared `ApiResponse<T>`; failures use shared `ApiErrorResponse` and trace identity is
  header-only.

## Observability Plan

- Expose liveness, readiness, and Prometheus endpoints declaratively.
- Liveness does not depend on Redis/Kafka/PostgreSQL. Readiness fails when Redis or PostgreSQL cannot
  safely admit/persist purchases. Kafka/Registry outage does not reject a durably accepted purchase;
  outbox backlog/age alerts expose it.
- Create Micrometer observations around HTTP admission, Redis Lua, handoff persistence, expiration,
  Campaign projection, and outbox publication. Propagate W3C `traceparent`/`tracestate` over HTTP and
  Kafka; preserve the repository's `X-Trace-Id` compatibility header.
- Metrics use only bounded tags such as `operation`, `outcome`, and `dependency`; user, Campaign,
  Variant, reservation, request, and idempotency values are forbidden as tags.
- Logs may contain safe hashed/correlation identifiers but never JWTs, raw idempotency keys,
  Redis credentials, secrets, or full Kafka payloads.

## Validation Strategy

1. **Unit**: domain policies, use cases, canonical hashing, status transitions, mapper behavior, and
   retry calculations.
2. **Architecture**: ArchUnit prevents domain/application imports of framework and adapter types.
3. **Web/security**: endpoint contract, envelopes, status/header matrix, JWT audience/issuer,
   ownership hiding, validation, and Gateway forwarding.
4. **Redis integration**: real Redis executes Lua under 1,000-way quota contention and 100 same-key
   retries; verifies limit, exact stable IDs, atomic XADD, expiry restoration, crash recovery, and
   `XAUTOCLAIM`.
5. **PostgreSQL integration**: real PostgreSQL validates Liquibase/JPA, unique constraints,
   acceptance-versus-expiry arbitration, rollback, idempotent request/Stream races, and multiple
   `SKIP LOCKED` outbox workers.
6. **Kafka/Registry contract**: generated schema, compatibility, headers, key, serialization,
   duplicate publication, consumer replay, and Kafka/Registry outage recovery.
7. **Compose smoke**: real Authentication -> Gateway -> Flash Sale -> Redis/PostgreSQL ->
   Kafka/Schema Registry, followed by authenticated reservation lookup.
8. **Load**: k6 records environment, traffic model, throughput, p50/p95/p99, error rate, and winner
   count; results are evidence, not a universal production SLA.
9. **Build**: `./mvnw -pl services/flashsale-service -am verify` then `./mvnw clean verify`.
10. **Kubernetes**: no manifest is added in this feature because `infra/k8s` currently contains only
    a future-work README. Kubernetes deployment assets require their own approved infrastructure
    plan; the service remains configuration-driven and container-ready.

## Delivery Sequence

1. Module dependencies, typed properties, configuration, architecture tests, and Liquibase schema.
2. Campaign projection consumer, Redis projection adapter, and snapshot recovery client.
3. Reservation domain/application model and Redis Lua/Stream adapter.
4. Durable acceptance persistence, Stream recovery, expiry arbitration, and idempotency cleanup.
5. HTTP/security/Gateway contract.
6. Avro `PurchaseAcceptedV1`, outbox relay, Schema Registry/topic setup, and failure recovery.
7. Observability, Compose smoke, concurrency tests, load script, and full reactor validation.

`tasks.md` may now convert this approved sequence into dependency-ordered implementation work.

## Complexity Tracking

No Constitution violation is requested. Redis Stream is additional infrastructure usage rather than
a new deployed service; it reuses the approved single Redis instance and is justified by ADR 0017 as
the smallest mechanism that closes the Redis-winner/PostgreSQL-failure crash window.
