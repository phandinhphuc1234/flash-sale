# Implementation Plan: Order-Owned Purchase Saga Completion

**Branch**: `codex/order-purchase-saga` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)
**Status**: Approved for implementation by owner on 2026-08-24
**Input**: Approved A/A/A/A decisions in `specs/044-order-purchase-saga/spec.md`

## Summary

Complete the currently missing Order-owned Purchase Saga from durable Flash Sale acceptance through
Payment and reservation finalization. Order will create one durable Saga and
`PaymentRequestedV1` outbox intent with the existing Order transaction, consume monotonic Payment
results, issue explicit confirm/release commands, consume Flash Sale outcomes, and finalize the
Order only after reservation acknowledgement. Flash Sale will add idempotent reservation command
handling, PostgreSQL state/inbox/outbox transactions, and recoverable Redis finalization.

Delivery is local-first. One scenario-selectable PowerShell runner validates contracts, start,
success, terminal failure, replay, and late success after each coherent implementation group. The
aggregate local gate and full reactor must pass before one immutable image-promotion release of the
affected services. Cart Service and Notification Service remain deferred.

## Technical Context

**Language/Version**: Java 21; PowerShell 7 for operator runners; Avro v1 contracts

**Framework**: Spring Boot 3.5.16; Spring Data JPA; Spring Kafka; Liquibase; Spring Data Redis;
Spring Boot Actuator/Micrometer

**Primary Dependencies**: Existing `contracts/kafka-avro-contracts`, Spring Kafka, Confluent Avro
serializer, PostgreSQL driver, Redis client, runtime Prometheus registry, and existing service test
dependencies. No new production dependency is planned.

**Storage**:

- `order_db`: Order, Purchase Saga, Saga inbox, generalized Order outbox.
- `flashsale_db`: reservation, reservation-command inbox, generalized Flash Sale outbox, durable
  Redis-reconciliation marker.
- Payment storage remains unchanged.
- Redis remains a recoverable Flash Sale hot-path projection; PostgreSQL remains durable truth.

**Testing**: JUnit 5, Spring Boot Test, ArchUnit, Testcontainers PostgreSQL/Kafka/Redis where already
available, Schema Registry contract tests, Maven module/full-reactor verify, Docker Compose smoke,
and bounded PowerShell scenario runner

**Target Platform**: Local Docker Compose first; existing Linux containers on AWS EKS after all
local gates pass

**Project Type**: Maven monorepo containing independently deployable Spring Boot microservices and
shared schema/infrastructure modules

**Performance Goals**: No new public HTTP latency SLA. Business transitions must be asynchronous and
non-blocking with bounded local evidence; duplicate/concurrency tests must prove one semantic effect
for at least 100 repeated or competing deliveries.

**Constraints**:

- `paymentDeadline = reservationExpiresAt - 30 seconds`.
- At-least-once Kafka delivery, stable IDs, durable inboxes, transactional outboxes.
- One `orderId` Kafka key for all post-Order Saga messages.
- Payment success is dominant; no automatic refund.
- Order remains `PENDING_PAYMENT` during durable `MANUAL_REVIEW`.
- Reopening an unpaid terminal Order for verified paid manual review emits one additive correction
  fact so downstream state is not left terminal.
- No cross-service database access and no distributed transaction.
- No deploy-per-group; cloud promotion occurs only after the aggregate local gate.

**Scale/Scope**: One-item Order MVP, Order/Payment/Flash Sale Saga, one new business topic, eight new
Avro record types, three consumer DLTs, two service-owned schema migrations, one local runner, and
one final affected-service cloud image promotion. Cart, Notification, refunds, and other candidate
topic families are excluded.

No `NEEDS CLARIFICATION` remains.

## Constitution Check

*GATE: Passed before Phase 0 research and re-checked after Phase 1 design.*

