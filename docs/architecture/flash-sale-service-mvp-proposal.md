# Flash Sale Service MVP — Repository-Aligned Proposal

> Service: `flashsale-service`  
> Candidate feature: `019-flash-sale-service-mvp`  
> Status: design proposal, not an approved Spec Kit artifact  
> Reviewed against repository state: 2026-08-10

## 1. Verdict

The supplied MVP design has the correct core idea: reject losing traffic in Redis, use one atomic
Lua decision, persist only winners, and hand accepted purchases to Order asynchronously. It must
not be implemented unchanged, however. This proposal aligns it with the repository's current
contracts and removes duplicated or conflicting mechanisms.

The important corrections are:

1. Consume only the currently approved `CampaignScheduled.v1` and `CampaignActivated.v1` Avro
   records from `campaign.lifecycle.v1`. Feature 017 does not publish `CampaignEnded.v1`.
2. Use the exact Feature 017 single `data.item` snapshot, including allocation ID, SKU, currency,
   decimal price, quantity, and purchase limit. Do not invent an `items[]` wire shape.
3. Close the Redis-to-PostgreSQL crash gap with an atomic Redis Stream handoff created inside the
   reservation Lua script. A separate pending-persistence ZSET is unnecessary.
4. Keep PostgreSQL as durable truth. Persist an accepted purchase/reservation plus its PostgreSQL
   outbox row before returning the documented durable acceptance response.
5. Publish candidate `PurchaseAccepted.v1` to `flashsale.purchase.events.v1`, keyed by
   `purchaseRequestId`. Do not create the incompatible `flash-sale.reservation.v1` topic or
   `FlashSaleReservationAccepted.v1` name.
6. Use Avro schema-first generated `SpecificRecord` classes and Confluent Schema Registry. Kafka
   values are not JSON strings, JPA entities, domain objects, or reflection records.
7. Wrap successful public HTTP bodies in `common-web.ApiResponse<T>` and failures in
   `ApiErrorResponse`. Trace identity stays in `X-Trace-Id` and W3C transport headers, not the body.
8. Use the already-approved `flashsale-service` OAuth2 Client Credentials identity only for
   recovery calls to Campaign's internal snapshot endpoint. OpenFeign never enters the purchase
   hot path.
9. Treat this service as a package-by-feature `CORE_DOMAIN`; keep recovery and expiration inside
   the reservation feature rather than creating thin top-level technical features.
10. Do not create Feature 018 for this work: Feature 018 already owns HTTP response
    standardization. The next candidate number is Feature 019.

## 2. Scope and complexity

**Boundary**: one independently deployable Spring Boot microservice.  
**Complexity**: `CORE_DOMAIN`.

The classification is justified by:

- atomic stock/quota invariants;
- concurrent idempotency and per-user purchase limits;
- Redis-to-PostgreSQL recovery;
- at-least-once Kafka consumption and publication;
- reservation lifecycle and compensation;
- correctness under process and dependency failures.

This is not ordinary CRUD. Domain, application, inbound adapters, outbound adapters, and explicit
ports are justified.

## 3. Service ownership

```text
Campaign Service
  -> Campaign configuration, schedule, activation, and immutable sale snapshot

Inventory Service
  -> physical stock, durable Campaign allocation, movements, and later reconciliation

Flash Sale Service
  -> hot Campaign projection, winner selection, reservation, and durable purchase acceptance

Order Service
  -> Purchase Saga and final Order lifecycle

Payment Service
  -> payment attempts and provider reconciliation
```

Flash Sale Service owns:

- the Redis projection derived from approved Campaign events;
- atomic hot-quota reservation;
- per-user purchase-limit enforcement;
- purchase-request idempotency;
- its durable accepted-purchase/reservation journal;
- Redis Stream handoff recovery;
- the PostgreSQL outbox for `PurchaseAccepted.v1`;
- reservation lookup for the authenticated owner.

It does not:

- read Product, Campaign, Inventory, Order, or Payment databases;
- call Inventory, Order, or Payment during a purchase request;
- decrement Inventory `on_hand_quantity`;
- decide Campaign configuration or final Order status;
- put a distributed lock around Lua;
- treat Redis as durable business truth;
- publish Kafka directly after a Lua mutation without a durable handoff.

## 4. Current repository baseline

Already present:

- `services/flashsale-service` Spring Boot skeleton, Dockerfile, Actuator, Prometheus runtime
  registry, and empty Liquibase master changelog;
