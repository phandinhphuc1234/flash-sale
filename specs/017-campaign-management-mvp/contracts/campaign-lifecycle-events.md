# Campaign Lifecycle Kafka Contract v1

**Owner/producer**: Campaign Service  
**Topic**: `campaign.lifecycle.v1`  
**Kafka key**: textual `campaignId`  
**Local topology**: 3 partitions, replication factor 1  
**Delivery**: at least once through transactional outbox

## Compatibility rules

- The topic name and `eventVersion=1` identify the v1 contract family.
- `eventId` is the stable consumer-deduplication key and is never regenerated on publisher retry or
  operator requeue.
- All events for one Campaign use the same Kafka key and therefore the same partition.
- `CampaignScheduled` must be published before `CampaignActivated` for the same aggregate.
- Consumers must ignore unknown additive fields and remain idempotent by `eventId`.
- Removing/renaming a required field, changing meaning/type, or changing partition-key semantics is
  breaking and requires a new approved version/contract.
- Feature 017 does not publish `CampaignEnded`, `CampaignCancelled`, or release events.

## Common envelope

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `eventId` | UUID string | yes | outbox row ID and deduplication identity |
| `eventType` | string | yes | `CampaignScheduled` or `CampaignActivated` |
| `eventVersion` | integer | yes | `1` |
| `aggregateType` | string | yes | `CAMPAIGN` |
| `aggregateId` | UUID string | yes | Campaign ID |
| `aggregateVersion` | integer | yes | Campaign version after transition |
| `occurredAt` | ISO-8601 UTC instant | yes | business transition time |
| `traceId` | string | yes | originating HTTP or scheduler correlation identity |
| `data` | object | yes | event-specific immutable snapshot |

## `CampaignScheduled.v1`

Written in the same Campaign database transaction as `DRAFT -> SCHEDULED` and operation completion.

```json
{
  "eventId": "daef0922-2230-4706-bfd2-0c1116774ac4",
  "eventType": "CampaignScheduled",
  "eventVersion": 1,
  "aggregateType": "CAMPAIGN",
  "aggregateId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
  "aggregateVersion": 3,
  "occurredAt": "2026-07-30T02:00:00Z",
  "traceId": "trace-id",
  "data": {
    "campaignId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
    "campaignCode": "FLASH-SALE-2026-08-01-IPHONE",
    "startAt": "2026-08-01T05:30:00Z",
    "endAt": "2026-08-01T07:30:00Z",
    "item": {
      "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57",
      "inventoryAllocationId": "21aa1d8a-3037-4f2c-aa0e-19ee4f2901ef",
      "variantSku": "IPHONE-16-128-BLACK",
      "campaignPrice": 19900000.0000,
      "currency": "VND",
      "allocatedQuantity": 1000,
      "purchaseLimitPerUser": 1
    }
  }
}
```

Required `data` fields:

- Campaign: ID, normalized code, start/end window;
- item: Variant ID, Inventory allocation ID, SKU snapshot, Campaign price, currency, allocated
  quantity, and purchase limit.

Base Product price and administrator/audit details are intentionally not integration-event fields.

## `CampaignActivated.v1`

Written in the same Campaign database transaction as `SCHEDULED -> ACTIVE`.

```json
{
  "eventId": "f5353fce-f1b3-4196-b63d-f73f3cb51e31",
  "eventType": "CampaignActivated",
  "eventVersion": 1,
  "aggregateType": "CAMPAIGN",
  "aggregateId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
  "aggregateVersion": 4,
  "occurredAt": "2026-08-01T05:30:01Z",
  "traceId": "scheduler-generated-trace-id",
  "data": {
    "campaignId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
    "startAt": "2026-08-01T05:30:00Z",
    "endAt": "2026-08-01T07:30:00Z"
  }
}
```

Activation is not emitted by the schedule workflow. Automatic/manual activation produces it only
after the time/status/snapshot/allocation conditions pass and one conditional transition succeeds.

## Headers

The JSON envelope is canonical. Kafka headers MAY duplicate non-sensitive routing/observability
values for infrastructure use:

```text
eventId
eventType
eventVersion
traceId
contentType=application/json
```

Consumers must not require a duplicated header that already exists in the v1 envelope. No bearer
token, client secret, administrator token, or user PII is placed in the record or headers.

## Publisher retry and recovery

- due rows: `PENDING` and `nextAttemptAt <= now`, oldest eligible aggregate event first;
- publisher claim: short database transaction and expiring processing lease;
- success: mark the same row `PUBLISHED`, record `publishedAt`;
- failure: increment retry count, sanitize `lastError`, and schedule
  `min(60 seconds, 2^retryCount seconds)`;
- tenth failed automatic attempt: mark `FAILED`;
- authorized requeue: same `eventId` and payload return to `PENDING`, attempt counter resets, requeue
  audit increments;
- failed predecessor blocks later events for that Campaign to preserve ordering;
- crash after broker acknowledgement may redeliver the same `eventId`.

## Required contract tests

- exact required envelope/payload types for both events;
- Kafka key equals Campaign ID;
- Scheduled precedes Activated for one Campaign;
- duplicate publication retains the same event ID;
- separate Campaign IDs may be processed independently;
- retry/requeue never recreates a business transition or changes payload/version;
- trace ID propagates from admin/scheduler to envelope and optional header;
- secrets/tokens are absent.
