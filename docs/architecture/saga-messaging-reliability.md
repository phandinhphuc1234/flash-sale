# Saga Messaging and Reliability Design

## Status

This is a proposed repository architecture guide derived from the Flash Sale Saga V2 review. It
does not create or approve Kafka contracts. Existing approved feature contracts remain authoritative.

In particular:

- Feature 017 owns `CampaignScheduled.v1`, `CampaignActivated.v1`, and topic
  `campaign.lifecycle.v1`;
- Schema Registry is available in local infrastructure, but Avro is not yet the approved repository
  serialization format;
- Purchase, Payment, Order, Campaign end, and cancellation messaging require future Spec Kit
  artifacts and, where boundaries change, ADRs.

## 1. Architecture verdict

Use a hybrid approach:

| Interaction | Pattern |
|---|---|
| Campaign schedule needing immediate validation/allocation | Synchronous HTTP orchestration plus durable recovery |
| Purchase/order/payment/reservation | Order-owned orchestrated Saga over Kafka |
| Campaign end/cancellation | Campaign-owned orchestrated Saga over Kafka |
| Product lifecycle, notifications, analytics, projections | Choreography |

Do not create a global Saga service. An orchestrator owns workflow state and next-step decisions;
participants continue to own their domain invariants and data.

## 2. Commands and events

A command requests one specific owner to perform work:

```text
PaymentRequested
ConfirmPurchaseReservation
ReleasePurchaseReservation
StopCampaignSales
ReconcileCampaignStock
```

An event reports a fact that already happened:

```text
PaymentSucceeded
PurchaseReservationConfirmed
CampaignSalesStopped
CampaignStockReconciled
OrderConfirmed
```

Rules:

- One critical command has an explicit success or terminal-result event.
- Transient technical failure is retried internally and is not immediately published as a terminal
  business failure.
- A failure event means the participant reached a defined terminal/review outcome, not merely that
  one network attempt failed.
- Final domain events are not hidden commands to Saga participants.
- DLT records are operational failures, not business compensation events.

## 3. Current and candidate topic catalog

| Topic | Status | Purpose |
|---|---|---|
| `campaign.lifecycle.v1` | Approved by Feature 017 | `CampaignScheduled.v1`, `CampaignActivated.v1` |
| `flashsale.purchase.events.v1` | Candidate | Purchase acceptance and reservation results |
| `flashsale.purchase.commands.v1` | Candidate | Confirm/release reservation commands consumed by Flash Sale |
| `flashsale.payment.commands.v1` | Candidate | Payment commands consumed by Payment |
| `flashsale.payment.events.v1` | Candidate | Payment results consumed by Order |
| `flashsale.order.events.v1` | Candidate | Final Order lifecycle fan-out |
| `flashsale.campaign.commands.v1` | Candidate | Stop/disable projection commands consumed by Flash Sale |
| `flashsale.campaign.results.v1` | Candidate | Stop/disable/drain results produced by Flash Sale |
| `flashsale.inventory.commands.v1` | Candidate | Reconcile/release commands consumed by Inventory |
| `flashsale.inventory.events.v1` | Candidate | Inventory operation results consumed by Campaign |
| `flashsale.product.events.v1` | Candidate | Product lifecycle choreography |

Candidate names must not be provisioned or referenced by production code until the owning feature
defines producer, consumers, schema subjects, authorization, rollout, retention, and recovery.

DLT names may follow `<source-topic>.dlt`, but each feature must define who can replay it and how a
record returns to normal processing.

## 4. Partition keys and ordering

| Message family | Proposed key |
|---|---|
| Product lifecycle | `variantId` |
| Campaign lifecycle and workflow | `campaignId` |
| Purchase before Order exists | `purchaseRequestId` |
| Order/Payment Saga after Order creation | `orderId` |

Kafka ordering exists only within one partition. A feature must keep every message requiring
per-aggregate order on the same stable key. `correlationId` identifies the workflow but does not
automatically define the Kafka partition.

