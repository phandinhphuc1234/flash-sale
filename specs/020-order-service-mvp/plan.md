# Implementation Plan: Order Service Core MVP

**Status**: Approved for implementation — G1, G2, G3, G4, G5, G6, and G7 authorized by project owner on 2026-08-16
**Branch**: `020-order-service-mvp` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)
**Input**: Approved Feature 020 specification at `/specs/020-order-service-mvp/spec.md`

## Summary

Build the first Order-owned durable boundary downstream of Feature 019. `order-service` consumes the
implemented Avro `PurchaseAcceptedV1` record, validates and canonicalizes it at the Kafka boundary,
then commits one `PENDING_PAYMENT` Order, one Order line, one consumer-inbox identity, and one
`OrderCreatedV1` outbox row in a single PostgreSQL transaction. A leased outbox relay publishes the
new Avro fact on `flashsale.order.events.v1` with at-least-once delivery and stable identity.

Authenticated shoppers query only their own committed Orders through API Gateway. The service uses
package-by-feature with Clean/Hexagonal boundaries: Avro, Kafka, HTTP, Spring Security, JPA,
PostgreSQL, and scheduling remain in adapters/configuration; the application core expresses Order
creation and query capabilities without framework types. Payment commands/results, reservation
confirmation/release, terminal Order states, and payment deadlines remain outside this feature.

## Technical Context

**Language/Version**: Java 21
**Framework**: Spring Boot 3.5.16
**Primary Dependencies**: `common-web`, `kafka-avro-contracts`, Spring MVC, Validation, Spring Data
JPA, Spring Security OAuth2 Resource Server, Spring Kafka, Liquibase, Confluent Avro serializer,
Micrometer Tracing bridge for OpenTelemetry, Actuator, and runtime Prometheus registry
**Storage**: Service-owned PostgreSQL 17 database `order_db`; no Redis
**Testing**: JUnit 5, Spring Boot Test, Spring Security Test, Spring Kafka Test, Testcontainers
PostgreSQL/Kafka, opt-in local Schema Registry validation, ArchUnit 1.4.2, and k6 for owner-query
latency
**Target Platform**: Linux containers under root Docker Compose and future Kubernetes deployment
**Project Type**: Independently deployable Spring Boot microservice in the Maven monorepo
**Performance Goals**: Owner detail-query p95 below 200 ms and delivered-event-to-committed-Order
p95 below one second under the documented nominal local profile
**Constraints**: One Order per `purchaseRequestId` and `reservationId`; at-least-once Kafka;
consumer inbox plus database uniqueness; one local transaction for Order/inbox/outbox; stable outbox
identity; no synchronous downstream calls; no Redis/distributed lock; no payment or terminal Order
transition
**Scale/Scope**: One-item accepted purchases, at least 100 concurrent/repeated duplicates, multiple
safe Order replicas, three-partition local Kafka topics, one local broker/Registry, and one
PostgreSQL instance. Multi-region, multi-item checkout, Payment integration, and retention deletion
are deferred.

No `NEEDS CLARIFICATION` remains in the approved Feature 020 scope. Payment deadline, retry policy,
late-payment handling, reservation confirmation/release, and terminal Order status are explicitly
outside this plan rather than silently defaulted.

## Constitution Check

*GATE: Passed before Phase 0 research and re-checked after Phase 1 design.*

| Gate | Result | Design evidence |
|------|--------|-----------------|
| Specification traceability | PASS | The plan implements FR-001–FR-026 and SC-001–SC-006 only; payment and terminal lifecycle behavior remain excluded. |
| Service ownership | PASS | `order-service` owns `order_db`, migrations, domain, application, adapters, and tests. No cross-service database or shared JPA/domain model is introduced. |
| Architecture | PASS | Package-by-feature retains `adapter -> application -> domain`; Avro, Kafka, JPA, HTTP, and Spring types stop at adapters/configuration. |
| External ingress | PASS | `/api/v1/orders/**` is added only to API Gateway and is revalidated by Order Service. Kubernetes DNS remains the service-discovery direction. |
| Synchronous communication | PASS | Order creation and query make no downstream HTTP call. No Feign/client dependency is added. |
| Asynchronous communication | PASS | Existing `PurchaseAcceptedV1` is consumed unchanged; `OrderCreatedV1` is schema-first and documented before producer code. |
| Data and messaging | PASS | PostgreSQL is durable truth; inbox/business uniqueness handles redelivery; Order + inbox + outbox commit locally; relay publication remains at least once. |
| Root infrastructure ownership | PASS | Topic/schema bootstrap, Compose, smoke, and monitoring assets stay under root `infra/`; service configuration/migrations stay in `services/order-service`. |
| Observability | PASS | Declarative Actuator/Prometheus, bounded Micrometer observations, W3C Kafka/HTTP propagation, and query/consumer/outbox readiness signals are planned. |
| Contract/dependency control | PASS | HTTP and Kafka contracts plus every added production/test dependency are listed below. No unsupported provider or payment dependency is added. |
| Validation | PASS | Domain, architecture, PostgreSQL, Kafka/Registry, duplicate/concurrency, recovery, HTTP/security, smoke, performance, module, and full-reactor gates are specified. |

