# Phase 0 Research: Order Service Core MVP

**Feature**: [Order Service Core MVP](./spec.md)
**Date**: 2026-08-15
**Status**: Complete for draft-plan review

All technical unknowns for the scoped feature are resolved below. No decision in this document adds
Payment behavior, a terminal Order transition, a reservation-expiry event, or another service/data
owner.

## R1. Consume the implemented purchase contract, not the source draft names

**Decision**: Consume `PurchaseAcceptedV1` from `flashsale.purchase.events.v1` with
`purchaseRequestId` as key and group `order-purchase-accepted-v1`. Do not introduce
`FlashSaleReservationAcceptedV1`, `FlashSaleReservationExpiredV1`, or
`flash-sale.reservation.v1`.

**Rationale**: Feature 019, its contract document, its generated Avro schema, runtime configuration,
and live validation all agree on `PurchaseAcceptedV1`. Feature 019 explicitly says reservation
expiry publishes no event.

**Alternatives considered**:

- Rename the existing event to match the source draft: rejected because it would break an approved,
  implemented producer contract.
- Add an expiry event in Feature 020: rejected because Flash Sale owns that behavior and Feature 019
  explicitly excluded the event.

## R2. Keep Order Core separate from Payment orchestration

**Decision**: Feature 020 creates/query Orders and publishes `OrderCreatedV1` as a fact. It does not
emit `PaymentRequestedV1`, consume payment results, confirm/release reservations, or select terminal
Order states.

**Rationale**: The repository target flow uses an explicit Payment command and an Order-owned Saga.
Using `OrderCreatedV1` as a hidden charge instruction would confuse facts and commands. Payment
deadline, provider ambiguity, retry, late success, compensation, and refund are financial decisions
not approved by this feature.

**Alternatives considered**:

- Let Payment consume `OrderCreatedV1` as a command: rejected because it hides intent and conflicts
  with the repository message catalog.
- Implement the complete Saga now: rejected because the required financial and compensation rules
  are outside the approved scope.

## R3. Use PostgreSQL-local identity arbitration plus unique constraints

**Decision**: Before creating a new Order, the persistence adapter obtains transaction-scoped
PostgreSQL advisory locks for canonical purchase and reservation identities in deterministic order,
then evaluates inbox and Order identities. Unique constraints remain the final backstop.

**Rationale**: Multiple Kafka consumers or replays may race before either transaction is visible.
Database-local transaction locks serialize the relevant service-owned identities without a Redis,
JVM, or cross-service lock. Deterministic ordering prevents lock-order inversion. Unique constraints
still protect correctness if a code path misses arbitration.

**Alternatives considered**:

- JVM/local lock: rejected because it is unsafe across replicas.
- Redis/distributed lock: rejected because Order does not otherwise need Redis and PostgreSQL owns
  durable Order integrity.
- Unique constraints only with exception/reload: viable, but makes multi-row transaction recovery
  and contradictory dual-identity races harder to reason about and test.
- Kafka partition ordering only: rejected because replay, operator action, topic changes, and
  multiple business identities still require database integrity.

## R4. Deduplicate by event identity and canonical business fingerprint

**Decision**: Store each `eventId` with a SHA-256 fingerprint of canonical business content.
Normalize UUIDs, exact scale-4 decimal price, uppercase currency, and UTC instants before hashing.
Exclude topic/partition/offset and trace headers from business equivalence.

**Rationale**: `eventId` detects physical redelivery, while `purchaseRequestId` and `reservationId`
detect logical duplication even if a producer accidentally creates another event ID. A canonical
fingerprint distinguishes safe equivalence from contradictory identity reuse without depending on
Avro binary encoding or infrastructure metadata.

**Alternatives considered**:

- Hash raw Avro bytes: rejected because equivalent data may serialize differently after compatible
  schema evolution.
- Inbox by event ID only: rejected because different event IDs could still claim one logical
  accepted purchase.
- Treat all repeated business identities as success: rejected because changed shopper/amount data
  would be hidden corruption.

## R5. Persist Order, inbox, and outbox through one atomic capability

**Decision**: The application depends on one `PersistOrderCreationPort`. Its PostgreSQL adapter
atomically persists or resolves the Order, one line, inbox receipt, and `OrderCreatedV1` outbox
intent. It returns created, replayed, or conflict semantics without leaking JPA exceptions.

**Rationale**: Separate low-level save ports would allow application orchestration to commit Order
without deduplication or required publication. One capability makes the atomic invariant explicit
and keeps transaction management in the driven adapter.

**Alternatives considered**:

- `SaveOrderPort` + `SaveInboxPort` + `SaveOutboxPort`: rejected because callers could compose them
  non-atomically.
- Publish Kafka directly inside the database transaction: rejected because broker and PostgreSQL do
  not share a safe transaction boundary here.
- Kafka transactions instead of outbox: rejected because they do not atomically include PostgreSQL
  state and complicate recovery.

