# Feature 020 Validation Ledger

**Feature**: Order Service Core MVP
**Scope for this branch**: G3 / T015-T029
**Status**: G3 complete

This ledger records commands, scope, exit status, and evidence for each approved task group. A
checked task is not complete until its evidence is recorded here.

## G1 Foundation

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T001 | `./mvnw -pl services/order-service -am test` | Approved dependency set and module compilation | PASS | Reactor: common-web 9 tests, Kafka contracts 9 tests, Order 3 tests; BUILD SUCCESS (2026-08-15) |
| T002-T003 | `./mvnw -pl services/order-service -am test` | Generated `OrderCreatedV1`, logical types, forbidden fields, subject and compatibility tests | PASS | `OrderCreatedSchemaTests`: 3/3; contract module: 9/9 tests (2026-08-15) |
| T004-T005 | `./mvnw -pl services/order-service -am test` | Typed properties and baseline runtime configuration load | PASS | `OrderServiceApplicationTests`: 1/1; `@ConfigurationPropertiesScan` context started (2026-08-15) |
| T006 | `./mvnw -pl services/order-service -am test` | Testcontainers support compiles; containers remain opt-in outside integration profile | PASS | PostgreSQL/Kafka support and Registry condition compiled; no container started by baseline suite (2026-08-15) |
| T007 | `./mvnw -pl services/order-service -am test` | Inward dependency and adapter boundary rules | PASS | `OrderArchitectureTests`: 2/2 (2026-08-15) |
| T008 | `git diff --check` and this ledger review | Documentation and evidence slot integrity | PASS | Whitespace and ledger review passed (2026-08-15) |

## Group Checkpoint

- [x] Order module compiles against generated contracts.
- [x] Typed runtime properties bind from `application.yml`.
- [x] Architecture guard tests pass with no domain/application framework leakage.
- [x] Contract and module validation commands have recorded exit status.

## G2 PostgreSQL and Local Transaction Foundation

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T009-T010 | `./mvnw -pl services/order-service -am test -Dtest=OrderSchemaMigrationIntegrationTests` | Liquibase changeset and master include | PASS | PostgreSQL 17 Testcontainers created all four Order tables and one Liquibase changeset; 6/6 tests passed (2026-08-15) |
| T011 | `./mvnw -pl services/order-service -am test -Dtest=OrderSchemaMigrationIntegrationTests` | Clean migration, exact types, constraints, indexes, rollback | PASS | Exact NUMERIC(19,4), TIMESTAMPTZ, JSONB, identity/status/FK/check/index assertions and reverse rollback passed; 6/6 (2026-08-15) |
| T012 | `./mvnw -pl services/order-service -am test -Dtest=PostgreSqlOrderIdentityLockKeyTests` | Deterministic, domain-separated advisory-lock keys | PASS | 3/3 tests passed; same identity is stable and purchase/reservation namespaces are separated (2026-08-15) |
| T013 | `./mvnw -pl services/order-service -am test -Dtest=OrderPropertiesTests` | Property validation and supported defaults | PASS | 3/3 tests passed for required values, bounded batch/page settings, retry defaults, and invalid input (2026-08-15) |
| T014 | `./mvnw -pl services/order-service -am verify` | Order module, contract, architecture, and G2 foundation suite | PASS | Reactor common-web 9, Kafka contracts 9, Order 15 tests; BUILD SUCCESS (2026-08-15) |

## Command Results

- `./mvnw -pl services/order-service -am test`: PASS; common-web 9 tests, Kafka contracts 9 tests,
  Order 3 tests, 0 failures.
- `./mvnw -pl services/order-service -am verify`: PASS; all four reactor projects succeeded.
- Production dependency scan: PASS; no Redis, Feign, MapStruct, Payment SDK, or distributed-lock
  dependency introduced.
- `git diff --check`: PASS; no whitespace errors in the G1 diff.

## G2 Checkpoint

- [x] `order_db` Liquibase migration creates the four service-owned durable tables.
- [x] PostgreSQL constraints, exact types, indexes, and development rollback are verified.
- [x] Advisory-lock identity keys are deterministic and collision-domain separated.
- [x] Typed property constraints and supported defaults are verified.
- [x] No Kafka consumer or public endpoint was activated in G2.

## G3 User Story 1 — Durable Order creation

| Task | Validation command | Scope | Result | Evidence |
|------|--------------------|-------|--------|----------|
| T015-T017 | `./mvnw -pl services/order-service -am test -Dtest=OrderDomainTests,AcceptedPurchaseFingerprintTests,CreateOrderFromAcceptedPurchaseServiceTests -Dsurefire.failIfNoSpecifiedTests=false` | Domain invariants, exact scale-4 money, canonical fingerprint, use-case outcomes, and framework-free application boundary | PASS | 9/9 tests passed; BUILD SUCCESS (2026-08-15) |
| T018-T022 | `./mvnw -pl services/order-service -am test -Dtest=OrderDomainTests,AcceptedPurchaseFingerprintTests,CreateOrderFromAcceptedPurchaseServiceTests -Dsurefire.failIfNoSpecifiedTests=false` | Domain models, command/result/ports, fingerprinting, and Order creation orchestration | PASS | Production and test compilation succeeded; 9/9 focused tests passed (2026-08-15) |
| T023-T026 | `./mvnw -pl services/order-service -am test -Dtest=AcceptedPurchasePersistenceIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | JPA entities, repositories, mapper, advisory-lock adapter, identity/clock wiring, and four-row transaction | PASS | PostgreSQL 17 Testcontainers; 5/5 tests passed, including full rollback and Order-number collision recovery (2026-08-15) |
| T027 | `./mvnw -pl services/order-service -am test -Dtest=AcceptedPurchasePersistenceIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | Exact snapshot, event/business replay, contradiction, rollback, and stable outbox identity | PASS | 5/5 PostgreSQL integration tests passed; one Order, line, inbox, and outbox row on the create path (2026-08-15) |
| T028 | `./mvnw -pl services/order-service -am test -Dtest=AcceptedPurchaseConcurrencyIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false` | 100 equivalent deliveries and 100 contradictory concurrent deliveries | PASS | 2/2 tests passed; each scenario leaves exactly one Order/line/outbox fact and conflicting deliveries do not mutate the winner (2026-08-15) |
| T029 | `./mvnw -pl services/order-service -am verify` | Full G3 module validation and reactor dependencies | PASS | Common Web 9, Kafka contracts 9, Order 31 tests; all reactor projects succeeded and the Order JAR was packaged (2026-08-15) |

## G3 Checkpoint

- [x] A valid accepted purchase creates one `PENDING_PAYMENT` Order, one line, one inbox receipt,
  and one stable `OrderCreated` outbox identity in one PostgreSQL transaction.
- [x] Same event and equivalent different-event replays are no-ops; contradictory identities return
  `CONFLICT` without changing the established Order.
- [x] 100-way equivalent and contradictory concurrency evidence passes against PostgreSQL.
- [x] No Kafka consumer or HTTP endpoint was activated in G3; those remain G4/G6 work.

## Later Evidence Slots

The following sections will be expanded by the corresponding approved groups:

- G2: Liquibase migration, PostgreSQL schema, constraints, rollback, and persistence foundation.
- G3-G4: accepted-purchase idempotency, atomic Order creation, Kafka consumer retry/DLT, and replay.
- G5: outbox lease/retry, Kafka publication, Schema Registry compatibility, and recovery.
- G6: owner-only HTTP query, JWT trust, Gateway route, and non-enumerating errors.
- G7-G8: readiness, Compose smoke, failure matrix, concurrency, performance, module, and full build.