### Architecture Decision Gate

No ADR is required. Order Service, its database, the Order-owned Saga direction, Kafka, Avro,
Schema Registry, API Gateway ingress, and transactional outbox are already repository architecture.
Feature 020 activates one approved service boundary and one catalogued candidate fact without
changing service ownership, communication style, discovery, or durability. Any move to direct
payment calls, shared persistence, a different broker contract, or another ownership model would
require an ADR and plan amendment.

## Project Structure

### Documentation (this feature)

```text
specs/020-order-service-mvp/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── public-order-query-http.md
│   └── order-created-kafka.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)

Only packages receiving real responsibilities are created. Existing layer-first marker files are
removed as their first feature-owned packages replace them.

```text
services/order-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/philia/flashsale/order/
    │   │   ├── OrderServiceApplication.java
    │   │   ├── order/
    │   │   │   ├── domain/{model,valueobject,exception}/
    │   │   │   ├── application/{command,query,result,port/in,port/out,usecase}/
    │   │   │   └── adapter/
    │   │   │       ├── in/{messaging/kafka,web}/
    │   │   │       └── out/{identity,persistence/jpa/{entity,repository,mapper}}/
    │   │   ├── outbox/
    │   │   │   ├── application/{model,port,usecase}/
    │   │   │   └── adapter/{in/scheduling,out/persistence,out/messaging/kafka}/
    │   │   ├── configuration/
    │   │   ├── security/
    │   │   ├── observability/
    │   │   └── websupport/{context,error}/
    │   └── resources/
    │       ├── application.yml
    │       └── db/changelog/
    │           ├── db.changelog-master.yaml
    │           └── changes/001-create-order-core-schema.sql
    └── test/java/com/philia/flashsale/order/
        ├── architecture/
        ├── order/{domain,application,adapter,integration}/
        ├── outbox/{application,integration}/
        ├── security/
        ├── observability/
        └── support/

contracts/kafka-avro-contracts/
├── src/main/avro/topics/flashsale.order.events.v1/OrderCreatedV1.avsc
└── src/test/java/com/philia/flashsale/contract/order/event/OrderCreatedSchemaTests.java

services/api-gateway/
├── src/main/resources/application.yml
└── src/test/java/com/philia/flashsale/gateway/OrderGatewayRouteTests.java

infra/docker/
├── compose.yml
├── compose.dev.yml
├── .env.example
├── kafka/init-order-topics.sh
├── schema-registry/register-order-schemas.ps1
└── smoke/feature-020-order.ps1

load-tests/order-service/
└── order-query.js
```

**Structure Decision**: Order is a core-risk service and uses package-by-feature. The `order`
capability owns Aggregate, use cases, inbound Kafka/HTTP adapters, and persistence. Outbox is a
service-wide reliable-publication capability with its own application and adapters. Security,
observability, error translation, and configuration remain thin technical support packages. Inbox
is persisted by the Order-creation capability rather than becoming a framework-like root feature.

### Repository Convention Alignment

Feature 020 follows the folder conventions already implemented in this repository rather than
inventing a parallel structure:

- `flashsale-service/reservation` is the closest accepted pattern for a core flow: a business
  feature contains `domain`, `application`, and `adapter/in` plus `adapter/out`, while service-wide
  `configuration`, `security`, `observability`, `websupport`, and `outbox` remain technical
  capabilities.
- `campaign-service/campaign` demonstrates the same feature-first boundaries with separate inbound
  web capabilities and outbound client/persistence adapters.
- `inventory-service/stock`, `allocation`, and `movement` show how multiple cohesive capabilities
  coexist without sharing JPA repositories or domain models.
- `authentication-service/account` and `session` show the approved service-wide technical package
  convention for security, cleanup, throttling, observability, and configuration.

Order therefore uses `order/{domain,application,adapter}` with the same `port/in`, `port/out`,
`adapter/in`, and `adapter/out/persistence/jpa` boundaries. It adds no Redis-specific package,
admin/internal web split, or provider client because Feature 020 does not need those
responsibilities. Packages are created on demand with their first real class; the plan tree is a
navigation contract, not a request to create empty folders. Tests mirror the owning package under
`src/test/java/.../order`, with `architecture`, `integration`, and `support` as service-level test
capabilities.

## Dependency and Boundary Rules

```text
Kafka listener / HTTP controller
  -> input port
  -> application use case
  -> Order domain
  -> atomic creation/query output port
  <- PostgreSQL adapter

