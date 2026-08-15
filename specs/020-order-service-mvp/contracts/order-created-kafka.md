# OrderCreated Kafka Contract

**Status**: Approved for G1 schema implementation by project owner on 2026-08-15
**Producer**: `order-service`
**Consumers in Feature 020**: Contract verification only; no business consumer is implemented
**Topic**: `flashsale.order.events.v1`
**Key**: `orderId` UUID string
**Delivery**: At least once through the Order PostgreSQL transactional outbox

`OrderCreated.v1` is a committed fact. It is not authorization to charge a shopper and must not be
reinterpreted as `PaymentRequested.v1`. Payment orchestration requires a later approved command
contract.

## Schema Governance

| Setting | Value |
|---------|-------|
| Schema source | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.order.events.v1/OrderCreatedV1.avsc` |
| Namespace | `com.philia.flashsale.contract.order.event.v1` |
| Record | `OrderCreatedV1` generated `SpecificRecord` |
| Subject strategy | `TopicRecordNameStrategy` |
| Subject | `flashsale.order.events.v1-com.philia.flashsale.contract.order.event.v1.OrderCreatedV1` |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Stable environment registration | `auto.register.schemas=false` |
| Local partitions | 3 |
| Local replication factor | 1 |

The Kafka adapter alone maps the internal immutable outbox snapshot to the generated record. Domain,
application, HTTP DTOs, JPA entities, and persistence mappers do not import the generated type.

## Canonical Avro v1 Shape

```json
{
  "type": "record",
  "name": "OrderCreatedV1",
  "namespace": "com.philia.flashsale.contract.order.event.v1",
  "fields": [
    {"name": "eventId", "type": {"type": "string", "logicalType": "uuid"}},
    {"name": "eventType", "type": "string"},
    {"name": "eventVersion", "type": "int"},
    {"name": "producer", "type": "string"},
    {"name": "aggregateType", "type": "string"},
    {"name": "aggregateId", "type": {"type": "string", "logicalType": "uuid"}},
    {"name": "aggregateVersion", "type": "long"},
    {"name": "correlationId", "type": {"type": "string", "logicalType": "uuid"}},
    {"name": "causationId", "type": {"type": "string", "logicalType": "uuid"}},
    {"name": "occurredAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
    {
      "name": "data",
      "type": {
        "type": "record",
        "name": "OrderCreatedDataV1",
        "fields": [
          {"name": "orderId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "orderNumber", "type": "string"},
          {"name": "purchaseRequestId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "reservationId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "campaignId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "userId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "status", "type": "string"},
          {"name": "currency", "type": "string"},
          {"name": "subtotalAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 19, "scale": 4}},
          {"name": "totalAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 19, "scale": 4}},
          {"name": "acceptedAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
          {"name": "reservationExpiresAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
          {
            "name": "items",
            "type": {
              "type": "array",
              "items": {
                "type": "record",
                "name": "OrderCreatedItemV1",
                "fields": [
                  {"name": "variantId", "type": {"type": "string", "logicalType": "uuid"}},
                  {"name": "quantity", "type": "long"},
                  {"name": "unitPrice", "type": {"type": "bytes", "logicalType": "decimal", "precision": 19, "scale": 4}},
                  {"name": "lineAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 19, "scale": 4}}
                ]
              }
            }
          }
        ]
      }
    }
  ]
}
```

The implementation schema adds documentation strings but must preserve this field identity and
logical-type contract. Feature 020 emits exactly one item.

## Envelope Rules

| Field | Rule |
|-------|------|
| `eventId` | Stable UUID generated with the creation outbox row; unchanged across retries |
| `eventType` | Exactly `OrderCreated` |
| `eventVersion` | Exactly `1` |
| `producer` | Exactly `order-service` |
| `aggregateType` | Exactly `ORDER` |
| `aggregateId` | Equal to `data.orderId` and Kafka key |
| `aggregateVersion` | `1` for initial Order creation |
| `correlationId` | Preserved from `PurchaseAcceptedV1.correlationId` |
| `causationId` | Inbound `PurchaseAcceptedV1.eventId` |
| `occurredAt` | Order creation instant stored with the outbox snapshot |

## Data Rules

- `orderId`, `purchaseRequestId`, `reservationId`, `campaignId`, `userId`, and item `variantId` are
  valid UUIDs.
- `orderNumber` is nonblank and at most 64 characters.
- `status` is exactly `PENDING_PAYMENT` in v1.
- `currency` is exactly three uppercase ASCII letters.
- Quantity and all amounts are positive.
- Decimal amounts use precision 19 and scale 4. `lineAmount = unitPrice * quantity`, and subtotal
  and total equal the single line amount.
- `acceptedAt < reservationExpiresAt`.
- The payload contains exactly one item in Feature 020.

The message intentionally excludes JWTs, authorization headers, passwords, secrets, provider/payment
credentials, Kafka offsets, database/JPA types, inbox/outbox status, row versions, SQL errors, and
internal retry state.

## Headers

- `traceparent`: W3C trace context when a valid inbound context is available.
- `tracestate`: optional W3C vendor context.
- Optional diagnostic headers may duplicate `eventId`, `eventType`, and `eventVersion`, but the Avro
  payload remains authoritative.
- `Authorization`, JWT, cookies, raw exceptions, and credentials are forbidden.

## Ordering, Idempotency, and Recovery

- Partition by `orderId` so future Order lifecycle facts retain per-Order ordering.
- Producer uses `acks=all` and idempotence, but consumers must assume at-least-once delivery.
- Outbox retry preserves topic, key, event ID, payload, versions, correlation, causation, occurrence
  time, and trace linkage.
- A crash after broker acknowledgement and before marking the outbox row published may cause a
  duplicate physical delivery with the same event identity.
- Future consumers deduplicate by `eventId` and protect their own business identity/invariants.
- Schema serialization, Registry, broker, timeout, and acknowledgement failures keep the outbox
  fact durably retryable. They do not roll back or delete the committed Order.

## Compatibility Rules

- v1 fields are never renamed, removed, narrowed, or semantically repurposed.
- Compatible additions require defaults and contract tests under `BACKWARD_TRANSITIVE`.
- A meaning-breaking change uses a new record/event version and an explicit producer/consumer
  rollout plan.
- `PurchaseAcceptedV1` remains unchanged by this contract.
