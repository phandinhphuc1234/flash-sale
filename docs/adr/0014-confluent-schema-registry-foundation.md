# ADR 0014: Local Confluent Schema Registry Foundation

## Status

Accepted for local development infrastructure.

## Context

The repository already provisions a single-node Apache Kafka broker, while future Kafka features
will publish versioned integration events. A centralized schema service is useful for validating
event compatibility, but no current service should become coupled to it before an event contract
is approved.

Feature 017's Avro adoption details are proposed separately in
[ADR 0016](0016-avro-schema-registry-campaign-lifecycle.md). This ADR remains the local platform
foundation and does not by itself authorize Avro in a service.

## Decision

Add one root-owned Confluent Schema Registry container to `infra/docker/compose.yml`:

- image: `confluentinc/cp-schema-registry:8.3.0`;
- internal Kafka bootstrap: `PLAINTEXT://kafka:9092`;
- internal listener: `http://schema-registry:8081`;
- host binding: loopback `127.0.0.1:8081` by default;
- single-node schema topic replication factor: `1`;
- readiness depends on the existing Kafka health check.

The registry stores metadata in Kafka's compacted `_schemas` topic. Application services do not
depend on this container until a separately approved Kafka serialization task defines format,
subject naming, compatibility, and serializer dependencies.

## Alternatives considered

- No registry: simpler now, but every future producer/consumer must manage compatibility alone.
- Apicurio Registry: viable alternative, but it introduces a second platform choice without a
  current project requirement.
- Confluent Schema Registry: selected because it is a widely used Kafka ecosystem component and
  supports the future Avro, Protobuf, and JSON Schema adoption paths.

## Consequences

Positive:

- local developers can validate the registry and Kafka topology before event implementation;
- schema metadata has durable Kafka-backed storage;
- service code remains unchanged until a contract explicitly adopts a serializer.

Trade-offs:

- one additional local container and image pull;
- compatibility policy remains undefined until the first event feature;
- this local plaintext setup is not a production security configuration.

## Validation

```powershell
docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml config
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml up -d kafka schema-registry
curl http://localhost:8081/subjects
```