Outbox scheduler
  -> publication use case
  -> claim/update and publish output ports
  <- PostgreSQL and Kafka adapters
```

- `PurchaseAcceptedV1` and Kafka headers are converted to an application command inside
  `order/adapter/in/messaging/kafka`; generated Avro types never enter application/domain.
- The application core supplies one `PersistOrderCreationPort` capability that preserves the
  Order + inbox + outbox transaction. It does not expose unrelated `saveOrder`, `saveInbox`, and
  `saveOutbox` ports that callers could compose non-atomically.
- Domain `Order`, `OrderLine`, money values, status, and invariant failures remain Java-only.
- JPA entities, Spring Data repositories, PostgreSQL advisory-lock/native statements, and
  persistence mapping stay under `order/adapter/out/persistence`.
- HTTP DTOs and mapping stay beside the web adapter. Small explicit mappers are preferred; this
  feature adds no MapStruct dependency.
- Outbox application models and ports contain no Kafka/JPA types. Generated `OrderCreatedV1` is
  constructed only in the Kafka publishing adapter.
- Security and HTTP error translation reuse project semantics but remain service-owned; no security
  framework type crosses into the Order application/domain core.
- ArchUnit tests enforce inward dependency direction and prevent cross-feature adapter imports.

## Phase 0: Research Decisions

The resolved alternatives and rationale are recorded in [research.md](./research.md). The principal
decisions are:

1. Consume the implemented `PurchaseAcceptedV1`; reject the draft's nonexistent reservation topic
   and expiry event.
2. Use a canonical SHA-256 business fingerprint plus inbox event identity, unique
   `purchaseRequestId`/`reservationId`, and transaction-scoped PostgreSQL identity arbitration.
3. Persist Order, line, inbox, and `OrderCreatedV1` outbox intent atomically in `order_db`.
4. Publish `OrderCreatedV1` as a fact; do not use it as a Payment command.
5. Use Avro SpecificRecord, `TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE`, controlled schema
   registration, producer idempotence, and at-least-once delivery.
6. Use bounded consumer retry followed by a consumer-specific DLT and operator replay; use durable
   unbounded outbox retry with capped backoff for an already-committed required fact.
7. Reuse shared public HTTP envelopes and JWT trust while keeping owner authorization inside Order.
8. Keep published outbox and inbox records indefinitely in this MVP because no retention policy is
   approved; retention requires a later specification and migration.

## Phase 1: Design Outputs

- [Data model](./data-model.md): Order Aggregate, state/invariants, consumer inbox, outbox, indexes,
  and migration/rollback design.
- [Public HTTP contract](./contracts/public-order-query-http.md): owner detail/list queries, shared
  envelopes, pagination, security, errors, and trace/cache headers.
- [Order-created Kafka contract](./contracts/order-created-kafka.md): schema, topic/key, envelope,
  headers, identity, compatibility, retry, and consumer expectations.
- [Quickstart](./quickstart.md): module, contract, integration, Compose smoke, failure, concurrency,
  performance, and full-reactor validation sequence.

## Dependency Changes

### Production dependencies added to `order-service`

| Dependency | Purpose and boundary |
|------------|----------------------|
| `libs/common-web` | Shared HTTP success/error/pagination representations at the web boundary only. |
| `contracts/kafka-avro-contracts` | Generated inbound/outbound Avro protocol types used only by Kafka adapters. |
| `spring-boot-starter-validation` | Validate HTTP configuration/parameters and adapter input shape. |
| `spring-boot-starter-data-jpa` | Service-owned Order, inbox, and outbox persistence. |
| `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | Independently validate and authorize public shopper JWTs. |
| `spring-kafka` | Consume accepted purchases and publish Order-created events. |
| `kafka-avro-serializer` | Confluent Schema Registry Avro deserialization/serialization. |
| `micrometer-tracing-bridge-otel` | W3C trace/MDC propagation using the repository tracing direction. |
| PostgreSQL runtime driver and existing Liquibase | Connect to `order_db` and own forward migrations. |

