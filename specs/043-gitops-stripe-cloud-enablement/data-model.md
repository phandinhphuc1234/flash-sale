# Data and runtime model: Stripe cloud enablement

Phase 24 does not introduce a new business entity or database schema. It activates existing Payment
state and infrastructure contracts.

## Runtime configuration

| Key | Owner | Cloud value when enabled | Sensitivity |
|---|---|---:|---|
| `PAYMENT_ACCEPTANCE_ENABLED` | Payment ConfigMap | `true` | non-secret |
| `PAYMENT_CHECKOUT_ENABLED` | Payment ConfigMap | `true` | non-secret |
| `STRIPE_ENABLED` | Payment ConfigMap | `true` | non-secret |
| `STRIPE_MODE` | Payment ConfigMap | `test` | non-secret |
| `PAYMENT_CONSUMER_ENABLED` | Payment ConfigMap | `true` | non-secret |
| `PAYMENT_OUTBOX_PUBLISHER_ENABLED` | Payment ConfigMap | `true` | non-secret |
| `PAYMENT_RECOVERY_ENABLED` | Payment ConfigMap | `true` | non-secret |
| `PAYMENT_WEBHOOK_PROCESSING_ENABLED` | Payment ConfigMap | `true` | non-secret |

`PAYMENT_REQUESTED_TOPIC`, `PAYMENT_EVENTS_TOPIC`, DLT, consumer group, Stripe API base URL, and
JWT settings remain the existing approved values.

## Secret boundary

`payment-secrets` contains only Kubernetes Secret keys mapped from the owner-managed ignored local
environment: `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, and `STRIPE_WEBHOOK_SECRET`, plus the
existing Payment database credentials. The values are not represented in this file, logs, Git, or
evidence. `auth-jwt` remains a separate file-based Secret.

## ACM certificate

The development HTTPS certificate covers `flashsale123.tech` and `*.flashsale123.tech` in AWS
region `ap-southeast-2`. ACM DNS validation records are output by Terraform and entered manually at
Get.Tech. Route 53 is not an owner of this DNS zone.

## Existing durable entities affected

- `Payment`: one logical Order obligation and monotonic lifecycle.
- `PaymentAttempt`: one active/unresolved hosted Checkout workflow.
- `PaymentProviderEventReceipt`: verified event identity and processing lease; no raw body/signature.
- `PaymentOutboxMessage`: durable Payment result awaiting Kafka publication.
- `PaymentRecoveryWork`: retry/reconciliation eligibility for ambiguous provider state.

## Invariants

1. One Order has at most one Payment.
2. One Payment has at most one unresolved/active attempt.
3. One provider event ID has at most one semantic receipt/effect.
4. A verified provider outcome, not browser navigation, decides Payment state.
5. Rollback changes runtime switches only; it does not delete durable evidence.