| Gate | Result | Design evidence |
|---|---|---|
| Specification traceability | PASS | A/A/A/A decisions and FR-001–FR-020 map to explicit design artifacts and implementation groups. |
| Service ownership | PASS | Order owns Saga/Order persistence; Flash Sale owns reservation persistence/Redis projection; Payment remains unchanged and no cross-service FK/query is added. |
| Clean/Hexagonal architecture | PASS | New business policies live in service-owned domain/application packages; Kafka, Avro, JPA, Redis, scheduling, and configuration remain adapters. |
| Asynchronous communication | PASS | One approved command topic and additive versioned SpecificRecords are documented before producer/consumer code. |
| Data and messaging | PASS | PostgreSQL is truth; each state/inbox/outbox transition is local and atomic; Redis finalization is idempotently reconciled; all consumers are replay-safe. |
| Root infrastructure | PASS | Shared topics, Registry provisioning, Compose runner, K8s config, and monitoring remain under root `infra/`; service migrations/config stay in their modules. |
| External ingress | PASS | No new external endpoint is added. Phase 24 continues to enter through API Gateway HTTPS. |
| Observability | PASS | Existing declarative Actuator/Prometheus direction is retained; Saga/manual-review, consumer, outbox, and Redis-reconciliation metrics are planned without constructing a registry. |
| Contracts and dependencies | PASS | Contract shapes, keying, subjects, compatibility, DLTs, and rollout are documented; no new production dependency is required. |
| Validation | PASS | Contract, unit, architecture, migration, integration, replay, concurrency, local E2E, module, full reactor, cloud rollout, and Stripe smoke gates are defined. HTTP load testing is omitted because this feature adds no high-volume HTTP endpoint; duplicate concurrency is tested at message/persistence boundaries. |

### ADR gate

No new ADR is required. Accepted ADR 0018 already approves Order-owned orchestration, explicit
Payment commands/results, outbox/inbox reliability, success-dominant late payment, forward recovery,
manual review, and no automatic refund. Feature 044 is its named follow-up for reservation commands
and Order terminalization. Any later automatic refund, new coordinator, provider, public review
status, or service-boundary change requires a separate spec/ADR.

## Project Structure

### Documentation

```text
specs/044-order-purchase-saga/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── reservation-commands-kafka.md
│   ├── reservation-results-kafka.md
│   ├── order-payment-and-terminal-kafka.md
│   └── local-validation-runner.md
├── checklists/requirements.md
└── tasks.md
```

### Contract module

```text
contracts/kafka-avro-contracts/src/main/avro/topics/
├── flashsale.purchase.commands.v1/
│   ├── ConfirmPurchaseReservationV1.avsc
│   └── ReleasePurchaseReservationV1.avsc
├── flashsale.purchase.events.v1/
│   ├── PurchaseAcceptedV1.avsc                 # unchanged
│   ├── PurchaseReservationConfirmedV1.avsc
│   └── PurchaseReservationReleasedV1.avsc
└── flashsale.order.events.v1/
    ├── OrderCreatedV1.avsc                     # unchanged
    ├── OrderConfirmedV1.avsc
    ├── OrderCancelledV1.avsc
    ├── OrderExpiredV1.avsc
    └── OrderPaymentReviewRequiredV1.avsc
```

Schema tests remain under the matching `contracts/kafka-avro-contracts/src/test/java/...` package.

### Order Service

```text
services/order-service/src/main/java/com/philia/flashsale/order/
├── order/
│   ├── domain/model/                            # extend Order status/transitions
│   ├── application/                             # creation/query remains feature-owned
│   └── adapter/                                 # existing PurchaseAccepted and Order persistence
├── purchasesaga/
│   ├── domain/{model,exception,policy}/
│   ├── application/
│   │   ├── {command,result}/
│   │   ├── port/{in,out}/
│   │   └── usecase/
│   └── adapter/
│       ├── in/messaging/kafka/
│       └── out/persistence/jpa/{entity,repository,mapper}/
├── outbox/
│   ├── application/{model,port,usecase}/
│   └── adapter/{in/scheduling,out/persistence,out/messaging/kafka}/
├── configuration/
└── observability/

services/order-service/src/main/resources/
├── application.yml
└── db/changelog/changes/002-add-purchase-saga.sql
```

Tests mirror these packages and add contract/integration coverage without putting Avro/JPA types in
domain/application code.

### Flash Sale Service

```text
services/flashsale-service/src/main/java/com/philia/flashsale/flashsale/
├── reservation/
│   ├── domain/{model,exception,policy}/
│   ├── application/{command,result,port,usecase}/
│   └── adapter/
│       ├── in/messaging/kafka/
│       └── out/{persistence/jpa,redis}/
├── outbox/
│   ├── application/{model,port,usecase}/
│   └── adapter/{in/scheduling,out/persistence,out/messaging/kafka}/
├── configuration/
└── observability/

services/flashsale-service/src/main/resources/
├── application.yml
├── db/changelog/changes/002-add-reservation-finalization.sql
└── redis/reservation/
    ├── confirm-reservation.lua
    └── release-reservation.lua
```

