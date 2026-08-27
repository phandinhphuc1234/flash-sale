# Kafka Contract: Purchase Reservation Results v1

## Topic and ownership

| Property | Value |
|---|---|
| Topic | `flashsale.purchase.events.v1` |
| Producer | Flash Sale Service |
| Consumer | Order Service / Purchase Saga |
| Pre-Order key | `PurchaseAcceptedV1` remains keyed by `purchaseRequestId` |
| Post-Order key | Result records below are keyed by `orderId` |
| Serialization | Avro SpecificRecord with `TopicRecordNameStrategy` |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Consumer DLT | `flashsale.order.purchase-reservation-result.dlt.v1` |

The two records are additive to the existing `PurchaseAcceptedV1`; that accepted schema is not
changed.

Because this contract intentionally shares `flashsale.purchase.events.v1` with the existing
`PurchaseAcceptedV1` consumer, both consumers can observe a record type that is not their own.
Each consumer DLT therefore registers every SpecificRecord that can arrive from the shared source:
the reservation-results DLT also registers `PurchaseAcceptedV1`, while the purchase-accepted DLT
registers `PurchaseAcceptedV1`, `PurchaseReservationConfirmedV1`, and `PurchaseReservationReleasedV1`.
These bindings are poison-record serialization boundaries only; they do not add business events or
change the accepted-purchase contract.

## Common envelope

| Field | Rule |
|---|---|
| `eventId` | Stable UUID generated once with the durable outbox row. |
| `eventType` | Exact semantic name below. |
| `eventVersion` | `1`. |
| `producer` | `flashsale-service`. |
| `aggregateType` | `PURCHASE_RESERVATION`. |
| `aggregateId` | `reservationId`. |
| `aggregateVersion` | Positive durable reservation version. |
| `correlationId` | Saga ID / `purchaseRequestId`. |
| `causationId` | Reservation command ID that caused the result. |
| `occurredAt` | UTC timestamp-millis. |

The Kafka key is `orderId`. Valid W3C trace headers may be propagated; credentials and sensitive
payloads are forbidden.

## PurchaseReservationConfirmedV1

Namespace: `com.philia.flashsale.contract.purchase.event.v1`

`eventType=PurchaseReservationConfirmed`

Data record `PurchaseReservationConfirmedDataV1`:

| Field | Type | Rule |
|---|---|---|
| `sagaId` | UUID | Stable Saga/purchase-request identity. |
| `orderId` | UUID | Equals Kafka key. |
| `purchaseRequestId` | UUID | Equals Saga ID. |
| `reservationId` | UUID | Equals aggregate ID. |
| `paymentId` | UUID | Payment identity supplied by the confirm command. |
| `confirmedAt` | timestamp-millis | Durable PostgreSQL transition time. |

The event means the reservation is durably `CONFIRMED`; Redis projection cleanup may still be
retrying but cannot cause the durable reservation to expire or release quota.

## PurchaseReservationReleasedV1

Namespace: `com.philia.flashsale.contract.purchase.event.v1`

`eventType=PurchaseReservationReleased`

Data record `PurchaseReservationReleasedDataV1`:

| Field | Type | Rule |
|---|---|---|
| `sagaId` | UUID | Stable Saga/purchase-request identity. |
| `orderId` | UUID | Equals Kafka key. |
| `purchaseRequestId` | UUID | Equals Saga ID. |
| `reservationId` | UUID | Equals aggregate ID. |
| `reservationStatus` | string | `RELEASED` or `EXPIRED`. |
| `reason` | string | Approved Payment failure reason or stable `RESERVATION_EXPIRED`. |
| `releasedAt` | timestamp-millis | Durable terminal transition/observation time. |

This event reports that the hold cannot be used for confirmation. Redis quota restoration is
idempotently reconciled and separately observable. An Order waiting to release maps its own stored
Payment reason to `CANCELLED` or `EXPIRED`; an Order waiting to confirm treats this result as a
paid-but-unconfirmable condition and enters manual review.

## Ordering, replay, and conflict rules

- Order validates topic, record type, producer, aggregate identity/version, key, Saga identity,
  reservation identity, and current expected step.
- Every accepted reservation command has exactly one stable result whose `causationId` equals that
  command's `eventId`; replaying the command reuses the same result `eventId`.
- A different command may receive a new result describing the reservation's current durable state,
  even when the reservation aggregate version is unchanged. This is required for a confirm command
  that arrives after release/expiry.
- Same event ID/same fingerprint is a durable no-op.
- Same version/contradictory result is a non-retryable conflict.
- Lower reservation versions are stale and cannot regress state.
- An unchanged-version current-state result is actionable when its `causationId` equals the Saga's
  current `activeCommandId`; it is not discarded merely because an earlier command already reported
  that aggregate version.
- `PurchaseReservationConfirmedV1` completes the Saga only when the verified Payment identity
  agrees.
- `PurchaseReservationReleasedV1` can compensate an unpaid Saga or place a paid Saga into manual
  review; it never triggers automatic refund.
