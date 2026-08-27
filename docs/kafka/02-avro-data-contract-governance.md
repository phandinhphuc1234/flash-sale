# Avro Data Contract Governance

## 1. Status

Avro SpecificRecord is the repository's approved Kafka wire format. The protocol module remains
schema-first: Feature 017 established Campaign lifecycle contracts, Features 019–021 added the
accepted Purchase, Order, and Payment records, and Feature 044 adds reservation finalization plus
terminal/review-required Order records. Runtime Registry publication remains controlled and is not
performed by ordinary unit tests or application startup.

Feature 044 reuses `PaymentRequestedV1`, `PaymentSucceededV1`, and `PaymentFailedV1` unchanged and
adds eight new records. It uses `TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE` compatibility, and
controlled registration for eight main subjects plus six DLT topic/record bindings.

## 2. Contract model

Use:

```text
Avro schema-first
  -> avro-maven-plugin
  -> generated SpecificRecord Java classes
  -> Confluent KafkaAvroSerializer/KafkaAvroDeserializer
  -> Confluent Schema Registry
```

Do not use:

- JPA entities, domain aggregates, REST DTOs, or application commands as wire contracts;
- reflection-generated schemas from domain classes;
- `GenericRecord` throughout application/domain code;
- a generic string `payload` that hides arbitrary JSON from Schema Registry;
- one shared Java domain model across services.

### Java implementation rule

Every approved Java producer/consumer uses the generated `SpecificRecord` type for its Kafka
adapter boundary together with Confluent's `KafkaAvroSerializer` and `KafkaAvroDeserializer`.
`GenericRecord` is not an application-wide message model. Reflection-based Avro generation,
JSON strings as Kafka values, and JPA entities as Kafka contracts are prohibited. The serializer
stores a Schema Registry ID plus the Avro binary payload; the full schema is not duplicated in
each message.

The shared artifact is a protocol contract only. Each service maps between the generated record
and its service-owned application/domain model.

## 3. Proposed monorepo structure

The approved Feature 017 amendment implements this protocol-only module. Its Maven module,
deterministic generated sources, and Campaign publisher dependency are part of the root reactor:

```text
contracts/
└── kafka-avro-contracts/
    ├── pom.xml
    └── src/main/avro/com/philia/flashsale/contract/
        ├── campaign/
        │   ├── command/v1/
        │   └── event/v1/
        ├── purchase/
        │   ├── command/v1/
        │   └── event/v1/
        ├── payment/
        │   ├── command/v1/
        │   └── event/v1/
        ├── order/event/v1/
        ├── inventory/
        │   ├── command/v1/
        │   └── event/v1/
        └── product/event/v1/
```

The module produces:

```text
.avsc source of truth
  -> generated sources under target/
  -> kafka-avro-contracts.jar
  -> producer/consumer adapter dependencies
```

Generated source must never be committed if the approved build regenerates it deterministically.

## 4. Clean/Hexagonal placement

```text
Producer
domain/application result
  -> adapter/out/messaging/kafka mapper
  -> generated Avro SpecificRecord
  -> Kafka publisher

Consumer
generated Avro SpecificRecord
  -> adapter/in/messaging/kafka mapper
  -> application command
  -> use case/domain
```

Allowed locations:

```text
<feature>/adapter/out/messaging/kafka/
<feature>/adapter/in/messaging/kafka/
<service>/configuration/              # serializer/listener/wiring policy
```

Generated Avro types must not appear in `domain`, application use-case signatures, JPA entities,
REST controllers, or public HTTP DTOs.

## 5. One record per command or event

Prefer a separate record for each semantic message:

```text
CampaignScheduledV1
CampaignActivatedV1
PaymentRequestedV1
PaymentSucceededV1
ConfirmPurchaseReservationV1
```

Do not use an envelope containing an unvalidated string payload:

```json
{
  "messageType": "PaymentRequested",
  "payload": "{ arbitrary JSON }"
}
```

Logical metadata should remain consistent with the Saga guide:

| Field | Meaning |
|---|---|
| `messageId` | Stable deduplication identity; unchanged during technical retry |
| message type/version | Semantic dispatch and compatibility identity |
| `producer` | Observability only; never authentication |
| `correlationId` | Workflow/Saga identity |
| `causationId` | Message that caused this message |
| `aggregateId`/`aggregateVersion` | Aggregate traceability and optional stale-event protection |
| `occurredAt` | UTC business occurrence time |
| business fields | Typed payload owned by this command/event |

Whether each field is physically top-level or represented by an approved reusable Avro record is a
contract decision. Do not create a generic Java event envelope that application/domain code must
share.

W3C `traceparent` and `tracestate` belong in Kafka headers and are propagated by
Micrometer/OpenTelemetry. JWTs, cookies, HMAC secrets, payment credentials, and raw Authorization
headers must never appear in Kafka payloads, headers, logs, or traces.

## 6. Data type rules

Use explicit, portable representations:

| Business value | Avro direction |
|---|---|
| UUID | `string` with `uuid` logical type when tooling support is verified |
| UTC instant | `long` with `timestamp-millis` or one project-approved precision |
| Money | an approved exact representation (Feature 017 uses Avro decimal bytes, precision 19, scale 4, plus ISO currency); never `double` |
| Enum | Avro enum only when evolution rules are understood; otherwise approved stable string |
| Optional field | union with `null` and a default compatible with field order |

Adding a field without an appropriate default commonly breaks backward compatibility. Renaming a
field, changing its meaning, or changing an incompatible primitive type is not an additive change.

## 7. Topic and record versioning

Keep three version concepts separate:

```text
flashsale.payment.commands.v1
  = major topic generation

PaymentRequestedV1
  = major semantic record generation

Schema Registry subject version 1, 2, 3...
  = compatible revisions of the same record
```

An additive compatible field normally increments only the Registry subject version. A breaking
semantic change requires an approved `V2` record and possibly a `.v2` topic when coexistence,
retention, or routing requires it.

## 8. Subject naming strategy

The proposed Saga topic families contain multiple record types, for example payment success and
failure results on one payment-events topic. For that design, use:

```properties
value.subject.name.strategy=io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
```

Subject shape:

```text
<topic>-<fully-qualified-record-name>
```

This keeps compatibility history per record type within a topic. Confluent's default
`TopicNameStrategy` creates one `<topic>-value` subject and is simplest only when every value on the
topic conforms to one compatible schema family.

A DLT is a different topic, so it has a different subject even when it retains the original record
type. Feature 044 therefore registers its three DLT families explicitly; DLT serialization must not
depend on broker auto-creation or runtime schema auto-registration.

The feature contract must explicitly choose the strategy; client configuration and Registry/CI
registration must use the same subject names.

## 9. Source layout by topic family

The protocol module groups `.avsc` files by the Kafka topic that carries them:

```text
contracts/kafka-avro-contracts/src/main/avro/topics/<topic-name>/
```

For example, the currently approved lifecycle schemas are stored under
`topics/campaign.lifecycle.v1/`. This directory convention improves discovery and ownership
without changing the Avro namespace, record name, Schema Registry subject, or generated Java
package. Each event or command remains a separate schema file and generated `SpecificRecord`.

Only schemas approved by an owning feature may be added to a topic folder. The folder is not a
replacement for the feature contract, compatibility policy, or topic provisioning decision.

## 10. Compatibility policy

Recommended project baseline:

```text
BACKWARD_TRANSITIVE
```

It requires a new consumer schema to read records written by every registered older schema for the
subject. Confluent's default is `BACKWARD`, which checks only the latest prior version.

For backward-compatible evolution, rollout order is:

```text
1. Validate and register compatible schema
2. Deploy every consumer that understands the new revision
3. Confirm consumer health
4. Deploy the producer that writes the new revision
```

Typical rules:

| Change | Direction |
|---|---|
| Add optional/defaulted field | Usually compatible; prove with compatibility test |
| Add required field without default | Reject |
| Change `string` to `long` | Reject |
| Reuse a field with new meaning | Reject; introduce a semantic version |
| Delete/rename a field | Treat as breaking unless compatibility evidence proves otherwise |

## 11. Registration policy

Local experiments may temporarily use:

```properties
auto.register.schemas=true
```

The stable project direction is:

```properties
auto.register.schemas=false
```

The controlled flow is:

```text
schema committed to Git
  -> syntax validation and Java generation
  -> compatibility check
  -> review and merge
  -> controlled Registry registration
  -> consumer-first rollout
  -> producer rollout
```

Never edit a Registry schema only through a UI without committing the same source contract to Git.
Git is the contract source of truth; Registry is the runtime version/ID authority.

## 12. Maven and CI responsibilities

An approved contract-module plan should pin and document:

- Apache Avro version and `avro-maven-plugin`;
- Confluent serializer/client and Maven plugin versions;
- the Confluent Maven repository when required;
- deterministic SpecificRecord generation;
- compatibility test subject mapping;
- Registry credentials supplied only through CI secrets.

Pull-request gates:

```text
validate .avsc
generate SpecificRecord sources
compile contract module
run schema compatibility tests
run producer/consumer serialization contract tests
reject incompatible changes
```

After approval/merge, a controlled delivery job may use the Schema Registry Maven plugin's
`register` goal. Registration must not occur during ordinary unit tests or application startup.

## 13. Contract review checklist

- [ ] Producer and every consumer are named.
- [ ] Command versus event semantics are explicit.
- [ ] Topic, key, partition-order requirement, and schema subject are explicit.
- [ ] Message ID, correlation, causation, occurrence time, and trace headers are defined.
- [ ] No secret or mutable service-internal model leaks into the schema.
- [ ] Compatibility mode and rollout order are tested.
- [ ] Duplicate, stale, out-of-order, unreadable, retry, DLT, and replay behavior are defined.
- [ ] Outbox/inbox transaction boundaries are defined.
- [ ] Contract fixtures test exact serialization/deserialization.

## Official references

- [Confluent Avro serializers and deserializers](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/serdes-avro.html)
- [Subject name strategies](https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/overview.html)
- [Schema evolution and compatibility](https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html)
- [Schema Registry Maven plugin](https://docs.confluent.io/platform/current/schema-registry/develop/maven-plugin.html)
