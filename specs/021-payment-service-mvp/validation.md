# Feature 021 Validation Evidence

**Feature**: Payment Service Stripe Checkout MVP
**Group**: G1–G10 — complete Payment Service Stripe Checkout MVP
**Branch**: `codex/payment-g10-operational-evidence`
**Validated**: 2026-08-19
**Secret handling**: `infra/docker/.env` remained ignored, untracked, unedited, unstaged, and
uncommitted; runtime commands consumed it without rendering, copying, or recording secret values.

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
| T036 | `README-payment-dlt.md` and `PaymentRequestedDltIntegrationTests` | PASS; operator replay and safe-diagnostic rules are documented, poison records carry original-topic/cause headers without secret/card data, and corrected replay converges. |
| T032/T037 | `PaymentRequestedConsumerIntegrationTests` and `PaymentRequestedDltIntegrationTests` against local Kafka/Schema Registry | PASS; 3 live tests cover 100 physical duplicates, stable `orderId` key and trace headers, 1/3/10-second retry, commit-before-ack convergence, command DLT, and corrected operator replay. |

### G4 commands and results

1. `./mvnw -pl services/payment-service -am test` — exit `0`; Payment module 48 tests, 0 failures, 0 errors; contract module 13 tests, 0 failures, 0 errors.
2. `git diff --check` — exit `0` for tracked G4 changes.
3. `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/payment-service -am '-Dtest=PaymentRequestedConsumerIntegrationTests' '-Dpayment.kafka.integration=true' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 2 live Kafka/Registry/PostgreSQL tests, 0 failures, 0 errors; 100 duplicates produce exactly 1 Payment and 1 inbox row, while transient attempts observe the configured 1/3/10-second lower bounds.
4. `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/payment-service -am '-Dtest=PaymentRequestedDltIntegrationTests' '-Dpayment.kafka.integration=true' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 1 live DLT/replay test, 0 failures, 0 errors; poison input produces no Payment/inbox, and corrected replay produces exactly 1 Payment and 1 inbox row.

G4 live broker/registry and DLT evidence is complete. No Kafka or Schema Registry secret was read or
changed, and the Payment consumer remains disabled by default.

## G5 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T038 | `HostedCheckoutProviderContractTests` | PASS; exact VND zero-decimal conversion rejects fractional values and metadata is limited to server-owned identifiers |
| T039, T045–T046 | `StripeHostedCheckoutAdapterTests` | PASS; Stripe Java `33.2.0` mapping uses hosted `payment` mode, card-only payment methods, automatic capture, trusted amount/URLs/metadata, earliest valid provider expiry, configured timeouts/retries/API version, and normalized timeout classification |
| T040, T044, T047–T048 | `StartCheckoutServiceTests` | PASS; Transaction A persists the CREATING attempt before provider invocation, replay retrieves by persisted Session identity, client-key conflicts are rejected, and ambiguous provider results converge to durable UNKNOWN/202 semantics |
| T041 | `CheckoutAttemptConcurrencyIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; partial unresolved-attempt uniqueness, no raw client-key column, 100 distinct client-key rows, rollback, and stable response-loss recovery work are verified |
| T042, T049–T051 | `CheckoutControllerTests` | PASS; 201/202 outcomes, no-body rejection, Location, Retry-After, X-Trace-Id, and Cache-Control no-store are verified; error handlers expose only stable codes |
| T043, T052 | `PaymentJwtTrustConfigurationTests`, `PaymentPublicSecurityTests` | PASS; issuer-backed decoder composition plus JWT type/audience/subject/expiry boundary and printable idempotency-key checks are covered |
| T053 | `PaymentCheckoutConfiguration`, `PaymentCheckoutProperties`, `PAYMENT_CHECKOUT_ENABLED` | PASS; Checkout composition is disabled by default and requires acceptance, Checkout, and Stripe flags; provider secrets remain configuration-only |

### G5 commands and results

1. `./mvnw -pl services/payment-service -am '-Dtest=HostedCheckoutProviderContractTests,StripeHostedCheckoutAdapterTests,StartCheckoutServiceTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 11 tests, 0 failures, 0 errors.
2. `./mvnw -pl services/payment-service -am '-Dtest=CheckoutControllerTests,PaymentJwtTrustConfigurationTests,PaymentPublicSecurityTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 6 tests, 0 failures, 0 errors.
3. `./mvnw -pl services/payment-service -am '-Dtest=CheckoutAttemptConcurrencyIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 3 PostgreSQL Testcontainers tests, including 100 different-key inserts, 0 failures, 0 errors.
4. `./mvnw -pl services/payment-service -am verify` — exit `0`; Payment module 70 tests, 0 failures, 0 errors; contract module 13 tests, 0 failures, 0 errors; packaged service artifact produced.
5. `git diff --check` — PASS for tracked edits; `.worktrees/` remains untracked and excluded.

