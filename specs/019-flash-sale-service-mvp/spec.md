# Feature Specification: Flash Sale Reservation MVP

**Feature Branch**: `019-flash-sale-service-mvp`  
**Created**: 2026-08-10  
**Status**: Approved  
**Owner**: Project owner  
**Reviewers**: Project owner; architecture reviewer; security reviewer  
**Input**: Implement the repository-aligned Flash Sale Service MVP for an internship portfolio.

> This feature is classified under `flash-sale-risk-profile` because it affects stock correctness,
> concurrent idempotency, Redis atomicity, durable recovery, and Kafka delivery. Production code
> must not begin until this specification, contracts, plan, and tasks are approved.

## Problem and Scope

**Business problem**: A large number of shoppers may attempt to buy a small Campaign allocation at
the same time. Processing every attempt through durable Inventory storage would increase latency,
overload the database, and make overselling more likely. A winning shopper also needs a stable,
queryable result even when a process or downstream component temporarily fails.

**Goal**: Select valid winners without overselling, durably record each accepted purchase request,
and hand it to the future Order workflow without making Inventory or another service database part
of the purchase hot path.

**In scope**:

- Receive the currently approved Campaign scheduled and activated facts.
- Maintain a local sale projection without owning the Campaign lifecycle.
- Accept authenticated reservation requests through API Gateway.
- Enforce Campaign availability, Variant identity, positive quantity, per-user purchase limit,
  request idempotency, and remaining Campaign allocation as one atomic decision.
- Durably record accepted purchase requests and reservations before reporting durable acceptance.
- Publish one versioned purchase-accepted fact for a future Order consumer.
- Let an authenticated shopper query their own durable reservation.
- Recover safely from duplicate delivery, process crashes, and temporary dependency outages.
- Provide correctness, concurrency, contract, failure, smoke, and portfolio load-test evidence.

**Out of scope**:

- Order creation, payment processing, refunds, and notification.
- Reservation confirmation/release commands from Order.
- Campaign end/cancellation orchestration and Inventory reconciliation.
- Product or Inventory calls during a purchase request.
- Multi-item cart checkout, waiting room, lottery, CAPTCHA, or bot detection.
- Redis Cluster deployment, multi-region operation, stock sharding, Kafka transactions, Debezium,
  or a distributed scheduler framework.
- A dedicated Gateway purchase rate-limit policy; it may be added by a later approved feature after
  measured traffic evidence. Business purchase limits remain in this feature.
- Any claim of end-to-end exactly-once delivery.

## Clarifications

### Session 2026-08-10

- Q: What reservation lifetime and expiry outcome apply before Order integration? → A: Five-minute
  TTL; expiry releases quota idempotently and publishes no expiry event in Feature 019.
- Q: What happens when hot quota is reserved and handed off but PostgreSQL is unavailable? → A:
  Preserve the winner for Redis Stream forward recovery until reservation expiry; report acceptance
  pending and require same-key retry until the outcome becomes durable or expires.
- Q: What are the idempotency key scope, retention, and reuse rules? → A: Scope by user and
  Campaign, retain through Campaign end plus 24 hours, then allow reuse after idempotency cleanup.

## Baseline References and Requirement Delta

| Reference | Governing content | Confirmed delta |
|-----------|-------------------|-----------------|
| [Repository-aligned proposal](../../docs/architecture/flash-sale-service-mvp-proposal.md) | Reviewed service boundary, flow, risks, and implementation slices | This specification extracts observable Feature 019 behavior and leaves implementation detail for planning |
| [Feature 017 Campaign lifecycle contract](../017-campaign-management-mvp/contracts/campaign-lifecycle-events.md) | Approved scheduled and activated Campaign facts | Flash Sale becomes an idempotent consumer; no new Campaign event is added |
| [Feature 017 Campaign snapshot contract](../017-campaign-management-mvp/contracts/internal-campaign-snapshot-http.md) | Recovery-only Campaign snapshot and Flash Sale service identity | Flash Sale may recover a missing/stale projection through this existing contract |
| [Kafka topic catalog](../../docs/kafka/04-topic-message-catalog.md) | Candidate purchase topic, message name, key transition, and ownership | Feature 019 proposes approval of `PurchaseAccepted.v1` only |
| [Flash Sale end-to-end flow](../../docs/architecture/flash-sale-end-to-end-flow.md) | Redis handoff, PostgreSQL durability, and future Order-owned Saga | Feature 019 implements only acceptance through purchase-event publication |
| [HTTP response standardization](../018-http-response-standardization/spec.md) | Shared success/error envelopes and header-only trace identity | New public Flash Sale endpoints use the standardized shape from their first version |

