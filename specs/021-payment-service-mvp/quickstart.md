# Quickstart and Validation: Payment Service MVP

**Feature**: [Payment Service MVP](./spec.md)
**Purpose**: Reproducible evidence after implementation
**Platform shown**: PowerShell on Windows; Maven Wrapper remains canonical

This guide is a planned validation contract, not implementation approval. Commands that reference
Feature 021 scripts/tests become runnable only after their tasks are implemented. Record command,
scope, date/environment, exit status, counts, percentiles, and CI/PR link in future `validation.md`.

## 1. Prerequisites and secret safety

- Java 21, Docker Desktop with Compose, Maven Wrapper, and optional k6.
- A Stripe test environment and Stripe CLI for bounded external smoke tests.
- `infra/docker/.env` copied from `.env.example`; it is ignored by Git.
- Valid local JWT key/OAuth configuration required by the existing stack.

`infra/docker/.env` is owned and populated only by the project owner. Implementation agents must
not directly open/read, create, edit, overwrite, copy, print, delete, stage, or commit it. An agent may add a
placeholder to `.env.example`; when a real value is required, it must report the exact variable
name, purpose, expected non-secret format, and where the owner can obtain it, then pause that
validation. After the owner confirms the value was added, the agent may run the documented command
that consumes `.env`, but must not render resolved configuration or secret values.

Set only test values in ignored `infra/docker/.env`:

```dotenv
STRIPE_ENABLED=true
STRIPE_API_BASE_URL=https://api.stripe.com
STRIPE_SECRET_KEY=sk_test_REPLACE_LOCALLY
STRIPE_PUBLISHABLE_KEY=pk_test_REPLACE_LOCALLY
STRIPE_WEBHOOK_SECRET=whsec_REPLACE_FROM_STRIPE_CLI
```

The webhook secret is emitted by `stripe listen`; it is different from `STRIPE_SECRET_KEY`. The
project owner performs the edit. Never paste real values into chat, command transcripts, source
files, test reports, PRs, screenshots, or this guide. Do not use live-mode keys/cards.

## 2. Verify affected modules

After implementation:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl contracts/kafka-avro-contracts,services/payment-service,services/api-gateway `
  -am verify
```

Expected:

- Three Payment Avro SpecificRecords compile and pass evolution/shape tests.
- Domain, architecture, database, Kafka, Stripe mapping, webhook, security, HTTP, outbox, recovery,
  and observability tests pass.
- Gateway authenticates owner routes, permits only the exact webhook route, and preserves raw bytes.
- Hibernate validates the Liquibase-owned schema instead of creating it.

## 3. Start shared local topology

```powershell
docker compose `
  --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml `
  up -d postgres kafka schema-registry authentication-service api-gateway order-service payment-service

docker compose --env-file infra/docker/.env -f infra/docker/compose.yml ps
```

Wait for PostgreSQL, Kafka, Schema Registry, Authentication, Gateway, Order, and Payment readiness.
Stripe reachability is observed separately and does not make the service process unready during a
transient provider outage.

## 4. Provision Payment topics and schemas

```powershell
$kafkaContainer = docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml ps -q kafka

if ([string]::IsNullOrWhiteSpace($kafkaContainer)) {
  throw "Kafka container is not running"
}

docker cp infra/docker/kafka/init-payment-topics.sh "${kafkaContainer}:/tmp/init-payment-topics.sh"
docker exec $kafkaContainer bash /tmp/init-payment-topics.sh

.\infra\docker\schema-registry\register-payment-schemas.ps1 `
  -SchemaRegistryUrl http://localhost:8081
```

Expected:

```text
flashsale.payment.commands.v1: 3 partitions, replication factor 1
flashsale.payment.events.v1: 3 partitions, replication factor 1
flashsale.payment.payment-requested.dlt.v1: provisioned
PaymentRequestedV1, PaymentSucceededV1, PaymentFailedV1: BACKWARD_TRANSITIVE
```

Application runtime schema auto-registration remains disabled.

## 5. Focused PostgreSQL and concurrency tests

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/payment-service -am `
  "-Dtest=PaymentSchemaMigrationIntegrationTests,PaymentCommandInboxIntegrationTests,CheckoutAttemptConcurrencyIntegrationTests,PaymentOutboxConcurrencyIntegrationTests,RecoveryWorkClaimIntegrationTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Required evidence:

- duplicate commands converge to one Payment;
- contradictory command data is never overwritten;
- 100 concurrent same-key Checkout requests allocate one attempt;
- different concurrent keys still leave one unresolved attempt;
- the fourth sequential attempt is rejected;
- client idempotency, attempt, recovery, state, and outbox changes roll back atomically;
- multiple workers never own the same active lease;
- stale work is reclaimed after a simulated crash.