- `flashsale_db` creation in root PostgreSQL initialization;
- one authenticated `flashsale-service` machine client with
  `campaign.snapshot.read` in Authentication;
- Campaign internal snapshot HTTP contract;
- Kafka, Redis with AOF, and Confluent Schema Registry in root Docker Compose;
- generated `CampaignScheduledV1` and `CampaignActivatedV1` Avro contracts;
- `libs/common-web` response envelopes;
- Java 21, Spring Boot 3.5.x, and Compose virtual-thread settings.

Still required by the future feature:

- Gateway route and security/rate-limit contract for `/api/v1/flash-sales/**`;
- Flash Sale datasource, Redis, Kafka, Registry, JWT, and service-client runtime configuration;
- JPA, Redis, Kafka, Security, OAuth2 Client, OpenFeign, Avro-contract, and `common-web`
  dependencies approved in `plan.md`;
- service-owned Liquibase migrations;
- `PurchaseAcceptedV1.avsc`, Registry subject, topic provisioning, and contract tests;
- the complete application/domain/adapters described below.

## 5. MVP scope

### 5.1 Included

```text
CampaignScheduled.v1
  -> idempotently preload a non-purchasable Redis projection

CampaignActivated.v1
  -> activate the existing projection without resetting remaining quota

POST reservation
  -> authenticate user
  -> Redis Lua decision + Redis Stream XADD
  -> durable PostgreSQL acceptance + outbox
  -> XACK Stream entry
  -> 202 response

PostgreSQL outbox relay
  -> PurchaseAccepted.v1 Avro event

GET reservation
  -> authenticated owner reads durable status
```

MVP includes correctness tests, Kafka/Registry integration tests, recovery tests, and a small k6
portfolio benchmark after correctness is green.

### 5.2 Excluded

- Order and Payment implementation;
- `ConfirmPurchaseReservation` and `ReleasePurchaseReservation` consumers;
- Campaign end/cancellation Saga;
- Inventory reconciliation;
- refund, waiting room, lottery, bot detection, multi-region, and multi-item cart transactions;
- Redis Cluster deployment, stock sharding, Debezium, Kafka transactions, and a distributed
  scheduler framework;
- a production claim of exactly-once delivery.

The MVP may publish `PurchaseAccepted.v1` without an Order consumer. Its acceptance test inspects
Kafka and Schema Registry directly. Order becomes the next service feature.

## 6. Human decisions required before Spec Kit approval

These rules affect stock, money, idempotency, or failure semantics and must not be inferred during
implementation.

| ID | Priority | Decision and options | Recommendation and trade-off | Owner / deadline |
|---|---|---|---|---|
| D1 | P0 | `[NEEDS CLARIFICATION]` Reservation TTL and future payment deadline: one shared deadline, or separate deadlines with an ordering rule | One configurable reservation TTL approved together with Order's payment deadline; simple, but blocks final expiry semantics until Order is designed | Project owner / before Feature 019 spec approval |
| D2 | P0 | `[NEEDS CLARIFICATION]` PostgreSQL unavailable after Lua+Stream: forward recovery, or immediate compensation | Forward recovery through the Stream; protects an established winner, but the retry response/status contract must explain the temporarily ambiguous outcome | Project owner / before HTTP contract approval |
| D3 | P0 | `[NEEDS CLARIFICATION]` Idempotency retention and reuse: never reuse, fixed TTL, or Campaign-relative retention | Retain through Campaign end plus a buffer and permit reuse only after both durable and Redis retention end; bounded storage with more cleanup work | Project owner / before data-model approval |
| D4 | P1 | `[NEEDS CLARIFICATION]` Request quantity cap: Campaign limit only, or Campaign limit plus a lower technical cap | Start with the Campaign `purchaseLimitPerUser`; avoids a second business limit unless abuse testing proves a technical cap is needed | Project owner / before public API approval |
| D5 | P0 | `[NEEDS CLARIFICATION]` Expiry before Order exists: local expiry only, or local expiry plus Kafka event | Local state plus idempotent quota restoration; defer an expiry event until Order owns a consumer contract | Project owner / before lifecycle approval |
| D6 | P1 | `[NEEDS CLARIFICATION]` Redis Stream trim size and pending-entry claim time | Bounded approximate trimming plus consumer-group recovery; exact values follow a local capacity and maximum-outage estimate | Project owner / during plan research |
| D7 | P1 | `[NEEDS CLARIFICATION]` Campaign consumer retry/DLT: stop container, bounded retries plus DLT, or operator-only replay | Bounded retry plus documented DLT/operator replay; more operational code, but poison records cannot block the projection forever | Project owner / before Kafka consumer contract approval |
| D8 | P1 | `[NEEDS CLARIFICATION]` Gateway purchase rate, identity, and Redis-down failure mode | Dedicated authenticated-user policy; reuse the existing limiter implementation but not catalog values | Project owner / before Gateway contract approval |

