# Feature Specification: Cloud Kafka Contract Provisioning

**Feature Branch**: `codex/gitops-phase20-kafka-contracts`

**Created**: 2026-08-22

**Status**: Approved

**Input**: Repository owner requested Phase 20 after the full-cloud Argo CD ownership transition.

**Linked Business Requirement**: Technical enabler for the approved asynchronous purchase, order,
and payment flows on the cloud environment.

**Business Owner**: Repository owner

**Required Reviewers**: Repository owner and messaging-contract owner

## Problem and Scope

### Problem Statement

Kafka and Schema Registry are healthy on the cloud environment, but only two of the seven approved
application topics exist and no approved Avro subjects are registered. Services are configured not
to auto-register schemas, so healthy Pods alone do not prove that their approved event flows can
publish, retry, or recover successfully.

### In Scope

- Materialize the seven already-approved Kafka topics in the cloud environment.
- Materialize the nine already-approved topic-record schema subjects.
- Apply one explicitly approved cloud partition count and single-broker replication topology.
- Preserve subject-level backward-transitive compatibility.
- Include the Order and Payment dead-letter subjects required for exhausted-delivery recovery.
- Provide validation-only and explicit-apply operator modes with drift refusal and repeatable checks.
- Record live topic, subject, compatibility, and repeatability evidence.

### Out of Scope

- Adding, renaming, removing, or changing the meaning of an event contract.
- Choosing new retention, compaction, partition-key, retry, ordering, or consumer semantics.
- Enabling Payment consumers, checkout, Stripe, recovery workers, or outbox publishers.
- Introducing a Kafka operator, managed broker, additional broker, or public broker endpoint.
- Deleting/recreating topics, schemas, consumer groups, messages, or Schema Registry history.
- Changing Java service code, databases, Redis state, or Kubernetes Secrets.

## Baseline References

- `specs/017-campaign-management-mvp/contracts/campaign-lifecycle-events.md`
- `specs/019-flash-sale-service-mvp/contracts/purchase-accepted-kafka.md`
- `specs/020-order-service-mvp/spec.md` and its accepted Order/DLT infrastructure evidence
- `specs/021-payment-service-mvp/contracts/`
- `contracts/kafka-avro-contracts/src/main/avro/topics/`
- Root-owned local provisioning under `infra/docker/kafka/` and
  `infra/docker/schema-registry/`

## User Scenarios & Testing

### User Story 1 - Materialize approved cloud messaging contracts (Priority: P1)

As an operator, I want every approved topic and schema subject present on the cloud broker so that
services can use their versioned event contracts without relying on implicit topic or schema
creation.

**Why this priority**: Missing subjects make event publication fail when automatic registration is
disabled, even though all application Pods remain healthy.

**Independent Test**: Inspect the cloud broker and registry and verify seven exact topics, nine exact
subjects, the approved cloud topology, exact record identities, and approved compatibility.

**Acceptance Scenarios**:

1. **Given** Kafka and Schema Registry are healthy, **when** provisioning is explicitly applied,
   **then** all seven approved topics exist with the selected cloud partition count and replication
   factor one.
2. **Given** the accepted Avro catalog, **when** provisioning completes, **then** all nine approved
   topic-record subjects resolve to the expected record and use backward-transitive compatibility.
3. **Given** exhausted Order or Payment command delivery, **when** a producer targets its approved
   dead-letter topic, **then** the corresponding schema subject already exists.

### User Story 2 - Detect drift and rerun safely (Priority: P2)

As an operator, I want provisioning to be repeatable and to refuse incompatible live state so that
a rerun does not erase messages or silently change ordering and compatibility guarantees.

**Why this priority**: Topic recreation or silent topology changes can cause message loss or alter
consumer ordering.

**Independent Test**: Run validation and apply repeatedly; identical state succeeds without new
schema versions, while an unexpected partition, replication, record, or compatibility value is
reported and left unchanged.

**Acceptance Scenarios**:

1. **Given** all approved contracts already exist, **when** apply is repeated, **then** it succeeds
   without deleting topics, creating duplicate schema versions, or changing compatibility.
2. **Given** validation-only mode, **when** the operator runs it, **then** no broker or registry state
   changes.
3. **Given** an existing contract has incompatible topology or schema policy, **when** validation or
   apply runs, **then** it stops and reports the affected contract without destructive correction.
4. **Given** Phase 20 is not yet merged into `develop`, **when** apply is requested, **then** the
   workflow refuses before changing live broker or registry state.

### Edge Cases

- Some topics exist while others are missing.
- Topics exist correctly but the registry is empty.
- An exact schema is already registered under the expected subject.
- A subject exists with the expected record but the wrong compatibility level.
- The local port selected for temporary registry access is already occupied.
- Kafka or Schema Registry becomes unavailable between preflight and apply.

## Requirements