## R6. Use a leased PostgreSQL transactional outbox

**Decision**: Store immutable `OrderCreatedV1` publication snapshots. A scheduled relay claims due
rows with `FOR UPDATE SKIP LOCKED`, a 30-second lease, and batch size 100; it polls every 500 ms and
retries the same event identity with exponential backoff capped at 60 seconds.

**Rationale**: This is the repository's proven Campaign/Flash Sale pattern. Leases recover crashed
workers, `SKIP LOCKED` supports multiple replicas, and immutable snapshots prevent retry drift.

**Alternatives considered**:

- Direct post-commit send: rejected due to the commit/send crash gap.
- Delete after publish: rejected because publication audit and ambiguous-send recovery need the
  stable row; retention is not yet approved.
- Send failed committed facts to DLT and stop: rejected because an Order-created fact is a required
  publication and must remain durably retryable.

## R7. Use schema-first Avro with the existing registry policy

**Decision**: Add `OrderCreatedV1.avsc` under
`contracts/kafka-avro-contracts/src/main/avro/topics/flashsale.order.events.v1/`. Use generated
`SpecificRecord`, `TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE`, controlled registration, and
`auto.register.schemas=false` in stable environments.

**Rationale**: This matches the implemented Campaign and Purchase contract governance and prevents
service-local JSON payloads from becoming a parallel source of truth.

**Alternatives considered**:

- Ad-hoc JSON: rejected because the repository requires versioned schema-first Kafka contracts.
- `GenericRecord` throughout the application: rejected because protocol types must stay in Kafka
  adapters and compile-time generation gives clearer contract tests.
- Automatic runtime registration everywhere: rejected because application startup must not mutate
  stable Registry governance.

## R8. Separate consumer retry/DLT from outbox retry

**Decision**: A new inbound record gets an initial attempt plus three transient retries at 1, 3,
and 10 seconds. Exhausted transient or immediately non-retryable input goes to
`flashsale.order.purchase-accepted.dlt.v1` for explicit operator inspection/replay. Committed outbox
facts retry indefinitely with capped backoff and do not use a DLT.

**Rationale**: Poison inbound data must not block a partition indefinitely, while an already
committed required Order fact must not be abandoned. Operator replay remains safe because the
consumer is idempotent.

**Alternatives considered**:

- Infinite consumer retry: rejected because one poison record would block subsequent purchases in
  the partition.
- No DLT: rejected because non-retryable schema/business conflicts need durable operational
  visibility.
- Bounded outbox retry: rejected because it could permanently lose the required integration fact.

## R9. Use JPA by default and native SQL only for PostgreSQL capabilities

**Decision**: Use Spring Data JPA for Order/inbox/outbox entities and ordinary owner queries. Keep
advisory lock and leased `SKIP LOCKED` claim SQL inside outbound persistence adapters via
`JdbcTemplate` where JPA does not express the operation clearly. Liquibase owns the exact schema.

**Rationale**: This follows repository persistence defaults while containing database-specific
performance/correctness mechanisms at the adapter boundary. Hibernate validates but does not create
or update schema.

**Alternatives considered**:

- Pure JPA for lock/claim behavior: rejected because vendor-specific leasing is less explicit and
  harder to review.
- JDBC for all Order persistence: rejected because ordinary aggregate/query persistence does not
  need it and JPA is the repository default.
- Hibernate DDL auto-update: rejected because it bypasses service-owned migration review.

## R10. Reuse public HTTP/JWT conventions without a new shared business module

**Decision**: Add Gateway routing and Order-local JWT validation for the existing public trust
contract. Use `common-web.ApiResponse`, `PageResponse`, and `ApiErrorResponse`; use JWT `sub` for
owner-scoped queries and return the same 404 for absent/foreign Orders.

**Rationale**: Gateway is the single public ingress, but each business service independently
enforces trust and object authorization. Reusing only infrastructure-neutral HTTP envelopes avoids
sharing Order domain types.

**Alternatives considered**:

- Trust Gateway headers for user identity: rejected because headers can be substituted and the
  service must revalidate JWT.
- Share Order DTO/domain types through a library: rejected because it transfers business ownership.
- Return 403 for foreign Order and 404 for absent Order: rejected because it leaks existence.

## R11. Readiness and retention boundaries

**Decision**: PostgreSQL affects Order readiness. Kafka/Registry outage does not disable committed
Order queries; it is reported through consumer/outbox health and backlog/age metrics. Inbox,
Orders, and published outbox rows have no deletion job in Feature 020.

**Rationale**: Queries need durable storage but not broker publication. No business retention rule
has been approved, and deleting deduplication evidence prematurely would weaken correctness.

**Alternatives considered**:

- Fail all readiness when Kafka is down: rejected because it would remove healthy query replicas
  and reduce availability.
- Delete inbox/outbox rows on a guessed schedule: rejected because idempotency and audit retention
  are business/operational policy decisions requiring a later spec.