Until these are approved, this document remains a proposal and production implementation must not
start.

## 7. Canonical runtime flows

### 7.1 Campaign projection

```text
campaign.lifecycle.v1
  -> generated CampaignScheduledV1 / CampaignActivatedV1
  -> Kafka listener
  -> inbound event mapper
  -> ProjectCampaignLifecycleUseCase
  -> Redis projection Lua/adapter
  -> commit Kafka offset only after projection succeeds
```

Projection rules:

- Kafka key is textual `campaignId`.
- `CampaignScheduledV1.data.item` initializes the one-item MVP projection.
- Duplicate `eventId` or the same/older `aggregateVersion` is a successful no-op.
- `CampaignScheduled` must never reset stock after the projection has advanced or accepted a
  reservation.
- `CampaignActivated` changes state only; it never initializes unknown quota.
- If activation arrives without a usable scheduled projection, purchase remains fail-closed and a
  recovery use case loads the approved Campaign snapshot through OpenFeign.
- The current MVP does not wait for `CampaignEnded.v1`. Redis Lua rejects when `now >= endAt`, and
  local projection state may become `CLOSED` without claiming Campaign's authoritative status.

The Redis update, processed event identity, and aggregate version guard must happen atomically in
Redis. A PostgreSQL inbox does not make a Redis projection atomic and is intentionally omitted from
this consumer.

### 7.2 Purchase reservation

```text
Client
  -> API Gateway: JWT validation + technical rate limit
  -> Flash Sale Service: JWT revalidation
  -> ReservationController
  -> ReservePurchaseUseCase
  -> ReserveHotQuotaPort
  -> one Redis Lua execution
       validate Campaign state/time
       validate quantity and per-user limit
       enforce idempotency
       decrement remaining quota
       create Redis reservation
       XADD durable-handoff entry
  -> PersistAcceptedPurchasePort
       insert/load durable purchase request
       insert/load durable reservation
       insert/load PurchaseAccepted outbox row
       COMMIT
  -> acknowledge Redis Stream entry
  -> 202 Accepted with ApiResponse<ReservationResponse>
```

The HTTP thread may invoke the persistence use case immediately for low latency. The Redis Stream
consumer invokes the exact same idempotent use case after a crash or lost acknowledgement.

### 7.3 Crash recovery

```text
Redis consumer group
  -> new Stream entry or reclaimed pending entry
  -> PersistAcceptedPurchaseUseCase
  -> DB unique constraints establish/replay the same result
  -> PostgreSQL commit
  -> XACK
```

Recovery must handle:

- crash after Lua before PostgreSQL;
- crash after PostgreSQL commit before `XACK`;
- duplicate request-thread and Stream-consumer execution;
- two service replicas claiming/reclaiming entries;
- a temporary PostgreSQL outage.

No JVM lock or distributed Redis lock is required. Redis atomicity plus database uniqueness makes
duplicate recovery repeat-safe.

### 7.4 Outbox publication

```text
PostgreSQL outbox due row
  -> short claim transaction using FOR UPDATE SKIP LOCKED / lease
  -> map immutable payload to PurchaseAcceptedV1 SpecificRecord
  -> Confluent KafkaAvroSerializer
  -> Kafka ACK
  -> mark the same outbox row PUBLISHED
```

Kafka publication is at least once. A crash after Kafka acknowledgement and before the outbox row
is marked may publish the same `eventId` again. The future Order consumer must deduplicate it.

## 8. HTTP contract direction

### 8.1 Create reservation

```http
POST /api/v1/flash-sales/{campaignId}/reservations
Authorization: Bearer <user-access-token>
Idempotency-Key: <non-blank-key>
X-Trace-Id: <trace-id>
Content-Type: application/json
```

```json
{
  "variantId": "36f82b44-346f-421b-936b-7ec2dca68004",
  "quantity": 1
}
```

