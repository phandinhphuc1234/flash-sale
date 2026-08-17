# PaymentFailed Kafka Contract

**Status**: Draft — ready for approval with Feature 021 plan
**Producer**: `payment-service`
**Consumer**: `order-service` (future Purchase Saga implementation)
**Topic**: `flashsale.payment.events.v1`
**Key**: `orderId` UUID string
**Delivery**: At least once through the Payment PostgreSQL transactional outbox

`PaymentFailed.v1` is emitted only after Payment has established a business-terminal unpaid outcome.
Timeout, database/Kafka outage, ambiguous provider response, missing webhook, or exhausted synchronous
retry alone are not payment failure.

## Schema governance

| Setting | Value |
|---|---|
| Schema path | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.events.v1/PaymentFailedV1.avsc` |
| Namespace | `com.philia.flashsale.contract.payment.event.v1` |
| Record | `PaymentFailedV1` generated `SpecificRecord` |
| Subject strategy | `TopicRecordNameStrategy` |
| Subject | `flashsale.payment.events.v1-com.philia.flashsale.contract.payment.event.v1.PaymentFailedV1` |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Stable registration | `auto.register.schemas=false` |
| Local partitions / replication | 3 / 1 |

## Canonical Avro v1 shape

```json
{
  "type": "record",
  "name": "PaymentFailedV1",
  "namespace": "com.philia.flashsale.contract.payment.event.v1",
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
        "name": "PaymentFailedDataV1",
        "fields": [
          {"name": "paymentId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "orderId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 19, "scale": 4}},
          {"name": "currency", "type": "string"},
          {"name": "failedAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
          {"name": "reason", "type": "string"},
          {"name": "provider", "type": "string"},
          {"name": "providerSessionId", "type": ["null", "string"], "default": null}
        ]
      }
    }
  ]
}
```

## Rules

- Envelope constants are `PaymentFailed`, `1`, `payment-service`, and `PAYMENT`.
- `aggregateId = data.paymentId`; Kafka key is `data.orderId`; aggregate version is positive and
  monotonic for Payment.
- v1 `reason` is exactly one of:
  - `PAYMENT_DEADLINE_EXPIRED`
  - `CHECKOUT_ATTEMPT_LIMIT_REACHED`
  - `PROVIDER_TERMINAL_FAILURE`
- `PAYMENT_DEADLINE_EXPIRED` requires the internal deadline to have passed and reconciliation to
  exclude a known paid state before the fact is committed.
- `PROVIDER_TERMINAL_FAILURE` requires an established non-retryable provider outcome; a timeout or
  unknown state is forbidden.
- Amount/currency equal the accepted command. Provider is `STRIPE`; Session ID may be absent if no
  Session was ever established.
- `correlationId` remains the Purchase Saga ID; causation points to the command, provider receipt, or
  recovery operation that established the failure.
- There is no `PaymentExpired.v1`; expiry is represented by this fact and reason.

## Ordering, convergence, and prohibited data

Outbox retry keeps stable identity. Consumers deduplicate by event ID and process Payment aggregate
versions in order. A higher-version `PaymentSucceeded.v1` may follow this event after a verified late
charge; Order then performs forward recovery/manual review. Payment does not auto-refund.

No Checkout URL, raw webhook, card/customer data, secrets, JWT, raw exception/provider error, or
retry state appears in the payload or headers. W3C trace headers are allowed. Evolution follows
BACKWARD_TRANSITIVE compatibility and requires contract tests.
