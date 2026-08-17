# PaymentSucceeded Kafka Contract

**Status**: Approved with Feature 021 plan by the project owner on 2026-08-17
**Producer**: `payment-service`
**Consumer**: `order-service` (future Purchase Saga implementation)
**Topic**: `flashsale.payment.events.v1`
**Key**: `orderId` UUID string
**Delivery**: At least once through the Payment PostgreSQL transactional outbox

`PaymentSucceeded.v1` is the durable fact that Stripe-confirmed payment succeeded. It may follow a
higher-level failure race; consumers converge by Payment aggregate version and treat verified
success as requiring Saga forward recovery/manual review when prior compensation already ran.

## Schema governance

| Setting | Value |
|---|---|
| Schema path | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.payment.events.v1/PaymentSucceededV1.avsc` |
| Namespace | `com.philia.flashsale.contract.payment.event.v1` |
| Record | `PaymentSucceededV1` generated `SpecificRecord` |
| Subject strategy | `TopicRecordNameStrategy` |
| Subject | `flashsale.payment.events.v1-com.philia.flashsale.contract.payment.event.v1.PaymentSucceededV1` |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Stable registration | `auto.register.schemas=false` |
| Local partitions / replication | 3 / 1 |

## Canonical Avro v1 shape

```json
{
  "type": "record",
  "name": "PaymentSucceededV1",
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
        "name": "PaymentSucceededDataV1",
        "fields": [
          {"name": "paymentId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "orderId", "type": {"type": "string", "logicalType": "uuid"}},
          {"name": "amount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 19, "scale": 4}},
          {"name": "currency", "type": "string"},
          {"name": "paidAt", "type": {"type": "long", "logicalType": "timestamp-millis"}},
          {"name": "provider", "type": "string"},
          {"name": "providerSessionId", "type": "string"},
          {"name": "providerPaymentIntentId", "type": ["null", "string"], "default": null}
        ]
      }
    }
  ]
}
```

## Rules

- `eventType=PaymentSucceeded`, `eventVersion=1`, `producer=payment-service`, and
  `aggregateType=PAYMENT`.
- `aggregateId = data.paymentId`; the Kafka key is `data.orderId`. The differing aggregate/key is
  intentional: event version belongs to Payment while partition ordering belongs to the purchase.
- `aggregateVersion` is positive and monotonically increases for the Payment.
- `provider=STRIPE`; Session ID is nonblank; Payment Intent ID is nullable because provider object
  availability can differ at observation time.
- Amount/currency exactly equal the accepted `PaymentRequested.v1`; `paidAt` is provider-confirmed.
- `correlationId` stays the Purchase Saga ID. `causationId` is the internal UUID of the verified
  provider-event receipt when webhook-driven, or the stable recovery-work UUID when
  reconciliation-driven; a Stripe `evt_...` string is not placed in this UUID field.
- Checkout URL, raw webhook, card/customer data, provider secret, JWT, provider error, and internal
  retry state are forbidden.

## Ordering, idempotency, and late success

- The outbox preserves event ID, payload, key, version, times, and trace context across retries.
- Consumers deduplicate by `eventId` and enforce monotonic Payment `aggregateVersion` per order.
- A duplicate physical delivery has the same event identity and is a no-op.
- A success may validly follow `PaymentFailed.v1` with a lower aggregate version after a deadline or
  provider-visibility race. Consumers must not discard that higher-version verified success.
- `SUCCEEDED` cannot later emit a failure fact.

Use W3C `traceparent` and optional `tracestate`; never propagate credentials. Schema evolution follows
BACKWARD_TRANSITIVE rules and requires contract tests.
