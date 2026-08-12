# Implementation Plan: Inventory Service

**Branch**: `016-inventory-service` | **Date**: 2026-07-27 | **Spec**: [spec.md](spec.md)

**Input**: `C:\Users\MSi\Downloads\inventory-service-spec.md`, normalized into [spec.md](spec.md)

## Summary

Build the durable inventory bounded context for Product variants. PostgreSQL owns physical stock,
campaign allocations, immutable movements, and a transactional outbox. The plan covers initialization,
stock adjustments, inventory reads, campaign allocation queries, release, reconciliation, and movement
history. Flash-sale customer contention and Redis runtime stock remain in `flashsale-service`.

The implementation uses feature-oriented Clean/Hexagonal packages, Spring Data JPA persistence with
explicit row-locking queries for competing stock commands, Liquibase migrations, documented HTTP/Kafka
contracts, and an idempotent outbox publisher.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.x

**Primary Dependencies**: Spring Web MVC, Spring Data JPA, PostgreSQL JDBC, Liquibase, Spring Kafka,
Micrometer Actuator, and only other dependencies approved in this plan.

**Storage**: PostgreSQL database `inventory_db`; service-owned Liquibase migrations; Kafka outbox.

**Testing**: JUnit domain tests, PostgreSQL/Testcontainers transaction tests, HTTP contract tests,
outbox/publisher tests, Kafka redelivery/idempotency tests, and concurrency tests.

**Target Platform**: Independently deployable Spring Boot service in local Compose and future Kubernetes.

**Project Type**: Microservice web application with internal HTTP and asynchronous Kafka integration.

**Performance Goals**: Correctness-first durable commands; concurrent allocation must never oversell.
Customer purchase traffic must not call this service per attempt.

**Constraints**: No negative balances; allocated cannot exceed on-hand; one allocation per campaign
and variant in Phase 1; every state-changing command is idempotent; state, movement, and outbox commit
atomically; no Redis runtime-stock or cross-service foreign keys.

**Scale/Scope**: Phase 1 portfolio MVP; one inventory item per Product variant and one allocation per
`(campaign_id, variant_id)`.

## Constitution Check

*GATE: Draft plan. Implementation is blocked until P0/P1 decisions are resolved and this plan/spec are approved.*

- **Specification traceability**: PASS for the eight use cases and invariants in [spec.md](spec.md).
  Open decisions are explicit and not silently implemented.
- **Service ownership**: PASS. `inventory-service` owns its schema, JPA entities, repositories,
  migrations, and tests; no cross-service database access or shared domain model is introduced.
- **Communication**: PASS subject to contract decisions. Public/admin traffic enters through Gateway;
  internal synchronous calls use documented HTTP; asynchronous calls use versioned Kafka.
- **Data and messaging**: PASS. PostgreSQL is durable truth; Redis Lua/runtime stock is out of scope;
  successful mutations use a transactional outbox and consumers are idempotent.
- **Root infrastructure ownership**: PASS. Shared orchestration remains under `infra/`; runtime
  configuration and Liquibase migrations remain in the service.
- **Observability**: PASS. Actuator liveness/readiness/Prometheus endpoints and trace propagation are
  baseline requirements; no manual Prometheus registry is constructed.
- **Contracts and dependencies**: BLOCKED only for the four deferred Kafka decisions. Authorization,
  SKU snapshot, release ownership, and pagination are approved below. New dependencies are listed with
  purpose.
- **Validation**: PASS for domain, PostgreSQL integration, HTTP contract, Kafka/outbox, concurrency,
  operational, and focused load layers. A customer purchase load test is omitted because that path is
  explicitly owned by `flashsale-service`.

## Approved Decisions

- **Authorization**: Human inventory operations use `INVENTORY_ADMIN` through Gateway at
  `/api/v1/admin/inventory/**`; `inventory-service` revalidates it. Internal allocation lifecycle
  endpoints use service-to-service JWT with `SCOPE_INVENTORY_WRITE` and are not public Gateway routes.
- **SKU snapshot**: Capture the SKU at initialization as a read-only, non-authoritative snapshot. Product
  Service remains authoritative; a future versioned Product update event may refresh the snapshot.
- **Release ownership**: Campaign Service invokes release for cancellation/pre-start. Flash Sale Service
  owns reconciliation because it knows sold and returned runtime quantities.
- **Movement pagination**: Reuse `common-web` `PageResponse<T>` and `PageMeta`; order by
  `createdAt DESC, id DESC`, default `page=0,size=20`, and reject sizes greater than 100.

## Human Decisions Required Before Implementation