## User Scenarios & Testing

### User Story 1 - Reserve Campaign quota safely (Priority: P1)

As an authenticated shopper, I want one purchase attempt to reserve available Campaign quota
without overselling or exceeding my Campaign limit, so that a successful response represents one
stable winning request.

**Why this priority**: Winner selection is the core business value and highest correctness risk.

**Independent Test**: With an active Campaign allocation of 100 units, submit at least 1,000
concurrent valid attempts and prove that no more than 100 units are accepted, remaining quota never
becomes negative, and each shopper remains within the configured purchase limit.

**Use-case references**: UC-FS-001 Reserve a Campaign purchase

**Acceptance Scenarios**:

1. **Given** an active Campaign item with sufficient remaining quota, **When** an authenticated
   shopper submits a valid request, **Then** one reservation identity is established and the exact
   quantity is removed from remaining Campaign quota.
2. **Given** remaining quota below the requested quantity, **When** a shopper attempts a
   reservation, **Then** the request is rejected as sold out and no quota or user quantity changes.
3. **Given** a shopper has reached the Campaign purchase limit, **When** another request is made,
   **Then** it is rejected and remaining quota is unchanged.
4. **Given** a Campaign is not active or its sale window has not started/has ended, **When** a
   reservation is requested, **Then** it is rejected and no quota changes.
5. **Given** many requests race for the final units, **When** they complete, **Then** accepted plus
   remaining quota reconciles to the allocation and remaining quota is never negative.

---

### User Story 2 - Establish a durable accepted purchase (Priority: P1)

As a winning shopper, I want my accepted request to survive process and broker failures, so that it
can be queried and handed to Order without silently disappearing.

**Why this priority**: A fast result without durable recovery would create lost winners and invalid
stock accounting.

**Independent Test**: Stop the process at each boundary between the atomic quota decision, durable
acceptance, handoff acknowledgement, and event publication; after recovery, prove one durable
reservation and one stable purchase-event identity exist.

**Use-case references**: UC-FS-002 Persist accepted purchase; UC-FS-003 Publish purchase fact

**Acceptance Scenarios**:

1. **Given** a winning atomic decision, **When** durable acceptance succeeds, **Then** the purchase
   request, reservation, and required publication record exist as one durable outcome.
2. **Given** the service stops after winner selection but before durable acceptance, **When** it
   recovers, **Then** the same accepted request is processed without a second quota decrement.
3. **Given** durable acceptance exists but the handoff acknowledgement was lost, **When** recovery
   repeats, **Then** the existing result is replayed without another reservation or event identity.
4. **Given** the broker or Schema Registry is unavailable, **When** acceptance commits, **Then** the
   reservation remains queryable and the original publication identity remains retryable.
5. **Given** an event was delivered but its publication acknowledgement was lost, **When** delivery
   repeats, **Then** the repeated message retains the same event identity.
6. **Given** PostgreSQL is temporarily unavailable after winner selection, **When** the request
   cannot become durable immediately, **Then** the winner remains recoverable until its expiry and
   the shopper receives an acceptance-pending outcome rather than a false durable success.

---

### User Story 3 - Project Campaign availability (Priority: P1)

As an operator, I want Flash Sale to follow approved Campaign schedule and activation facts without
resetting consumed quota, so that purchases open only for a valid prepared Campaign.

**Why this priority**: The reservation path cannot operate safely without an accurate Campaign
snapshot and activation boundary.

**Independent Test**: Deliver scheduled, duplicate scheduled, activated, stale, and out-of-order
Campaign facts and verify projection state/version, initial allocation, price, purchase limit, and
remaining quota.

**Use-case references**: UC-FS-004 Project Campaign lifecycle

**Acceptance Scenarios**:

1. **Given** a new approved scheduled Campaign fact, **When** Flash Sale processes it, **Then** a
   non-purchasable projection contains the exact approved item snapshot and initial allocation.
2. **Given** a projection has processed the same or newer Campaign version, **When** a duplicate or
   stale scheduled fact arrives, **Then** it is a successful no-op and remaining quota is not reset.
3. **Given** a prepared projection, **When** its activation fact arrives, **Then** purchases may be
   evaluated without reinitializing quota.