The transition from `purchaseRequestId` to `orderId` must be explicit: `PurchaseAccepted` is keyed
by `purchaseRequestId`; after Order creation, Order-owned commands/events use `orderId` and retain
the purchase Saga ID as correlation metadata.

## 5. Contract metadata

Every future wire contract must define these logical fields, even if an already approved contract
uses different field names:

| Logical field | Purpose |
|---|---|
| Message/event ID | Consumer deduplication; unchanged across technical publication retry |
| Message type and schema version | Dispatch and compatibility |
| Producer | Observability metadata, not authentication |
| Correlation/Saga ID | End-to-end workflow reconstruction |
| Causation ID | The command/event that caused this message |
| Aggregate ID/version | Business traceability and optional stale-event protection |
| Occurred time | Business occurrence timestamp in UTC |
| Payload/data | Message-specific contract |

W3C `traceparent` and approved baggage belong in Kafka headers. JWTs, client secrets, provider
credentials, and raw authorization headers must never enter payloads, headers, logs, or traces.

Do not create one shared Java event class used by all services. Schemas are wire contracts; every
adapter maps them to its service-owned application/domain model. The existing Feature 017 Campaign
event envelope remains unchanged unless a separately approved compatibility migration replaces it.

## 6. PostgreSQL transactional outbox

Campaign, Flash Sale durable acceptance, Order, Payment, and Inventory persist a required outbound
message with the related business change in one local transaction:

```text
BEGIN
  update aggregate/workflow state
  insert outbox row with stable message ID and immutable payload
COMMIT

outbox relay
  -> claim due row in a short transaction
  -> publish Kafka
  -> on ACK mark published
  -> on ambiguous result retry the same message ID
```

The owning feature defines claim leases, batch size, backoff, maximum automatic attempts, terminal
state, operator requeue, cleanup, and aggregate ordering. Kafka producer idempotence does not replace
the outbox because it cannot atomically commit a service PostgreSQL transaction.

## 7. Redis Stream atomic handoff

Flash Sale Lua must atomically mutate reservation/quota and append a recovery entry:

```text
EVAL Lua
  + validate state/idempotency/limit
  + mutate reservation and quota
  + XADD handoff entry
```

The Stream is an atomic Redis handoff log, not the authoritative business outbox. The relay invokes
an idempotent Flash Sale persistence use case that commits the durable purchase/reservation record
and PostgreSQL outbox before acknowledging the Stream entry.

```text
Stream entry
  -> durable Flash Sale PostgreSQL transaction
  -> XACK after commit
  -> PostgreSQL outbox publishes Kafka
```

This corrects two unsafe designs:

- Lua success followed by direct Java-to-Kafka publication;
- treating a single Redis instance/Stream as the only durable record while the Constitution assigns
  durable truth to PostgreSQL.

The feature must also define Redis persistence mode, Stream trimming, pending-entry recovery,
consumer-group ownership, reservation expiry, PostgreSQL reconciliation, and Redis rebuild.

## 8. Idempotent consumers

Database-backed consumers process deduplication, business state, and the next outbox record in one
local transaction:

```text
BEGIN
  insert (consumerName, messageId) into processed_messages
  if duplicate: return the previously established outcome
  apply business transition
  insert result/next-command outbox row when required
COMMIT
commit Kafka offset
```

Required command behavior includes:

- duplicate `PaymentRequested` never charges twice;
- duplicate reservation confirmation returns the existing confirmed result;
- duplicate reservation release never restores quota twice;
- duplicate Inventory reconcile/release never creates a second movement;
- technical redelivery reuses the same incoming message ID;
- a new deliberate payment attempt has a new attempt ID and provider idempotency key.

## 9. Minimum workflow state

### Purchase Saga candidate states

```text
STARTED
PAYMENT_REQUESTED
CONFIRMING_RESERVATION
RELEASING_RESERVATION
COMPLETED
COMPENSATED
FAILED_MANUAL_REVIEW
```

Order status and Saga status remain separate. The feature may add states only when they represent a
recoverable business checkpoint rather than implementation detail.

### Campaign end candidate states

