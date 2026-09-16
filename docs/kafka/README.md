# Kafka and Data Contract Guide

This section explains the implemented Kafka/Schema Registry foundation and the rules for extending
it. Approved feature artifacts and executable Avro schemas remain authoritative over prose.

## Current implementation

| Capability | State |
|---|---|
| Local broker | One Apache Kafka 4 KRaft node (broker + controller) |
| Schema Registry | One Confluent Schema Registry instance using Kafka `_schemas` |
| Wire format | Avro schema-first generated `SpecificRecord` values |
| Subject naming | `TopicRecordNameStrategy` for multi-record topics |
| Stable compatibility | Controlled registration with `BACKWARD_TRANSITIVE` |
| Producer reliability | Service-owned PostgreSQL transactional outbox |
| Flash Sale handoff | Redis Lua + Redis Stream, then PostgreSQL journal/outbox |
| Consumer reliability | Durable/idempotent mutation, bounded retry, owned DLT |
| Implemented families | Campaign lifecycle, purchase, payment, Order, regular Inventory hold, Cart reconciliation |

The one-node local/cloud internship topology has no Kafka high availability. Replication factor one
is a cost/learning trade-off, not a production recommendation.

## Guide map

1. [`01-local-kafka-schema-registry.md`](01-local-kafka-schema-registry.md) — runtime topology,
   addresses, persistence, and local troubleshooting.
2. [`02-avro-data-contract-governance.md`](02-avro-data-contract-governance.md) — schema evolution,
   subject naming, compatibility, and rollout order.
3. [`03-reliable-producer-consumer-integration.md`](03-reliable-producer-consumer-integration.md) —
   ports/adapters, outbox, idempotency, retry, DLT, and trace propagation.
4. [`04-topic-message-catalog.md`](04-topic-message-catalog.md) — implemented and candidate topics,
   owners, keys, messages, and Saga order.
5. [`../../contracts/kafka-avro-contracts/README.md`](../../contracts/kafka-avro-contracts/README.md)
   — executable schema module.

## Core rules

- A command asks one owner to act; an event reports a committed fact.
- A Kafka record is not a remote database row and must not expose JPA/domain/provider objects.
- Producer state plus required publication commits locally through an outbox.
- Consumers are idempotent and distinguish transient delivery failure from business rejection.
- DLT presence is diagnostic evidence, not a business outcome.
- Stable aggregate/workflow identity is the Kafka key; correlation and causation remain explicit.
- W3C trace headers may cross Kafka; JWTs and secrets never do.

## Local checks

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d kafka schema-registry
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml ps kafka schema-registry
Invoke-RestMethod http://localhost:8081/subjects
.\mvnw.cmd -pl contracts/kafka-avro-contracts -am verify
```

Kafka being `Running` does not prove a consumer is processing. When a flow stalls, inspect the
producer outbox, topic end offset, consumer group lag, consumer logs/DLT, and downstream inbox/state
in that order.

## Adding a contract

1. Approve the owner, semantic outcome, key, consumers, retry/DLT, and recovery rule.
2. Add/evolve the Avro schema and compatibility fixtures.
3. Map between service-owned domain/application types and `SpecificRecord` only in Kafka adapters.
4. Update topic/subject provisioning idempotently.
5. Deploy compatible consumers before new producers when required.
6. Record local and cloud evidence separately.

Do not create every candidate topic pre-emptively. Candidate Product lifecycle and Campaign
end/cancellation families in the catalog remain unimplemented until an owning feature approves
them.