### Root infrastructure and validation

```text
infra/docker/
├── kafka/init-purchase-saga-topics.sh
├── schema-registry/register-purchase-saga-schemas.ps1
└── smoke/feature-044-purchase-saga.ps1

infra/k8s/overlays/cloud/config/
├── order-service-runtime-config.yaml
└── flash-sale-service-runtime-config.yaml

infra/scripts/gitops/
├── phase20-kafka-contracts.ps1                  # extend approved inventory/registration
└── phase24-stripe-cloud.ps1                     # use final Order result evidence

infra/monitoring/prometheus/rules/
├── order-service-alerts.yml
└── flash-sale-service-alerts.yml
```

**Structure Decision**: Purchase Saga is a core-domain feature within Order, not a root technical
package or new service. Reservation finalization extends the existing Flash Sale reservation
feature. Service-wide outboxes remain reusable technical capabilities with application ports and
adapters. Shared Avro and environment assets stay at repository root.

## Dependency and boundary rules

```text
Order Payment/Reservation Kafka adapter
  -> PurchaseSaga input port
  -> PurchaseSaga domain policy
  -> one transition-specific atomic output port
  <- Order PostgreSQL adapter

Order outbox scheduler
  -> generic Order outbox publication use case
  -> event-type Kafka publishing port
  <- Payment command / reservation command / Order event Avro adapters

Flash Sale command Kafka adapter
  -> Reservation finalization input port
  -> Reservation domain
  -> one atomic reservation + inbox + outcome-outbox port
  <- Flash Sale PostgreSQL adapter

Flash Sale Redis reconciler
  -> finalization reconciliation use case
  -> Redis finalization port
  <- Lua adapter
  -> durable reconciliation-complete port
```

- Generated Avro types stop at messaging adapters.
- JPA entities/repositories and Spring transactions stop at persistence adapters.
- Redis templates/scripts stop at Redis adapters.
- Application services receive framework-free commands/results and depend on capability-level ports.
- Ports preserve atomic intent; callers do not manually compose save-Order/save-Saga/save-inbox/
  save-outbox calls.
- No synchronous Order-to-Payment or Order-to-Flash-Sale HTTP call is added.

## Contract impact

| Surface | Change |
|---|---|
| Payment command/results | Reuse existing schemas unchanged. |
| Purchase commands | New topic with confirm/release SpecificRecords, keyed by `orderId`. |
| Purchase events | Add confirmed/released SpecificRecords; preserve PurchaseAccepted schema/key. |
| Order events | Add confirmed/cancelled/expired and paid-review correction SpecificRecords; preserve OrderCreated. |
| DLTs | Add three consumer-specific operational DLTs. |
| Public Order HTTP | Existing response shape remains; status enum can now return `CONFIRMED`, `CANCELLED`, or `EXPIRED`. No `PAYMENT_REVIEW` status. |
| Phase 24 | Replace the incomplete wait-after-Order assumption with Checkout/webhook/final Order evidence from the real Saga. |

## Phase 0: Research outputs

[research.md](./research.md) records the resolved decisions:

1. Order retains orchestration; no new service/framework.
2. Existing Payment contracts remain unchanged.
3. One reservation command topic plus additive purchase/order records.
4. Consumer-specific DLTs and durable outbox retry.
5. Dedicated Saga aggregate/inbox and generalized existing Order outbox.
6. PostgreSQL reservation transition plus idempotent Redis reconciliation.
7. Exact A/A/A/A deadline, terminal-state, manual-review, and correction-event rules.
8. Monotonic participant versions and stable fingerprints.
9. One scenario-selectable local runner and one final image promotion.

## Phase 1: Design outputs

- [Data model](./data-model.md)
- [Reservation command contract](./contracts/reservation-commands-kafka.md)
- [Reservation result contract](./contracts/reservation-results-kafka.md)
- [Order Payment/terminal contract](./contracts/order-payment-and-terminal-kafka.md)
- [Local runner contract](./contracts/local-validation-runner.md)
- [Quickstart](./quickstart.md)

