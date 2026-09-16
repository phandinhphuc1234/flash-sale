# Cross-Service Contracts

This directory contains repository-owned contracts that cross service boundaries. A contract is not
a shared domain model: each service maps it to its own application/domain types.

## Contents

- [`kafka-avro-contracts/`](kafka-avro-contracts/README.md) — executable Avro schemas and generated
  Java `SpecificRecord` classes.
- `asyncapi/` — reserved for an approved AsyncAPI publication; currently no generated catalog.
- `openapi/` — reserved for exported HTTP specifications. Runtime OpenAPI is service-owned and
  locally aggregated by Gateway.
- `events/` — historical/reserved catalog folders; implemented Kafka authority is the Avro module.

## Change rules

- Update the owning feature contract before changing an HTTP or Kafka boundary.
- Prefer backward-compatible additive Avro changes; compatibility is verified against fixtures and
  Schema Registry policy.
- Never publish JPA entities, web DTOs, provider SDK objects, or unversioned maps as Kafka values.
- Keep trace context in headers; never put JWTs, credentials, or secrets into event records.
- Producers own semantic correctness; consumers own idempotency, retry, and DLT handling.

See the [Kafka topic/message catalog](../docs/kafka/04-topic-message-catalog.md) for ownership and
runtime topic names.
