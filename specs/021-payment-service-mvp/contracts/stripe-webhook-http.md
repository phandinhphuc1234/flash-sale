# Stripe Webhook HTTP Contract

**Status**: Draft — ready for approval with Feature 021 plan
**Ingress**: API Gateway only
**Endpoint**: `POST /webhooks/v1/payments/stripe`
**Authentication**: no user JWT; Stripe signature verification is mandatory in Payment Service
**Response body**: always empty

## Gateway contract

- Permit unauthenticated access only to the exact webhook path and method; other Payment paths retain
  JWT authentication and unknown-path default deny.
- Route directly to Payment Service using Kubernetes Service/DNS in deployed environments and the
  existing local Compose service name locally.
- Preserve the exact raw HTTP body bytes and `Stripe-Signature`; do not parse, re-encode, aggregate,
  or mutate the payload before signature verification.
- Preserve/generate the repository trace header/context without adding secrets to tracing.
- Apply a bounded request-body size suitable for Stripe events; do not log the body or signature.

## Payment Service verification contract

Before durable acknowledgement, Payment Service must:

1. Read exact raw bytes once.
2. Require `Stripe-Signature`.
3. Verify the signature with the configured endpoint secret and timestamp tolerance (default five
   minutes).
4. Verify that event live/test mode matches the running environment.
5. Parse only the supported envelope/object fields and allowlisted opaque metadata.
6. Insert or observe the unique provider event receipt in PostgreSQL.
7. Return `204` only after the receipt transaction commits.

The endpoint secret is not the Stripe API key. Both are external secrets and never committed.

## Supported event types

- `checkout.session.completed`
- `checkout.session.expired`

A valid, correctly signed but unsupported event may be recorded as `IGNORED` using safe metadata and
acknowledged. Event arrival order does not establish Payment state; asynchronous processing retrieves
and verifies current Session state where required.

## Responses

| Outcome | Status | Retry meaning |
|---|---:|---|
| Valid event receipt durably inserted | 204 | Stripe need not retry this delivery. |
| Duplicate valid event already durably present | 204 | Idempotent acknowledgement. |
| Valid unsupported event durably classified ignored | 204 | Intentionally ignored. |
| Missing/invalid signature, stale timestamp, mode mismatch, or malformed payload | 400 | Permanent rejection; empty body avoids data leakage. |
| Signature-valid event cannot be durably recorded | 503 | Transient; Stripe should retry. |
| Other unexpected failure before durable receipt | 500 | Transient; no acknowledgement is claimed. |

Do not return `ApiResponse`/`ApiErrorResponse` here; this is a provider protocol endpoint. No Stripe
error detail, signature, payload fragment, Checkout URL, or exception text is returned.

## Durable receipt allowlist

The persisted receipt may contain event ID/type, API version, live mode, provider creation time,
Checkout Session/object ID, safe metadata IDs (`orderId`, `paymentId`, `attemptId`), verification
time, processing status, lease/backoff state, and a redacted error category. It must not contain the
raw body, signature, URL, customer/card/payment-method details, email/address, secret, or full Stripe
object.

## Processing and convergence

- Webhook request threads never call Kafka, wait for Order Service, or perform full reconciliation.
- A worker claims the receipt, retrieves provider truth if necessary, locks the Payment, applies an
  idempotent success-dominant state transition, writes any result fact to the outbox, and completes
  the receipt atomically.
- Duplicate event IDs are no-ops. Different duplicate/out-of-order events converge through Payment
  state invariants and aggregate versioning.
- Browser redirects do not substitute for this endpoint.

## Test cases

Required tests cover exact raw-body preservation through Gateway, valid signature, mutated body,
wrong secret, stale timestamp, missing header, wrong live/test mode, duplicate event ID, unsupported
type, database failure before receipt commit, crash after receipt commit, out-of-order expired then
paid observations, late paid success, and absence of prohibited data in responses/logs/traces.
