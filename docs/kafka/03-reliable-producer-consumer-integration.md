# Reliable Producer and Consumer Integration

## 1. Scope

Schema Registry validates wire structure and evolution. It does not guarantee exactly-once business
effects, valid state transitions, payment idempotency, stock correctness, or database/Kafka atomicity.

This guide defines the repository's target reliability boundaries. Exact timeouts, attempts,
backoff, retention, TTL, compensation, and replay authorization remain owning-feature decisions.

## 2. Producer boundary for PostgreSQL services

PostgreSQL-backed services must not perform this dual write:

```text
repository.save(aggregate)
kafkaTemplate.send(record)
```

The database commit may succeed while the Kafka call is lost, or Kafka may accept the record while
the database transaction later fails.

Use a transactional outbox:

```text
local database transaction
├── change business state
└── insert immutable outbox row
COMMIT

outbox relay
├── claim due rows with a lease
├── map stored payload to generated Avro record
├── publish with stable message ID and Kafka key
└── after broker acknowledgement mark PUBLISHED
```

The relay must not call Schema Registry or Kafka while holding the original business transaction.
Serialization and publication happen after the durable state/outbox commit.

An outbox row commonly needs:

```text
message_id
message_type
message_version
aggregate_id
aggregate_version
message_key
correlation_id
causation_id
payload
publish_status
attempt_count
next_attempt_at
lease_owner / lease_until
occurred_at / created_at / published_at
sanitized_last_error
```

The exact table is service-owned. Do not share a JPA entity or repository across services.

## 3. Producer adapter placement

```text
<capability>/domain/event/                         # domain fact, when appropriate
<capability>/application/port/out/                 # publish/persist capability
<capability>/adapter/out/messaging/kafka/          # Avro mapper and Kafka publisher
outbox/application/                                # relay/recovery use cases
outbox/adapter/out/persistence/jpa/                # service-owned outbox persistence
outbox/adapter/in/scheduling/                      # relay trigger only
<service>/configuration/                           # Kafka producer/runtime wiring
```

The scheduler drives a use case; it does not contain publication policy or business transitions.
The Kafka adapter knows topics, headers, serializers, and generated records. Domain/application
code does not know `KafkaTemplate`, Schema Registry, or SpecificRecord.

## 4. Flash Sale Redis hot-path boundary

Do not implement:

```text
Redis Lua succeeds
  -> Java sends directly to Kafka
```

A crash between those steps loses the purchase handoff. Also do not treat a single Redis Stream as
the only durable business truth because the repository Constitution assigns durable truth to
PostgreSQL.

Use:

```text
Redis Lua atomically
├── validate campaign/quota/user/idempotency
├── mutate reservation/quota
└── XADD recovery handoff entry

Stream consumer
  -> invoke idempotent Flash Sale persistence use case
     ├── insert/load durable purchase/reservation journal
     └── insert/load PostgreSQL outbox row
  -> COMMIT
  -> XACK Stream entry

PostgreSQL outbox relay
  -> Kafka
```

The purchase feature must define Stream trimming, pending-entry recovery, Redis persistence,
reservation expiry, reconciliation, and the point at which HTTP may safely return `202 Accepted`.

## 5. Consumer transaction boundary

A database-backed consumer processes one message as one local transaction:

```text
Kafka generated Avro record
  -> inbound Kafka mapper
  -> application command

BEGIN
  insert (consumer_name, message_id) into processed_messages
  if duplicate: preserve the already established outcome
  validate and apply the business transition
  insert result/next-command outbox row when required
COMMIT

acknowledge/commit Kafka offset
```

Required database constraints make duplicate detection multi-instance safe. A memory cache is not
an inbox. The deduplication insert, business mutation, and required result outbox must not be three
unrelated transactions.

## 6. Consumer adapter placement

```text
<capability>/adapter/in/messaging/kafka/
├── <Message>KafkaListener.java
├── <Message>KafkaMapper.java
└── contract-specific failure translation

<capability>/application/
├── command/
├── port/in/
└── usecase/

<capability>/adapter/out/persistence/jpa/
└── atomic inbox + state + result-outbox persistence capability
```

The listener handles deserialization/broker concerns and invokes the input port. It must not contain
domain decisions, JPA queries, or cross-service orchestration.

## 7. Kafka keys and ordering

Use a stable string key:

| Message family | Proposed key |
|---|---|
| Product lifecycle | `variantId` |
| Campaign lifecycle/workflow | `campaignId` |
| Campaign stock operation | approved `campaignId:variantId` representation |
| Purchase before Order exists | `purchaseRequestId` |
| Order/Payment Saga after Order creation | `orderId` |