Existing Spring MVC, Actuator, runtime Prometheus registry, Liquibase, and Boot test dependencies
remain. No Redis, Feign, OAuth2 client, payment-provider SDK, MapStruct, distributed-lock,
Resilience4j, Kafka transaction, Debezium, Spring Modulith, or OpenAPI dependency is introduced.

### Test-only dependencies

Spring Security Test, Spring Kafka Test, Spring Boot Testcontainers, Testcontainers JUnit/PostgreSQL/
Kafka, and ArchUnit 1.4.2. Live Schema Registry checks and Compose smoke reuse root infrastructure.

## Runtime Design

### New accepted purchase

```text
flashsale.purchase.events.v1 / PurchaseAcceptedV1
  -> Order Kafka adapter validates schema/envelope and maps command
  -> CreateOrderFromAcceptedPurchase use case
  -> PostgreSQL transaction
       + arbitrate event/purchaseRequest/reservation identities
       + insert one Order(PENDING_PAYMENT)
       + insert one Order line
       + insert one inbox receipt + canonical fingerprint
       + insert one OrderCreatedV1 outbox intent
  -> COMMIT
  -> acknowledge consumed offset

Order outbox scheduler
  -> claim due rows with lease
  -> publish OrderCreatedV1 keyed by orderId
  -> mark published or schedule same-identity retry
```

### Duplicate, conflict, and crash windows

- A transaction-scoped PostgreSQL identity lock serializes competing attempts for the canonical
  purchase identity. Unique constraints on event ID, purchase request, reservation, Order number,
  and logical outbox transition remain final integrity backstops.
- The canonical fingerprint includes event type/version, producer/aggregate identity and version,
  purchase/reservation/Campaign/Variant/shopper identities, quantity, scale-4 unit price, uppercase
  currency, accepted time, and reservation expiry. Topic partition/offset and trace headers are not
  business equivalence fields.
- Same event ID + same fingerprint is a duplicate no-op. Same event ID + different fingerprint is a
  non-retryable conflict.
- Different event ID + same purchase/reservation identities + same business fingerprint reuses the
  existing Order without another Order-created fact. Contradictory identity reuse is a conflict.
- A database failure rolls back all four durable components and leaves the Kafka record
  unacknowledged. A crash after commit and before offset acknowledgement replays to the same Order.
- Domain/application code never catches or depends on JPA/Kafka exceptions; adapters translate them
  into typed application outcomes or retryable/non-retryable listener failures.

### Owner queries

```text
Shopper -> Gateway /api/v1/orders/**
  -> Gateway validates public JWT and forwards auth/trace
  -> Order validates issuer/signature/audience/type/expiry/subject
  -> application query includes JWT subject as owner identity
  -> owner-scoped PostgreSQL query
  <- ApiResponse<OrderDetail> or ApiResponse<PageResponse<OrderSummary>>
```

Unknown and foreign Order IDs use the same `404 ORDER_NOT_FOUND`. List sorting is `createdAt DESC,
id DESC`; page defaults and maximum come directly from FR-018. Query endpoints do not depend on
Kafka/Schema Registry readiness.

## Persistence and Migration Plan

- Use Liquibase formatted PostgreSQL SQL at
  `services/order-service/src/main/resources/db/changelog/changes/001-create-order-core-schema.sql`.
- Create `orders`, `order_lines`, `order_consumer_inbox`, and `order_outbox_events` exactly as
  detailed in [data-model.md](./data-model.md).
- Use UUID primary keys, exact `NUMERIC(19,4)` money, `TIMESTAMPTZ`, explicit check/unique/foreign-key
  constraints, and user/order/outbox claim indexes.
- Keep `spring.jpa.hibernate.ddl-auto=validate`. Liquibase owns schema creation through a one-off
  `order-migration` Compose service before normal Order replicas start.
- Use Spring Data JPA for ordinary aggregate/query persistence. PostgreSQL-specific advisory-lock
  and `FOR UPDATE SKIP LOCKED` statements remain inside the persistence/outbox adapters using
  `JdbcTemplate` where clearer.