Post-design Constitution re-check: PASS; no justified violation remains.

## Implementation groups

The task ledger must preserve this local-first order.

### G1 — Schema-first contracts and local contract gate

- Add eight Avro records and schema/identity/compatibility tests.
- Add one main topic, three DLTs, Registry provisioning, and documentation/catalog updates.
- Create the Feature 044 runner shell with `Contracts` and safe bounded helpers.
- Gate: contract module verify plus local Registry compatibility.

### G2 — Order creates durable Saga and Payment command

- Add Order migration, Saga domain/application/persistence foundation, and 30-second deadline policy.
- Extend accepted-purchase atomic creation to include Saga and `PaymentRequested` outbox intent.
- Generalize Order outbox dispatch and publish the existing `PaymentRequestedV1`.
- Gate: domain, migration, duplicate/concurrency, outbox, Kafka/Registry tests and `Start` scenario.

This group is the first direct blocker for the current Phase 24 timeout.

### G3 — Order consumes Payment results

- Add strict Avro mappers/listeners, Saga inbox/fingerprint/version handling, and DLT policy.
- Success creates a stable confirm command; approved failures create a stable release command and
  store desired terminal Order status.
- Higher-version success supersedes failure and triggers forward recovery.
- Gate: Payment result success/failure/replay/order tests and module verify.

### G4 — Flash Sale confirm/release participant

- Add reservation statuses, command inbox, atomic transition/outcome outbox, and generalized relay.
- Scope reservation outcome identity to the causing command so command replay reuses one result,
  while a distinct confirm-after-release command can receive a correlated current-state result.
- Add confirm/release Lua adapters and durable Redis-reconciliation worker.
- Correct expiry queries/transitions so confirmed/released reservations cannot restore quota.
- Gate: domain, migration, concurrency, Redis outage/recovery, Kafka/DLT/Registry tests.

### G5 — Order finalization and manual review

- Consume confirmed/released outcomes with strict identity/version validation.
- Complete `CONFIRMED`, `CANCELLED`, and `EXPIRED` transitions only after participant outcome.
- Add controlled late-success correction and durable `MANUAL_REVIEW` evidence without new public
  Order status or refund.
- Publish the three terminal Order events and `OrderPaymentReviewRequiredV1` correction fact through
  the generalized outbox.
- Verify the existing authenticated Order query exposes `CONFIRMED`, `CANCELLED`, `EXPIRED`, and the
  corrected `PENDING_PAYMENT` state without changing its response envelope.
- Gate: `Paid`, `Failed`, `Replay`, and `LateSuccess` scenarios plus Order module verify.

### G6 — Aggregate local E2E and operational evidence

- Complete `All` topology/fixture orchestration using real service boundaries.
- Verify restart, Kafka/Registry/PostgreSQL/Redis recovery and sanitized diagnostics.
- Add Saga/outbox/reconciliation/manual-review metrics, alerts, readiness details, and runbook.
- Gate: affected modules and `feature-044-purchase-saga.ps1 -Scenario All`.

### G7 — Repository convergence

- Run architecture, formatting, contract, script syntax, Kustomize dry-run, affected module verify,
  and full monorepo verify.
- Update validation evidence and close every task only with command/result references.
- No cloud state changes occur in G1–G7.

### G8 — One cloud release and Phase 24 completion

- Provision the new EKS topic/subjects with the reviewed Phase 20 workflow.
- Run selective delivery for affected service images once, merge one immutable promotion PR, and
  wait for Argo and Deployments.
- Run Phase 21 release verification and Phase 24 Stripe Checkout/webhook/replay/final-Order smoke.
- Exercise rollback by disabling Order command production first and restoring prior immutable tags
  without deleting Saga/payment/reservation data.

Cart and Notification remain deferred after G8.

## Runtime design

### Saga start

```text
PurchaseAcceptedV1
  -> existing Order adapter validates/maps
  -> atomic order_db transaction
       + Order(PENDING_PAYMENT) + line
       + accepted-purchase inbox
       + PurchaseSaga(PAYMENT_PENDING, deadline=expiresAt-30s)
       + OrderCreated outbox
       + PaymentRequested outbox
  -> commit / Kafka ack
  -> outbox publishes both stable messages
```

### Paid path

