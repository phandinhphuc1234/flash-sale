# Feature 020 Validation Ledger

**Feature**: Order Service Core MVP
**Scope for this branch**: G1 / T001-T008
**Status**: G1 complete

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

## Command Results

- `./mvnw -pl services/order-service -am test`: PASS; common-web 9 tests, Kafka contracts 9 tests,
  Order 3 tests, 0 failures.
- `./mvnw -pl services/order-service -am verify`: PASS; all four reactor projects succeeded.
- Production dependency scan: PASS; no Redis, Feign, MapStruct, Payment SDK, or distributed-lock
  dependency introduced.
- `git diff --check`: PASS; no whitespace errors in the G1 diff.

## Later Evidence Slots

The following sections will be expanded by the corresponding approved groups:

- G2: Liquibase migration, PostgreSQL schema, constraints, rollback, and persistence foundation.
- G3-G4: accepted-purchase idempotency, atomic Order creation, Kafka consumer retry/DLT, and replay.
- G5: outbox lease/retry, Kafka publication, Schema Registry compatibility, and recovery.
- G6: owner-only HTTP query, JWT trust, Gateway route, and non-enumerating errors.
- G7-G8: readiness, Compose smoke, failure matrix, concurrency, performance, module, and full build.