```text
STARTED
STOPPING_SALES
DRAINING_RESERVATIONS
RECONCILING_STOCK
COMPLETED
FAILED_MANUAL_REVIEW
```

### Scheduled cancellation candidate states

```text
STARTED
DISABLING_SALES
RELEASING_ALLOCATION
COMPLETED
FAILED_MANUAL_REVIEW
```

These Campaign states are future operation states; they do not amend Feature 017's Campaign status
enum by themselves.

## 10. Security and identity

### HTTP

- Public traffic enters through API Gateway.
- Gateway validates route-level user authentication/authority.
- The receiving business service validates the user JWT again and applies resource/business
  authorization.
- Current service tokens use RS256, subject/client identity, audience
  `flash-sale-internal-api`, and endpoint-specific scopes.
- Current Campaign calls use `catalog.read` and `inventory.campaign.allocate`.
- Internal endpoint calls do not relay an administrator token.

### Kafka

The `producer` field is metadata and cannot authenticate a Kafka client.

- Local Compose currently relies on the private Docker network and is development-only.
- A production feature must introduce TLS/SASL and topic ACLs before public/VPS exposure.
- Events carry audit subject IDs only when their contract requires them; they never carry JWTs.

## 11. Retry, timeout, DLT, and recovery

- Retry only classified transient technical failures with durable attempt metadata and bounded
  backoff.
- Do not retry business rejection indefinitely.
- Persist deadlines; never use an in-memory timer or `Thread.sleep()` for business expiry.
- Distinguish ambiguous result from confirmed failure.
- Query/reconcile external Payment state before issuing another charge.
- Route poison/unreadable records to a DLT after the approved handling limit.
- Do not trigger compensation from a message the service cannot safely interpret.
- Provide authenticated operator inspection/requeue for terminal operational failures.

Exact TTLs, attempts, backoff, timeout, and manual-review policies remain feature decisions.

## 12. Schema Registry and compatibility

Confluent Schema Registry is available in local infrastructure. Before adopting Avro, Protobuf, or
JSON Schema, an approved feature must define:

- serialization format;
- subject naming strategy;
- compatibility mode;
- required/defaulted/optional field rules;
- producer-first or consumer-first rollout order;
- generated-code ownership;
- contract tests and rollback.

Registry availability does not make Avro approved automatically and does not replace repository
contract files.

## 13. Observability

Logs and traces must reconstruct one workflow using message ID, correlation/Saga ID, causation ID,
aggregate ID, purchase request ID, Order ID, Campaign ID, and W3C trace context.

Monitor at minimum:

- PostgreSQL outbox backlog and oldest row age;
- Redis Stream pending entries and oldest pending age;
- Kafka consumer lag;
- retry and DLT counts;
- Saga state age and manual-review count;
- payment ambiguity/reconciliation count;
- Campaign drain age and unreconciled Campaign count.

## 14. Recommended implementation order

1. Approve one Purchase Saga feature specification and ADR.
2. Approve message contracts, keys, schema strategy, participant ownership, and failure semantics.
3. Implement PostgreSQL outbox/inbox foundations and contract tests.
4. Implement Flash Sale durable reservation journal plus Redis Lua/Stream handoff.
5. Implement the Purchase Saga happy path.
6. Add payment ambiguity, timeout, release compensation, and manual recovery.
7. Implement Campaign end with a drain boundary.
8. Implement scheduled cancellation only after its lifecycle/status policy is approved.

Do not provision all candidate topics first and invent their meaning later. Each phase creates only
the contracts and infrastructure its approved feature actually uses.

## 15. Required acceptance categories

- database commit while Kafka is unavailable;
- relay crash after Kafka ACK before outbox acknowledgement;
- consumer crash after database commit before offset commit;
- Redis mutation followed by application crash before PostgreSQL acceptance;
- duplicate command/event delivery;
- concurrent workflow advancement by multiple instances;
- payment provider timeout after a possible successful charge;
- payment deadline and reservation release;
- post-payment reservation confirmation retry;
- Campaign stop while purchases are in flight;
- no Campaign end before drain and Inventory reconciliation;
- terminal failure inspection and authenticated recovery.
