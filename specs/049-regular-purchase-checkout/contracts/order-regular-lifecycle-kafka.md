# Kafka Contract: Regular Order Lifecycle Facts

**Owner/producer**: Order Service
**Topic**: `flashsale.order.events.v1`
**Key**: `orderId`
**Serialization**: SpecificRecord Avro with TopicRecordNameStrategy

Existing Flash Sale `OrderCreatedV1`, `OrderConfirmedV1`, `OrderCancelledV1`, and `OrderExpiredV1`
remain byte-for-byte and semantically unchanged. They still require Flash Sale campaign and
reservation identity. Regular purchases publish additive V2 record names on the same topic.

## `OrderCreatedV2`

Payload fields:

| Field | Rule |
|---|---|
| `orderId`, `orderNumber`, `purchaseRequestId`, `userId` | Stable Order identity. |
| `purchaseSource` | `BUY_NOW` or `CART`. |
| `stockParticipantType` | `REGULAR_STOCK_HOLD`. |
| `stockReferenceId` | Inventory hold ID. |
| `cartId`, `cartVersion` | Nullable union; both present only for `CART`. |
| `status` | `PENDING_PAYMENT`. |
| `currency`, `subtotalAmount`, `totalAmount` | Immutable commercial snapshot; total equals subtotal in this release. |
| `acceptedAt`, `stockHoldExpiresAt`, `paymentDeadline` | UTC timestamps; deadline is expiry minus 30 seconds. |
| `items` | Non-empty immutable line array with variant, quantity, unit price, line amount, and optional Product/SKU display snapshot. |

The envelope follows existing Order records: producer `order-service`, aggregate type `ORDER`,
aggregate ID/key `orderId`, positive Order version, purchase request correlation, stable causation,
and trace context.

## Terminal V2 facts

- `OrderConfirmedV2`: generic stock reference, Payment ID, source, and `confirmedAt`.
- `OrderCancelledV2`: generic stock reference, source, bounded reason, and `cancelledAt`.
- `OrderExpiredV2`: generic stock reference, source, bounded reason, and `expiredAt`.

Cart cleanup is not implied by an Order fact. Order separately emits the explicit Cart command only
for a confirmed `CART` source.

## Compatibility and consumers

- V1 subjects and Flash Sale publishing logic do not change.
- V2 is a new record-name subject family; BACKWARD_TRANSITIVE applies independently.
- Consumers must explicitly support V1, V2, or both. They may not deserialize V2 as V1 or invent
  campaign IDs.
- Existing Payment does not consume Order facts and is unaffected.
- Operational DLT subjects are registered for every record type accepted by that DLT topic.

## Ordering and replay

- All facts for one Order use the same `orderId` key.
- Outbox uniqueness is `(aggregateId, aggregateVersion, eventType)` and stable across retry.
- A consumer treats same event ID/fingerprint as replay and same ID/different content as conflict.
- Terminal facts are emitted only after the owning stock participant outcome is durable.
