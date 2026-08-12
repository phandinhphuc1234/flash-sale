# Tasks: Inventory Service Core

**Feature**: `016-inventory-service`  
**Status**: Approved for core PostgreSQL/HTTP phase; Kafka decisions and publisher deferred  
**Source**: [spec.md](spec.md), [plan.md](plan.md)

## Execution policy

Implement only the core phase below. Tasks T027–T030 are intentionally blocked until the four Kafka
decisions in the spec are approved. Every completed task must record its validation command and result.

## Phase 1 — Setup

- [ ] T001 Add approved Spring Data JPA, PostgreSQL runtime, and test dependencies to `services/inventory-service/pom.xml`; defer Spring Kafka until Kafka decisions are approved.
- [ ] T002 Configure inventory application identity, datasource placeholders, Liquibase path, Actuator probes/Prometheus exposure, and trace propagation in `services/inventory-service/src/main/resources/application.yml`.
- [ ] T003 Add inventory database initialization to `infra/docker/postgres/init/01-create-databases.sql` only if the database is not already present.
- [x] T004 Update the service Compose environment and health dependency under `infra/docker/compose.yml` without exposing internal allocation endpoints publicly.

## Phase 2 — Foundational schema and boundaries

- [ ] T005 Create Liquibase master/includes under `services/inventory-service/src/main/resources/db/changelog/`.
- [ ] T006 Create the `inventory_items`, `campaign_stock_allocations`, `stock_movements`, and `outbox_events` schema with checks, indexes, and unique constraints in `services/inventory-service/src/main/resources/db/changelog/changes/001-create-inventory-schema.sql`.
- [x] T007 Add feature-local Clean/Hexagonal packages under `services/inventory-service/src/main/java/com/philia/flashsale/inventory/`; create only the domain/application/adapter boundaries required by each feature.
- [ ] T008 Add common inventory error codes and cross-feature HTTP error mapping in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/websupport/error/`.
- [x] T009 Add Gateway route/security configuration for `/api/v1/admin/inventory/**` requiring `INVENTORY_ADMIN` in `services/api-gateway/src/main/resources/application.yml` and `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/`.
- [x] T010 Add `INVENTORY_ADMIN` to the existing admin authority contract in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/domain/AccountRole.java` and cover it with JWT compatibility tests.
- [x] T011 Add inventory service-side JWT validation for `INVENTORY_ADMIN` and service-to-service `SCOPE_INVENTORY_WRITE` in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/configuration/`.

## Phase 3 — US1: Initialize and adjust inventory

**Goal**: Maintain one durable inventory item per variant and safely adjust physical stock.

**Independent test**: Initialize/retry a variant and apply valid/invalid stock adjustments against PostgreSQL.

- [ ] T012 [P] [US1] Add `InventoryItem` and quantity invariants in the flat `stock/` feature package.
- [ ] T013 [P] [US1] Add stock commands/results and application orchestration in `stock/application/`.
- [ ] T014 [US1] Add inventory JPA entity, repository, pessimistic-lock query, and mapper beside the model in `stock/`.
- [ ] T015 [US1] Add initialization and adjustment application services with request-id replay/conflict handling in `stock/application/`.
- [ ] T016 [US1] Add admin HTTP request/response DTOs and controller for adjustment/read routes in `web/`.
- [ ] T017 [US1] Add domain tests for available quantity, positive quantities, and decrease-below-allocated rejection in `src/test/java/.../stock/`.
- [ ] T018 [US1] Add PostgreSQL integration tests for initialization uniqueness, adjustment transactions, and idempotent retries in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/stock/integration/`.
- [ ] T019 [US1] Add HTTP contract tests for `INVENTORY_ADMIN`, validation, success envelopes, and 400/404/409 errors in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/stock/contract/`.

## Phase 4 — US2: Read inventory and movements

**Goal**: Expose derived inventory state and immutable movement history using the shared pagination contract.

**Independent test**: Query an existing variant and movement page with `PageResponse<T>` and stable ordering.

- [ ] T020 [P] [US2] Add immutable movement model and persistence entity/repository in the flat `movement/` feature package.
- [ ] T021 [US2] Add movement query orchestration ordered by `createdAt DESC, id DESC` in `movement/application/`.
- [ ] T022 [US2] Add paginated movement endpoint using `common-web` `PageResponse<T>`/`PageMeta`, default page 0/size 20 and maximum size 100, in `web/`.
- [ ] T023 [US2] Add movement pagination and immutability contract tests in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/movement/contract/`.

## Phase 5 — US3: Campaign allocation lifecycle

**Goal**: Allocate, query, release, and reconcile campaign stock without overselling or double mutation.

**Independent test**: Concurrent 80+80 allocation against 100 succeeds once; release and balanced reconciliation produce correct terminal states.

- [ ] T024 [P] [US3] Add `CampaignStockAllocation`, status transitions, and reconciliation invariants in the flat `allocation/` feature package.
- [ ] T025 [US3] Add allocation/release/reconcile commands, results, and orchestration in `allocation/application/`.
- [ ] T026 [US3] Add allocation JPA entity/repository with unique `(campaign_id, variant_id)` and locked lifecycle queries in `allocation/`.
- [ ] T027 [US3] Add allocation application services with deterministic lock ordering, idempotency replay/conflict, release ownership, and reconciliation behavior in `allocation/application/`.
- [ ] T028 [US3] Add internal allocation lifecycle controller and service-JWT boundary for `/internal/v1/campaign-stock-allocations` in `web/`.
- [ ] T029 [US3] Add domain tests for allocation, terminal states, release, and balanced reconciliation in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/allocation/domain/`.
- [ ] T030 [US3] Add PostgreSQL concurrency/transaction tests for allocation, duplicate requests, release, invalid reconciliation, and rollback in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/allocation/integration/`.
- [ ] T031 [US3] Add internal HTTP contract tests for 404/409 behavior, service authority, idempotency, and lifecycle responses in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/allocation/contract/`.

## Phase 6 — Deferred Kafka integration

- [ ] T032 Resolve initialization transport and update `contracts/initialization.md` before adding Kafka or initialization adapters.
- [ ] T033 Resolve rejection-event persistence, Kafka topics/versioning, and update `contracts/events.md`.
- [ ] T034 Resolve outbox polling, retry, claim, backoff, and permanent-failure policy in `research.md` and `plan.md`.
- [ ] T035 Add approved Spring Kafka dependency and implement outbox publisher/initialization consumer in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/outbox/`.
- [ ] T036 Add Kafka contract, redelivery/idempotency, trace-header, and publisher failure tests in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/outbox/`.

## Phase 7 — Polish and validation

- [ ] T037 Add mutation/outbox/lock/HTTP metrics and trace assertions without constructing a Prometheus registry in Java.
- [ ] T038 Update `docs/database/inventory-service-schema.md`, service README, and architecture diagrams after the schema is implemented.
- [x] T039 Run `./mvnw -pl services/inventory-service -am verify` and record evidence in this feature.
- [ ] T040 Run Compose smoke scenarios from `quickstart.md`; record health, migration, authorization, pagination, and concurrency results.
- [ ] T041 Add Springdoc OpenAPI/Swagger UI, separated web-adapter API documentation interfaces,
  and a disabled-by-default documentation switch; verify `/v3/api-docs` only when
  `INVENTORY_API_DOCS_ENABLED=true`.

## Dependencies

```text
T001-T011 -> T012-T019 -> T020-T023 -> T024-T031 -> T037-T040
T032-T036 are blocked until the four Kafka decisions are approved.
```

## Parallel opportunities

- T012 and T013 can proceed in parallel after T007.
- T020 can proceed in parallel with the later US1 tests after the schema is available.
- T024 can proceed in parallel with T020 after foundational boundaries are complete.

## MVP scope

Implement T001–T031 plus T037–T040. Defer T032–T036 until Kafka decisions are approved. The MVP must
not claim that Product variant initialization or Kafka publication is complete while those tasks remain
deferred.

## Validation evidence

### 2026-07-28 — Inventory package-boundary refactor

- **Scope**: T007 and T039 only; no HTTP route, schema, stock rule, idempotency behavior, outbox
  payload, or deferred Kafka behavior was changed.
- **Architecture red test before refactor**:
  `./mvnw -pl services/inventory-service -am -Dtest=InventoryCleanArchitectureTest
  -Dsurefire.failIfNoSpecifiedTests=false test` — exit `1`; nine application-to-adapter/Spring Data
  dependency violations were detected.
- **Validation command**: `./mvnw -pl services/inventory-service -am verify`.
- **Result**: exit `0`; `common-web` and `inventory-service` built successfully; 10 Inventory tests
  passed, including domain, application, dependency-boundary, PostgreSQL 17/Liquibase context, and
  six-route mapping regression coverage.
- **CI/PR reference**: local validation only; no CI or PR reference was created in this session.

### 2026-07-29 — Partial T040 Compose topology and JWT compatibility evidence

- **Scope**: Partial T040 evidence only. This run covered migration, health, Gateway/service JWT
  compatibility, `INVENTORY_ADMIN` enforcement, and a real Inventory PostgreSQL not-found query. It
  did not cover movement pagination or the concurrent allocation/reconciliation scenarios, so T040
  remains unchecked.
- **Detailed evidence**:
  [`docs/local-compose-smoke-validation.md`](../../docs/local-compose-smoke-validation.md).
- **Image/migration result**: Inventory image built successfully; the one-off Liquibase container
  exited `0` and reported the single Inventory changeset already applied.
- **Health result**: Inventory `/actuator/health` returned HTTP 200.
- **Authorization result**: anonymous request returned `401 UNAUTHENTICATED`; a `ROLE_USER` JWT
  returned Gateway-owned `403 INVENTORY_ADMIN_REQUIRED`; an Auth-issued admin JWT containing
  `INVENTORY_ADMIN` passed both Gateway and Inventory validation.
- **Persistence result**: the authorized request for a random missing variant reached Inventory and
  returned `404 INVENTORY_NOT_FOUND` from the Inventory-owned PostgreSQL flow.
- **Remaining T040 evidence**: movement pagination, initialized fixture behavior, idempotency,
  allocation concurrency, release, reconciliation, rollback, and outbox-row assertions.
- **Focused JWT/security regression**:
  `.\mvnw.cmd --% -pl services/inventory-service -am
  -Dtest=InventoryJwtTrustConfigurationTests,InventorySecurityConfigurationTests
  -Dsurefire.failIfNoSpecifiedTests=false test` — exit `0`; canonical `iss`, `aud`, `sub`, and
  `typ=at+jwt` acceptance/rejection plus non-web migration security isolation passed.
- **Final reactor gate**: `.\mvnw.cmd clean verify` — exit `0`; 12 modules, 257 tests, zero
  failures/errors/skips. This completes T004 and verifies T009–T011; it does not complete T040.
- **CI/PR reference**: local validation only.