G5 uses deterministic provider fakes and Stripe SDK mapping tests; no live Stripe secret, Checkout URL,
or `infra/docker/.env` content was read or changed. Live Stripe test-mode smoke remains an operational
follow-up before enabling the feature flags in a shared environment.

## G6 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T054, T060 | `StripeWebhookVerifierTests` | PASS; official Stripe signature verification covers exact body bytes, wrong secret, stale timestamp, test/live mode, API-version mismatch, malformed payload, unsupported event, allowlisted metadata, and redaction-safe failures |
| T055, T061 | `StripeWebhookControllerTests` | PASS; durable/duplicate/ignored paths return empty `204`, invalid verification returns empty `400`, storage failure returns empty `503`, and no shared API envelope is emitted |
| T056 | `StripeWebhookReceiptIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; 3 tests verify unique provider event identity, receipt-before-processing, raw-body/URL/signature column absence, lease reclaim, processed completion, and transaction rollback |
| T057, T062 | `ApplyProviderOutcomeServiceTests` and `ProcessProviderEventService` | PASS; paid/processing/unknown/expiry policies, duplicate paid no-op, retry-before-deadline, late success dominance, stable outbox fact, and redacted causation payload are covered |
| T058 | `StripeProviderOutcomeIntegrationTests` | PASS; out-of-order/duplicate provider observations converge to `SUCCEEDED` with one semantic outbox fact and no regression |
| T059 | Webhook application models and `AcceptProviderEventUseCase`/`ProcessProviderEventUseCase` | PASS; verified provider metadata, receipt acceptance result, and processing result remain framework/provider neutral |
| T063 | `ProviderEventProcessingConfiguration` and `ProviderEventProcessingJob` | PASS; receipt claims use a bounded lease/batch/backoff policy and the scheduler is disabled by default behind typed configuration |
| T064 | `PaymentWebhookGatewayRouteTests` | PASS; exact POST-only route, unauthenticated webhook access, signed-body/trace pass-through, bounded request size, and default-deny behavior are verified |
| T065 | Commands below | PASS; G6 focused and module/Gateway suites recorded with zero failures |

### G6 commands and results

1. `./mvnw -pl services/payment-service -am '-Dtest=StripeWebhookVerifierTests,StripeWebhookControllerTests,ApplyProviderOutcomeServiceTests,StripeProviderOutcomeIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; focused verifier/controller/outcome tests pass.
2. `./mvnw -pl services/payment-service -am '-Dtest=StripeWebhookReceiptIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 3 PostgreSQL Testcontainers tests pass.
3. `./mvnw -pl services/api-gateway -am '-Dtest=PaymentWebhookGatewayRouteTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 2 Gateway route/security tests pass.
4. `./mvnw -pl services/payment-service -am verify '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; Payment module 83 tests, 0 failures, 0 errors; Kafka contract module 13 tests, 0 failures, 0 errors; packaged service artifact produced.
5. `./mvnw -pl services/api-gateway -am test '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; Gateway module 190 tests, 0 failures, 0 errors.
6. `git diff --check` — exit `0`; the project-owned `infra/docker/.env` was not opened, read, rendered, edited, staged, or committed.

