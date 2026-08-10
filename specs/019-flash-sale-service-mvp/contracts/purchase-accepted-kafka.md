# PurchaseAccepted Kafka Contract

**Producer**: `flashsale-service`  
**Future primary consumer**: `order-service`  
**Topic**: `flashsale.purchase.events.v1`  
**Key**: `purchaseRequestId` UUID string  
**Delivery**: At least once through PostgreSQL transactional outbox

## Schema Governance

| Setting | Value |
|---|---|
| Schema source | `contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.purchase.events.v1/PurchaseAcceptedV1.avsc` |
| Namespace | `com.philia.flashsale.contract.purchase.event.v1` |
| Record | `PurchaseAcceptedV1` generated `SpecificRecord` |
| Subject strategy | `TopicRecordNameStrategy` |
| Compatibility | `BACKWARD_TRANSITIVE` |
| Stable environment registration | `auto.register.schemas=false` |
| Local partitions | 3 |
| Local replication factor | 1 |

The Kafka adapter alone maps the internal outbox snapshot to the generated Avro record. Domain,
application, JPA entities, and HTTP DTOs do not import the generated type.

## `PurchaseAcceptedV1` Fields

Envelope:

| Field | Avro representation | Rule |
|---|---|---|
| `eventId` | string logical UUID | Stable outbox identity across retries. |
| `eventType` | string | Exactly `PurchaseAccepted`. |
| `eventVersion` | int | `1`. |
| `producer` | string | `flashsale-service`. |
| `aggregateType` | string | `PURCHASE_REQUEST`. |
| `aggregateId` | string logical UUID | Same as `purchaseRequestId` and message key. |
| `aggregateVersion` | long | `1` for initial acceptance. |
| `correlationId` | string logical UUID | Stable purchase request correlation ID. |
| `causationId` | nullable string | `null` for the originating HTTP command. |
| `occurredAt` | long logical timestamp-millis | Durable acceptance instant. |
| `data` | nested record | Immutable business snapshot below. |

Data:

| Field | Avro representation | Rule |
|---|---|---|
| `purchaseRequestId` | string logical UUID | Stable logical request. |
| `reservationId` | string logical UUID | Stable reservation identity. |
| `campaignId` | string logical UUID | Campaign reference. |
| `variantId` | string logical UUID | Accepted Variant reference. |
| `userId` | string logical UUID | Authenticated shopper identity. |
| `quantity` | long | Positive. |
| `unitPrice` | bytes decimal(19,4) | Exact accepted unit price snapshot. |
| `currency` | string | Three uppercase letters. |
| `acceptedAt` | long logical timestamp-millis | Acceptance time. |
| `expiresAt` | long logical timestamp-millis | Exactly five minutes later. |

The message intentionally excludes JWTs, secrets, raw authorization/idempotency values, JPA types,
and Redis implementation detail.

## Headers

- `traceparent`: W3C trace context when available.
- `tracestate`: optional W3C vendor context.
- Optional routing/diagnostic headers may duplicate `eventId`, `eventType`, and `eventVersion`, but
  the Avro payload remains authoritative.

## Ordering and Idempotency

- Partition by `purchaseRequestId`.
- Producer uses `acks=all` and idempotence, but end-to-end delivery remains at-least-once.
- Outbox retry preserves key, `eventId`, payload, timestamps, correlation, and trace linkage.
- A consumer deduplicates by `eventId` or stable business identity and must not assume one physical
  delivery.
- Feature 019 does not implement `order-service`; its tests consume and validate the record without
  creating an Order.

## Registry/Broker Failure

Serializer, Registry, broker, timeout, and acknowledgement failures leave the outbox row unpublished.
The relay retries the original event snapshot with exponential backoff capped at 60 seconds. No
second event ID is generated and the accepted HTTP result is not rolled back.

