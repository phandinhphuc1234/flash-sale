# Kafka Contract: Confirmed Cart Reconciliation

**Command owner/producer**: Order Service
**Consumer**: Cart Service
**Topic**: `flashsale.cart.checkout.commands.v1`
**DLT**: `flashsale.cart.checkout-reconciliation.dlt.v1`
**Key**: `cartId`
**Serialization**: SpecificRecord Avro with TopicRecordNameStrategy
**Compatibility**: BACKWARD_TRANSITIVE per record-name subject

## `ReconcilePurchasedCartSnapshotV1`

Envelope:

| Field | Rule |
|---|---|
| `eventId` | Stable command UUID. |
| `eventType` | `ReconcilePurchasedCartSnapshot`. |
| `eventVersion` | `1`. |
| `producer` | `order-service`. |
| `aggregateType` | `ORDER`. |
| `aggregateId` | Confirmed `orderId`. |
| `aggregateVersion` | Positive terminal Order version. |
| `correlationId` | `purchaseRequestId`. |
| `causationId` | Inventory hold-confirmed fact ID. |
| `occurredAt` | UTC timestamp-millis. |
| `traceparent`, `tracestate` | Nullable bounded W3C fields. |

Payload example:

```json
{
  "orderId": "cb52a787-d452-4e57-817b-ee3e5e972af9",
  "purchaseRequestId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
  "cartId": "b5bfd90f-55b6-45f5-84e0-0f9ecb51fe21",
  "ownerId": "8090d071-cce3-4384-9009-394ae9a6bb75",
  "snapshotCartVersion": 12,
  "confirmedAt": "2026-09-03T04:32:02Z",
  "items": [
    {
      "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
      "quantity": 1,
      "itemVersion": 10
    }
  ]
}
```

Items are non-empty, unique, and sorted by variant ID. The snapshot Cart version is audit/context;
Cart does not require the current whole-Cart version to remain equal because unrelated or later
edits must survive.

## Apply semantics

Cart locks the target Cart and processes the complete command in one local transaction:

1. Validate producer, version, identities, owner, canonical fingerprint, and source position.
2. If the same command/fingerprint is already in the inbox, acknowledge with no mutation.
3. For each item, delete only when `(cartId, variantId, quantity, itemVersion)` still matches.
4. Preserve missing items, changed quantity, removed/re-added items, or newer item versions as
   no-op outcomes.
5. Increment Cart version once only when at least one item is deleted.
6. Store one inbox row with applied/no-op counts and commit.
7. Acknowledge Kafka only after commit.

This command is emitted only after the Order and regular stock hold are both confirmed. Buy Now,
payment failure, cancellation, and expiry never emit it.

## Idempotency and concurrency

- One semantic command per confirmed Cart Order; Order outbox uniqueness prevents duplicates.
- Cart inbox primary key is `eventId`; `orderId` is unique for this command semantic.
- Same ID/same fingerprint is replay. Same ID/different fingerprint is conflict and goes to DLT.
- Keying by `cartId` orders reconciliation commands for the same Cart.
- Database row locking serializes the command with concurrent Cart HTTP mutations.
- HTTP mutation that committed first changes revision and is preserved; reconciliation that committed
  first removes the old row, after which a new HTTP add creates a higher revision and remains.

## Retry and DLT

- Transient database/broker errors retry with bounded configured delays.
- Malformed record, wrong producer/version, invalid identity, duplicate variant, impossible revision,
  identity conflict, or same-version conflicting content is non-retryable and goes to
  `flashsale.cart.checkout-reconciliation.dlt.v1`.
- DLT preserves the original `cartId` key and source diagnostics without logging owner ID, item IDs,
  token, idempotency key, or raw customer data.

## Observability

Counters use bounded outcome labels:

- `cart.checkout.reconciliation{outcome=applied|partial_noop|noop|replay|conflict|dlt}`
- `cart.checkout.reconciliation.items{outcome=removed|preserved}`
- consumer lag/backlog and oldest unprocessed age

No metric label contains Cart, shopper, Order, purchase, variant, or event identity.

## Registry and rollout

Register the source subject and its DLT topic-record subject before enabling the Order producer.
Deploy the Cart consumer first. Disabling the producer during rollback does not delete already
published commands; Cart continues draining safe reconciliation work unless an explicit reviewed
incident action pauses it.
