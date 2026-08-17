# Feature 021 Validation Evidence

**Feature**: Payment Service Stripe Checkout MVP  
**Group**: G1 — Module, Versioned Contracts, and Build Foundation  
**Branch**: `codex/payment-g1-contracts`  
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