| Priority | Decision | Blocking artifact | Options |
|---|---|---|---|
| P0 | Variant initialization trigger | `contracts/initialization.md`, spec | `ProductVariantCreated` event, internal HTTP command, or both |
| P0 | Rejected allocation event strategy | `contracts/events.md`, outbox design | Synchronous rejection only; separate rejection transaction; durable command record |
| P1 | Kafka topics, keys, schema version, headers, rollout order | `contracts/events.md` | Confirm repository event convention |
| P1 | Outbox polling, claim, retry, backoff, permanent failure | `research.md`, plan | Confirm operational policy |

## Design Decisions and Boundaries

### Ownership

`inventory-service` owns `inventory_db` and every table in [data-model.md](data-model.md). `variant_id`
and `campaign_id` are logical UUID references; no foreign key crosses a service database. Product Service
remains the source of Product/Variant identity. `sku_snapshot` is captured at initialization and is
read-only/non-authoritative until a future approved Product update event refreshes it.

### Command transaction and locking

Every state-changing command runs in one PostgreSQL transaction. The transaction applies the aggregate
mutation, writes one immutable movement, and writes required outbox rows before commit. Allocation,
release, reconciliation, and stock adjustment lock the affected inventory row with `SELECT ... FOR UPDATE`;
lifecycle operations also lock the allocation row. Lock ordering must be consistent across handlers.

Optimistic `version` supports ordinary updates/read concurrency, but pessimistic row locking is the
correctness mechanism for competing stock commands. `available_quantity` is derived and never stored.

### Idempotency

All commands carry `requestId`. Allocation and movement records have unique request identifiers. A repeat
with the same business payload returns the original/equivalent result; conflicting reuse returns a
conflict error. Initialization replay follows the approved initialization contract; release and
reconciliation are owned by their approved callers.

### Event and outbox boundary

Successful allocation, release, and reconciliation events are written transactionally. `InventoryAdjusted`
is optional until approved. A rejected allocation cannot be written into the same transaction that rolls
back the rejected mutation; the rejection-event decision is therefore a P0 gate. Publication is
at-least-once and consumers tolerate duplicates.

### API boundary

Public/admin HTTP enters through Gateway and requires `INVENTORY_ADMIN`; the inventory service repeats
the check. Campaign allocation lifecycle endpoints are internal HTTP contracts authenticated with a
service JWT carrying `SCOPE_INVENTORY_WRITE`, not shopper APIs. Responses/errors reuse the accepted
common-web contract after service-specific mapping is documented. Movement pagination uses the shared
envelope with default size 20 and maximum size 100.

## Project Structure

```text
services/inventory-service/
├── pom.xml
├── Dockerfile
└── src/
    ├── main/java/com/philia/flashsale/inventory/
    │   ├── InventoryServiceApplication.java
    │   ├── stock/
    │   │   ├── domain/{model,exception}
    │   │   ├── application/{command,query,result,port/in,port/out,usecase,exception}
    │   │   ├── adapter/in/web/{request,response,mapper}
    │   │   └── adapter/out/persistence/jpa/{entity,repository,mapper}
    │   ├── allocation/
    │   │   ├── domain/{model,exception}
    │   │   ├── application/{command,result,port/in,port/out,usecase,exception}
    │   │   ├── adapter/in/web/{request,response,mapper}
    │   │   └── adapter/out/persistence/jpa/{entity,repository,mapper}
    │   ├── movement/
    │   │   ├── domain/model
    │   │   ├── application/{query,result,port/in,port/out,usecase}
    │   │   ├── adapter/in/web/{response,mapper}
    │   │   └── adapter/out/persistence/jpa/{entity,repository,mapper}
    │   ├── outbox/adapter/out/persistence/jpa/{entity,repository}
    │   ├── websupport/             # cross-feature HTTP error translation
    │   └── configuration/
    ├── main/resources/
    │   ├── application.yml
    │   └── db/changelog/
    └── test/java/com/philia/flashsale/inventory/
        ├── stock/
        ├── allocation/
        ├── outbox/
        ├── contract/
        └── integration/
```

**Structure Decision**: Group by business feature and create Clean/Hexagonal boundary packages only
when a feature has that responsibility. Stock and allocation have domain/application/driving HTTP
and persistence adapters. Movement owns its movement-history HTTP adapter as well as its
domain/application/persistence boundaries; moving the controller does not change the approved URL.
Outbox has only its
driven persistence adapter until Kafka publishing is approved. Cross-feature HTTP error translation
is under `websupport`; Spring wiring remains in `configuration`. Domain rules do not import JPA,
HTTP, Kafka, or provider SDK types. See [ADR 0011](../../docs/adr/0011-inventory-feature-hexagonal-boundaries.md).
The renamed service boundary is recorded in [ADR 0010](../../docs/adr/0010-inventory-service-boundary.md).

## New Dependencies and Purpose

