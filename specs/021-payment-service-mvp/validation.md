# Feature 021 Validation Evidence

**Feature**: Payment Service Stripe Checkout MVP
**Group**: G1–G4 — Contracts, Domain, PostgreSQL Foundation, Atomic Acceptance, and Kafka Ingress
**Branch**: `codex/payment-g4-kafka-boundary`
**Validated**: 2026-08-18
**Secret handling**: `infra/docker/.env` was not opened, read, rendered, edited, staged, or committed.

## Approved baselines

- `spec.md`, `plan.md`, and `tasks.md` are approved artifacts from Feature 021.
- ADR 0018 records Order-owned Purchase Saga orchestration and Payment command/fact ownership.
- Payment command topic: `flashsale.payment.commands.v1`.
- Payment fact topic: `flashsale.payment.events.v1`.
- Payment command DLT: `flashsale.payment.payment-requested.dlt.v1`.
- All Payment subjects use TopicRecordNameStrategy and `BACKWARD_TRANSITIVE` compatibility.
- Generated records: `PaymentRequestedV1`, `PaymentSucceededV1`, `PaymentFailedV1`.

## G1 implementation evidence

| Task | Validation command/scope | Result |
|---|---|---|
| T001 | `./mvnw -pl contracts/kafka-avro-contracts,services/payment-service -am verify` | PASS; Stripe Java `33.2.0` resolved and Payment packaged |
| T002–T003 | Contract module Avro generation and `PaymentContractSchemaTests` | PASS; 4 Payment contract tests, 13 contract-module tests total |
| T004 | `PaymentArchitectureTests` | PASS; 2 architecture tests |
| T005–T006 | `PaymentConfigurationPropertiesTests` and `PaymentServiceApplicationTests` with non-secret context placeholders and disabled workers | PASS; 3 configuration/context tests; no external provider, broker, or database call |
| T007 | PowerShell AST parse plus schema/topic path and policy review | PASS; `register-payment-schemas.ps1` parses; registration is idempotent and `-CheckOnly` is supported |
| T008 | Reactor `verify` for common-web, Avro contracts, and Payment Service | PASS; exit code 0 |

## Commands and exit status

1. `./mvnw -pl contracts/kafka-avro-contracts -am test` — exit `0`; 13 tests, 0 failures, 0 errors.
2. `./mvnw -pl services/payment-service -am test` — exit `0`; 5 tests, 0 failures, 0 errors.
3. `./mvnw -pl contracts/kafka-avro-contracts,services/payment-service -am verify` — exit `0`.
4. PowerShell parser for `infra/docker/schema-registry/register-payment-schemas.ps1` — exit `0`.
5. `git diff --check` — exit `0`.

The local Windows environment has no executable POSIX shell runtime, so `bash -n` was not available;
the Kafka script follows the repository's existing Bash pattern and will receive executable Linux
validation in CI/infrastructure verification (T104).

## Scope boundary

G1 intentionally does not enable Kafka consumers, outbox publishers, recovery workers, Stripe calls,
HTTP routes, migrations, or Gateway routes. Those behaviors remain in G2–G10 as ordered by `tasks.md`.

## G2 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T009–T011 | Pure domain/value-object tests and `PaymentArchitectureTests` | PASS; Money/identity/deadline validation, six Payment states, seven attempt states, success-dominant transitions, attempt limit, and dependency rules verified |
| T012 | Liquibase `001-create-payment-core-schema.sql` applied to PostgreSQL 16 Testcontainers | PASS; seven Payment-owned tables, checks, unique/partial indexes, claim indexes, safe-data comments, and explicit empty-schema rollback |
| T013–T018 | Hibernate validation plus repository/adapter compilation and wiring | PASS; JPA mappings agree with Liquibase; aggregate, inbox, idempotency, provider receipt, recovery, and outbox ports remain framework-free |
| T019 | `PaymentSchemaMigrationIntegrationTests` and `PaymentJpaSchemaAgreementIntegrationTests` | PASS; schema shape, NUMERIC(19,4), prohibited columns, transaction rollback, Liquibase rollback, and Hibernate `ddl-auto=validate` |
| T020 | `PaymentPersistenceConcurrencyIntegrationTests` | PASS; partial unresolved-attempt uniqueness, `FOR UPDATE SKIP LOCKED`, stale lease reclaim, and outbox claims |
| T021 | `PaymentFoundationConfiguration` and architecture tests | PASS; clock/identity/transaction capabilities are composition-wired without business component scanning |

### G2 commands and results

1. `./mvnw -pl services/payment-service -am test` — exit `0`; Payment module 25 tests, 0 failures, 0 errors; contract module 13 tests, 0 failures, 0 errors.
2. `./mvnw -pl services/payment-service -am -Dtest=PaymentSchemaMigrationIntegrationTests,PaymentPersistenceConcurrencyIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` — exit `0`; 5 PostgreSQL Testcontainers tests pass.
3. `./mvnw -pl services/payment-service -am -Dtest=PaymentJpaSchemaAgreementIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` — exit `0`; Hibernate validates the Liquibase schema.
4. `./mvnw -pl services/payment-service -am verify` — exit `0`; common-web, Avro contracts, and Payment Service reactor build succeeds with 25 Payment tests and 13 contract tests passing.
5. `git diff --check` — exit `0` for tracked changes; `.worktrees/` remains untracked and excluded.

