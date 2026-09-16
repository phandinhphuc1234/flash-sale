# Payment Service

Payment Service owns payment aggregates, provider Checkout Sessions, signed webhook receipts,
provider reconciliation, and payment outcome publication. It never writes Order state directly.

## Boundaries

- consume `PaymentRequestedV1` from `flashsale.payment.commands.v1`;
- expose owner-protected Payment query and Checkout Session APIs;
- call Stripe over HTTPS with bounded timeouts/retries and idempotency keys;
- receive raw signed Stripe events at `POST /webhooks/v1/payments/stripe`;
- publish `PaymentSucceededV1` or `PaymentFailedV1` through the outbox.

## Webhook processing

```text
Stripe -> verify signature against raw body
       -> persist provider receipt keyed by provider event ID
       -> acknowledge duplicate delivery safely
worker -> apply event to matching Payment
       -> insert outcome outbox record in the same transaction
       -> Kafka -> Order Saga
```

HTTP acknowledgement proves receipt handling, not that Order has already reached its terminal state.
Provider IDs, Checkout URLs, tokens, signatures, and raw webhook bodies must not be logged.

## Runtime flags and secrets

Payment has explicit acceptance, consumer, Checkout, Stripe, webhook-processing, outbox, and recovery
switches. Local/cloud enablement must change the complete reviewed flag set together. Secrets belong
in the ignored `.env` or Kubernetes `payment-secrets`, never in Git.

Important configuration groups include datasource/Liquibase, JWT trust, Kafka/Schema Registry,
Stripe API/version/credentials/webhook tolerance, Checkout return URLs, provider timeouts, outbox,
webhook worker, and recovery lease/backoff/deadline values.

## Verify

```powershell
.\mvnw.cmd -pl services/payment-service -am verify
```

The Stripe cloud smoke requires a live environment, test-mode credentials, a public HTTPS webhook,
and manual test payment. Never run it against live-mode keys by accident.

## Troubleshooting

- Checkout unavailable: verify all reviewed Payment flags and the Stripe mode/key pair.
- Webhook acknowledged but Payment unchanged: inspect durable receipts and webhook worker state.
- Payment event exists but Order does not move: inspect Kafka offset, Order consumer/DLT, and Saga
  identity/version; Kafka broker health alone does not prove consumer progress.
- Duplicate webhook: expected provider behavior; deduplication must acknowledge without a second
  business transition.