G6 does not enable live Stripe webhooks or the receipt scheduler by default. To run the local Stripe
CLI smoke later, the owner must supply `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, and
`STRIPE_WEBHOOK_SECRET` in the ignored `infra/docker/.env`; no secret value is required for this
branch's automated tests.

---

## G7 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T066 | `PaymentOutboxPublicationServiceTests` | PASS; 6 unit tests cover claim failure, sequential publish-before-mark, stable retry identity, capped backoff, lease loss, and sanitized broker error persistence |
| T067 | `PaymentResultAvroMapperTests` and `PaymentResultKafkaPublisherTests` | PASS; 7 tests cover both generated v1 records, exact envelope/data/decimal/nullable fields, order key, versions, correlation/causation, W3C headers, topic mismatch, and forbidden fields |
| T068 | `PaymentOutboxConcurrencyIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; 2 tests prove one worker claim, wrong-owner acknowledgement rejection, expired lease reclaim, same event identity, retry requeue, and attempt increment |
| T069 | `PaymentResultKafkaIntegrationTests` against local Kafka/Schema Registry | PASS; 2 live tests prove `auto.register.schemas=false`, TopicRecordNameStrategy subjects, `BACKWARD_TRANSITIVE` subject compatibility, both result records, `orderId` key, event/type/version/content/trace headers, physical duplicate identity, and retry after Registry outage |
| T070–T073 | Immutable outbox model/ports, retry policy, JPA claim/update adapter, mapper/publisher, scheduler, and producer configuration | PASS; Kafka I/O is outside Payment truth transactions, producer uses `acks=all`, idempotence, explicit schemas, and the relay is disabled by default |
| T074 | Commands below | PASS; Payment module verify and live Kafka/Registry evidence recorded with no secret access |

### G7 commands and results

1. `./mvnw -pl services/payment-service -am '-Dtest=PaymentOutboxPublicationServiceTests,PaymentResultAvroMapperTests,PaymentResultKafkaPublisherTests,PaymentOutboxConfigurationTests,PaymentOutboxConcurrencyIntegrationTests,PaymentSchemaMigrationIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 19 tests, 0 failures, 0 errors.
2. `pwsh -NoProfile -File infra/docker/schema-registry/register-payment-schemas.ps1 -SchemaRegistryUrl http://localhost:8081` — exit `0`; command, success, and failure subjects registered/verified with `BACKWARD_TRANSITIVE` (Payment result schema IDs 8/9 in the local runtime).
3. `./mvnw -pl services/payment-service -am '-Dtest=PaymentResultKafkaIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dpayment.kafka.integration=true' test` — exit `0`; 2 live tests, 0 failures, 0 errors; topic `flashsale.payment.events.v1`, key `orderId`, both records and duplicate event identity observed.
4. `./mvnw -pl services/payment-service -am verify '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; Payment module 101 tests, 0 failures, 0 errors, 2 intentionally skipped live-gated tests; Kafka contract module 13 tests passed and the Payment artifact was packaged.
5. `git diff --check` — exit `0`; `infra/docker/.env` was not opened, read, rendered, edited, staged, or committed.

The registry script was hardened to transform string members inside Avro nullable unions and to resolve
its default schema directory inside the script body. This prevents a Windows PowerShell invocation from
registering a schema that differs from the generated Avro SpecificRecord schema.

G7 enables no publisher flag by default. The relay requires both `PAYMENT_ACCEPTANCE_ENABLED=true` and
`PAYMENT_OUTBOX_PUBLISHER_ENABLED=true`; no new secret or `.env` value is required by this branch.

---

## G8 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T075 | `PaymentRecoveryPolicyTests` | PASS; deterministic 1/3/10/30/60-second capped backoff, 23-hour replay boundary, bounded attempts, unknown-state deferral, and manual-review escalation are covered |
| T076 | `ReconcilePaymentServiceTests` | PASS; same-key create recovery, provider Session refresh, deadline expiry after verified provider expiry, late success dominance, and unknown-state deferral are covered |
| T077 | `CheckoutRecoveryIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; response-loss/process-interruption state preserves one attempt, one recovery work item, and one provider idempotency identity; duplicate active work is rejected |
| T078 | `PaymentLateSuccessIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; late verified success is monotonic at a higher aggregate version, produces one stable result fact, and creates no refund boundary |
| T079 | `PaymentRecoveryWorkIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; stale lease reclaim, provider-outage manual-review visibility, and no-attempt deadline-work uniqueness are verified |
| T080–T083 | Recovery models/ports/policy, claim-call-converge service, JPA persistence adapter, Liquibase index, and disabled-by-property scheduled jobs | PASS; provider calls remain outside the local Payment/outbox transaction; defaults are batch 100, poll 1 second, lease 30 seconds, recovery disabled unless all capability flags are enabled |
| T084 | `PaymentRecoveryObservabilityTests` | PASS; queue, outcome, attempt, and age metrics use bounded work-type/outcome labels only |
| T085 | Commands below | PASS; focused and full Payment module suites completed with no failures |