4. **Given** activation arrives without a usable prepared projection, **When** it is processed,
   **Then** the Campaign remains unavailable for purchase and recovery is requested.
5. **Given** Campaign snapshot recovery returns an older or incomplete snapshot, **When** recovery
   evaluates it, **Then** the current projection is not regressed or opened for purchase.

---

### User Story 4 - Query an owned reservation (Priority: P2)

As an authenticated shopper, I want to query my accepted reservation, so that retries and
asynchronous Order processing do not leave me uncertain about the request identity and state.

**Why this priority**: It makes durable acceptance observable and gives clients a safe recovery
path after an ambiguous network response.

**Independent Test**: Create one durable reservation, retrieve it as its owner, and verify that a
different user receives the same safe not-found outcome as an unknown reservation.

**Use-case references**: UC-FS-005 Get reservation status

**Acceptance Scenarios**:

1. **Given** a shopper owns a durable reservation, **When** they query its identity, **Then** they
   receive its Campaign, Variant, quantity, exact price/currency, status, and expiry data.
2. **Given** a reservation is absent or belongs to another shopper, **When** it is queried, **Then**
   the caller receives a safe not-found outcome that does not reveal ownership.

### Edge Cases and Failure Outcomes

- Missing, malformed, or conflicting idempotency headers do not change quota.
- A repeated logical request never decrements quota or cumulative user quantity twice.
- A Variant not matching the Campaign snapshot is rejected before quota changes.
- Redis unavailable causes purchase admission to fail closed; there is no database or Inventory
  fallback for stock decisions.
- A stale/unknown Campaign projection fails closed and recovery remains outside the ordinary
  purchase hot path.
- Concurrent request-thread and recovery-worker persistence establishes one durable result.
- A temporary PostgreSQL failure after winner selection does not immediately compensate the
  winner; retry/recovery establishes the durable result before expiry or preserves the terminal
  expiry outcome after that deadline.
- Repeated expiry/release processing restores quota at most once.
- Unsupported or poison Campaign messages do not corrupt or regress the current projection.
- Kafka, Schema Registry, PostgreSQL, or process recovery never generates a replacement business
  identity for the same established purchase.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST accept reservation requests only from authenticated users entering
  through API Gateway, and Flash Sale MUST validate the access token independently.
- **FR-002**: The authenticated token subject MUST be the user identity; user-controlled request
  fields or identity headers MUST NOT override it.
- **FR-003**: A reservation request MUST identify one Campaign, one Variant, a positive quantity,
  and one non-blank idempotency key.
- **FR-004**: The system MUST evaluate Campaign state/window, Variant identity, requested quantity,
  remaining Campaign quota, cumulative user quantity, and idempotency as one atomic admission
  decision.
- **FR-005**: The system MUST reject attempts for missing, unprepared, inactive, not-started,
  ended, stale, or internally inconsistent Campaign projections without changing quota.
- **FR-006**: The system MUST reject a request when the requested quantity exceeds remaining quota
  or would exceed the Campaign's approved per-user purchase limit.
- **FR-007**: The system MUST NOT introduce a second fixed quantity cap in this feature; the
  Campaign purchase limit is the business limit.
- **FR-008**: A winning decision MUST establish stable purchase-request and reservation identities
  and a recoverable handoff in the same atomic operation that changes hot quota.
- **FR-009**: A reservation MUST expire five minutes after it is established. Expiry MUST move an
  eligible reservation from `RESERVED` to `EXPIRED` and restore its hot quota and cumulative user
  quantity exactly once. Feature 019 MUST NOT publish a reservation-expired Kafka event.
- **FR-010**: A successful durable acceptance MUST create or replay exactly one purchase request,
  one reservation, and one required purchase-event publication identity.
- **FR-011**: The system MUST report the asynchronous purchase as durably accepted only after the
  service-owned durable acceptance record and required publication record commit.
- **FR-012**: If hot quota is reserved and its recoverable handoff exists but PostgreSQL is
  temporarily unavailable, the system MUST preserve the winner for forward recovery until the
  five-minute reservation expiry. It MUST return HTTP 503 with
  `FLASH_SALE_ACCEPTANCE_PENDING` and a retry hint, MUST NOT report HTTP 202 before durable
  acceptance, and MUST require the client to retry the same request with the same idempotency key.
  If durable acceptance is established before expiry, replay returns that durable result. If expiry
  becomes terminal first, recovery MUST NOT later create an accepted purchase or event.
