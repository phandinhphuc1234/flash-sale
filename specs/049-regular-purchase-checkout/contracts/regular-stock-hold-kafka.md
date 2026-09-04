# Kafka Contract: Regular Stock Hold

**Command owner/producer**: Order Service
**Command consumer**: Inventory Service
**Fact owner/producer**: Inventory Service
**Fact consumer**: Order Service
**Serialization**: SpecificRecord Avro with TopicRecordNameStrategy
**Compatibility**: BACKWARD_TRANSITIVE per record-name subject

## Topics and keys

| Topic | Records | Key |
|---|---|---|
| `flashsale.inventory.regular-hold.commands.v1` | `ConfirmRegularStockHoldV1`, `ReleaseRegularStockHoldV1` | `orderId` |
| `flashsale.inventory.regular-hold.events.v1` | `RegularStockHoldConfirmedV1`, `RegularStockHoldReleasedV1`, `RegularStockHoldExpiredV1` | `orderId` |
| `flashsale.inventory.regular-hold-command.dlt.v1` | rejected command SpecificRecords | original `orderId` |
| `flashsale.order.regular-hold-result.dlt.v1` | rejected fact SpecificRecords | original `orderId` |

The two main topics are created explicitly with automatic topic creation disabled. The first cloud
release uses the current single-broker-safe replication factor and reviewed partition inventory;
production sizing is outside this feature. Partition count must never be reduced. Every message for
one Order uses `orderId`, preserving Saga ordering across command/fact families.

## Common envelope

Every record contains:

| Field | Rule |
|---|---|
| `eventId` | UUID; stable command/fact identity. |
| `eventType` | Exact record semantic without version suffix. |
| `eventVersion` | `1`. |
| `producer` | `order-service` for commands; `inventory-service` for facts. |
| `aggregateType` | `PURCHASE_SAGA` for commands; `REGULAR_STOCK_HOLD` for facts. |
| `aggregateId` | Purchase Saga ID for commands; hold ID for facts. |
| `aggregateVersion` | Positive monotonic owner version. |
| `correlationId` | `purchaseRequestId`. |
| `causationId` | Payment fact ID for commands; command ID for command outcomes. Expiry uses the stable hold-creation request ID. |
| `occurredAt` | UTC timestamp-millis. |
| `traceparent`, `tracestate` | Nullable bounded W3C propagation fields. |
| `data` | Typed record payload. |

## `ConfirmRegularStockHoldV1`

Payload:

```json
{
  "sagaId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
  "orderId": "cb52a787-d452-4e57-817b-ee3e5e972af9",
  "purchaseRequestId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
  "holdId": "da29aa23-c4ae-4d62-9fd0-298c1ef952c4",
  "paymentId": "f9e43910-2af3-4b37-8b1e-a9686e7764b4",
  "paidAt": "2026-09-03T04:32:00Z"
}
```

Inventory confirms only the matching `HELD` aggregate while it is safely confirmable. Confirmation
deducts on-hand quantity and writes one movement per line in the same transaction as inbox and
result outbox. Equivalent replay returns/re-publishes the stable result without another deduction.

## `ReleaseRegularStockHoldV1`

Payload:

```json
{
  "sagaId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
  "orderId": "cb52a787-d452-4e57-817b-ee3e5e972af9",
  "purchaseRequestId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
  "holdId": "da29aa23-c4ae-4d62-9fd0-298c1ef952c4",
  "paymentId": "f9e43910-2af3-4b37-8b1e-a9686e7764b4",
  "reason": "PAYMENT_DEADLINE_EXPIRED",
  "desiredOrderStatus": "EXPIRED"
}
```

Allowed desired statuses are `CANCELLED` and `EXPIRED`; reason values remain bounded and aligned
with the existing Payment/Saga mapping. Release removes the quantity from active held availability
without deducting on-hand stock.

## Inventory result facts

All result payloads include:

```json
{
  "holdId": "da29aa23-c4ae-4d62-9fd0-298c1ef952c4",
  "purchaseRequestId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
  "orderId": "cb52a787-d452-4e57-817b-ee3e5e972af9",
  "status": "CONFIRMED",
  "items": [
    {
      "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
      "quantity": 1
    }
  ],
  "transitionedAt": "2026-09-03T04:32:01Z"
}
```

- `RegularStockHoldConfirmedV1`: `status=CONFIRMED`; may include `paymentId` for identity evidence.
- `RegularStockHoldReleasedV1`: `status=RELEASED`; includes bounded release reason.
- `RegularStockHoldExpiredV1`: `status=EXPIRED`; emitted by Inventory's expiry transition, not by
  an Order release command.

Items are sorted by variant ID and must exactly match the durable hold. Order compares their
canonical fingerprint to its immutable Order lines before terminalizing.

## Idempotency and monotonicity

- Command consumer primary deduplication is `eventId`; source position is also unique.
- Same ID/same canonical fingerprint reuses one stable outcome `eventId`.
- Same ID/different content is a non-retryable identity conflict.
- Inventory aggregate version never decreases. Order ignores exact duplicates, rejects conflicting
  same-version content, and ignores stale lower versions after recording safe diagnostics.
- A command referencing another order/purchase/hold combination is rejected without state change.
- Competing confirm/release operations lock the hold; only one legal transition wins.

## Current-state outcomes and late success

A new command against an already terminal hold receives a correlated current-state result with a
new result event ID caused by that command; it does not rewrite the hold. This lets Order distinguish
an equivalent command replay from a later recovery attempt. `CONFIRMED` remains confirmed. A
released/expired hold cannot be recreated or confirmed automatically, so verified late payment
advances the Order Saga to its existing manual-review boundary.

## Retry and DLT policy

- Transient broker/database errors retry using configured bounded delays and no Kafka auto-commit.
- Malformed Avro, unsupported record, wrong producer/version, invalid UUID/time, identity conflict,
  or impossible state is non-retryable and sent to the consumer-specific DLT.
- DLT publication preserves original key, source topic/partition/offset, exception classification,
  trace context, and SpecificRecord payload where safe. It never logs tokens or shopper fields.
- Acknowledgement occurs only after the local transaction or DLT publication completes.

## Registry provisioning order

1. Create main topics and DLTs idempotently.
2. Register the five source record-name subjects.
3. Register each source record type under the matching DLT topic-record subject.
4. Verify BACKWARD_TRANSITIVE compatibility and exact subject inventory.
5. Deploy Inventory/Order consumers disabled, then enable consumers.
6. Enable Order regular-hold command production only after consumers are healthy.