## 6. Deterministic provider failure matrix

Use the test provider adapter selected by the test profile:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress `
  -pl services/payment-service -am `
  "-Dtest=CheckoutRecoveryIntegrationTests,StripeWebhookProcessingIntegrationTests,PaymentLateSuccessIntegrationTests,PaymentTelemetryRedactionTests" `
  "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Required scenarios:

| Failure window | Required result |
|---|---|
| Process stops after durable attempt, before Stripe call | Worker uses the same provider key and establishes one Session. |
| Stripe accepts create but response is lost | HTTP is `202`; replay/recovery uses the same key; no second attempt/Session. |
| Process stops after provider result, before Transaction B | Recovery converges by same key/Session identity. |
| Webhook duplicated or reordered | One logical state transition/fact; higher-version verified success wins. |
| Database unavailable before webhook receipt commit | Empty `503`; provider retry later persists the event. |
| Process stops after receipt commit | Request already got `204`; worker later reclaims and processes it. |
| Kafka/Schema Registry unavailable | Payment remains queryable; stable outbox row retries and later publishes. |
| Internal deadline races Stripe completion/expiry | Paid verification wins; unpaid verified deadline emits one failure reason. |
| Recovery safety window exhausted | Work becomes manual review; no blind new provider POST. |
| Telemetry inspection | No URL, key, secret, signature, raw webhook, card/customer data, or provider body. |

## 7. Stripe CLI webhook path through Gateway

Start forwarding through the public edge, not directly to Payment Service:

```powershell
stripe login
stripe listen --forward-to http://localhost:8080/webhooks/v1/payments/stripe
```

Copy the displayed `whsec_...` into ignored `infra/docker/.env`, then recreate Payment Service so the
secret is loaded. Use Stripe test-mode fixtures or the test card `4242 4242 4242 4242` only through
the hosted Checkout page. Any future expiry date and CVC are acceptable in Stripe test mode.

Verify:

- webhook receives `204` after durable receipt;
- browser success/cancel alone changes no Payment state;
- Payment status changes only after verified provider truth;
- duplicate delivery creates no duplicate fact;
- Checkout URL appears only in the authenticated create/resume response with `no-store`.

## 8. Feature 021 smoke

After implementation:

```powershell
.\infra\docker\smoke\feature-021-payment.ps1
```

Expected logical flow:

```text
Order fixture -> PaymentRequested.v1
Payment inbox + aggregate COMMIT
Owner -> Gateway -> create Checkout (durable attempt first)
Stripe test/fake -> signed webhook
Webhook receipt COMMIT -> asynchronous reconciliation
Payment state + outbox COMMIT -> PaymentSucceeded.v1
Owner query -> SUCCEEDED
Foreign owner / unknown ID -> identical safe 404
```

Expected marker:

```text
FEATURE_021_SMOKE=PASS
```

Run failure mode:

```powershell
.\infra\docker\smoke\feature-021-payment.ps1 -RunFailureMatrix
```

Expected marker:

```text
FEATURE_021_FAILURE_MATRIX=PASS
```

The script must reconcile Payment/attempt/inbox/receipt/recovery/outbox rows and Kafka event identity,
not merely check HTTP success.

## 9. Performance profiles

Run a warm-up and step-load owner queries separately from Stripe-bound orchestration. A Checkout
provider round trip is an external latency measure and must not be mixed into the service-local
threshold.

```powershell
k6 run `
  -e PAYMENT_BASE_URL=http://localhost:8080 `
  -e PAYMENT_QUERY_TOKEN_FILE=<temporary-ignored-token-file> `
  -e PAYMENT_ID=<owned-payment-id> `
  -e PAYMENT_QUERY_VUS=10 `
  -e PAYMENT_QUERY_DURATION=30s `
  .\load-tests\payment-service\feature-021-payment.js
```

Then repeat documented stages (for example 10, 25, 50, and 100 VUs) only while error and saturation
signals remain healthy. Record request count/rate, p50/p95/p99, error rate, connection-pool state,
CPU/memory, recovery/outbox lag, and whether the provider adapter was fake or Stripe test mode.

Required nominal thresholds:

```text
authenticated owned Payment query p95 < 200 ms
service-local Checkout orchestration excluding provider time p95 <= 150 ms
unexpected error rate = 0
foreign/unknown non-enumeration = 100% pass
```

Delete the temporary token file after the run.

## 10. Full validation

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
git diff --check
```

If Kubernetes assets are added by an approved task, run:

```powershell
kubectl apply --dry-run=client -k <affected-overlay>
```

Do not mark Feature 021 verified while a required check is failing or skipped without recorded plan
rationale. Store evidence in `specs/021-payment-service-mvp/validation.md`, including CI/PR reference
and the exact Stripe mode used; never copy secrets or Checkout URLs into evidence.