| Module | Dependency | Purpose | Alternative considered |
|---|---|---|---|
| `services/inventory-service` | `spring-boot-starter-data-jpa` | Persistence mappings, transactions, pessimistic locking | Plain JDBC would require hand-written mapping/transaction plumbing and diverge from repo JPA conventions |
| `services/inventory-service` | PostgreSQL JDBC runtime | PostgreSQL datasource | No alternative for the approved durable store |
| `services/inventory-service` | `springdoc-openapi-starter-webmvc-ui` 2.8.17 | Development-only live OpenAPI JSON/YAML and Swagger UI for the implemented HTTP boundary | A hand-maintained static OpenAPI file would drift from controller routes and validation; documentation is disabled by default outside explicit development use |
| `services/inventory-service` | `spring-kafka` | Versioned outbox publisher and initialization consumer if event trigger is approved | HTTP-only cannot provide the required event integration |
| test scope | Testcontainers PostgreSQL/Kafka, if not already supplied by `test-support` | Real integration/concurrency evidence | H2 does not reproduce PostgreSQL locking/constraints |

Dependencies must be added only after this plan is approved.

## Implementation Phases

### Core implementation note

The current implementation may complete the PostgreSQL/HTTP adjustment, read, allocation, release,
and reconciliation paths without selecting an initialization transport. The initialization use case is
kept behind the application boundary; no public initialization endpoint or Kafka consumer is introduced
until the deferred transport decision is approved. Outbox rows are persisted as `PENDING`, but no Kafka
publisher is wired in this feature phase.

### Phase 0 — Resolve and record decisions

1. Record the four approved decisions in the spec and contracts.
2. Confirm the common-web success/error and pagination contracts.
3. Mark initialization transport, rejection-event delivery, Kafka topic/versioning, and outbox retry
   policy as deferred Kafka work; do not implement those adapters yet.

### Phase 1 — Baseline and schema

1. Add approved dependencies to `services/inventory-service/pom.xml`.
2. Configure Actuator, trace propagation, datasource, and Liquibase declaratively.
3. Add Liquibase changesets for `inventory_items`, `campaign_stock_allocations`, `stock_movements`,
   `outbox_events`, indexes, checks, and unique constraints.
4. Verify forward migration, rollback/mitigation, and context loading.

### Phase 2 — Stock and read use cases

1. Implement `InventoryItem` invariants and derived available quantity.
2. Implement initialization and stock adjustment input ports/adapters.
3. Implement inventory-state and movement-history queries using the accepted pagination contract.
4. Add domain, application, persistence, and HTTP contract tests.

### Phase 3 — Allocation lifecycle

1. Implement campaign allocation with deterministic row locking and one allocation per campaign/variant.
2. Implement allocation query, release, and reconciliation state transitions.
3. Write movements and approved success outbox events in the same transaction.
4. Add idempotency conflict/replay, invalid-state, insufficient-stock, and concurrency tests.

### Phase 4 — Deferred Kafka publisher and integration

1. After the four Kafka decisions are approved, implement the polling/claim/retry strategy.
2. Publish versioned events with event ID, aggregate ID, occurred time, trace/correlation headers, and
   stable Kafka key.
3. Add publisher failure/retry and duplicate-consumer contract tests.
4. Document Flash Sale initialization and release/reconciliation compatibility.

### Phase 5 — Operational validation

1. Run module verification and PostgreSQL integration scenarios.
2. Run HTTP contract and Kafka/outbox tests.
3. Run the concurrent-allocation test and record evidence.
4. Run Compose smoke validation for migration, health, readiness, metrics, trace ID, and event path.
5. Validate any changed Kubernetes overlay with client-side dry run.
6. With API documentation explicitly enabled, verify the generated OpenAPI document contains only
   the approved Inventory HTTP routes and Swagger UI remains disabled by default.

## Test Matrix

| Layer | Required coverage |
|---|---|
| Domain unit | Quantity invariants, derived availability, allocation lifecycle, balanced reconciliation |
| Application unit | Idempotency decisions, port orchestration, error mapping |
| PostgreSQL integration | Row locks, unique constraints, transaction rollback, migrations, cleanup |
| HTTP contract | Request validation, envelope, 404/409 errors, idempotent retries, pagination |
| Kafka/outbox | Atomic rows, publish success/failure, retry, trace headers, duplicate delivery |
| Concurrency | Two allocations competing for the same available quantity |
| Operational | Health probes, Prometheus endpoint, logs/trace propagation, Compose smoke |
| Load | Focused allocation concurrency/load check; no customer-purchase load because it is out of scope |

## Complexity Tracking

| Complexity | Why needed | Simpler alternative rejected because |
|---|---|---|
| Feature-oriented Hexagonal packages | Stock/allocation/outbox have distinct invariants and adapters | A single layer-first package would hide business ownership as the service grows |
| Pessimistic row locks plus idempotency constraints | Concurrent allocation correctness and safe retries | Optimistic retry alone cannot provide the specified clear oversell behavior |
| Transactional outbox publisher | Durable state and event publication must not diverge | Direct post-commit Kafka publish can lose events on process failure |
