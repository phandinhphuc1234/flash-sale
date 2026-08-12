# Campaign Projection Kafka Input Contract

**Owner of facts**: `campaign-service`  
**Consumer**: `flashsale-service`  
**Topic**: `campaign.lifecycle.v1`  
**Key**: `campaignId` UUID string  
**Consumer group**: `flashsale-campaign-projection-v1`

## Accepted Records

Feature 019 consumes exactly the already approved generated Avro records:

- `com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1`
- `com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1`

It does not consume JSON strings, JPA entities, `GenericRecord`, or unapproved lifecycle events.
Generated Avro types stop at the inbound Kafka adapter and are mapped to application commands.

## Scheduled Projection

`CampaignScheduledV1` supplies the full purchase snapshot:

- Campaign identity/code, start/end, and aggregate version;
- the one approved Campaign item's Variant ID, Inventory allocation ID, SKU snapshot, exact sale
  unit price, currency, allocated quantity, and per-user limit;
- event/correlation/causation/timestamp metadata from the approved Campaign contract.

The Redis projection update is atomic:

1. Compare the incoming aggregate version with stored version.
2. Duplicate or lower version: no-op and acknowledge.
3. Newer version: replace the complete scheduled snapshot and initial remaining quota, then clear a
   stale recovery marker.

The consumer must not add stock to an existing newer projection when replaying a scheduled event.

## Activated Projection

`CampaignActivatedV1` may transition an already prepared, matching/newer projection to `ACTIVE`.

- Duplicate/stale version: no-op and acknowledge.
- Known compatible projection: update version/state/window atomically.
- Missing scheduled state or inconsistent snapshot: do not initialize quota; mark
  `RECOVERY_REQUIRED`, fail purchase admission closed, and queue snapshot recovery.

## Delivery and Error Policy

- `enable.auto.commit=false`.
- A record is acknowledged only after the idempotent Redis projection result succeeds.
- Delivery is at-least-once; duplicate and out-of-order records are normal.
- Three total delivery attempts use bounded delays of 250 ms then 500 ms.
- After exhaustion, stop the affected listener/container with its offset uncommitted and expose the
  failure through readiness, metrics, and sanitized logs.
- No DLT is introduced by Feature 019. An operator repairs the dependency/schema and restarts from
  the uncommitted offset.
- Deserialization/schema failures are never skipped silently.

## Trace and Security Headers

Consume W3C `traceparent` and optional `tracestate`; start/link a consumer observation according to
Spring Kafka/Micrometer semantics. JWTs, OAuth tokens, passwords, raw idempotency keys, and secrets
are forbidden in Kafka headers and payloads.

## Redis Loss Recovery Boundary

Complete Redis data loss causes purchase admission to fail closed. Feature 019 can rebuild a known
Campaign from lifecycle replay or the approved per-Campaign snapshot recovery endpoint. Before a
Campaign is reopened, remaining quota must account for this service's durable non-expired
reservations. Zero-loss automatic rebuild across every Campaign without an operator/replay source is
not claimed by this MVP.