User identity comes only from the validated JWT subject. The service independently validates
signature, issuer, expiry, `flash-sale-api` audience, and token type. It never trusts `X-User-Id`
or a body user ID.

After durable acceptance:

```http
202 Accepted
Location: /api/v1/flash-sales/reservations/{reservationId}
X-Trace-Id: <trace-id>
```

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Purchase reservation accepted",
  "data": {
    "purchaseRequestId": "4c73a876-c945-4d73-af11-dc311d54a81a",
    "reservationId": "1de6abbe-095b-4126-a313-01758e75c6d2",
    "campaignId": "535766d3-ce80-4488-88bd-c3a90a4dfcc6",
    "variantId": "36f82b44-346f-421b-936b-7ec2dca68004",
    "quantity": 1,
    "unitPrice": 199000.0000,
    "currency": "VND",
    "status": "RESERVED",
    "expiresAt": "<approved UTC instant>"
  },
  "timestamp": "2026-08-10T07:20:00Z"
}
```

Same user/key/request hash returns the established logical response without another decrement.
Same user/key with a different canonical request hash returns HTTP 409.

### 8.2 Read reservation

```http
GET /api/v1/flash-sales/reservations/{reservationId}
Authorization: Bearer <user-access-token>
X-Trace-Id: <trace-id>
```

Return `200 ApiResponse<ReservationResponse>` only to the owner. A missing or foreign reservation
returns the same safe `404 FLASH_SALE_RESERVATION_NOT_FOUND` response to prevent enumeration.

### 8.3 Error baseline

| Status | Example service-owned code |
|---:|---|
| 400 | `FLASH_SALE_VALIDATION_ERROR`, malformed/missing idempotency key |
| 401 | `FLASH_SALE_UNAUTHENTICATED` |
| 403 | `FLASH_SALE_ACCESS_DENIED` |
| 404 | `FLASH_SALE_RESERVATION_NOT_FOUND` |
| 409 | `FLASH_SALE_NOT_ACTIVE`, `FLASH_SALE_SOLD_OUT`, `FLASH_SALE_PURCHASE_LIMIT_EXCEEDED`, `FLASH_SALE_IDEMPOTENCY_CONFLICT` |
| 429 | Gateway-owned rate-limit error with `Retry-After` and approved rate-limit headers |
| 503 | `FLASH_SALE_TEMPORARILY_UNAVAILABLE`, approved durable-acceptance-pending outcome |
| 500 | `FLASH_SALE_INTERNAL_ERROR` with no internal detail |

Every error body uses `common-web.ApiErrorResponse`. Gateway-owned errors remain Gateway-owned;
committed downstream responses pass through unchanged. JSON bodies do not contain `traceId`.

## 9. Kafka and Schema Registry contracts

### 9.1 Approved inbound contracts

```text
Topic: campaign.lifecycle.v1
Key: campaignId
Subjects:
  campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1
  campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1
Format: generated Avro SpecificRecord
Compatibility: BACKWARD_TRANSITIVE
```

The consumer group name must be approved in the feature contract. It must be stable across
replicas and different from unrelated projection consumers.

### 9.2 Candidate outbound contract

```text
Topic: flashsale.purchase.events.v1
Record: PurchaseAcceptedV1
Key: purchaseRequestId
Producer: Flash Sale Service
Consumer: future Order Service
Subject strategy: TopicRecordNameStrategy
Compatibility: BACKWARD_TRANSITIVE
auto.register.schemas: false
```

Schema source location:

```text
contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/
└── PurchaseAcceptedV1.avsc
```

The schema must define stable message identity, occurrence time, purchase request, reservation,
Campaign, Variant, user, exact money/currency, quantity, and expiry fields. Its final field list is
owned by the future approved Kafka contract; this proposal does not register or provision it.

W3C `traceparent` and optional `tracestate` are Kafka headers. JWTs, cookies, OAuth client secrets,
raw authorization headers, and HMAC secrets must never enter Kafka records or headers.

## 10. Redis model direction

All keys touched by the reservation Lua script share one Redis Cluster hash tag even though the
MVP runs one Redis instance:

```text
tag = {campaignId:variantId}

