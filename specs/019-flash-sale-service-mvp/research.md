# Feature 019 Research: Flash Sale Service MVP

**Status**: Complete for plan approval  
**Date**: 2026-08-10  
**Scope**: Technical decisions needed to implement the approved Feature 019 specification

## Decision 1: Atomic admission uses Redis Lua

**Decision**: Execute Campaign-window checks, Variant validation, available-quota decrement,
per-user purchase-limit increment, stable identity/idempotency recording, expiry indexing, and Redis
Stream `XADD` in one Lua script.

**Rationale**: Redis executes a Lua script atomically. One script makes the quota and recovery
handoff indivisible, so an accepted Redis winner cannot exist without a recoverable Stream entry.
This is the minimal mechanism that satisfies the no-oversell and crash-window requirements.

**Alternatives rejected**:

- Java read/check/write sequence: concurrent callers can oversell between commands.
- Redis transaction assembled by application code: conditional business decisions become harder to
  reason about and still need a server-side script for an exact decision.
- PostgreSQL row locking on the hot path: correct but serializes the traffic burst on the durable
  database and contradicts the approved Redis hot-path design.
- Redisson distributed locks: adds a dependency and per-request lock lifecycle without improving on
  a single atomic Lua operation.

## Decision 2: Redis Stream bridges a winner to durable acceptance

**Decision**: The Lua script appends each winner to one `fs:{hot}:handoff` Stream. The request thread
immediately attempts the durable persistence use case; a consumer group retries abandoned or failed
work, and `XAUTOCLAIM` reclaims entries left pending after a process crash.

**Rationale**: Publishing directly from Java after Lua introduces a crash window. A Stream entry
created by the same atomic script survives process failure and can be replayed. One global stream is
appropriate for the approved single Redis instance and avoids pretending the MVP is Redis
Cluster-ready.

**Operational settings**:

| Setting | Value |
|---|---|
| Stream | `fs:{hot}:handoff` |
| Consumer group | `flashsale-durable-acceptance-v1` |
| Poll block | 1 second |
| Batch | 100 |
| Pending reclaim idle | 30 seconds |
| Reclaim batch | 100 |
| Trimming | Disabled while entries may be pending |
| Completion | Atomic `XACK` + `XDEL` Lua after terminal PostgreSQL outcome |

**Alternatives rejected**:

- Java sends Kafka immediately after Lua: can lose the accepted purchase when the process crashes.
- Redis Pub/Sub: has no durable replay or consumer pending list.
- A second Redis instance: unnecessary operational scope for the personal-project MVP.
- Kafka inside Lua: impossible; Lua cannot perform external network calls.
- Kafka Connect/Debezium: not needed for a Redis-to-PostgreSQL command handoff and adds a standalone
  connector platform absent from Feature 019.

## Decision 3: PostgreSQL is the terminal outcome arbiter

**Decision**: A stable `purchaseRequestId` is created before Lua. Both the request thread and Stream
worker call one idempotent PostgreSQL transaction. Unique identities and row locking serialize
`ACCEPTED` versus `EXPIRED`; once `EXPIRED` commits, late persistence must never create a reservation
or accepted outbox event.

**Rationale**: Redis decides scarce quota quickly, but the constitution makes PostgreSQL the durable
source of truth. A terminal durable record resolves the otherwise ambiguous race between a database
recovery and the five-minute expiry worker.

**Expiry ordering**:

1. At `expiresAt`, the reservation is no longer purchaseable.
2. The worker records/observes the terminal PostgreSQL outcome first.
3. If `EXPIRED` wins, an idempotent Redis Lua script restores quota exactly once and removes the
   expiry index entry.
4. If `ACCEPTED` already won, the durable reservation may transition from `RESERVED` to `EXPIRED`,
   then the same Redis release is applied. Feature 019 emits no expiry Kafka event.
5. PostgreSQL outage delays physical Redis restoration, but never permits a late accepted event.

**Alternatives rejected**:

- Restore Redis before durable expiry: a crash can restore quota while a late persistence thread
  still commits `ACCEPTED`, creating oversell.
- Treat Redis as final truth: violates the repository durability rule and loses auditability.
- Distributed transaction/2PC: unsupported across Redis and PostgreSQL and disproportionate for the
  MVP.

## Decision 4: Transactional outbox publishes PurchaseAccepted

**Decision**: Insert the purchase, reservation, idempotency record, and one
`PurchaseAcceptedV1` outbox row in the same PostgreSQL transaction. A 500 ms polling relay claims up
to 100 rows using a lease and `FOR UPDATE SKIP LOCKED`, publishes sequentially, and retries the same
event identity with exponential backoff capped at 60 seconds.

**Rationale**: A database commit followed by direct Kafka send has another dual-write crash window.
The outbox gives at-least-once publication without Kafka transactions and is already the repository's
approved pattern.

**Failure policy**: Kafka or Schema Registry unavailability leaves the row durable. Retry is
indefinite in this MVP; backlog count, oldest age, attempts, and last sanitized error are observable.
No terminal DLT or administrative replay API is invented because the approved spec defines neither
authorization nor retention for those operations.

**Alternatives rejected**:

- Direct Kafka send in the web transaction: cannot atomically commit with PostgreSQL.
- Kafka transactions: do not atomically include PostgreSQL and add operational complexity.
- Debezium outbox/Kafka Connect: viable later, but introduces a new standalone service and deployment
  topology beyond Feature 019.

## Decision 5: Idempotency is explicit and privacy-safe

**Decision**: The identity is `(userId, campaignId, SHA-256(idempotencyKey))`. The raw key is never
stored or logged. A canonical SHA-256 request hash covers user, Campaign, Variant, and quantity. Keys
are case-sensitive, nonblank, and limited to 128 characters at the HTTP boundary.

**Rationale**: The approved scope prevents one user's key from affecting another Campaign or user.
Stable hashes support safe comparison and diagnostics without persisting client secrets. The first
winner's `purchaseRequestId` and `reservationId` are replayed for identical requests.

**Retention**: Keep the replay record until Campaign end plus 24 hours, then allow reuse as a new
command. Cleanup never deletes the durable purchase/reservation audit.

**Alternatives rejected**:

- Global idempotency key: accidental cross-user/campaign collisions.
- Raw key storage/logging: unnecessary sensitive data exposure.
- Request-body equality using JSON text: unstable field order/formatting.

## Decision 6: Campaign projection is event-driven with snapshot recovery

**Decision**: Consume only `CampaignScheduledV1` and `CampaignActivatedV1` from
`campaign.lifecycle.v1`. An idempotent Redis script applies newer aggregate versions and ignores
duplicate/stale records. Activation never initializes missing quota; it records a recovery marker.
A five-second recovery scheduler calls Campaign's approved snapshot endpoint with OpenFeign and
OAuth2 Client Credentials.

**Rationale**: Kafka keeps the purchase path local and cheap. The snapshot is a control-plane repair
mechanism when an event is missing or state is stale, not a fallback inside a shopper request.

**Consumer failure policy**: Disable auto-commit and acknowledge only after Redis succeeds. Permit
three total delivery attempts with bounded 250 ms and 500 ms delays; then stop the affected listener
with its offset uncommitted and expose an unhealthy projection/readiness signal. The operator repairs
the dependency/schema and restarts/replays. No DLT is added in this MVP.

**Alternatives rejected**:

- OpenFeign lookup on every reservation: turns Campaign into a hot-path dependency.
- Activation initializes quota: can open a Campaign without the scheduled allocation snapshot.
- Infinite tight consumer retries: stalls a partition and burns CPU without an operational signal.

## Decision 7: Synchronous internal HTTP uses OpenFeign

**Decision**: Reuse `GET /internal/v1/campaigns/{campaignId}/snapshot` through an outbound port and a
Feign adapter. Use connect timeout 500 ms, read timeout 1,000 ms, no Feign retry, and a short-lived
`flashsale-service` client-credentials token with scope `campaign.snapshot.read` and audience
`flash-sale-internal-api`.