G2 does not enable the Kafka consumer, Checkout provider, HTTP routes, webhook processing, or
recovery scheduler. Those behaviors remain gated by G3–G10.

## G3 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T022 | `AcceptPaymentRequestServiceTests` | PASS; 5 application tests cover immutable snapshots, same-event and equivalent-event replay, contradictory conflict visibility, no provider call, and the expired `PaymentFailed` outbox fact |
| T023 | `PaymentCommandAcceptanceIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; 5 tests prove atomic Payment/inbox persistence, equivalent replay, contradictory fingerprint handling, rollback, and one stable deadline-failure outbox row |
| T024 | `PaymentCommandConcurrencyIntegrationTests` with 100 deliveries per scenario | PASS; 3 tests prove one Payment/inbox for same-event copies and equivalent event IDs, and no mutation of the winning snapshot during contradictory races |
| T025–T026 | Application command/result models and `AcceptPaymentRequestService` | PASS; canonical SHA-256 business fingerprint excludes transport IDs, validates the v1 envelope, and performs inbox/Payment/outbox work through the transaction port |
| T027 | `PaymentCommandInboxPersistenceAdapter` | PASS; JPA types remain in the adapter, advisory transaction locks serialize first acceptance for an Order, and repository uniqueness handles durable identity |
| T028 | `PaymentAcceptanceConfiguration` and `PAYMENT_ACCEPTANCE_ENABLED` | PASS; application capability is composition-wired only when explicitly enabled; no Kafka listener or Stripe/provider call is enabled in G3 |
| T029 | Commands and results below | PASS; evidence recorded on the G3 branch |

### G3 commands and results

1. `./mvnw -pl services/payment-service -am -Dtest=AcceptPaymentRequestServiceTests -Dsurefire.failIfNoSpecifiedTests=false test` — exit `0`; 5 application tests, 0 failures, 0 errors.
2. `./mvnw -pl services/payment-service -am -Dtest=PaymentCommandAcceptanceIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` — exit `0`; 5 PostgreSQL Testcontainers tests, 0 failures, 0 errors.
3. `./mvnw -pl services/payment-service -am -Dtest=PaymentCommandConcurrencyIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test` — exit `0`; 3 PostgreSQL Testcontainers tests with 100 deliveries per scenario, 0 failures, 0 errors.
4. Each acceptance test truncates Payment-owned tables before execution and verifies one Payment/inbox identity; expired acceptance verifies exactly one stable `PaymentFailed` outbox identity and replay does not add another row.
5. `git diff --check` — PASS after G3 implementation changes.

G3 intentionally stops at the application acceptance port. The Kafka `PaymentRequested.v1`
consumer, retry/DLT behavior, Stripe Checkout, webhook processing, publication, recovery jobs,
HTTP routes, and Gateway wiring remain disabled and are implemented by G4–G10.

## G4 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T030 | `PaymentRequestedAvroMapperTests` | PASS; 4 tests cover envelope/logical fields, key and aggregate alignment, money/currency validation, W3C headers, and malformed trace input |
| T031 | `PaymentRequestedKafkaConsumerTests` | PASS; 4 tests cover successful acknowledgement, conflict/poison classification, storage retry propagation, and no acknowledgement before use-case return |
| T033–T034 | `PaymentRequestedAvroMapper`, typed exceptions, and `PaymentRequestedKafkaConsumer` | PASS; Kafka/Avro types stop at the inbound adapter, trace context is restored to MDC, and the application use case is acknowledged only after it returns |
| T035 | `PaymentKafkaConsumerConfiguration` | PASS; SpecificRecord deserialization, manual-immediate acknowledgement, delivery-attempt headers, 1/3/10-second backoff, typed non-retryable classification, and command-specific DLT routing are wired behind `PAYMENT_KAFKA_CONSUMER_ENABLED` |
| T036 | `README-payment-dlt.md` | PARTIAL; operator replay and safe-diagnostic rules are documented; live DLT header assertion remains part of the pending integration suite |
| T032/T037 | Live Kafka + Schema Registry suite | PENDING; requires the project-owned Kafka/Schema Registry runtime and will be run without opening or rendering `infra/docker/.env` |

### G4 commands and results

1. `./mvnw -pl services/payment-service -am test` — exit `0`; Payment module 46 tests, 0 failures, 0 errors; contract module 13 tests, 0 failures, 0 errors.
2. `git diff --check` — exit `0` for tracked G4 changes.

G4 code is now present but the live broker/registry and DLT evidence (T032, T036, and T037) remains
intentionally open until the project owner starts the approved runtime. No Kafka or Schema Registry
secret was read or changed.
