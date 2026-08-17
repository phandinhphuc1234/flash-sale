# Research: Payment Service MVP with Stripe Checkout

**Date**: 2026-08-17
**Scope**: Resolve technical choices required by the approved specification before implementation.
**Outcome**: No unresolved clarification remains. Product behavior stays owned by `spec.md`; this
document records implementation choices and their rationale.

## R1. Purchase Saga ownership and message intent

**Decision**: Order Service owns and persists Purchase Saga workflow state. It publishes an explicit
`PaymentRequested.v1` command to Payment Service. Payment publishes `PaymentSucceeded.v1` or
`PaymentFailed.v1` facts back to the Saga. `OrderCreated.v1` remains an order fact.

**Rationale**: The repository already treats Order as the durable purchase lifecycle owner. An
explicit command preserves intent, retry ownership, causation, and contract evolution. Payment owns
provider/payment correctness but does not update Order or reservation state.

**Alternatives rejected**:

- Choreography in which Payment consumes `OrderCreated.v1`: a fact would be reinterpreted as a
  command and payment policy would become implicit.
- Synchronous Order-to-Payment charge call: couples availability and cannot remove provider
  ambiguity.
- A new global Saga service: adds a boundary without an approved system decomposition change.
- Distributed transaction/2PC: Stripe and Kafka cannot participate in the database transaction.

**Recorded by**: [ADR 0018](../../docs/adr/0018-order-owned-purchase-saga-payment-contracts.md).

## R2. Stripe SDK and API-version policy

**Decision**: Pin `com.stripe:stripe-java:33.2.0`, use an injected `StripeClient`, and wrap it behind
the capability-oriented `HostedCheckoutProviderPort`. Do not use the legacy global API-key pattern.
Configure the Stripe webhook endpoint to the API version pinned by the chosen SDK. Upgrading the SDK
requires reviewing API-version changes, contract fixtures, and provider adapter tests together.

**Rationale**: The official Java SDK supports Java 21, exposes per-client timeout/retry options, and
strongly types resources against a pinned Stripe API version. Pinning avoids an unreviewed provider
contract change during a build.

**Alternatives rejected**:

- Direct HTTP calls: duplicates signing/error/version behavior already maintained by Stripe.
- Floating SDK/API versions: non-repeatable builds and webhook/resource drift.
- Stripe SDK types in application/domain: creates provider coupling across the hexagonal boundary.

**Primary sources**:

- [Stripe Java SDK repository](https://github.com/stripe/stripe-java)
- [Stripe Java releases](https://github.com/stripe/stripe-java/releases)
- [Stripe API versioning for Java](https://docs.stripe.com/api/versioning?lang=java)

## R3. Checkout shape and internal deadline mismatch

**Decision**: Create a Stripe-hosted Session with `mode=payment`, card-only payment methods,
automatic capture, trusted server-side amount/currency, success/cancel URLs from validated
configuration, and allowlisted opaque metadata. The internal `paymentDeadline` is authoritative.
Stripe's configured Session expiry is defense in depth only; the reconciliation worker retrieves and
expires an open Session when the internal deadline is reached.

**Rationale**: Stripe Checkout accepts a custom `expires_at` only within its allowed 30-minute to
24-hour range, while this system can have a five-minute payment deadline. Pretending these clocks
are identical would leave a chargeable Session after the Saga deadline. Explicit deadline recovery
keeps the business rule local and testable.

**Metadata allowlist**: `orderId`, `paymentId`, `attemptId`, and `correlationId`. No user profile,
credential, authorization header, card data, or Checkout URL.

**Alternative rejected**: using only Stripe expiry, because it cannot represent the approved shorter
business deadline.

**Primary sources**:

- [Create a Checkout Session](https://docs.stripe.com/api/checkout/sessions/create)
- [Expire a Checkout Session](https://docs.stripe.com/api/checkout/sessions/expire)
- [Checkout fulfillment](https://docs.stripe.com/checkout/fulfillment)

## R4. Money representation and VND conversion

**Decision**: The service persists amount as `NUMERIC(19,4)` and ISO 4217 currency as uppercase
`CHAR(3)`. Provider conversion is an explicit adapter operation. For VND, the amount must have zero
fractional value and is converted exactly to a bounded integer before sending lower-case `vnd` to
Stripe. Rounding is forbidden.

**Rationale**: Stripe treats VND as a zero-decimal currency and accepts integer API amounts. An
implicit decimal shift or rounding could charge the wrong amount. The Payment command remains the
trusted immutable source; browser-supplied totals are ignored.

**Alternative rejected**: using floating point or multiplying every currency by 100.

**Primary source**: [Stripe currencies and minor units](https://docs.stripe.com/currencies).

## R5. Durable provider idempotency and ambiguous create results

**Decision**: Every Payment attempt receives one stable provider idempotency key generated by the
service. Transaction A durably creates that attempt and recovery work before the network call. The
Stripe call happens outside a database transaction. A timeout/connection loss after submission is
an `UNKNOWN` outcome; recovery replays the identical operation with the identical key. A new attempt
is not opened until the previous outcome is terminally known.

Create recovery is bounded by a configurable `safeReplayUntil` set to 23 hours after first
submission, leaving a safety margin beneath Stripe's documented minimum idempotency-key retention.
If a Session cannot be established by then, the work becomes `MANUAL_REVIEW`; the system never sends
a fresh POST key automatically.

**Rationale**: Stripe saves the first result for an idempotency key, including some failures, and a
network error does not prove whether the operation executed. Stable replay is the only safe way to
learn/recover without creating a duplicate Session.

**Alternatives rejected**:

- Retrying with a new key after timeout: may duplicate the provider operation.
- Holding a database lock while calling Stripe: harms availability and cannot make the remote call
  transactional.
- Treating timeout as `FAILED`: may publish a false failure before a successful webhook arrives.

**Primary sources**:

- [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests)
- [Stripe advanced error handling](https://docs.stripe.com/error-low-level)

## R6. Client idempotency and URL replay

**Decision**: `POST /api/v1/payments/{paymentId}/checkout-sessions` requires a case-sensitive
`Idempotency-Key` of 1–255 printable characters. Persist a SHA-256 digest of the raw key, operation,
owner, Payment ID, and request fingerprint under a unique `(operation, key_digest)` constraint.
Matching replays return the existing attempt; a different owner/Payment/fingerprint returns
`409 PAYMENT_IDEMPOTENCY_CONFLICT`.

The Checkout URL is never persisted. When a replay/resume needs a URL, the provider adapter retrieves
the current Session using the persisted Session ID. If current state is unknown/unavailable, return
`202` without a URL and let durable recovery continue.

**Rationale**: The approved security boundary prohibits durable URL storage, while idempotency must
survive restarts. Persisted provider identity, not the bearer-like URL, is the replay anchor.

**Alternative rejected**: caching the URL in PostgreSQL/Redis or reconstructing it from an ID.

## R7. Webhook verification, acknowledgement, and duplicate delivery

**Decision**: API Gateway passes the exact webhook body bytes and `Stripe-Signature`. Payment uses
the official library to verify the signature against the endpoint secret with a configurable
timestamp tolerance defaulting to five minutes. It validates expected test/live mode, persists an
allowlisted provider-event receipt keyed by Stripe event ID, and only then returns empty `204`.
Invalid signature/payload returns empty `400`; inability to persist a valid event returns empty
`503` so Stripe can retry.

Business processing is asynchronous. A worker claims the receipt, retrieves current provider state
when needed, locks the Payment, applies a success-dominant transition, writes a stable outbox fact,
and marks the receipt processed atomically. Raw webhook JSON is neither stored nor logged.

**Rationale**: Stripe can retry, duplicate, and reorder events. Signature verification requires the
unmodified raw body. Fast durable acknowledgement prevents provider retries from depending on
database reconciliation or Kafka availability.

**Supported event types for card-only MVP**:

- `checkout.session.completed`
- `checkout.session.expired`

A valid unsupported event may be durably classified `IGNORED` and acknowledged. Browser success or
cancel redirects never mutate payment state.

**Primary sources**:

- [Stripe webhooks](https://docs.stripe.com/webhooks)
- [Resolve webhook signature errors](https://docs.stripe.com/webhooks/signature)
- [Checkout fulfillment](https://docs.stripe.com/checkout/fulfillment)

## R8. Success-dominant convergence and late payment

**Decision**: A provider-confirmed paid Session moves Payment to `SUCCEEDED` even when a local
deadline/failure fact was recorded first. `SUCCEEDED` is terminal and cannot regress. The success
fact uses the next Payment aggregate version, so downstream consumers can converge deterministically.
Payment does not auto-refund; Order Service attempts forward Saga recovery and escalates an
irreconcilable purchase to manual review.

**Rationale**: Provider truth can arrive after a timeout or deadline race. Ignoring a real charge
would lose money correctness; automatically refunding is an unapproved refund policy.

**Alternative rejected**: “first terminal event wins,” which can preserve a false failure after the
customer was charged.

## R9. Command, outbox, and recovery retry policy

**Decision**:

| Operation | Default technical policy |
|---|---|
| Kafka command | Initial delivery plus retries after 1s, 3s, and 10s for transient infrastructure errors; then `flashsale.payment.payment-requested.dlt.v1`. Business duplicates/conflicts are classified without retry loops. |
| Stripe SDK | `maxNetworkRetries=2`, connect timeout 5s, read timeout 20s; all values validated and configurable. Timeout remains ambiguous. |
| Recovery worker | Poll 1s, batch 100, lease 30s, backoff 1s/3s/10s/30s/60s capped; stale lease is reclaimable. |
| Outbox | Stable event identity; retry until Kafka acknowledgement with a capped 60s backoff. |

Set JVM DNS cache TTL to 60 seconds in deployed runtime configuration, following Stripe's client
guidance. Metrics and alerts expose recovery age, attempt count, DLT, manual-review, and outbox lag.

**Rationale**: Short synchronous retries cover transient network faults; durable work handles crashes
and long outages. Business outcomes are never inferred from exhausted infrastructure retries.

**Primary source**: [Stripe Java client configuration](https://github.com/stripe/stripe-java).

## R10. Persistence and concurrent work claiming

**Decision**: Use Spring Data JPA for aggregates/CRUD and native PostgreSQL queries inside the
persistence adapter for partial indexes and `FOR UPDATE SKIP LOCKED` work claims. Use separate JPA
`row_version` for optimistic concurrency and business `aggregate_version` for event ordering.

**Rationale**: JPA is the repository default for durable service state, while queue-like batch claims
and partial uniqueness are legitimate database-specific adapter concerns. Neither SQL nor JPA types
leak into application/domain.

**Alternative rejected**: a distributed lock or Redis queue; PostgreSQL already owns the durable
state and can claim work atomically.

## R11. Kafka serialization and evolution

**Decision**: Add SpecificRecord Avro schemas to `contracts/kafka-avro-contracts`. Use
TopicRecordNameStrategy, BACKWARD_TRANSITIVE compatibility, auto-registration disabled outside
explicit provisioning, Kafka key `orderId`, and W3C trace headers. Commands and facts have separate
topics:

- `flashsale.payment.commands.v1`
- `flashsale.payment.events.v1`
- `flashsale.payment.payment-requested.dlt.v1`

**Rationale**: Command intent and result facts evolve independently. `orderId` ordering prevents two
Payment commands/results for the same purchase from overtaking each other across partitions.

**Alternative rejected**: JSON/unversioned topics or a generic `payment.lifecycle.v1` stream.

## R12. HTTP boundary and error model

**Decision**: Owner APIs use repository-standard `ApiResponse`/`ApiErrorResponse`, trace headers,
trusted JWT ownership, and indistinguishable `404` for missing/foreign resources. The Checkout
response is `201` for a newly known open Session, `200` for a known replay/resume, or `202` for a
durably accepted ambiguous/recovery state. Every Checkout response is `Cache-Control: no-store`.
The webhook is a provider protocol endpoint and returns an empty body rather than the user API
envelope.

**Rationale**: One public envelope simplifies clients while the provider webhook needs exact Stripe
retry semantics. Owner masking prevents identifier probing. `202` accurately represents durable
acceptance without claiming provider completion.

## R13. PCI-aware scope and secret handling

**Decision**: Use Stripe-hosted Checkout so this service never receives card number/CVC input.
Secrets are injected from environment/secret storage; separate test and production settings are
required. Redaction tests cover logs, errors, metrics, and trace attributes. Documentation describes
this as a PCI-aware boundary, not PCI certification or blanket compliance.

**Rationale**: Hosted Checkout reduces card-data exposure, but PCI obligations remain shared and
depend on the complete deployed business environment.

**Primary source**: [Stripe integration security guide](https://docs.stripe.com/security/guide).

## R14. Test doubles and external validation

**Decision**: Use a deterministic in-process fake of `HostedCheckoutProviderPort` for the complete
failure matrix, plus bounded Stripe test-mode/Stripe CLI smoke for real request/signature compatibility.
Do not make `stripe-mock` a required correctness gate.

**Rationale**: A port fake can model response loss, delayed success, duplicates, out-of-order events,
and deadline races deterministically. Stripe test mode catches integration drift. Stripe's own mock
server is useful for request shape but is intentionally stateless/hardcoded and does not prove these
failure semantics.

**Primary source**: [stripe-mock repository](https://github.com/stripe/stripe-mock).

## R15. Feature placement and architecture

**Decision**: Use one top-level `payment` feature with `domain`, `application`, and `adapter` layers.
Inbound adapters are separated by protocol (`web`, `webhook`, `messaging/kafka`, `scheduling`);
outbound adapters by mechanism (`persistence/jpa`, `provider/stripe`, `messaging/kafka`). Keep
service-wide configuration, security, observability, web support, and outbox outside the feature
only where they are genuinely cross-cutting.

**Rationale**: The Payment aggregate is the business center. Package-by-feature keeps change
locality while ports protect it from Spring, Stripe, Kafka, Avro, and JPA concerns.

**Alternative rejected**: controller/service/repository top-level layering or a technical
`reconciliation` feature that splits one aggregate lifecycle across packages.
