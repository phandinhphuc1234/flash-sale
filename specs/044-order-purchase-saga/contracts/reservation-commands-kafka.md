# Kafka Contract: Purchase Reservation Commands v1

## Topic and ownership

| Property | Value |
|---|---|
| Topic | `flashsale.purchase.commands.v1` |
| Producer | Order Service / Purchase Saga |
| Consumer | Flash Sale Service |
| Partition key | canonical lowercase `orderId` UUID |
| Serialization | Avro SpecificRecord |
| Subject strategy | `TopicRecordNameStrategy` |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Main-topic partitions | 3 local/cloud target, subject to environment provisioning policy |
| Consumer DLT | `flashsale.flash-sale.purchase-command.dlt.v1` |

The topic contains two distinct records. Technical retries retain the same event ID, key, payload,
and trace headers.

## Common envelope

Both records contain:

| Field | Type | Rule |
|---|---|---|
| `eventId` | UUID logical string | Stable command/deduplication identity. |
| `eventType` | string | Exact semantic name below. |
| `eventVersion` | int | `1`. |
| `producer` | string | `order-service`. |
| `aggregateType` | string | `PURCHASE_SAGA`. |
| `aggregateId` | UUID | Saga ID; equal to `purchaseRequestId`. |
| `aggregateVersion` | long | Positive Order-owned Saga version. |
| `correlationId` | UUID | Same stable Saga ID. |
| `causationId` | UUID | Payment result event that caused the command. |
| `occurredAt` | timestamp-millis | UTC command occurrence. |
| `data` | typed record | Message-specific fields. |

Kafka headers may contain valid W3C `traceparent` and `tracestate`. JWTs, authorization headers,
provider credentials, Checkout URLs, webhook signatures, and raw provider payloads are forbidden.

## ConfirmPurchaseReservationV1

Namespace: `com.philia.flashsale.contract.purchase.command.v1`

`eventType=ConfirmPurchaseReservation`

Data record `ConfirmPurchaseReservationDataV1`:

| Field | Type | Rule |
|---|---|---|
| `sagaId` | UUID | Equals envelope aggregate/correlation ID and `purchaseRequestId`. |
| `orderId` | UUID | Equals Kafka key. |
| `purchaseRequestId` | UUID | Existing Flash Sale purchase identity. |
| `reservationId` | UUID | Existing reservation identity. |
| `paymentId` | UUID | Verified Payment aggregate identity. |
| `paidAt` | timestamp-millis | Verified provider success time. |

Flash Sale confirms only a matching `RESERVED` reservation before its expiry. A replay of an
already-confirmed command returns the existing semantic result. A released/expired reservation is
never recreated or re-reserved.

## ReleasePurchaseReservationV1

Namespace: `com.philia.flashsale.contract.purchase.command.v1`

`eventType=ReleasePurchaseReservation`

Data record `ReleasePurchaseReservationDataV1`:

| Field | Type | Rule |
|---|---|---|
| `sagaId` | UUID | Equals envelope aggregate/correlation ID and `purchaseRequestId`. |
| `orderId` | UUID | Equals Kafka key. |
| `purchaseRequestId` | UUID | Existing Flash Sale purchase identity. |
| `reservationId` | UUID | Existing reservation identity. |
| `reason` | string | One of the approved Payment failure reason codes. |

Flash Sale records the reason for audit but never chooses Order `CANCELLED` versus `EXPIRED`; that
decision remains in Order Saga state.

## Validation and idempotency

- Topic, record type, exact envelope constants, UUIDs, version, key, and business identities are
  validated before invoking the application use case.
- `orderId` must equal the Kafka key.
- `sagaId`, `purchaseRequestId`, envelope `aggregateId`, and `correlationId` must match.
- Same `eventId` and same fingerprint is a replay no-op.
- Same `eventId` with different content or reused reservation identity with contradictory Order is a
  non-retryable conflict sent to the consumer DLT.
- PostgreSQL reservation transition, command inbox, and result outbox intent commit atomically.
- Kafka acknowledgement occurs only after that commit.

## Provisioning and rollout

The topic and both subjects are provisioned explicitly before the Order producer is enabled. Broker
auto-creation remains disabled. Rollback disables Order command production before restoring images;
committed commands are retained for drain/review.