### Functional Requirements

- **FR-001**: The desired cloud inventory MUST contain exactly the seven accepted application topic
  names: `campaign.lifecycle.v1`, `flashsale.purchase.events.v1`,
  `flashsale.order.events.v1`, `flashsale.order.purchase-accepted.dlt.v1`,
  `flashsale.payment.commands.v1`, `flashsale.payment.events.v1`, and
  `flashsale.payment.payment-requested.dlt.v1`.
- **FR-002**: Each desired application topic MUST have three partitions and replication factor one.
  An existing topic MAY be increased from one to three partitions only after proving that it has no
  records; the workflow MUST otherwise stop for operator review.
- **FR-003**: The desired schema inventory MUST contain the two Campaign lifecycle records,
  PurchaseAccepted for its primary and Order dead-letter topics, OrderCreated, PaymentRequested for
  its command and dead-letter topics, and both Payment result records.
- **FR-004**: Every desired subject MUST use the accepted topic-plus-fully-qualified-record identity
  and `BACKWARD_TRANSITIVE` compatibility.
- **FR-005**: Provisioning MUST be idempotent and MUST verify live state after every apply.
- **FR-006**: The workflow MUST NOT delete or recreate a topic, schema, subject, message, consumer
  group, or compatibility history.
- **FR-007**: Existing topology, record-identity, or compatibility drift MUST stop the workflow and
  remain unchanged for operator review.
- **FR-008**: Validation-only mode MUST inspect prerequisites and live state without mutation.
- **FR-009**: Live apply MUST be explicit and MUST refuse until the reviewed Phase 20 desired state
  exists on `origin/develop`.
- **FR-010**: The workflow MUST reject an occupied local forwarding port and MUST clean up only the
  temporary process that it created.
- **FR-011**: The workflow MUST neither read nor print Kubernetes Secret values.
- **FR-012**: Payment runtime feature flags MUST remain disabled in this phase.
- **FR-013**: Commands, results, contract counts, topology, compatibility, and rerun evidence MUST be
  recorded before the feature is marked verified.

### Key Entities

- **Topic contract**: An approved topic identity and immutable cloud topology expectation.
- **Schema subject contract**: An approved topic-record identity, record schema, and compatibility
  policy.
- **Provisioning run**: A validation-only or explicit-apply attempt with observed preconditions,
  changes, verification, and outcome.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Seven of seven approved application topics exist with the explicitly selected cloud
  topology.
- **SC-002**: Nine of nine approved schema subjects resolve to the expected record identity and
  compatibility policy.
- **SC-003**: Two consecutive apply executions complete without adding schema versions or changing
  topic topology on the second execution.
- **SC-004**: A validation-only execution makes zero live changes.
- **SC-005**: Zero topics, schemas, messages, consumer groups, Secrets, or Payment feature flags are
  deleted or modified outside the approved inventory.

## Assumptions

- Phase 19 is complete and `flash-sale-cloud` is `Synced` and `Healthy`.
- Kafka 4.0.0 and Schema Registry are reachable only inside the `flash-sale` namespace.
- The accepted root Avro catalog is the schema source of truth.
- Broker default retention remains unchanged because no approved retention delta exists.

## Constitutional Constraints

- **Service ownership**: No service database, migration, JPA type, or business code changes.
- **External ingress**: No public route; temporary operator access remains bound to localhost.
- **API/event contracts**: Existing versioned Kafka contracts are materialized without semantic or
  schema changes.
- **Durable and hot-path data**: No PostgreSQL or Redis behavior changes.
- **Messaging reliability**: Existing outbox, idempotency, key, retry, and DLT semantics remain
  unchanged; this phase only creates their required broker/registry identities.
- **Root infrastructure ownership**: All provisioning and operator assets remain under root
  `infra/`; no service owns broker administration.
- **Observability**: Evidence uses broker descriptions, registry subject/configuration checks, and
  bounded operator output; application metrics and trace propagation are unchanged.
- **Verification**: PowerShell parsing, accepted Avro contract-module verification, live
  validation/apply/rerun checks, and drift guards apply. Service module, load, and database tests are
  omitted because service behavior and persistence do not change.
- **Architecture decisions**: ADR 0023 records explicit root-owned provisioning without introducing
  a Kafka operator.

## Approval and History

- 2026-08-22 — Draft created from the repository owner's request to proceed to Phase 20.
- 2026-08-22 — Cloud inspection found two auto-created one-partition topics with zero offsets;
  partition topology moved to an explicit owner decision.
- 2026-08-22 — Repository owner selected three partitions and replication factor one. Increasing
  an existing topic is authorized only while its total record count is zero.
- 2026-08-22 — Pre-merge implementation, contract build, cloud dry-run, validation-only inventory,
  and merge-gate rejection passed. Live apply and idempotence evidence remain pending merge.
