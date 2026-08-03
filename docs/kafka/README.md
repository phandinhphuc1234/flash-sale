# Kafka and Data Contract Guide

## Purpose and authority

This folder is the entry point for implementing Kafka, Confluent Schema Registry, Avro contracts,
outbox publication, and idempotent consumption in the Flash Sale monorepo.

It is an architecture and implementation guide. It does not approve a topic, schema, retry count,
retention period, DLT policy, or business event by itself. The governing order remains:

1. `.specify/memory/constitution.md`;
2. the owning feature's approved `spec.md`, `plan.md`, `tasks.md`, and contracts;
3. an accepted ADR when a repository boundary or technology decision changes;
4. these guides.

## Current repository state

| Capability | Current state |
|---|---|
| Kafka runtime | One `apache/kafka:4.0.0` KRaft node acting as broker and controller |
| Schema Registry | One `confluentinc/cp-schema-registry:8.3.0` instance |
| Local durability | Kafka data uses the `kafka-data` Docker volume |
| Schema storage | Schema Registry owns the compacted internal `_schemas` topic with RF `1` |
| Schema format | Avro selected in the Feature 017 amendment draft; runtime adoption pending approval |
| Avro contract module | Draft source path created; Maven module and generated types are still pending approval |
| Approved Kafka topic | `campaign.lifecycle.v1` from Feature 017 |
| Approved Campaign wire format | Existing baseline is JSON; Avro amendment is not approved for production yet |
| Candidate Saga topics | Documented but not approved for production code |

The current one-node topology is appropriate for local learning, integration tests, and a personal
MVP. It has no Kafka high availability: losing the node stops messaging and Schema Registry access.

## Guide map

1. [Local Kafka and Schema Registry](01-local-kafka-schema-registry.md)
   explains the actual Compose topology, addresses, image/version policy, smoke checks, topic
   provisioning, security boundaries, and the later production/Kubernetes direction.
2. [Avro Data Contract Governance](02-avro-data-contract-governance.md)
   explains schema-first SpecificRecord generation, contract ownership, subject naming,
   compatibility, versioning, CI registration, and rollout order.
3. [Reliable Producer and Consumer Integration](03-reliable-producer-consumer-integration.md)
   explains adapter placement, PostgreSQL outbox, Redis Stream handoff, inbox deduplication,
   retries, DLTs, tracing, and the required test matrix.

Related system-level material:

- [Service communication protocols](../architecture/service-communication-protocols.md)
- [Flash Sale end-to-end flow](../architecture/flash-sale-end-to-end-flow.md)
- [Saga messaging and reliability](../architecture/saga-messaging-reliability.md)
- [Schema Registry foundation](../architecture/schema-registry.md)

## Recommended project decisions

The following are the selected defaults for the Feature 017 Avro amendment. They become runtime
rules only after the amended artifacts and ADR 0016 are approved:

| Concern | Recommended direction |
|---|---|
| Contract style | Avro schema-first with generated SpecificRecord classes |
| Contract sharing | One protocol-only Maven artifact; never a shared domain library |
| Value subject naming | `TopicRecordNameStrategy` when one topic family carries several record types |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Stable environments | `auto.register.schemas=false`; register through controlled delivery tooling |
| Kafka key | Stable string aggregate/workflow ID |
| Producer reliability | Local business transaction plus PostgreSQL transactional outbox |
| Flash Sale hot path | Redis Lua plus Stream recovery handoff, then PostgreSQL journal/outbox |
| Consumer reliability | Inbox/deduplication, business mutation, and result outbox in one local transaction |
| Trace context | W3C `traceparent`/`tracestate` in Kafka headers through Micrometer/OpenTelemetry |

## Adoption sequence

```text
K0  Keep local Kafka + Schema Registry healthy
K1  Approve Avro adoption and create the contract build module
K2  Add one approved producer contract and compatibility tests
K3  Publish through a PostgreSQL outbox relay
K4  Add one idempotent consumer and its inbox transaction
K5  Add retry/DLT/operator recovery defined by that feature
K6  Expand to Purchase Saga only after the simple flow is green
```

Feature 017's previous baseline approved exact JSON serialization tests. The current amendment
replaces that wire contract with Avro only after re-approval; until then no service may silently
switch serializers or publish to the topic.

## Fast local checks

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d kafka schema-registry
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml ps kafka schema-registry
curl http://localhost:8081/subjects
```

Expected Registry output before any schema is registered:

```json
[]
```
