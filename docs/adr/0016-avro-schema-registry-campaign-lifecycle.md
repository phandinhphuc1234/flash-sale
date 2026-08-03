# ADR 0016: Avro and Schema Registry for Campaign Lifecycle Events

**Status**: Accepted — Feature 017 amendment approved
**Date**: 2026-08-03  
**Owners**: Project owner; architecture reviewer  
**Scope**: Feature 017 first adopter; protocol baseline for future Kafka features

## Context

ADR 0014 established a local Confluent Schema Registry beside the single-node KRaft broker, but it
deliberately did not select a serialization format. Feature 017 currently defines JSON lifecycle
envelopes for `CampaignScheduled.v1` and `CampaignActivated.v1` on `campaign.lifecycle.v1`.

The project now wants schema-first contracts, compatibility checks, generated Java types, and
controlled schema registration before Campaign's Kafka publisher is implemented. This changes the
wire contract and introduces a shared protocol artifact; it is therefore an architecture and
dependency decision, not a local serializer refactor.

## Decision proposed

1. Use Avro schema-first contracts with generated `SpecificRecord` classes.
2. Use Confluent Schema Registry for runtime schema IDs and compatibility checks.
3. Keep `.avsc` sources in a root protocol-only Maven module, proposed at
   `contracts/kafka-avro-contracts/`.
4. Use `TopicRecordNameStrategy` for topic families that carry multiple record types.
5. Use `BACKWARD_TRANSITIVE` compatibility for the first approved contract baseline.
6. Set `auto.register.schemas=false` in stable environments. Registration is a controlled CI/release
   step, not application startup behavior.
7. Keep stable `eventId`, Campaign ID Kafka key, at-least-once outbox delivery, and idempotent
   consumers unchanged.
8. Propagate W3C `traceparent`/`tracestate` through Kafka headers. Do not put JWTs, secrets, raw
   authorization headers, or trace IDs in the Avro business payload.
9. Keep PostgreSQL `JSONB` outbox storage as an internal durable representation if useful; the
   outbox relay maps it to the generated Avro record outside the business transaction.

## Boundary rules

- Generated Avro types are allowed only at Kafka adapters and the protocol module.
- Campaign domain/application code uses owned commands, results, and domain events; it does not
  import Schema Registry, Kafka serializers, or generated wire types.
- A shared protocol artifact does not share JPA entities, domain models, or business rules.
- The already approved Campaign event names, topic, key, ordering, retry, and requeue semantics
  remain authoritative unless Feature 017's amendment changes them explicitly.

## Consequences

### Positive

- Compile-time typed producer and consumer contracts.
- Compatibility failures are caught in CI before a producer rollout.
- Schema history and runtime IDs are managed consistently.
- Future services can adopt the same protocol governance without copying domain models.

### Costs and risks

- A root Maven contract module and Confluent serializer dependencies are added.
- Backward-compatible changes require consumer-first rollout.
- Registry availability becomes part of serializer/deserializer operation; outbox retry must keep
  business transitions durable when Registry or Kafka is unavailable.
- Feature 017's exact JSON contract needs an explicit migration decision; no silent wire change is
  allowed.

## Alternatives rejected

- **Keep JSON forever**: preserves the current contract but does not provide the selected schema
  governance and generated type safety.
- **Generic JSON payload inside an envelope**: Registry validates only the envelope and cannot
  protect the business payload.
- **TopicNameStrategy for multi-record topics**: one subject per topic makes unrelated record types
  share compatibility history; it remains valid for a topic that intentionally carries one schema.
- **Auto-register on every application startup**: makes deployment order and schema ownership
  implicit and allows an application to mutate Registry state unexpectedly.
- **Avro types in domain/application**: couples the business core to the transport and violates the
  repository's Clean/Hexagonal boundary rules.

## Required follow-up implementation work

- Keep Feature 017 `spec.md`, `plan.md`, `tasks.md`, and
  `contracts/campaign-lifecycle-events.md` synchronized with this accepted decision.
- Add/approve the contract Maven module and dependency versions in the plan.
- Resolve the trace-body compatibility choice.
- Add schema syntax, generation, compatibility, serialization, Registry, producer, and consumer
  integration tests.
- Complete the protocol-module and runtime-adapter tasks before publishing to Kafka.
