# Confluent Schema Registry Foundation

## Scope

The repository provides a local Confluent Schema Registry alongside the existing single-node
Apache Kafka broker. This is platform support only. No service currently depends on Schema Registry
for startup, serialization, or message publication.

## Local topology

```text
application services (future Kafka serializers)
                  |
                  v
        http://schema-registry:8081
                  |
                  v
             kafka:9092
                  |
                  v
        compacted topic: _schemas
```

From the host, the registry is available at `http://localhost:8081` and the basic smoke check is:

```powershell
curl http://localhost:8081/subjects
```

The expected initial response is an empty JSON array (`[]`).

## Configuration ownership

- Compose service and image pin: `infra/docker/compose.yml`.
- Local overrides: `infra/docker/.env` (ignored by Git).
- Safe placeholders: `infra/docker/.env.example`.
- Kafka broker owns the transport; Schema Registry owns schema metadata.
- Individual services must not access the `_schemas` topic directly.

## Adoption rule

An approved Kafka feature must define the following before adding a service serializer or deserializer:

1. topic and event version;
2. schema format (Avro, Protobuf, or JSON Schema);
3. subject naming strategy and compatibility mode;
4. producer/consumer ownership and rollout compatibility;
5. failure, retry, duplicate, and trace-header behavior.

Schema Registry does not replace the repository's versioned event contracts, outbox, or idempotent
consumer requirements.
