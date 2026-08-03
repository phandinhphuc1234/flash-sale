# Kafka Topic and Message Catalog

## Status and authority

This document is the repository-wide target catalog for Kafka topics, commands, events, ownership,
and Saga ordering. It reconciles the Saga and end-to-end architecture guides; it does not approve
candidate contracts by itself.

Current approved production scope remains:

- topic `campaign.lifecycle.v1`;
- `CampaignScheduled.v1`;
- `CampaignActivated.v1`.

Every other topic or message below is a candidate. Its owning feature must approve the Avro schema,
producer, consumers, partition key, authorization, retention, retry/DLT, rollout, and recovery
policy before production code or topic provisioning is added.

## 1. Reconciliation result

The proposed `8 topics / 16 events / 3 commands / 19 messages` catalog is not Saga-complete.
Its own list contains 17 events, and it omits the reservation-confirmation round trip and the
Campaign stop/disable result boundaries.

The corrected full-system target is:

| Item | Count |
|---|---:|
| Main business topics | 11 |
| Event types | 22 |
| Command types | 7 |
| Total business message types | 29 |

The count excludes Kafka internal topics, Schema Registry's `_schemas`, retry topics, and DLTs.
Those are operational artifacts rather than business message types.

## 2. Canonical topic catalog

| # | Topic | Status | Owner / consumer | Messages | Key |
|---:|---|---|---|---|---|
| 1 | `flashsale.product.events.v1` | Candidate | Product -> Inventory; Campaign only through a future approved policy | `ProductVariantCreated.v1`, `ProductVariantUpdated.v1`, `ProductVariantDisabled.v1` | `variantId` |
| 2 | `campaign.lifecycle.v1` | Two events approved; two candidate | Campaign -> Flash Sale and fan-out consumers | `CampaignScheduled.v1`, `CampaignActivated.v1`, `CampaignEnded.v1`, `CampaignCancelled.v1` | `campaignId` |
| 3 | `flashsale.campaign.commands.v1` | Candidate | Campaign -> Flash Sale | `StopCampaignSales.v1`, `DisableCampaignSales.v1` | `campaignId` |
| 4 | `flashsale.campaign.results.v1` | Candidate | Flash Sale -> Campaign | `CampaignSalesStopped.v1`, `CampaignReservationsDrained.v1`, `CampaignSalesDisabled.v1` | `campaignId` |
| 5 | `flashsale.purchase.commands.v1` | Candidate | Order -> Flash Sale | `ConfirmPurchaseReservation.v1`, `ReleasePurchaseReservation.v1` | `orderId` |
| 6 | `flashsale.purchase.events.v1` | Candidate | Flash Sale -> Order | `PurchaseAccepted.v1`, `PurchaseReservationConfirmed.v1`, `PurchaseReservationReleased.v1` | See key transition below |
| 7 | `flashsale.payment.commands.v1` | Candidate | Order -> Payment | `PaymentRequested.v1` | `orderId` |
| 8 | `flashsale.payment.events.v1` | Candidate | Payment -> Order | `PaymentSucceeded.v1`, `PaymentFailed.v1` | `orderId` |
| 9 | `flashsale.order.events.v1` | Candidate | Order -> notification, analytics, audit, and optional projections | `OrderCreated.v1`, `OrderConfirmed.v1`, `OrderCancelled.v1`, `OrderExpired.v1` | `orderId` |
| 10 | `flashsale.inventory.commands.v1` | Candidate | Campaign -> Inventory | `ReconcileCampaignStock.v1`, `ReleaseCampaignAllocation.v1` | `campaignId` |
| 11 | `flashsale.inventory.events.v1` | Candidate | Inventory -> Campaign | `CampaignStockReconciled.v1`, `CampaignStockReconciliationFailed.v1`, `CampaignAllocationReleased.v1` | `campaignId` |

`PurchaseAccepted.v1` is keyed by `purchaseRequestId` because the Order does not exist yet. After
Order creation, Order-owned commands and results use `orderId` and retain `purchaseRequestId` plus
the Saga/correlation ID as metadata. This identity transition must be explicit in every schema.

## 3. Message totals by family

| Family | Events | Commands | Total |
|---|---:|---:|---:|
| Product | 3 | 0 | 3 |
| Campaign lifecycle | 4 | 0 | 4 |
| Campaign control/results | 3 | 2 | 5 |
| Purchase reservation | 3 | 2 | 5 |
| Payment | 2 | 1 | 3 |
| Order | 4 | 0 | 4 |
| Inventory | 3 | 2 | 5 |
| **Total** | **22** | **7** | **29** |

## 4. Correct purchase Saga

```text
Client -> Gateway -> Flash Sale                         HTTPS/REST
Flash Sale -> Redis Lua + Redis Stream                 atomic reservation handoff
Flash Sale -> PostgreSQL journal + outbox              durable acceptance
Flash Sale -> PurchaseAccepted                         Kafka event

Order -> create PENDING_PAYMENT + Purchase Saga
Order -> PaymentRequested                              Kafka command
Payment -> payment provider                            HTTPS
Payment -> PaymentSucceeded / PaymentFailed            Kafka event

PaymentSucceeded
  -> Order sends ConfirmPurchaseReservation
  -> Flash Sale confirms reservation idempotently
  -> PurchaseReservationConfirmed
  -> Order becomes CONFIRMED
  -> OrderConfirmed

terminal PaymentFailed or payment deadline
  -> Order sends ReleasePurchaseReservation
  -> Flash Sale releases reservation idempotently
  -> PurchaseReservationReleased
  -> Order becomes CANCELLED or EXPIRED
  -> OrderCancelled or OrderExpired
```

