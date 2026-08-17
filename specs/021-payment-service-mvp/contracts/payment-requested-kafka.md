# PaymentRequested Kafka Contract

**Status**: Approved with Feature 021 plan by the project owner on 2026-08-17
**Producer**: `order-service` (future Purchase Saga implementation)
**Consumer**: `payment-service`
**Topic**: `flashsale.payment.commands.v1`
**Key**: `orderId` UUID string
**Delivery**: At least once through the Order PostgreSQL transactional outbox

`PaymentRequested.v1` is an explicit Saga command authorizing Payment Service to prepare payment
for one immutable Order amount until an absolute deadline. `OrderCreated.v1` is not this command.

## Schema governance

| Setting | Value |
|---|---|
| Schema path | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.commands.v1/PaymentRequestedV1.avsc` |
| Namespace | `com.philia.flashsale.contract.payment.command.v1` |
| Record | `PaymentRequestedV1` generated `SpecificRecord` |
| Subject strategy | `TopicRecordNameStrategy` |
| Subject | `flashsale.payment.commands.v1-com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1` |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Stable registration | `auto.register.schemas=false` |
| Local partitions / replication | 3 / 1 |

## Canonical Avro v1 shape

```json
{
  "type": "record",
  "name": "PaymentRequestedV1",
  "namespace": "com.philia.flashsale.contract.payment.command.v1",
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
        "name": "PaymentRequestedDataV1",
        "fields": [
          {"name": "orderId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "userId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 19, "scale": 4}},
          {"name": "currency", "type": "string"},
          {"name": "paymentDeadline", "type": {"type": "long", "logicalType": "timestamp-millis"}}
        ]
      }
    }
  ]
}
```

## Envelope rules

| Field | Rule |
|---|---|
| `eventType` / `eventVersion` | Exactly `PaymentRequested` / `1`. |
| `producer` | Exactly `order-service`. |
| `aggregateType` | Exactly `ORDER`. |
| `aggregateId` | Equal to `data.orderId` and the Kafka key. |
| `aggregateVersion` | The Order/Saga version that issued this command; positive and monotonically increasing for the Order. |
| `correlationId` | Stable Purchase Saga correlation ID. |
| `causationId` | ID of the Order fact/transition that caused the command. |
| `occurredAt` | Immutable outbox creation instant. |

## Data rules

- IDs are valid UUIDs; `amount > 0`; currency is exactly three uppercase ASCII letters.
- Amount is an exact decimal with precision 19 and scale 4.
- `paymentDeadline` is an absolute instant after the authorizing Order transition. Payment rejects
  new Checkout attempts at or after it.
- Order owns amount, currency, user, and deadline. Payment never accepts browser overrides.
- The command contains no Checkout URL, card/customer data, provider secret, JWT, reservation
  command, JPA type, or retry state.

## Idempotency, conflicts, and failures

- Payment persists an inbox identity before acknowledging successful processing.
- Duplicate `eventId` with identical canonical data is a no-op.
- The same `orderId` with different user/amount/currency/deadline is a contract conflict, is not
  overwritten, and is surfaced operationally.
- Transient infrastructure processing gets initial delivery plus retries after 1s, 3s, and 10s.
- Exhausted messages go to `flashsale.payment.payment-requested.dlt.v1`, keyed by `orderId`, with
  the original serialized command and safe diagnostic headers. This DLT is not a business fact.
- `payment.order.dlt.v1` is explicitly not used.

## Headers and compatibility

Use `traceparent` and optional `tracestate`. Never propagate `Authorization`, JWT, cookies, provider
credentials, or raw exceptions. v1 fields may not be removed/renamed/repurposed. Compatible additions
require defaults and compatibility tests; meaning-breaking changes require a new record version and
rollout plan.