- **FR-013**: An idempotency identity MUST be scoped by `(userId, campaignId, idempotencyKey)` and
  retained until 24 hours after the Campaign end time. After that idempotency record is safely
  cleaned up, the key MAY be reused. Durable reservation and audit retention are independent and
  MUST NOT be deleted merely because the idempotency lookup expires.
- **FR-014**: A replay within the approved idempotency scope with the same canonical request MUST
  return the established logical reservation without changing quota again.
- **FR-015**: A replay within the approved idempotency scope with different canonical request data
  MUST return a conflict without changing quota.
- **FR-016**: The canonical idempotency comparison MUST include user, Campaign, Variant, and
  quantity, and MUST exclude trace, authorization, timestamps, and generated identifiers.
- **FR-017**: The system MUST consume only the approved scheduled and activated Campaign v1 facts in
  this feature and MUST process duplicates and stale aggregate versions without resetting quota or
  regressing state.
- **FR-018**: An activation fact MUST NOT initialize unknown quota. Missing/stale state MUST remain
  unavailable until recovered from the approved Campaign snapshot contract.
- **FR-019**: The current feature MUST enforce the Campaign end time locally for purchase admission
  and MUST NOT require or invent a Campaign-ended integration fact.
- **FR-020**: A durably accepted request MUST produce `PurchaseAccepted.v1` with one stable message
  identity and the approved purchase-request partition identity.
- **FR-021**: Publication retry or operator replay MUST preserve message identity, business data,
  and version.
- **FR-022**: The future consumer contract MUST assume at-least-once delivery and MUST NOT rely on
  the producer for end-to-end exactly-once behavior.
- **FR-023**: An authenticated shopper MUST be able to read their own durable reservation and MUST
  receive a non-enumerating not-found outcome for absent or foreign reservations.
- **FR-024**: Successful JSON responses MUST use the repository shared success envelope; errors
  MUST use the shared error envelope with Flash Sale-owned error codes.
- **FR-025**: Trace identity MUST be carried in approved HTTP/Kafka headers and MUST NOT be added to
  business JSON or event bodies.
- **FR-026**: The ordinary purchase path MUST NOT call Campaign, Product, Inventory, Order,
  Payment, or Authentication synchronously after token validation material is available locally.

### Non-Functional Requirements

- **NFR-001**: Under a concurrency test with at least ten attempts per available unit, the system
  MUST accept no more than the configured Campaign allocation and remaining quota MUST never be
  negative.
- **NFR-002**: One hundred concurrent replays of the same logical request MUST establish one
  logical reservation and one quota decrement.
- **NFR-003**: Temporary process, broker, Registry, or database interruption at every documented
  boundary MUST be recoverable without lost or duplicated logical winners.
- **NFR-004**: The public reservation path MUST expose measured p50, p95, and p99 latency plus
  throughput for a documented local test environment; no hardware-independent RPS claim is made.
- **NFR-005**: Liveness, readiness, metrics, logs, and traces MUST distinguish Redis admission,
  durable acceptance, projection, recovery, and publication failures without high-cardinality
  metric labels or sensitive data.
- **NFR-006**: Public and internal security failures MUST not expose token, secret, database,
  framework, class, stack-trace, or raw dependency details.
- **NFR-007**: The Flash Sale service module MUST remain independently buildable and deployable,
  and the full Maven reactor MUST remain green.

### Business Rules and Invariants

- **INV-001**: Remaining Campaign quota is never negative.
- **INV-002**: Accepted quantity plus released quantity plus remaining quantity reconciles to the
  initial Campaign allocation for the modeled MVP lifecycle.
- **INV-003**: One user never holds accepted quantity above the Campaign purchase limit.
- **INV-004**: One logical idempotent request establishes at most one reservation and one hot-quota
  decrement.
- **INV-005**: A duplicate/stale Campaign fact never resets already consumed quota.
- **INV-006**: Exact price and currency are copied from the immutable Campaign snapshot; the shopper
  cannot submit or override price.
- **INV-007**: PostgreSQL is the durable source of accepted purchases; Redis coordinates atomic hot
  admission but is not the authoritative business store.
- **INV-008**: A required purchase event is represented by one stable durable publication identity
  committed with the accepted purchase.
- **INV-009**: Repeated release or expiry processing restores quota and user quantity at most once.
- **INV-010**: A reservation owned by one user is not observable to another user.

