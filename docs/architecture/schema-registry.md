# Confluent Schema Registry Foundation

> Implementation guide: start at
> [Kafka and Data Contract Guide](../kafka/README.md), then use the dedicated local-platform,
> Avro-governance, and reliable-integration documents linked there.

## Scope

The repository provides a local Confluent Schema Registry alongside the existing single-node
Apache Kafka broker. Feature 017 approves Avro schema-first lifecycle contracts. Runtime service
serializer wiring remains owned by the feature implementation and must use generated
`SpecificRecord` classes with Confluent serializers.

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

For Java services, the approved adapter rule is:

```text
.avsc -> generated SpecificRecord -> KafkaAvroSerializer/KafkaAvroDeserializer
```

Do not use `GenericRecord` throughout application code, reflection-generated schemas, JSON string
values, or JPA entities as Kafka contracts. Schema Registry wire data contains a schema ID and
Avro binary payload; consumers resolve the schema by ID.

Schema Registry does not replace the repository's versioned event contracts, outbox, or idempotent
consumer requirements.

The current Compose addresses are `kafka:9092` and `http://schema-registry:8081` inside Docker,
and `localhost:29092` and `http://localhost:8081` from the host. Feature 017 uses Avro
SpecificRecords, `TopicRecordNameStrategy`, and `BACKWARD_TRANSITIVE`; Registry availability alone
does not replace the feature's outbox and idempotent-consumer requirements.