fs:{campaignId:variantId}:meta
fs:{campaignId:variantId}:stock
fs:{campaignId:variantId}:user:{userId}:qty
fs:{campaignId:variantId}:idem:{userId}:{idempotencyKeyHash}
fs:{campaignId:variantId}:reservation:{reservationId}
fs:{campaignId:variantId}:expirations
fs:{campaignId:variantId}:handoff
```

The reservation Lua script atomically:

1. validates projection existence, state, start/end time, Variant, quantity, and stored price;
2. replays or rejects an idempotency key before changing quota;
3. validates remaining quota and cumulative user quantity;
4. decrements quota and increments user quantity;
5. creates the Redis reservation and idempotency result;
6. adds the reservation to the expiration ZSET;
7. `XADD`s the durable-handoff entry to the Stream;
8. returns a typed result code and established reservation identity.

The release/expiry Lua script restores quota and user quantity at most once and preserves enough
idempotency state to stop an old retry from winning again.

Do not use:

- `synchronized`, `ReentrantLock`, or an in-memory lock for distributed correctness;
- Redisson or another distributed lock around Lua;
- a pending-persistence ZSET that duplicates the Redis Stream consumer-group pending list;
- Redis key eviction as lifecycle management for business-critical quota.

## 11. PostgreSQL model direction

The exact Liquibase SQL belongs to the approved feature plan. The minimal durable model is:

```text
purchase_requests
  purchase_request_id
  user_id
  idempotency_key_hash
  request_hash
  established_reservation_id
  created_at
  retained_until
  unique approved idempotency scope

flash_sale_reservations
  reservation_id
  purchase_request_id
  campaign_id
  variant_id
  user_id
  quantity
  unit_price NUMERIC(19,4)
  currency CHAR(3)
  status
  expires_at
  order_id nullable
  version
  created_at / updated_at

flash_sale_outbox_events
  stable event identity
  aggregate identity/version
  immutable event type/version/payload
  publish status, attempts, next attempt
  claim lease, timestamps, sanitized error