### Key Entities and Domain Model Delta

- **Campaign Sale Projection**: Flash Sale-owned read/coordination model of one approved Campaign
  item, its version, sale window, exact price/currency, initial allocation, remaining quota, and
  per-user limit. It does not become the authoritative Campaign aggregate.
- **Purchase Request**: Stable logical request identity linking authenticated user, Campaign,
  Variant, quantity, Campaign-scoped user idempotency identity, request fingerprint, established
  reservation, and retention through 24 hours after Campaign end.
- **Flash Sale Reservation**: Durable record of quota held for a winning purchase request, including
  exact snapshot price/currency, quantity, lifecycle status, and a five-minute expiry.
- **Publication Record**: Durable identity and immutable content required to publish the accepted
  purchase fact.
- **Added/changed domain terms**: Use `Campaign allocation` for the initial quota assigned by
  Inventory, `remaining quota` for hot availability, `PurchaseAccepted` for the durable fact handed
  to Order, and `reservation` for the winner's held quota.
- **Changed states or transitions**: The MVP starts a reservation as `RESERVED`. Five-minute expiry
  moves an eligible reservation to `EXPIRED` and restores quota exactly once. Order-created,
  confirmed, released, and expiry-event integration are future feature scope.

## Distributed-System Risk Decisions

| Risk area | Decision and required behavior | Requirement/scenario reference |
|-----------|--------------------------------|--------------------------------|
| Money/payment | Campaign snapshot is the exact unit-price/currency source; Payment and final charge policy are out of scope | INV-006; US1 |
| Inventory/oversell | Inventory owns physical stock and allocation; Flash Sale atomically reserves only allocated hot quota and never calls Inventory per purchase | FR-004–FR-008; INV-001–INV-003 |
| Concurrency | One atomic admission decision plus durable uniqueness must select at most the available winners under races | US1; NFR-001–NFR-002 |
| Idempotency/deduplication | Identity is scoped by user, Campaign, and key; retained through Campaign end plus 24 hours; same request replays, changed request conflicts, and reuse is allowed only after cleanup | FR-013–FR-016 |
| Consistency/ordering | Scheduled precedes activated per Campaign key; duplicate/stale versions are no-ops; accepted-event delivery is at least once | FR-017–FR-022 |
| Retry/timeout/compensation | Broker publication is durable/retryable; post-admission database outage preserves the winner for Stream recovery until five-minute expiry, reports acceptance pending, and never reports durable success early | FR-010–FR-012; US2 |
| Security/authorization | Shopper identity comes from independently validated public JWT; owner-only query; internal snapshot uses the existing service identity | FR-001–FR-002; FR-023; US4 |
| TTL/quota/retention | Campaign allocation and per-user limit are authoritative inputs; reservation TTL is five minutes with local idempotent expiry/release and no expiry event; idempotency lasts through Campaign end plus 24 hours | FR-006; FR-009; FR-013 |

## Dependencies and Compatibility

- **Upstream dependencies**: API Gateway public ingress and JWT forwarding; Authentication JWKS;
  Feature 017 `CampaignScheduled.v1`, `CampaignActivated.v1`, and recovery snapshot contract.
- **Downstream consumers**: Future Order Service consumes candidate `PurchaseAccepted.v1`; Feature
  019 validates publication without requiring Order implementation.
- **Compatibility promise**: Existing Campaign schemas and subjects remain unchanged. The new
  purchase event requires a separately reviewed Avro v1 schema, topic, subject, producer/consumer
  ownership, registration, and compatibility tests before production publication.
- **HTTP compatibility**: These are new v1 endpoints and use the current shared response/error
  contract from their first release. Gateway passes committed service responses through.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In a demonstrated race with at least 1,000 attempts for 100 available units, exactly
  100 units or fewer are accepted according to request quantities, no oversell occurs, and all
  quota/accounting invariants pass.
- **SC-002**: In 100 concurrent identical retries, every successful/replayed result identifies the
  same logical reservation and quota changes once.
- **SC-003**: Every tested crash window recovers to one durable accepted reservation/event identity
  or the approved compensation outcome, with no permanent quota leak.
- **SC-004**: A shopper can submit and query an accepted reservation through Gateway using a real
  Authentication-issued token in the local Compose topology.
- **SC-005**: Campaign duplicate, stale, and out-of-order integration tests produce no quota reset
  or unauthorized sale opening.