```text
PaymentSucceededV1
  -> Order locks Saga/Order and applies participant version
  -> Saga CONFIRMING_RESERVATION + Confirm command outbox + inbox

ConfirmPurchaseReservationV1
  -> Flash Sale locks reservation
  -> reservation CONFIRMED + command inbox + confirmed-event outbox
  -> commit; Redis confirmation reconciliation retries independently

PurchaseReservationConfirmedV1
  -> Order CONFIRMED + Saga COMPLETED + inbox + OrderConfirmed outbox
```

### Unpaid terminal path

```text
PaymentFailedV1
  -> map reason to desired Order status
  -> Saga RELEASING_RESERVATION + Release command outbox + inbox

ReleasePurchaseReservationV1
  -> Flash Sale RELEASED (or reports already EXPIRED)
  -> command inbox + released-event outbox
  -> Redis quota reconciliation exactly once

PurchaseReservationReleasedV1
  -> Order desired CANCELLED/EXPIRED + Saga COMPENSATED
  -> terminal Order event outbox + inbox
```

### Late success

```text
higher-version PaymentSucceededV1
  -> ignore any lower failure as superseded
  -> while release is in flight: attempt Confirm command with stable new Saga version
  -> confirmed result: Order CONFIRMED / Saga COMPLETED
  -> released/expired current-state result while Order is still pending:
       Order PENDING_PAYMENT / Saga MANUAL_REVIEW
  -> if Order is already CANCELLED/EXPIRED and Saga COMPENSATED:
       atomically correct Order PENDING_PAYMENT / Saga MANUAL_REVIEW
       + OrderPaymentReviewRequired outbox caused by PaymentSucceededV1
       no automatic refund
```

## Configuration and rollout

Planned runtime flags are service-owned and explicit:

- Order Payment-result consumer enabled.
- Order reservation-result consumer enabled.
- Order Saga command production enabled.
- Flash Sale reservation-command consumer enabled.
- Flash Sale Redis-finalization reconciliation enabled.

Local Compose enables them for Feature 044. Cloud config is reviewed with the implementation PR;
old images ignore unknown values. Main topics/schemas are provisioned before promoted images start.
Kafka buffers messages if one Deployment rolls out before another.

Rollback order:

1. disable new Order Payment/reservation command production;
2. leave consumers/outboxes draining or explicitly paused with evidence;
3. restore prior immutable image tags through GitOps;
4. preserve all database rows, topics, schemas, receipts, and PVCs.

## Observability

- Low-cardinality metrics: Saga transitions, current state counts, step age, duplicate/stale/conflict
  results, manual-review count, participant consumer outcome, outbox lag/failures, Redis
  reconciliation lag/failures.
- Structured logs contain safe Order/Saga/reservation/payment IDs and result categories only.
- Trace context propagates from the accepted purchase through all commands/results and terminal
  events.
- PostgreSQL/readiness remains gating for durable work. Kafka/Registry/Redis incidents surface as
  named health components/metrics without making owner Order queries unavailable.
- Alerts cover old pending steps, manual review, outbox backlog, DLT growth, and Redis reconciliation
  backlog.

## Validation strategy

Required implementation evidence:

1. Contract generation/compatibility and controlled Registry registration.
2. Pure domain state/deadline/reason/late-success tests.
3. ArchUnit inward-dependency tests for both affected services.
4. Liquibase/Testcontainers migration and constraint tests.
5. Atomic transaction rollback and duplicate/concurrent-delivery tests.
6. Kafka key/header/schema/DLT/outbox retry integration tests.
7. Redis Lua exact-once and PostgreSQL-to-Redis recovery tests.
8. Scenario-selectable Docker Compose smoke without cross-service SQL fixtures.
9. Affected module verification:
   - `./mvnw -pl contracts/kafka-avro-contracts -am verify`
   - `./mvnw -pl services/order-service -am verify`
   - `./mvnw -pl services/flashsale-service -am verify`
10. Full `./mvnw clean verify`.
11. `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`.
12. One cloud image release, Argo/rollout verification, Phase 21, Phase 24, and rollback evidence.

## Complexity Tracking

No Constitution violation requires an exception. The new Saga table, inbox table, command inbox,
and Redis reconciliation marker are the minimum durable state required to coordinate an at-least-once
distributed workflow without sharing databases or introducing 2PC.