Kafka preserves order only within one partition. Correlation ID reconstructs a workflow but does
not automatically choose the partition. A transition from `purchaseRequestId` to `orderId` must be
explicit in the approved Purchase Saga contract.

## 8. Producer configuration direction

The service's Spring Boot BOM should manage Kafka clients. An approved producer plan should include:

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      acks: all
      properties:
        enable.idempotence: true
        schema.registry.url: ${SCHEMA_REGISTRY_URL:http://schema-registry:8081}
        auto.register.schemas: false
        value.subject.name.strategy: io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
```

This is a target example, not configuration to paste into every service. A consumer-only service
does not need producer settings, and a service without an approved Avro contract must not gain the
serializer dependency.

Kafka producer idempotence reduces duplicate writes caused by producer retries. It does not replace
the database outbox or consumer idempotency.

## 9. Consumer configuration direction

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
    consumer:
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        schema.registry.url: ${SCHEMA_REGISTRY_URL:http://schema-registry:8081}
        specific.avro.reader: true
```

The owning feature must choose listener acknowledgement mode together with its database transaction,
retry, batch, and error-handler design. Do not copy `ack-mode` blindly between consumers.

## 10. Failure classification

| Failure | Direction |
|---|---|
| Temporary broker/network/Registry outage | Bounded technical retry with durable state |
| Database deadlock/transient availability | Roll back local processing and retry safely |
| Business rejection | Persist/emit the approved business outcome; do not retry forever |
| Invalid/unreadable schema or poison record | Stop normal processing and route under approved DLT policy |
| Duplicate delivery | Return the established idempotent outcome without repeating effects |
| Stale aggregate version | Apply approved ignore/reconcile/failure policy |
| Ambiguous payment provider result | Query/reconcile provider state before another charge |

A DLT record is an operational failure, not a compensation event. The feature must define who may
inspect and replay it, how secrets are redacted, and how the record re-enters normal processing.

## 11. Trace and observability

Propagate W3C context in Kafka headers:

```text
traceparent
tracestate              # when present/approved
approved baggage only
```

Micrometer Observation/Tracing and OpenTelemetry should create producer/consumer spans and restore
the logging context. Keep trace IDs out of the JSON/Avro business body under the repository's
header-only trace policy.

Record low-cardinality metrics for:

- publish success/failure and latency;
- outbox backlog and oldest pending age;
- consumer lag and processing latency;
- duplicate/stale message counts;
- retry and DLT counts;
- Redis Stream pending entries;
- Saga state age and manual-review count.

Never use message ID, Order ID, Campaign ID, user ID, or trace ID as metric tags.

## 12. Required test layers

### Contract tests

- exact SpecificRecord fields and logical types;
- serialization/deserialization through Schema Registry-compatible tooling;
- compatibility against every prior schema under the subject;
- no secrets in payload or headers;
- stable message ID, key, and trace headers.

### Producer/outbox integration tests

- business commit while Kafka is unavailable;
- relay recovery and stable message ID;
- crash after Kafka acknowledgement before outbox acknowledgement;
- ordering for two versions of the same aggregate;
- multiple relay instances claiming safely.

### Consumer/inbox integration tests

- duplicate delivery before and after restart;
- crash after database commit before Kafka offset commit;
- concurrent duplicate processing by two instances;
- state mutation and result outbox commit atomically;
- poison/unreadable record handling without compensation.

### Flash Sale recovery tests

- Redis mutation followed by process crash before PostgreSQL acceptance;
- pending Stream entry reclaim;
- PostgreSQL commit followed by lost Stream acknowledgement;
- duplicate relay invocation;
- Redis rebuild/reconciliation from durable data.

Use real Kafka-compatible, PostgreSQL, and Registry test infrastructure where the behavior cannot
be proved by mocks.

## 13. Recommended first implementation

The safest first production path is the Campaign lifecycle producer because its owner, topic, key,
event identity, ordering, and outbox behavior are specified. The approved Feature 017 amendment
now selects Avro SpecificRecords for this path; generated contracts and compatibility gates are
present, while live publisher/Registry smoke evidence is tracked by later runtime tasks.

After one producer/outbox path is green, create a separate Flash Sale projection-consumer feature
with inbox idempotency. Do not begin with the complete Payment/Purchase Saga because that combines
Kafka, Avro, outbox, Redis Lua, payment ambiguity, and compensation at once.