- **SC-006**: Kafka/Registry outage and recovery validation publishes the original event identity
  and compatible schema after dependencies return.
- **SC-007**: A recorded k6 run reports environment, traffic model, throughput, p50/p95/p99,
  unexpected errors, and correctness counters without claiming results for other hardware.

## Assumptions

- Feature 017 remains the authoritative owner of the one-item Campaign MVP snapshot.
- The existing `flashsale-service` Client Credentials registration and Campaign snapshot scope are
  available for projection recovery.
- The root local environment continues to use one Redis, one Kafka KRaft node, one Schema Registry,
  and separate PostgreSQL databases; production-like HA is outside this feature.
- Administrator and shopper UI implementation is not required; HTTP/Kafka contract and smoke tests
  are sufficient clients.
- Exact Redis Stream claim/trim and Kafka technical retry values may be selected during plan
  research only when they preserve the approved five-minute recovery deadline and Campaign-relative
  idempotency retention.
- The future Order payment deadline MUST be shorter than the five-minute reservation TTL; its exact
  value and payment behavior require the future Order/Payment feature.

## Human Decisions Required

| Priority | Question | Options/trade-off | Owner | Decision deadline | Resolution |
|----------|----------|-------------------|-------|-------------------|------------|
| P0 | What reservation lifetime and expiry outcome apply before Order integration? | A: fixed TTL with local idempotent expiry/release and no expiry event; B: fixed TTL plus new expiry event; C: no automatic expiry in MVP, risking held quota | Project owner | Before spec approval | RESOLVED — 5 minutes, local idempotent expiry/release, no expiry event |
| P0 | What happens when hot quota is reserved and recoverably handed off but PostgreSQL is unavailable? | A: forward-recover the winner and expose a retryable pending outcome until durable; B: compensate immediately and treat the request as failed; each changes user-visible ambiguity and quota ownership | Project owner | Before spec approval | RESOLVED — preserve winner and forward-recover until five-minute expiry; 503 pending until durable |
| P0 | What is the idempotency key scope, retention, and post-retention reuse behavior? | A: user+Campaign scope retained through Campaign end plus buffer, then reusable; B: global-per-user fixed retention; C: never reusable, with unbounded durable uniqueness | Project owner | Before spec approval | RESOLVED — user+Campaign scope, Campaign end plus 24 hours, reusable after cleanup |

## Constitutional Constraints

- **Service ownership**: Flash Sale owns `flashsale_db`, its domain, runtime configuration, tests,
  and migrations. It never reads another service database or shares JPA/domain models.
- **External ingress**: New public routes enter through API Gateway; internal Campaign snapshot
  recovery is not exposed through Gateway.
- **API/event contracts**: Public HTTP, Campaign-consumer compatibility, and the new Avro purchase
  event are documented and approved before implementation.
- **Durable and hot-path data**: PostgreSQL remains durable truth. The reservation admission change
  is atomic through Redis Lua and includes a recoverable handoff.
- **Messaging reliability**: Campaign consumption is idempotent by event/version. Accepted purchase
  plus required publication identity uses a transactional outbox. Retry, ordering, recovery, and
  duplicate behavior are required plan sections.
- **Root infrastructure ownership**: Shared Compose/Kafka topic/Schema Registry/Kubernetes changes
  remain under root `infra/`; Flash Sale dependencies, application configuration, and Liquibase
  migrations remain inside its service module.
- **Observability**: Liveness, readiness, and Prometheus remain declarative Spring Boot Actuator
  behavior. Important HTTP, Redis handoff, recovery, and Kafka work propagate trace context.
- **Verification**: Pure domain/application tests, Redis/PostgreSQL concurrency integration,
  Kafka/Registry contracts, web/security/Gateway contracts, failure recovery, module/full Maven
  builds, Compose smoke, and k6 load evidence apply.
- **Architecture decisions**: The feature plan must confirm whether the existing repository
  architecture guides are sufficient or an ADR is required to approve Redis Stream handoff and the
  new durability/communication boundary.

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-08-10 | Initial Feature 019 draft extracted from the reviewed MVP proposal | Codex | Project owner | Draft — clarification required |
| 2026-08-10 | Q1–Q3 resolved reservation expiry, durable recovery, and idempotency semantics | Codex | Project owner | Draft — ready for approval |
| 2026-08-10 | Feature 019 specification approved for implementation planning | Project owner | Project owner | Approved |