### G8 commands and results

1. `./mvnw -pl services/payment-service -am '-Dtest=PaymentRecoveryPolicyTests,ReconcilePaymentServiceTests,PaymentRecoveryObservabilityTests,CheckoutRecoveryIntegrationTests,PaymentLateSuccessIntegrationTests,PaymentRecoveryWorkIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 13 tests, 0 failures, 0 errors.
2. `./mvnw -pl services/payment-service -am '-Dtest=PaymentSchemaMigrationIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 3 PostgreSQL/Liquibase migration and rollback tests pass with the new active deadline-work uniqueness index.
3. `./mvnw -pl services/payment-service -am verify '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; Payment module 114 tests, 0 failures, 0 errors, 2 intentionally skipped live-gated tests; common-web 9 tests and Kafka contract module 13 tests also pass; packaged Payment artifact produced.
4. `git diff --check` — exit `0`; `infra/docker/.env` was not opened, read, rendered, edited, staged, or committed.

G8 does not enable recovery or deadline workers by default and requires no new secret. Live Stripe
provider outage and process-crash smoke can be run later with the existing test-mode configuration;
this branch keeps all provider credentials in configuration and does not inspect the ignored `.env` file.

---

## G9 implementation evidence

| Task | Evidence | Result |
|---|---|---|
| T086, T090–T091 | `PaymentQueryServiceTests` and query models/ports/service | PASS; Payment and Order lookups derive owner identity from the application query, preserve all six public statuses, and map absent/foreign rows to the same `PaymentNotFoundException` without Stripe/Kafka ports. |
| T087, T092 | `OwnedPaymentQueryIntegrationTests` with PostgreSQL 16 Testcontainers | PASS; `(payment_id,user_id)` and `(order_id,user_id)` predicates, attempt count, and safe closed projection fields are verified; provider session/idempotency data is not selected. |
| T088, T093–T094 | `PaymentQueryControllerTests`, `PaymentDetailsResponse`, `PaymentQueryWebMapper`, and `PaymentQueryController` | PASS; both GET routes return `ApiResponse`, `X-Trace-Id`, `Cache-Control: no-store`, stable 404/400 errors, owner masking, all public statuses, and no Checkout URL/provider/secrets. |
| T089, T095 | `PaymentGatewayRouteConfigurationTests`, existing `PaymentWebhookGatewayRouteTests`, and Gateway YAML | PASS; authenticated Payment API forwards path/query/trace, rejects anonymous and unsupported methods, and exact POST-only webhook routes target Payment Service; API route is limited to GET/POST and webhook remains the only unauthenticated exception. |
| T096 | `PaymentQueryConfiguration`, acceptance-only JWT/security conditions | PASS; query capability is wired only with durable Payment acceptance enabled and remains available when Stripe/Checkout flags are disabled. |
| T097 | Commands below | PASS; focused application/MVC tests, PostgreSQL projection tests, Gateway route test, selected existing Payment integration tests, full Payment verify, and `git diff --check` completed with no failures. |

### G9 commands and results

1. `./mvnw -pl services/payment-service -am '-Dtest=PaymentQueryServiceTests,PaymentQueryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 8 tests, 0 failures, 0 errors.
2. `./mvnw -pl services/payment-service -am '-Dtest=OwnedPaymentQueryIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 2 PostgreSQL 16 Testcontainers tests, 0 failures, 0 errors.
3. `./mvnw -pl services/api-gateway -am '-Dtest=PaymentGatewayRouteConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 4 Gateway route/authentication/forwarding tests, 0 failures, 0 errors.
4. `./mvnw -pl services/payment-service -am '-Dtest=PaymentServiceApplicationTests,PaymentOutboxConcurrencyIntegrationTests,PaymentCommandConcurrencyIntegrationTests,StripeWebhookReceiptIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 9 selected existing integration/application tests, 0 failures, 0 errors.
5. `./mvnw -pl services/payment-service -am verify '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; Payment module 124 tests, 0 failures, 0 errors, 2 intentionally skipped live-gated tests; common-web 9 tests and Kafka contract module 13 tests pass; Payment artifact packaged.
6. `./mvnw -pl services/api-gateway -am test '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; Gateway module 194 tests, 0 failures, 0 errors.
7. `git diff --check` — exit `0`; `infra/docker/.env` was not opened, read, rendered, edited, staged, or committed.

G9 adds no secret or `.env` requirement. Owner queries read only Payment PostgreSQL state; Stripe,
Kafka, Checkout sessions, and provider outage status cannot alter or leak the query result.

---

## G10 implementation evidence (local, non-secret gates)

| Task | Evidence | Result |
|---|---|---|
| T098 | `PaymentObservabilityTests`, `PaymentTelemetryRedactionTests`, `PaymentTraceContext`, `PaymentRequestContext`, and `PaymentRecoveryObservabilityTests` | PASS; observation names/outcomes are bounded, W3C/MDC scope is restored, safe identifiers are bounded, sensitive keys are redacted, recovery/manual-review/outbox gauges are registered without business IDs. |
| T099 | `PaymentReadinessIntegrationTests` and `PaymentReadinessHealthIndicator` | PASS; PostgreSQL is the only readiness gate; Kafka, Schema Registry, Stripe, recovery, and outbox appear as non-gating component details. |
| T100 | `PaymentObservabilityTests`, `PaymentReadinessIntegrationTests`, `PaymentTelemetryRedactionTests`, `PaymentServiceApplicationTests` | PASS; 13 focused tests, then Spring context startup, with no manually constructed Prometheus registry and no sensitive telemetry assertions. |
| T101 | `infra/monitoring/prometheus/rules/payment-service-alerts.yml`, `docs/runbooks/payment-service-recovery.md` | PASS static review; rules cover manual review, recovery age, and outbox lag and runbook actions forbid direct financial edits or secret/raw-body handling. |
| T102 | `StripeRuntimePlatformContractTests` | PASS; configured SDK-compatible Stripe API version is `2026-07-29.dahlia` and Payment container DNS cache TTL is 60 seconds. |
| T103 | Payment runtime `application.yml`, Compose payment environment/migration service, and `.env.example` | PASS static/Compose render; Payment DB, Kafka/Registry, JWT, Stripe placeholders, worker flags, health, port, migration, and JVM DNS wiring are declared. No secret value was rendered, copied, or changed. |
| T104 | `PaymentInfrastructureContractTests`, PowerShell parser, `docker compose config --quiet` | PASS; tracked topic/schema/smoke scripts, root infrastructure ownership, exact webhook security boundary, PowerShell syntax, and rendered Compose are verified. |
| T105 | `infra/docker/smoke/feature-021-payment.ps1` | PASS live; `FEATURE_021_SMOKE=PASS` and `FEATURE_021_FAILURE_MATRIX=PASS` were emitted only after owner/foreign access, command/inbox durability, signed webhook receipt, process restart, Redis/Kafka/PostgreSQL outage, bounded recovery, and identity assertions succeeded. |
| T106 | `load-tests/payment-service/feature-021-payment.js`, `k6 inspect`, staged local execution | PASS; warm-up plus staged profiles completed with zero unexpected failures. Owner query p95 was 78.14 ms in the recorded 20-VU run; the final 5-VU Stripe replay profile completed 1,138 iterations with owner-query p95 63 ms and Checkout p95 408 ms. The deterministic service-local PostgreSQL profile measured Checkout p95 67 ms against the 150 ms budget. |
| T107 | names-only secret/flag validation, local Stripe CLI, failure matrix, k6, and PostgreSQL concurrency suites | PASS; configured test keys and webhook secret were validated without rendering values; listener API-version mismatch was rejected as designed, then `--latest` matched pinned `2026-07-29.dahlia`; `FEATURE_021_STRIPE_CLI=PASS` and `FEATURE_021_CONCURRENT_IDENTITIES=PASS count=100` were emitted. |
| T109 | `./mvnw.cmd --batch-mode --no-transfer-progress -pl contracts/kafka-avro-contracts,services/payment-service,services/api-gateway -am verify '-Dsurefire.failIfNoSpecifiedTests=false'` | PASS; common-web 9 tests, contracts 13 tests, Gateway 194 tests, and Payment 146 tests (0 failures/errors; 6 intentional live-gated skips); exit `0`. |
| T108 | `git check-ignore`, `git ls-files`, tracked secret-pattern scan, `git diff --check` | PASS; `infra/docker/.env` is ignored and untracked, no secret-like token pattern was found in tracked files, and no diff whitespace errors were found. Expected Checkout/webhook contract names remain documented and are not telemetry findings. |
| T110 | full Maven reactor plus repository validation gates | PASS; all 13 modules built, 782 Surefire tests reported 0 failures and 0 errors (16 intentional skips), Compose/parser/k6/link/pointer/ignore/ownership/secret/whitespace checks passed, and every task is reconciled. |

### G10 commands and results

1. `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/payment-service -am '-Dtest=PaymentObservabilityTests,PaymentReadinessIntegrationTests,PaymentTelemetryRedactionTests,StripeRuntimePlatformContractTests,PaymentInfrastructureContractTests,PaymentServiceApplicationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test` — exit `0`; 13 tests, 0 failures, 0 errors.
2. `./mvnw.cmd --batch-mode --no-transfer-progress -pl services/payment-service -am verify '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; Payment module 146 tests, 0 failures, 0 errors, 6 intentional live-gated skips; common-web 9 and Kafka contract 13 tests pass.
3. `./mvnw.cmd --batch-mode --no-transfer-progress -pl contracts/kafka-avro-contracts,services/payment-service,services/api-gateway -am verify '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0`; common-web 9, contracts 13, Gateway 194, and Payment 146 tests pass.
4. `docker compose --env-file infra/docker/.env.example -f infra/docker/compose.yml -f infra/docker/compose.dev.yml config --quiet` — exit `0`; Compose rendered successfully using tracked placeholders only.
5. PowerShell AST parsing of `infra/docker/smoke/feature-021-payment.ps1` and `infra/docker/schema-registry/register-payment-schemas.ps1` — exit `0`; `k6 inspect load-tests/payment-service/feature-021-payment.js` — exit `0`.
6. `git diff --check` — exit `0`; `.env` remained ignored/untracked and no value was rendered, edited, staged, committed, or copied.
7. `./mvnw.cmd --batch-mode --no-transfer-progress clean verify '-Dsurefire.failIfNoSpecifiedTests=false'` — exit `0` in 16m25s; all 13 reactor modules passed. Fresh Surefire reports outside unrelated `.worktrees/` contain 782 tests, 0 failures, 0 errors, and 16 intentional/gated skips.
8. Markdown-link validation, feature-pointer validation, ignored/untracked `.env` validation, and root/service infrastructure ownership audit — PASS; local links resolve, `.specify/feature.json` points to Feature 021, `infra/docker/.env` remains ignored/untracked without rendering values, and no service-owned infrastructure path was found.
9. `./infra/docker/smoke/feature-021-payment.ps1 -SkipTopology -RunFailureMatrix` — exit `0`; emitted `FEATURE_021_SMOKE=PASS` and `FEATURE_021_FAILURE_MATRIX=PASS`; Redis and Kafka outages did not make PostgreSQL-backed owner queries unavailable, PostgreSQL outage changed readiness to 503, and each component recovered within the script bounds.
10. `./infra/docker/smoke/feature-021-payment.ps1 -SkipTopology -RunK6` — exit `0`; zero unexpected errors, final Stripe-backed replay Checkout p95 408 ms, owner-query p95 63 ms, 1,138 iterations, and `FEATURE_021_CONCURRENT_IDENTITIES=PASS count=100`.
11. `CheckoutReplayConcurrencyIntegrationTests` — exit `0`; 3 PostgreSQL tests prove 20 same-key replays converge without deadlock, 100 distinct-key callers create exactly one provider attempt, and 200 deterministic-provider replays measure service-local p95 67 ms (<150 ms).
12. `./infra/docker/smoke/feature-021-payment.ps1 -SkipTopology -RunStripeCli` — exit `0`; Stripe test event travelled through CLI listener and Gateway to a durable PostgreSQL receipt and emitted `FEATURE_021_STRIPE_CLI=PASS`; the ephemeral listener secret and diagnostics were redacted and deleted.

