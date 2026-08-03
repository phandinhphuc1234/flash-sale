# Flash Sale End-to-End Business Flow

## Status and authority

This document is a target workflow guide. It does not approve production behavior, Kafka topics,
Redis TTLs, payment policy, or Campaign lifecycle changes by itself.

The Feature 017 baseline and pending amendment govern Campaign:

- `DRAFT -> SCHEDULED -> ACTIVE -> ENDED`;
- `CampaignScheduled.v1` and `CampaignActivated.v1`;
- synchronous Product validation and Inventory allocation during scheduling;
- no Campaign cancellation or ended event in that MVP.

The amendment draft selects Avro SpecificRecords through Confluent Schema Registry for the two
lifecycle events. Until re-approval, the existing JSON baseline remains the runtime contract.

Purchase, payment, Campaign draining/end orchestration, and cancellation below are proposed future
features. Their specs, ADRs, contracts, plans, and tasks must be approved before implementation.

## 1. Ownership and workflow style

| Workflow | Owner | Coordination style |
|---|---|---|
| Campaign scheduling | Campaign Service | Synchronous application orchestration plus durable recovery |
| Campaign activation | Campaign Service | Local scheduled use case plus outbox event |
| Purchase/payment | Order Service | Kafka Saga orchestration |
| Campaign end | Campaign Service | Proposed Kafka Saga orchestration |
| Scheduled Campaign cancellation | Campaign Service | Proposed Kafka Saga orchestration |
| Notifications, analytics, projections | Event owner plus independent consumers | Choreography |

There is no global Saga service. The owner stores workflow state but does not absorb Product,
Inventory, Payment, or Flash Sale business rules.

## 2. Complete lifecycle overview

```text
Product/Inventory setup
  -> Campaign schedule
  -> CampaignScheduled.v1
  -> Flash Sale projection prepared
  -> Campaign activation
  -> CampaignActivated.v1
  -> Flash Sale projection active
  -> customer purchase reservation
  -> Order-owned Purchase Saga
  -> payment result
  -> reservation confirm/release
  -> final Order event
  -> stop new Campaign sales
  -> drain accepted reservations
  -> Inventory reconciliation
  -> Campaign ended
```

The purchase and Campaign end portions are target behavior, not current code.

## 3. Campaign scheduling — current approved direction

The administrator needs an immediate answer, so scheduling stays synchronous.

```text
Admin
  -> API Gateway                       REST + administrator JWT
  -> Campaign Service                 REST + administrator JWT

Campaign Service
  -> create/load ScheduleOperation    local TX A, then COMMIT
  -> Authentication                   client_credentials token when needed
  -> Product Service                  REST, validate variant and obtain snapshot
  -> Inventory Service                REST, idempotently allocate Campaign quota
  -> revalidate Campaign              local TX B
     + DRAFT -> SCHEDULED
     + freeze Product/price/quota snapshot
     + complete ScheduleOperation
     + insert CampaignScheduled.v1 outbox row
  -> COMMIT
```

Rules:

- No database transaction remains open during OAuth2, Product, or Inventory calls.
- The same administrator idempotency key and request fingerprint reuse the same operation.
- Every Inventory retry uses the same Inventory request identity.
- The HTTP allocation result controls scheduling; no `StockAllocated` Kafka result is required.
- The approved integration fact is `CampaignScheduled.v1`, not `CampaignPrepared`.
- A recovery job resumes an ambiguous or interrupted operation.

After publication, Flash Sale may create a non-purchasable `SCHEDULED` projection. It must not
accept purchases until activation.

## 4. Campaign activation — current approved direction

```text
Campaign scheduler
  -> ActivateCampaign use case
  -> conditional SCHEDULED -> ACTIVE
  -> insert CampaignActivated.v1 outbox row in the same local transaction
  -> Kafka relay
  -> Flash Sale consumes CampaignActivated.v1 idempotently
  -> Redis projection becomes ACTIVE
```

The scheduler is only a driving adapter. The application/domain rules decide whether activation is
allowed. Duplicate activation delivery must not recreate the projection or reset quota.

## 5. Purchase acceptance — proposed future feature

### 5.1 Hot-path reservation

```text
Client
  -> API Gateway                       HTTPS/REST + user JWT
  -> Flash Sale Service                REST + user JWT revalidation
  -> Redis Lua atomically
     + validate Campaign ACTIVE
     + validate remaining quota
     + validate per-user limit
     + enforce request idempotency
     + create RESERVED reservation
     + XADD an atomic handoff entry to a Redis Stream
```

The Redis Stream closes the application-crash gap between the Lua mutation and the next step, but
it is not the durable business source of truth.

### 5.2 Durable acceptance boundary

PostgreSQL remains authoritative. Flash Sale therefore needs a service-owned durable acceptance
record and PostgreSQL outbox:

```text
Redis Lua + Stream entry
  -> Flash Sale idempotent persistence use case
     + insert/load purchase request
     + insert/load reservation journal
     + insert PurchaseAccepted outbox row
  -> PostgreSQL COMMIT
  -> acknowledge Redis Stream entry
  -> PostgreSQL outbox relay publishes PurchaseAccepted to Kafka
```

The request should return `202 Accepted` only after the durable acceptance record exists. If the
process crashes after Lua but before PostgreSQL commit, the Stream relay invokes the same idempotent
persistence use case. If PostgreSQL committed but Stream acknowledgement was lost, redelivery is
harmless.

