# Transactional Outbox Flow in Flash Sale

## 1. Purpose and authority

This document explains how the repository currently uses the Transactional Outbox pattern and how
the Campaign Service implementation behaves on one instance or several instances.

It is an implementation guide, not a replacement for an approved feature contract. The governing
order remains:

1. the repository constitution;
2. the owning feature's approved `spec.md`, `plan.md`, `tasks.md`, and contracts;
3. the applicable ADR;
4. this document.

Each service owns its own database and outbox schema. No service reads another service's outbox or
shares an outbox JPA entity/repository.

## 2. Why the Outbox exists

This unsafe dual write can lose an event:

```text
save business state
send Kafka message
```

The database commit and the Kafka publication are separate systems. If the database commits and the
process crashes before Kafka accepts the message, downstream services never receive the event. If
Kafka accepts the message and the database transaction rolls back, the event describes state that
does not exist.

The Outbox pattern separates the two operations safely:

```text
one local PostgreSQL transaction
├── change business state
└── insert an immutable outbox row
COMMIT

separate relay
├── claim due rows
├── publish after the commit
└── mark the same row as PUBLISHED after broker acknowledgement
```

The outbox is not a second business database. PostgreSQL remains the source of truth; the outbox
is a durable handoff record for required integration messages.

## 3. Current service ownership

| Service | Current Outbox state | Current boundary |
|---|---|---|
| Campaign | Implemented for lifecycle events | `campaign_db.campaign_outbox_events` and Avro Kafka relay |
| Inventory | Durable rows are persisted; Kafka publisher is deferred | `inventory_db.outbox_events` |
| Product | No outbox in the read-only catalog feature | Kafka/product events require a later approved feature |
| Authentication | No integration-event outbox in the current MVP | Security/session state stays service-owned |
| Flash Sale | Not implemented yet | Future Redis Stream handoff followed by PostgreSQL journal/outbox |
| Order/Payment | Not implemented yet | Future Saga-owned outboxes |

The Campaign path is the first complete producer path. Future services must adopt the same pattern
only after their topic, schema, retry, ordering, and recovery decisions are approved.

## 4. Campaign business transaction

Campaign scheduling and activation write the business transition and its event row atomically:

```text
Schedule or activate Campaign
        │
        ├── validate and update Campaign state/version
        ├── freeze the approved snapshot when scheduling
        └── insert CampaignScheduled.v1 or CampaignActivated.v1 outbox row
        │
       COMMIT
```

The transaction never calls Product, Inventory, Kafka, or Schema Registry. Remote HTTP calls happen
before the final short local transaction, and Kafka publication happens only after the transaction
has committed.

The row keeps a stable event identity and immutable payload. A publisher retry or operator requeue
does not create a new business transition or a new event ID.

## 5. Campaign relay flow

The relay is [CampaignOutboxPublisherJob.java](../../services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/adapter/in/scheduling/CampaignOutboxPublisherJob.java).
It runs with a fixed delay of 500 milliseconds and claims at most 100 rows per scan.

```text
scheduled scan
    │
    ├── claim PENDING rows that are due
    ├── reclaim PROCESSING rows whose lease expired
    ├── preserve aggregate ordering
    └── mark claimed rows PROCESSING with worker + lease
         │
         ├── publish Avro SpecificRecord to campaign.lifecycle.v1
         │       key = campaignId
         │       headers = W3C traceparent/tracestate and routing metadata
         │
         ├── broker success
         │      └── conditionally mark the same row PUBLISHED
         │
         └── technical failure
                └── record sanitized failure and schedule retry
```

The claim transaction is implemented in [OutboxPersistenceAdapter.java](../../services/campaign-service/src/main/java/com/philia/flashsale/campaign/outbox/adapter/out/persistence/jpa/OutboxPersistenceAdapter.java).
It uses PostgreSQL `FOR UPDATE SKIP LOCKED` and a processing lease:

```sql
FOR UPDATE SKIP LOCKED
```

The lock is held only during the short claim transaction. Kafka I/O does not happen while those
database row locks are held.

## 6. Why `SKIP LOCKED` is used

With one Campaign instance, there is normally one outbox scheduler and no competing claimers.
Nevertheless, `SKIP LOCKED` is retained because a VPS can later run multiple containers or the
service can be scaled horizontally.

```text
Instance A claims rows 1–100
Instance B skips locked rows and claims later eligible rows
Instance C skips both sets and claims the next rows
```

It prevents workers from waiting on rows already claimed by another worker. It does not make Kafka
delivery exactly once, and it does not automatically parallelize the current publisher loop.

The current Campaign relay publishes its claimed list sequentially. Virtual-thread support is
enabled for the service, but no parallel outbox worker pool is configured. A future worker pool must
remain bounded and retain the database lease/ownership checks.

## 7. Lease, retry, and recovery policy