**Rationale**: This matches the repository's typed service-to-service HTTP convention. Retries belong
to the recovery scheduler so they do not multiply inside Feign or run while a database transaction is
open.

**Alternatives rejected**:

- `RestTemplate`: older imperative client style and inconsistent with the current repo decision.
- WebClient: adds reactive complexity to a servlet service for one control-plane call.
- gRPC: no approved protobuf contract or operational need for this low-frequency recovery call.

## Decision 8: Avro SpecificRecord and Schema Registry govern PurchaseAccepted

**Decision**: Add `PurchaseAcceptedV1.avsc` under the topic-owned directory in
`contracts/kafka-avro-contracts`. Generate Java `SpecificRecord`; use Confluent serializer,
`TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE`, and `auto.register.schemas=false` outside developer
bootstrap.

**Rationale**: Compile-time types and compatibility checks match the existing Campaign event
standard. The contract module remains protocol-only and generated classes do not leak into domain or
application code.

**Alternatives rejected**:

- JSON string: no selected schema compatibility enforcement.
- `GenericRecord` throughout application code: transport schema leaks across boundaries and loses
  ergonomic type safety.
- Reflection Avro/domain serialization: domain changes could silently become wire changes.
- JPA entity as payload: couples database and event contracts.

## Decision 9: Servlet virtual threads are not a hot-path correctness mechanism

**Decision**: Keep the Spring MVC service and allow the repository's Spring Boot 3 virtual-thread
runtime setting where already standardized. Redis Lua correctness and bounded executor/container
settings remain authoritative; no custom thread pool is added until measurements require it.

**Rationale**: Virtual threads help waiting work scale but do not make compound Redis operations
atomic, remove connection-pool limits, or replace Kafka/Redis backpressure.

## Decision 10: OpenTelemetry semantics now, export topology later

**Decision**: Add `micrometer-tracing-bridge-otel`, use Micrometer Observation and W3C propagation,
and correlate safe logs through MDC. Keep OTLP exporter/Collector and centralized log shipping out of
Feature 019.

**Rationale**: The feature needs meaningful traces and propagation, while the repository deliberately
keeps the shared observability stack under root infrastructure. Adding an exporter without an
approved collector deployment would create half-configured production behavior.

**Alternatives rejected**:

- Business code calling OpenTelemetry SDK directly: technology coupling and duplicated
  instrumentation.
- Java-created Prometheus registry: prohibited by repository rules; Boot auto-configuration is
  sufficient.

## Decision 11: Real infrastructure tests are mandatory for concurrency claims

**Decision**: Use real Redis and PostgreSQL containers for Lua/concurrency and locking tests, and a
Kafka + Schema Registry profile for Avro/compatibility/recovery tests. Use ArchUnit for layer
boundaries and k6 for measured hot-path evidence.

**Rationale**: In-memory mocks cannot validate Redis atomicity, Stream pending recovery, PostgreSQL
locking/unique constraints, or real wire serialization. Load evidence must name the environment and
must not be presented as a universal SLA.

## Source References

- [Redis Lua atomic execution](https://redis.io/docs/latest/develop/programmability/eval-intro/)
- [Redis Streams](https://redis.io/docs/latest/develop/data-types/streams/)
- [Redis XAUTOCLAIM](https://redis.io/docs/latest/commands/xautoclaim/)
- [Spring Data Redis Streams](https://docs.spring.io/spring-data/redis/reference/redis/redis-streams.html)
- [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Cloud OpenFeign](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)
- [Spring Boot tracing](https://docs.spring.io/spring-boot/3.5/reference/actuator/tracing.html)
- [Spring Boot observability](https://docs.spring.io/spring-boot/3.5/reference/actuator/observability.html)
- [PostgreSQL INSERT / ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html)
- [ArchUnit](https://www.archunit.org/)
