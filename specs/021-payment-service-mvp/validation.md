# Feature 021 Validation Evidence

**Feature**: Payment Service Stripe Checkout MVP
**Group**: G1–G2 — Contracts, Domain, and PostgreSQL Foundation
**Branch**: `codex/payment-g2-domain-persistence`
**Validated**: 2026-08-17
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