Current Campaign settings are declarative:

| Setting | Current value |
|---|---:|
| Outbox scan delay | 500 ms |
| Batch size | 100 |
| Claim lease | 30 s |
| Automatic attempts | 10 |
| Backoff cap | 60 s |
| Retry schedule | bounded exponential backoff |

The relay marks a row as `PROCESSING` with `claimed_by` and `claimed_until`. If the process dies,
the expired lease makes the row eligible for a later scan. A successful acknowledgement is accepted
only when the current worker still owns the lease.

After the automatic-attempt limit, the row becomes `FAILED`. An authenticated and authorized
operator may requeue the same event through the Campaign admin endpoint. Requeue preserves the event
ID and payload, resets automatic retry state, and records the audit action.

## 8. Crash and failure behavior

| Failure point | Result |
|---|---|
| Business transaction rolls back | No business state or outbox row is committed |
| Kafka/Registry unavailable before publish | Outbox row remains durable and is retried |
| Publisher crashes before Kafka acknowledgement | Lease expires and the same row is retried |
| Kafka acknowledges, then publisher crashes before `PUBLISHED` | The same event may be published again |
| Failed predecessor for one Campaign | Later aggregate versions remain blocked |
| Ten automatic attempts fail | Row becomes `FAILED` until authorized requeue |
| Duplicate delivery to a consumer | Consumer must deduplicate by stable event ID |

At-least-once delivery is intentional. The Outbox guarantees durable handoff and stable identity; it
does not remove the need for idempotent consumers.

## 9. One VPS versus horizontal scaling

### One VPS and one Campaign instance

The current MVP is suitable for this topology:

```text
one Campaign container
    └── one scheduled relay
        └── sequential publication of one claimed batch
```

There is no need to add multiple polling threads just because the service runs on one VPS. More
threads can increase database contention and broker pressure before there is a measured need.

### Several instances on one VPS or several VPSs

The same code can run multiple replicas because the database claim is coordinated by PostgreSQL:

```text
Campaign replica A ─┐
Campaign replica B ─┼── campaign_db claim lease + SKIP LOCKED
Campaign replica C ─┘
```

Each worker receives a unique lease. Conditional `PUBLISHED` and failure updates prevent one worker
from acknowledging another worker's claim. This is horizontal-scaling readiness, not a claim that
the current MVP is already running in parallel.

## 10. Clean/Hexagonal placement

The Outbox responsibilities are split by boundary:

```text
campaign/domain/event/
    business event facts

campaign/application/port/out/
    persistence and publication capabilities

outbox/application/
    claim, retry, failure, and requeue use cases/models

outbox/adapter/out/persistence/jpa/
    PostgreSQL entity, repository, and SKIP LOCKED claim

outbox/adapter/out/messaging/kafka/
    Avro mapping, Kafka serializer, topic, key, and headers

outbox/adapter/in/scheduling/
    scheduled relay trigger only
```

The scheduler does not own business transitions. The domain/application layers do not depend on
`KafkaTemplate`, Schema Registry, JPA entities, or PostgreSQL SQL syntax.

## 11. Observability

Campaign records low-cardinality metrics for outbox claim, publish, retry, lease loss, and claim
failure. The relay restores the stored trace identity in MDC while processing each claim. W3C
`traceparent`/`tracestate` are sent in Kafka headers; trace IDs and credentials are not business
payload fields or metric dimensions.

The current implementation keeps Actuator/Prometheus configuration declarative. A
`PrometheusMeterRegistry` is not constructed in Campaign business code.

## 12. Verification and local operation

Relevant tests include:

- `CampaignOutboxRecoveryIntegrationTests`: claims, lease reclaim, ordering, retry, terminal
  failure, and requeue;
- `CampaignOutboxPublisherJobTests`: scheduler, publish/ack, failure, and trace restoration;
- `CampaignLifecycleEventContractTests`: Avro payload and stable event identity;
- `CampaignLifecycleKafkaPublisherTests`: key, headers, and SpecificRecord publication.

The approved local topic is provisioned by:

```bash
bash infra/docker/kafka/init-campaign-topics.sh
```

The full Campaign module verification is:

```powershell
.\mvnw.cmd -pl services/campaign-service -am verify
```

Live Kafka/Schema Registry integration and the complete Gateway-to-Registry topology smoke remain
explicit validation tasks in Feature 017; they are not implied by unit or module tests.

## 13. Deliberate non-goals

The current Campaign implementation does not introduce:

- Debezium or CDC;
- a shared outbox database/table;
- a DLT;
- Kafka exactly-once business semantics;
- an unbounded publisher thread pool;
- Redis in Campaign Service;
- a consumer inbox, because Campaign is currently a producer for this feature.

Those changes require an owning feature decision, updated contracts/plan, and appropriate tests.