### G10 completion and local runtime note

Feature 021 is Verified. The ignored local `.env` supplied valid Stripe test secret, publishable, and
webhook-secret formats without their values being recorded. The smoke runner temporarily enables all
Payment workers and restores caller configuration afterward. For an ordinary always-on local stack,
the project owner should explicitly add `PAYMENT_WEBHOOK_PROCESSING_ENABLED=true`,
`PAYMENT_CONSUMER_ENABLED=true`, and `PAYMENT_OUTBOX_PUBLISHER_ENABLED=true`; these are non-secret
feature flags, while the tracked default remains fail-closed. `STRIPE_API_VERSION` may be explicit or
inherit the tracked `2026-07-29.dahlia` default, but a deployed Stripe webhook destination must use the
same version.

The later Order-owned Saga integration that produces `PaymentRequested.v1` and consumes Payment
results is outside this feature's completion boundary and remains separate follow-up work.

### Final requirement reconciliation

| Approved requirement set | Implementation/evidence owner | Final result |
|---|---|---|
| `UC-PAY-001/AC-01..04` | G3 atomic acceptance plus G4 live Kafka duplicate/retry/DLT tests | PASS |
| `UC-PAY-002/AC-01..07` | G5 Checkout/idempotency tests plus G10 20-replay/100-concurrent PostgreSQL tests and k6 | PASS |
| `UC-PAY-003/AC-01..06` | G6 signed raw-body receipt/convergence plus G7 live result-outbox publication and Stripe CLI | PASS |
| `UC-PAY-004/AC-01..06` | G8 deadline/reconciliation/manual-review tests plus G10 provider/process/Kafka/PostgreSQL failure matrix | PASS |
| `UC-PAY-005/AC-01..04` | G9 owner/foreign/absent Gateway tests plus Redis/Kafka/provider-unavailable runtime checks | PASS |
| `FR-001..007` | G1 contracts/ADR and G3–G4 durable command acceptance | PASS |
| `FR-008..017` | G5 owner-scoped, idempotent, card-only hosted Checkout boundary | PASS |
| `FR-018..023`, `FR-037` | G6 exact Gateway webhook exception, signature/API-version/mode verification, and durable receipt-before-204 | PASS |
| `FR-024..034` | G6 success-dominant convergence, G7 transactional outbox, and G8 bounded reconciliation/deadline policy | PASS |
| `FR-035..036` | G9 owner-only safe `ApiResponse` query contracts | PASS |
| `FR-038..043` | G10 PCI-aware scans, PostgreSQL/Redis correctness boundary, retention decision, observability/readiness, and local-secret ownership | PASS |
| `NFR-REL-001..003` | Duplicate, retry, lease, restart, outage, and multi-instance/concurrency suites | PASS |
| `NFR-PERF-001..003` | Owner p95 78.14 ms (<200 ms), service-local Checkout p95 67 ms (<150 ms), and 100-way identity test | PASS |
| `NFR-SEC-001..004` | Gateway/JWT/signature tests, hosted card-data boundary, telemetry redaction, and zero secret-pattern findings | PASS |
| `NFR-OBS-001`, `NFR-COMPAT-001` | bounded metrics/traces/runbook plus generated Avro/TopicRecordNameStrategy compatibility tests | PASS |
| `SC-001..010` | G3–G10 automated and live evidence above, module/full builds, and repository validation gates | PASS |

No unresolved clarification, unchecked implementation task, or failed required gate remains in
Feature 021.