- Store source topic/partition/offset for diagnostics but use `eventId` plus business identities for
  semantic deduplication.
- Do not add retention cleanup. Order, inbox, and published outbox retention is unresolved future
  policy and therefore remains durable indefinitely in this MVP.
- Include development rollback SQL in reverse dependency order; production rollback prefers a
  forward corrective migration once data exists.

## Kafka and Schema Governance

### Inbound accepted purchase

- Topic: `flashsale.purchase.events.v1`; key: `purchaseRequestId`; record:
  `com.philia.flashsale.contract.purchase.event.v1.PurchaseAcceptedV1`.
- Consumer group: `order-purchase-accepted-v1`.
- Consume only `eventType=PurchaseAccepted`, `eventVersion=1`, producer `flashsale-service`,
  aggregate type `PURCHASE_REQUEST`, and matching aggregate/data/key identities.
- Use manual record acknowledgement after local success/duplicate handling. Unsupported, malformed,
  or contradictory records are not treated as successful business input.
- Transient processing gets three retries after the initial delivery with 1 s, 3 s, and 10 s
  delays. Exhausted or non-retryable input goes to operational topic
  `flashsale.order.purchase-accepted.dlt.v1` with original key/value when deserializable, safe error
  headers, and no JWT/secret/full stack trace. Operator replay is explicit and remains idempotent.

### Outbound Order-created fact

- Topic: `flashsale.order.events.v1`; key: `orderId`; record:
  `com.philia.flashsale.contract.order.event.v1.OrderCreatedV1`.
- Local topics use three partitions and replication factor one; stable environments choose their
  replication independently without changing the contract.
- Use `TopicRecordNameStrategy`, subject
  `flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderCreatedV1`,
  `BACKWARD_TRANSITIVE`, and `auto.register.schemas=false` outside controlled bootstrap.
- Producer uses `acks=all` and idempotence. This reduces broker duplicates but does not replace
  outbox/consumer idempotency.
- The relay polls every 500 ms, claims at most 100 due rows with a 30-second lease and
  `FOR UPDATE SKIP LOCKED`, publishes sequentially, and retries the same identity with exponential
  backoff capped at 60 seconds. A committed required fact is never discarded to a DLT.
- W3C `traceparent` and optional `tracestate` are copied from inbound headers to the outbox snapshot
  and outbound Kafka headers. JWTs and authorization headers never enter Kafka.

## Security and HTTP Design

- API Gateway adds an authenticated `order-public` route for `/api/v1/orders/**` to
  `${ORDER_SERVICE_URL:http://order-service:8080}` and preserves Authorization and trace headers.
- Order validates the existing issuer, JWKS URI, audience `flash-sale-api`, RS256 signature,
  `typ=at+jwt`, expiry/time claims, and nonblank subject. It does not accept the internal service
  audience for public Order queries.
- JWT `sub` is the only owner identity. No `userId` request/query/header is accepted.
- Detail and list responses use `common-web`; errors use the repository's standard security and MVC
  failure semantics. Error bodies never expose Kafka, Schema Registry, PostgreSQL, SQL, token, or
  stack-trace details.
- Detail/list responses use `Cache-Control: no-store` and return `X-Trace-Id`.
- There is no `POST`, `PUT`, `PATCH`, or `DELETE` Order route in this feature.

## Observability and Readiness Plan

- Expose liveness, readiness, and Prometheus endpoints declaratively through existing Actuator and
  runtime registry dependencies.
- Liveness reports process viability only. Readiness requires PostgreSQL for Order ingestion/query;
  Kafka/Registry publication outage leaves the service ready for queries but exposes outbox backlog
  and oldest-pending-age signals. Consumer health is separately observable.
- Create bounded Micrometer observations for inbound processing, durable Order creation, owner
  query, and outbox publication. Tags are limited to operation, event type, outcome, dependency,
  and status; IDs, Order numbers, users, Campaigns, Variants, partitions, and error messages are not
  labels.
- Structured logs may include trace/event/Order/purchase/reservation identities and safe outcomes,
  but never JWTs, credentials, raw Avro payloads, SQL, or secrets.
- The trace path is Flash Sale event headers -> Order consumer context -> database/outbox logs ->
  `OrderCreatedV1` headers and Gateway request -> Order HTTP response `X-Trace-Id`.

