# Contract: Stripe cloud evidence and operator boundary

This is an operator/evidence contract, not a new public business API or Kafka schema.

## Required checks

| Check | Expected result | Forbidden output |
|---|---|---|
| Argo Application | `Synced`, `Healthy`, expected revision | Secret data, full tokens |
| Payment flags | all seven `true` when enabled, all seven `false` after rollback | Secret values |
| Payment readiness | HTTP/Actuator healthy | raw provider response containing credentials |
| Checkout | bounded `201/200/202` according to existing contract | Checkout URL in evidence |
| Webhook | valid signature acknowledged after durable receipt | raw body/signature |
| Replay | same event/key has no second semantic effect | full event payload |
| Gateway boundary | Payment is reachable only through Gateway | direct public Payment endpoint |

## Existing routes and contracts

- User Checkout: `POST /api/v1/payments/{paymentId}/checkout-sessions`, owner JWT and
  `Idempotency-Key`.
- Stripe webhook: `POST /webhooks/v1/payments/stripe`, no user JWT; raw body and
  `Stripe-Signature` are verified by Payment.
- Kafka command: `flashsale.payment.commands.v1`, `PaymentRequested.v1`, keyed by `orderId`.
- Kafka result: `flashsale.payment.events.v1`, `PaymentSucceeded.v1` or `PaymentFailed.v1`, keyed by
  `orderId`.

## Evidence redaction

Evidence may include status codes, health states, safe UUIDs, image digests, event counts, and
elapsed durations. It MUST replace all URLs, credentials, Authorization headers, Stripe IDs, raw
payloads, signatures, and card numbers with `<redacted>` or a count.

