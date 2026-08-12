# Campaign Lifecycle Kafka Contract v1 — Avro SpecificRecord

**Status**: Approved by Feature 017 amendment and ADR 0016

**Owner/producer**: Campaign Service  
**Topic**: `campaign.lifecycle.v1`  
**Kafka key**: textual `campaignId`  
**Local topology**: 3 partitions, replication factor 1  
**Delivery**: at least once through PostgreSQL transactional outbox

The previous JSON envelope is superseded by this amendment. The selected wire format is Avro
schema-first with generated `SpecificRecord` classes and Confluent Schema Registry. The protocol
module, generated records, and Campaign runtime publisher are implemented; live Registry/Kafka
integration remains an explicit opt-in validation profile.

## Schema source files

```text
contracts/kafka-avro-contracts/src/main/avro/
└── com/philia/flashsale/contract/campaign/lifecycle/v1/
    └── topics/campaign.lifecycle.v1/
        ├── CampaignScheduledV1.avsc
        └── CampaignActivatedV1.avsc
```

These are protocol-only files. They must not contain JPA entities, domain models, or application
logic.

## Compatibility rules

```properties
value.subject.name.strategy=io.confluent.kafka.serializers.subject.TopicRecordNameStrategy
compatibility=BACKWARD_TRANSITIVE
auto.register.schemas=false
```

With that strategy the value subjects are:

```text
campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignScheduledV1
campaign.lifecycle.v1-com.philia.flashsale.contract.campaign.lifecycle.v1.CampaignActivatedV1
```

- Register and compatibility-check a schema before a producer rollout.
- Deploy consumers that support a compatible revision before producers emit it.

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
| `aggregateVersion` | long | yes | Campaign version after transition |
| `occurredAt` | ISO-8601 UTC instant | yes | business transition time |
| `data` | typed Avro record | yes | event-specific immutable snapshot |

`traceId` is not a business record field in this amendment. W3C `traceparent` and optional
`tracestate` are Kafka headers. The outbox may retain internal trace metadata for recovery, but the
publisher must not serialize it into the Avro business body.

## `CampaignScheduled.v1`

Written in the same Campaign database transaction as `DRAFT -> SCHEDULED` and operation completion.
The relay serializes the generated `CampaignScheduledV1` SpecificRecord from the schema source file;
there is no canonical JSON wire representation.

Required `data` fields:

- Campaign: ID, normalized code, start/end window;
- item: Variant ID, Inventory allocation ID, SKU snapshot, Campaign price, currency, allocated
  quantity, and purchase limit.

Base Product price and administrator/audit details are intentionally not integration-event fields.

## `CampaignActivated.v1`

Written in the same Campaign database transaction as `SCHEDULED -> ACTIVE`.
The relay serializes the generated `CampaignActivatedV1` SpecificRecord after the conditional
transition succeeds.

Activation is not emitted by the schedule workflow. Automatic/manual activation produces it only
after the time/status/snapshot/allocation conditions pass and one conditional transition succeeds.

## Headers

The Avro record is canonical. Kafka headers carry W3C distributed-tracing context and non-sensitive
routing metadata:

```text
traceparent
tracestate (optional)
eventId (optional routing copy)
eventType (optional routing copy)
eventVersion (optional routing copy)
contentType=application/avro
```

Consumers must not require optional routing copies. No bearer token, client secret, administrator
token, or user PII is placed in the record or headers.

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

## Registry rollout and outage recovery

- Register and compatibility-check the exact subjects above before deploying a producer revision;
  the Campaign runtime uses `auto.register.schemas=false` and therefore cannot silently create a
  new subject in production.
- Deploy or verify consumers that understand the compatible revision before enabling a producer
  that emits it. A breaking semantic change requires a new approved record/topic version.
- If Kafka or Schema Registry is unavailable, the publisher leaves the original outbox row leased
  only until the claim lease expires, then retries it with the same event ID and payload. The
  business transition is not rolled back and no replacement event is created.
- Topic provisioning is root-owned by `infra/docker/kafka/init-campaign-topics.sh`; it verifies
  the approved local three-partition, replication-factor-one shape before a live test is run.

## Required contract tests

- exact required envelope/payload types for both events;
- Kafka key equals Campaign ID;
- Scheduled precedes Activated for one Campaign;
- duplicate publication retains the same event ID;
- separate Campaign IDs may be processed independently;
- retry/requeue never recreates a business transition or changes payload/version;
- W3C trace context propagates from admin/scheduler to `traceparent` and optional `tracestate`;
- Schema Registry compatibility rejects incompatible revisions before publication;
- Registry/Kafka outage leaves the original outbox identity pending for retry;
- secrets/tokens are absent.