## Validation Strategy

1. **Contract**: Generate `OrderCreatedV1` SpecificRecord; assert exact schema identity, UUID/
   timestamp/decimal logical types, subject strategy, compatibility, required/forbidden fields,
   key, and W3C headers. Verify inbound compatibility without changing `PurchaseAcceptedV1`.
2. **Domain/unit**: Order creation, one-line amount arithmetic, immutability, positive values,
   currency, status, fingerprint normalization, duplicate/conflict outcomes, and retry calculations.
3. **Architecture**: ArchUnit blocks domain/application imports of Spring, JPA, Kafka, Avro,
   Jackson, HTTP, and adapters; boundary models remain separate.
4. **PostgreSQL integration**: Real PostgreSQL validates Liquibase/JPA, all constraints, rollback,
   advisory identity arbitration, 100 concurrent duplicates/conflicts, owner scoping, and multiple
   `SKIP LOCKED` outbox workers.
5. **Kafka/Registry integration**: Serialize/consume existing `PurchaseAcceptedV1`, verify key/group/
   headers, commit-before-ack redelivery, retry/DLT routing, stable outbox publication, and live
   controlled Registry registration.
6. **Web/security**: Detail/list envelopes, pagination/sort, no-store/trace headers, owner/non-owner
   404 equivalence, validation, valid JWT, missing/expired/wrong-audience/wrong-type tokens, and
   absence of mutation routes.
7. **Failure matrix**: PostgreSQL down before commit, process after commit before offset ack, Kafka
   down, Registry down, publisher after send before marking published, poison event, contradictory
   replay, and recovery/replay.
8. **Compose smoke**: Existing Feature 019 fixture emits `PurchaseAcceptedV1`; Order commits,
   publishes `OrderCreatedV1`, and serves the owner query through Gateway. Verify foreign-owner and
   wrong-audience rejection.
9. **Performance**: Run 100 duplicate/concurrent deliveries and a documented k6 owner-query profile;
   record event commit and HTTP p50/p95/p99 plus error rate. This does not repeat the 1,000-way Flash
   Sale winner-selection burst because Order receives only accepted winners.
10. **Build**: `./mvnw -pl services/order-service -am verify`, affected Gateway/contracts checks,
    then `./mvnw clean verify`.
11. **Kubernetes**: No manifest is added because `infra/k8s` still has no approved Order deployment
    overlay. Any later overlay must pass `kubectl apply --dry-run=client -k <overlay>`.

Evidence is recorded in `specs/020-order-service-mvp/validation.md` during implementation. Required
failure or build checks must pass before a task group is marked complete.

## Delivery Sequence

1. Contracts, module dependencies, typed configuration, migration, test support, and architecture
   guardrails.
2. Order domain plus atomic accepted-purchase persistence and idempotency.
3. `PurchaseAcceptedV1` consumer, retry/DLT behavior, and recovery tests.
4. `OrderCreatedV1` outbox relay and Kafka/Registry recovery.
5. Owner detail/list APIs, JWT security, Gateway route, and error contracts.
6. Observability/readiness, Compose smoke, concurrency/performance evidence, and full validation.

`tasks.md` was generated from this plan. The project owner authorized implementation of G1
(T001–T008), G2 (T009–T014), G3 (T015–T029), G4 (T030–T036), and G5 (T037–T046) on 2026-08-15,
and G6 (T047–T060) and G7 (T061–T067) on 2026-08-16; later groups remain gated by their
checkpoints and required review.

## Approval History

| Date | Decision | Scope | Approver |
|------|----------|-------|----------|
| 2026-08-15 | Approved for implementation | G1 / T001–T008 | Project owner |
| 2026-08-15 | Approved for implementation | G2 / T009–T014 | Project owner |
| 2026-08-15 | Approved for implementation | G3 / T015–T029 | Project owner |
| 2026-08-15 | Approved for implementation | G4 / T030–T036 | Project owner |
| 2026-08-15 | Approved for implementation | G5 / T037–T046 | Project owner |
| 2026-08-16 | Approved for implementation | G6 / T047–T060 | Project owner |
| 2026-08-16 | Approved for implementation | G7 / T061–T067 | Project owner |

## Complexity Tracking

No Constitution violation is requested. PostgreSQL transaction-scoped identity arbitration is
service-local persistence coordination, not a distributed lock or new infrastructure service. It
is paired with unique constraints because the database remains the final integrity boundary.
