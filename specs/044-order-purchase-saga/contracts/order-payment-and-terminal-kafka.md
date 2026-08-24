# Kafka Contract: Order Payment Orchestration and Terminal Events

## Existing Payment contracts reused

Feature 044 does not change these schemas:

| Topic | Record | Key | Direction |
|---|---|---|---|
| `flashsale.payment.commands.v1` | `PaymentRequestedV1` | `orderId` | Order -> Payment |
| `flashsale.payment.events.v1` | `PaymentSucceededV1` | `orderId` | Payment -> Order |
| `flashsale.payment.events.v1` | `PaymentFailedV1` | `orderId` | Payment -> Order |

Order creates exactly one semantic `PaymentRequestedV1` at Saga start. Its `paymentDeadline` is
exactly 30 seconds earlier than the accepted reservation expiry. The command outbox commits with
Order/Saga creation and uses the accepted amount, currency, user, and Order identity.

Order's Payment-result consumer validates:

- exact topic and SpecificRecord type;
- producer `payment-service`, aggregate type `PAYMENT`, and version `1` wire contract;
- Kafka key equals `data.orderId` and resolves to the local Saga;
- amount/currency equal the immutable Order total;
- first `paymentId` becomes stable for the Saga;
- Payment aggregate versions are monotonic;
- failure reason is exactly one of `PAYMENT_DEADLINE_EXPIRED`,
  `CHECKOUT_ATTEMPT_LIMIT_REACHED`, or `PROVIDER_TERMINAL_FAILURE`.

The consumer-specific DLT is `flashsale.order.payment-result.dlt.v1`.

## New Order terminal records

Topic: `flashsale.order.events.v1`
Producer: `order-service`
Key: canonical `orderId` UUID
Serialization: Avro SpecificRecord / TopicRecordNameStrategy
Compatibility: BACKWARD_TRANSITIVE

The existing `OrderCreatedV1` remains unchanged. Four additive records are introduced.

### Common envelope

| Field | Rule |
|---|---|
| `eventId` | Stable outbox UUID. |
| `eventType` | Exact name below. |
| `eventVersion` | `1`. |
| `producer` | `order-service`. |
| `aggregateType` | `ORDER`. |
| `aggregateId` | `orderId`, equal to Kafka key. |
| `aggregateVersion` | Positive durable Order version. |
| `correlationId` | Saga ID / purchase request ID. |
| `causationId` | Reservation result that caused a terminal event, or the higher-version Payment success that caused a review correction. |
| `occurredAt` | UTC timestamp-millis. |

### OrderConfirmedV1

Data record `OrderConfirmedDataV1`:

| Field | Type |
|---|---|
| `orderId` | UUID |
| `orderNumber` | string |
| `purchaseRequestId` | UUID |
| `reservationId` | UUID |
| `paymentId` | UUID |
| `confirmedAt` | timestamp-millis |

Meaning: verified payment and durable reservation confirmation are both complete.

### OrderCancelledV1

Data record `OrderCancelledDataV1`:

| Field | Type |
|---|---|
| `orderId` | UUID |
| `orderNumber` | string |
| `purchaseRequestId` | UUID |
| `reservationId` | UUID |
| `reason` | string |
| `cancelledAt` | timestamp-millis |

Allowed reasons: `CHECKOUT_ATTEMPT_LIMIT_REACHED`, `PROVIDER_TERMINAL_FAILURE`.

### OrderExpiredV1

Data record `OrderExpiredDataV1`:

| Field | Type |
|---|---|
| `orderId` | UUID |
| `orderNumber` | string |
| `purchaseRequestId` | UUID |
| `reservationId` | UUID |
| `reason` | string; exactly `PAYMENT_DEADLINE_EXPIRED` |
| `expiredAt` | timestamp-millis |

### OrderPaymentReviewRequiredV1

Data record `OrderPaymentReviewRequiredDataV1`:

| Field | Type |
|---|---|
| `orderId` | UUID |
| `orderNumber` | string |
| `purchaseRequestId` | UUID |
| `reservationId` | UUID |
| `paymentId` | UUID |
| `previousStatus` | string; `CANCELLED` or `EXPIRED` |
| `reviewReason` | string; exactly `LATE_PAYMENT_RESERVATION_UNAVAILABLE` |
| `reviewRequiredAt` | timestamp-millis |

Meaning: a higher-version verified Payment success arrived after an unpaid terminal Order fact,
but the reservation could not be safely confirmed. The Order has been corrected to public
`PENDING_PAYMENT` and its Saga is `MANUAL_REVIEW`. Consumers must treat this correction as
superseding the earlier `OrderCancelledV1` or `OrderExpiredV1`; it is not a payment refund or a new
public Order status.

## Publication rules

- Order terminal status, Saga terminal state, Saga inbox receipt, and terminal Order outbox intent
  commit atomically.
- No terminal Order event is written before the required reservation result is accepted.
- Publication retry preserves event identity and payload.
- `OrderConfirmedV1` is never followed by an unpaid terminal event.
- A higher-version verified success after an earlier unpaid terminal fact either converges to
  `OrderConfirmedV1` or atomically produces one stable `OrderPaymentReviewRequiredV1`. It never
  triggers automatic refund.
- Cart and Notification are not consumers in Feature 044; future consumers must handle the full
  lifecycle and manual-review policy before activation.