Returning `202` after Redis alone would make Redis the effective business truth and conflicts with
the repository Constitution. A future feature may propose another durability boundary only through
an explicit ADR and Constitution-compatible plan.

## 6. Purchase Saga — proposed Order-owned orchestration

### 6.1 Happy path

```text
PurchaseAccepted
  -> Order Service
     + create Order = PENDING_PAYMENT
     + create PurchaseSaga
     + insert PaymentRequested command in Order outbox

PaymentRequested
  -> Payment Service
     + create/reuse PaymentAttempt
     + call provider with stable provider idempotency key
     + persist provider result and PaymentSucceeded outbox event

PaymentSucceeded
  -> Order Saga = CONFIRMING_RESERVATION
  -> ConfirmPurchaseReservation command

ConfirmPurchaseReservation
  -> Flash Sale Lua: RESERVED -> CONFIRMED
  -> durable Flash Sale result record/outbox
  -> PurchaseReservationConfirmed event

PurchaseReservationConfirmed
  -> Order = CONFIRMED
  -> PurchaseSaga = COMPLETED
  -> OrderConfirmed outbox event
```

Order coordinates the process. Payment never cancels Order directly, and Flash Sale never decides
the final Order state.

### 6.2 Payment failure or deadline

```text
PaymentFailed
  -> Order evaluates the approved retry/deadline policy
     -> retry remains allowed: emit a new idempotent PaymentRequested attempt
     -> terminal failure/deadline: emit ReleasePurchaseReservation

ReleasePurchaseReservation
  -> Flash Sale Lua: RESERVED -> RELEASED exactly once
  -> PurchaseReservationReleased

PurchaseReservationReleased
  -> Order = CANCELLED or EXPIRED
  -> PurchaseSaga = COMPENSATED
  -> OrderCancelled or OrderExpired
```

The reservation TTL, payment deadline, retry count, and the distinction between `CANCELLED` and
`EXPIRED` are business decisions and must not be inferred during implementation.

### 6.3 Pivot and forward recovery

`PaymentSucceeded` is the proposed pivot point. After it:

- never charge the same payment attempt again;
- retry reservation confirmation idempotently;
- query/reconcile ambiguous provider state;
- prefer forward recovery to complete the paid Order;
- enter manual review if safe automatic recovery is impossible;
- treat refund as a separate audited workflow, not a database rollback.

## 7. Campaign end — proposed future Saga

Stopping new sales is not sufficient for reconciliation because accepted purchases may still be
paying or compensating. The workflow needs an explicit drain boundary.

```text
Campaign reaches endAt
  -> Campaign end operation = STOPPING_SALES
  -> StopCampaignSales command

Flash Sale
  -> reject new purchase acceptance
  -> CampaignSalesStopped event with a stable stop watermark

Campaign end operation = DRAINING_RESERVATIONS

Flash Sale
  -> wait until every reservation accepted before the watermark is terminal
     (CONFIRMED or RELEASED)
  -> build the sales summary from durable Flash Sale records
  -> CampaignReservationsDrained event

Campaign end operation = RECONCILING_STOCK
  -> ReconcileCampaignStock command carrying the approved summary identity

Inventory
  -> idempotently reconcile the Campaign allocation
  -> persist movement plus result outbox
  -> CampaignStockReconciled event

Campaign
  -> ENDED
  -> CampaignEnded event
```

`CampaignEnded` may be emitted only after:

1. new sales are stopped;
2. pre-stop accepted reservations are terminal;
3. Inventory reconciliation is committed.

Feature 017 currently ends a Campaign locally and emits no ended event. The workflow above requires
a new feature, lifecycle model change, migration, event contracts, and ADR review.

## 8. Scheduled Campaign cancellation — proposed future Saga

A scheduled Campaign has allocation/projection state but no active purchase reservations:

```text
Admin cancellation request
  -> Campaign cancellation operation = DISABLING_SALES
  -> DisableCampaignSales command
  -> Flash Sale removes/disables projection
  -> CampaignSalesDisabled result
  -> Campaign cancellation operation = RELEASING_ALLOCATION
  -> ReleaseCampaignAllocation command
  -> Inventory releases allocation idempotently
  -> CampaignAllocationReleased result
  -> Campaign = CANCELLED
  -> CampaignCancelled event
```

Active Campaign cancellation remains out of scope until policy exists for in-flight reservations,
payment attempts, captured payments, drain boundaries, and reconciliation.

## 9. Independent choreography

Final domain facts can fan out without becoming hidden workflow commands:

```text
OrderConfirmed
  -> Notification consumer
  -> Analytics consumer
  -> Audit consumer

CampaignActivated
  -> Flash Sale projection consumer
  -> Notification consumer
  -> Analytics consumer
```

A Notification failure never rolls back Order, Payment, or Campaign. Each consumer uses its own
consumer group and idempotency boundary.

## 10. Decisions still required before purchase implementation

| Decision | Why it is required |
|---|---|
| Reservation TTL versus payment deadline | Prevents a reservation expiring during a valid payment |
| Payment authorization versus immediate capture | Defines the pivot and compensation/refund policy |
| Final durable stock-consumption owner and movement | Defines Inventory/Flash Sale reconciliation truth |
| Durable sales-summary fields and watermark | Makes Campaign end reproducible and auditable |
| Terminal reservation-confirmation failure policy | Defines forward recovery versus refund/manual review |
| Active Campaign cancellation | Must handle in-flight purchases and captured payments |
| Kafka topic/schema compatibility policy | Required before producers and consumers are implemented |

These decisions belong in the governing feature artifacts rather than this architecture guide.