`PaymentFailed` represents an approved failed payment-attempt outcome. A transient timeout or
ambiguous provider response is retried/reconciled by Payment and must not be mislabeled as a final
business failure.

## 5. Correct Campaign preparation and activation

```text
Admin -> Gateway -> Campaign                           HTTPS/REST
Campaign -> Product                                    synchronous validation/snapshot
Campaign -> Inventory                                  synchronous idempotent allocation
Campaign -> local final transaction
  + DRAFT -> SCHEDULED
  + freeze snapshot/allocation
  + insert CampaignScheduled outbox row
Campaign -> CampaignScheduled                          Kafka event
Flash Sale -> create non-purchasable SCHEDULED projection

Campaign start boundary
  -> CampaignActivated                                 Kafka event
  -> Flash Sale activates Redis projection
```

The repository contract uses `CampaignScheduled.v1`, not `CampaignPrepared`. Scheduling needs an
immediate administrator result, so Product validation and Inventory allocation stay synchronous.
No `StockAllocated` Kafka event controls this step.

## 6. Correct Campaign end Saga

```text
Campaign reaches endAt
  -> StopCampaignSales command
Flash Sale stops new acceptance
  -> CampaignSalesStopped event with stop watermark
Flash Sale drains all pre-watermark reservations
  -> CampaignReservationsDrained event
Campaign
  -> ReconcileCampaignStock command
Inventory commits reconciliation
  -> CampaignStockReconciled event
Campaign
  -> status ENDED
  -> CampaignEnded event
```

`CampaignEnded` is the final fact. It must not be emitted before sales stop, reservation drain, and
Inventory reconciliation complete. A defined terminal reconciliation failure may emit
`CampaignStockReconciliationFailed`; transient failures stay in durable retry state.

## 7. Correct scheduled Campaign cancellation Saga

```text
Admin requests cancellation
Campaign -> DisableCampaignSales command
Flash Sale disables/removes the projection
  -> CampaignSalesDisabled event
Campaign -> ReleaseCampaignAllocation command
Inventory releases the allocation
  -> CampaignAllocationReleased event
Campaign
  -> status CANCELLED
  -> CampaignCancelled event
```

`CampaignCancelled` is also a final fact. Active Campaign cancellation remains out of scope until
the project defines how to handle in-flight reservations and captured payments.

## 8. Ownership rules

- Campaign owns Campaign lifecycle and the end/cancellation orchestration state.
- Flash Sale owns the hot-path reservation and Redis projection, but PostgreSQL remains its durable
  business truth.
- Order owns Purchase Saga progress and final Order status.
- Payment owns payment attempts and provider reconciliation; it never changes Order directly.
- Inventory owns authoritative stock, allocations, movements, reconciliation, and release.
- Product owns sellable Variant facts; consumers never read Product's database.
- Commands request one owner to act. Events report committed facts and must not act as hidden
  commands.
- Required PostgreSQL state changes plus publication use a transactional outbox. Flash Sale first
  closes the Redis Lua crash gap with a Redis Stream handoff, then persists its PostgreSQL
  journal/outbox.
- All contracts are Avro schema-first generated `SpecificRecord` types. Kafka values are not JSON
  strings, JPA entities, domain aggregates, reflection records, or application-wide
  `GenericRecord` values.
- W3C `traceparent` and optional `tracestate` travel in Kafka headers; JWTs and secrets never do.

## 9. Implementation status and order

```text
Implemented/approved now
  -> campaign.lifecycle.v1 contract family
  -> CampaignScheduled.v1 and CampaignActivated.v1 schemas
  -> Avro SpecificRecord contract module and compatibility tests

Still candidate
  -> Product lifecycle messages
  -> Purchase/Payment/Order Saga messages
  -> Campaign end/cancellation commands, results, and final events
  -> Inventory reconcile/release messages
```

Do not create all candidate topics immediately. Implement one approved feature slice at a time:

1. finish the Campaign lifecycle Avro outbox publisher and one idempotent consumer;
2. approve Purchase Saga decisions and contracts;
3. implement purchase/payment/reservation happy path;
4. add failure, deadline, compensation, and manual recovery;
5. add Campaign drain/end;
6. add scheduled cancellation only after its lifecycle amendment is approved.

## 10. Avro schema source layout

Schema files are grouped by their Kafka topic family under the protocol-only contract module:

```text
contracts/kafka-avro-contracts/
└── src/main/avro/
    └── topics/
        └── campaign.lifecycle.v1/
            ├── CampaignScheduledV1.avsc
            └── CampaignActivatedV1.avsc
```

The folder name documents the owning topic; it is source organization only. The Avro `namespace`
and generated Java package remain contract-owned names, so moving a schema into a topic folder does
not change its wire identity or the generated `SpecificRecord` type.

Each future topic family gets its own folder and each message type gets its own `.avsc` file:

```text
src/main/avro/topics/<topic-name>/
├── <MessageTypeA>V1.avsc
└── <MessageTypeB>V1.avsc
```

Do not place JPA entities, domain models, service implementation classes, or JSON payloads in this
module. A schema belongs in a topic folder only after its owning feature approves the topic,
producer, consumers, key, subject naming, compatibility, and recovery policy.