```

Required database uniqueness must protect:

- one logical purchase request per approved idempotency scope;
- one durable reservation per purchase request;
- one `PurchaseAccepted` event per reservation/purchase request;
- one future Order identity per reservation when Order integration is approved.

PostgreSQL persistence and the required outbox row commit in one local transaction. No service
reads `flashsale_db` except Flash Sale Service.

## 12. Security and communication

### Public path

```text
Client -> Gateway -> Flash Sale                         HTTPS/REST + user JWT
```

- Gateway route is `/api/v1/flash-sales/**` and requires authentication.
- Gateway applies its separately approved technical rate-limit policy.
- Flash Sale revalidates the public JWT independently with audience `flash-sale-api`.
- Gateway forwards `Authorization`, `Idempotency-Key`, `X-Trace-Id`, and W3C context as approved.

### Recovery control path

```text
Flash Sale -> Authentication                            OAuth2 client_credentials
Flash Sale -> Campaign snapshot endpoint                OpenFeign HTTP
```

- client ID/subject: `flashsale-service`;
- audience: `flash-sale-internal-api`;
- authority: `SCOPE_campaign.snapshot.read`;
- endpoint: `GET /internal/v1/campaigns/{campaignId}/snapshot`;
- service token is cached only in process memory and never logged or persisted;
- explicit connection/read timeouts and retry policy must be approved;
- no Feign call occurs for an ordinary purchase or while a database transaction is open.

## 13. Recommended package tree

Create packages together with their first real class; this is a target tree, not an instruction to
add empty folders.

```text
com.philia.flashsale.flashsale
├── FlashsaleServiceApplication.java
│
├── campaignprojection/
│   ├── domain/
│   │   ├── model/
│   │   └── exception/
│   ├── application/
│   │   ├── command/
│   │   ├── port/in/
│   │   ├── port/out/
│   │   └── usecase/
│   └── adapter/
│       ├── in/messaging/kafka/
│       └── out/
│           ├── redis/
│           └── client/campaign/
│               ├── dto/
│               └── error/
│
├── reservation/
│   ├── domain/
│   │   ├── model/
│   │   ├── exception/
│   │   └── policy/
│   ├── application/
│   │   ├── command/
│   │   ├── query/
│   │   ├── result/
│   │   ├── port/in/
│   │   ├── port/out/
│   │   └── usecase/
│   └── adapter/
│       ├── in/
│       │   ├── web/
│       │   │   ├── request/
│       │   │   ├── response/
│       │   │   ├── mapper/
│       │   │   └── error/
│       │   ├── messaging/redis/        # Redis Stream relay/reclaimer
│       │   └── scheduling/             # expiration trigger only
│       └── out/
│           ├── redis/
│           └── persistence/jpa/
│               ├── entity/
│               ├── repository/
│               └── mapper/
│
├── outbox/
│   ├── application/
│   └── adapter/
│       ├── in/scheduling/
│       └── out/
│           ├── persistence/jpa/
│           └── messaging/kafka/
│
├── configuration/
├── security/
├── observability/
└── websupport/error/
```

Recovery and expiration stay inside `reservation` because they operate on the same reservation
lifecycle and invariants. `outbox` is separated only because it is a real cross-cutting delivery
capability with persistence claiming, Kafka publication, retries, and operator recovery.

## 14. Dependency and placement rules

```text
adapter.in  -> application -> domain
adapter.out -> application.port.out
adapter.out -> domain
configuration -> adapters + application wiring
```

Forbidden:

- domain/application importing Spring MVC, Spring Security, JPA, Kafka, Redis, Feign, Avro,
  MapStruct, `Page`, or `ResponseEntity`;
- controllers parsing Lua result codes or owning stock decisions;
- Kafka listeners calling repositories directly;
- generated Avro records in domain or application signatures;
- JPA entities returned by web controllers;
- one global mapper crossing HTTP, Redis, JPA, Kafka, and Feign boundaries;
- one feature importing another feature's adapter or repository internals.

Boundary mappers remain separate:

```text
HTTP DTO <-> application command/result       ReservationWebMapper
JPA entity <-> domain/application model       ReservationPersistenceMapper
Avro SpecificRecord <-> application command   CampaignLifecycleKafkaMapper
outbox payload -> Avro SpecificRecord          PurchaseAcceptedAvroMapper
Campaign HTTP DTO <-> projection snapshot      CampaignSnapshotClientMapper
```

## 15. Failure policy direction

| Failure | Required behavior |
|---|---|
| Redis unavailable on purchase | Fail closed; return safe 503; never query Inventory/PostgreSQL for stock |
| Campaign projection missing/stale | Reject purchase; recover from the approved Campaign snapshot endpoint outside the hot path |
| PostgreSQL unavailable after Lua/Stream | Preserve the handoff for approved forward recovery; do not report durable acceptance yet |
| Crash before PostgreSQL commit | Stream pending/new entry invokes the same idempotent persistence use case |
| Crash after PostgreSQL commit before XACK | Redelivery loads the existing result and acknowledges without another reservation/outbox |
| Kafka/Registry unavailable | Reservation remains durable; PostgreSQL outbox retries the same event identity |
| Duplicate Campaign event | Redis projection operation is a no-op and offset may be committed |
| Unsupported/poison Avro record | Follow approved bounded retry/DLT/operator policy; do not corrupt projection |
| Redis hot-state loss during an active Campaign | Fail closed; rebuild from Campaign snapshot plus durable non-released reservations before reopening |

The service promises atomic decision plus recoverable at-least-once delivery, not end-to-end
exactly once.

## 16. Observability

Use Spring Boot Actuator auto-configuration, Micrometer Observation/Tracing, OpenTelemetry, and the
runtime Prometheus registry already required by the repository.

Low-cardinality metrics should cover:

- reservation outcomes and latency;
- Redis Lua latency/failure;
- idempotent replay, sold-out, and purchase-limit counts;
- Redis Stream lag/pending/reclaim counts;
- durable acceptance latency/failure;
- outbox backlog, oldest age, publication outcome, and retry;
- Campaign projection duplicate/stale/recovery outcomes.

Do not tag metrics with user, Campaign, Variant, reservation, purchase request, message, or trace
IDs. Do not log each sold-out request at INFO. Never log JWTs, OAuth secrets, idempotency keys,
cookies, raw authorization headers, or full sensitive payloads.

## 17. Testing and architecture enforcement

### Unit/application

- reservation state transitions and release idempotency;
- request-hash and replay/conflict behavior;
- use cases with fake Redis, persistence, clock, and snapshot ports;
- older/duplicate Campaign version handling.

### Redis integration

- real Redis Lua oversell test;
- concurrent same-key replay and different-payload conflict;
- purchase-limit race;
- atomic XADD with quota mutation;
- repeated compensation restores quota once;
- Stream pending-entry reclaim and duplicate consumer execution.

### PostgreSQL integration

- Testcontainers PostgreSQL with real unique constraints and row/claim locking;
- concurrent request-thread/Stream-worker persistence produces one reservation and one outbox row;
- transaction rollback leaves neither reservation nor outbox partially committed;
- multiple outbox relay workers claim safely.

### Kafka/Registry contract

- consume existing Campaign SpecificRecords;
- serialize/deserialize `PurchaseAcceptedV1` through compatible Registry tooling;
- stable key/event identity across retry and requeue;
- W3C headers and no secrets;
- Kafka/Registry outage preserves the original outbox row.

### Web/security/Gateway

- route forwards method, path, query/body, JWT, idempotency key, and trace headers;
- wrong signature, issuer, audience, expiry, and anonymous access are denied at both boundaries;
- owner-only reservation lookup;
- shared success/error envelopes and Gateway pass-through.

### Load and smoke

- k6 open-model sold-out, duplicate, and purchase-limit scenarios;
- report machine/container resources, request model, p50/p95/p99, throughput, errors, and business
  invariants;
- full Compose flow: Auth login -> Gateway -> Flash Sale -> Redis/PostgreSQL -> Kafka/Registry.

Correctness gates are more important than claiming a machine-independent RPS number:

```text
accepted <= allocated quantity
remaining >= 0
accepted + released + remaining reconciles to allocated quantity
one logical result per idempotency key/request hash
user accepted quantity <= purchase limit
one durable reservation and PurchaseAccepted event identity per winner
```

Add ArchUnit rules once packages exist so domain/application cannot depend on adapters or framework
types.

## 18. Implementation sequence

Do not implement this whole document as one large unreviewed change. After decisions D1-D8 are
resolved, create and approve Feature 019 artifacts, then use these coherent slices:

1. **Foundation** — dependencies, configuration properties, datasource/Redis/Kafka/Registry/JWT,
   Compose environment, Liquibase baseline, health checks, and architecture tests.
2. **Campaign projection** — approved Avro consumer, Redis projection model/Lua, duplicate/version
   safety, and snapshot-recovery port/client.
3. **Atomic reservation** — public contract, Gateway route/security, Redis key factory and Lua,
   shared response/error handling, and concurrency tests.
4. **Durable handoff** — Redis Stream entry/consumer group, PostgreSQL purchase/reservation model,
   same-transaction outbox, and crash-window tests.
5. **Kafka publication** — approved `PurchaseAcceptedV1` schema/topic/Registry rollout and outbox
   relay with retry/requeue.
6. **Query and expiry** — owner-only read API plus approved expiry/release behavior.
7. **Portfolio evidence** — full Compose failure smoke, k6 scenarios, dashboards/metrics, README
   architecture explanation, and recorded benchmark environment.

Each slice must keep the module build green. Production code begins only after the Spec Kit
`spec.md`, contracts, ADR where required, `plan.md`, and `tasks.md` are approved.

## 19. Deliberate simplifications

The proposal intentionally omits:

- Redis Cluster and stock-counter sharding until a benchmark proves a bottleneck;
- Redisson and distributed locking because Lua already owns atomicity;
- a generic Saga framework because this MVP only produces the first purchase event;
- separate top-level recovery and expiration feature trees;
- a shared domain/event Java model across services;
- a second Redis instance for local/VPS deployment;
- Resilience4j, Debezium, Kafka transactions, and Spring Modulith without an approved need;
- an Inventory/Product/Order/Payment call in the hot path.

This keeps the project interview-worthy through correctness, failure recovery, contract-first
Kafka, and measurable performance without turning a personal project into a platform project.

## 20. Governing references

- [Flash Sale end-to-end flow](flash-sale-end-to-end-flow.md)
- [Saga messaging and reliability](saga-messaging-reliability.md)
- [Outbox flow](outbox-flow.md)
- [Service communication protocols](service-communication-protocols.md)
- [Kafka topic and message catalog](../kafka/04-topic-message-catalog.md)
- [Avro data-contract governance](../kafka/02-avro-data-contract-governance.md)
- [Reliable Kafka producer/consumer integration](../kafka/03-reliable-producer-consumer-integration.md)
- [Feature 017 Campaign lifecycle contract](../../specs/017-campaign-management-mvp/contracts/campaign-lifecycle-events.md)
- [Feature 017 internal Campaign snapshot contract](../../specs/017-campaign-management-mvp/contracts/internal-campaign-snapshot-http.md)
- [Feature 017 service-authentication contract](../../specs/017-campaign-management-mvp/contracts/service-authentication.md)
