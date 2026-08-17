# Feature Specification: Payment Service Stripe Checkout MVP

**Feature Branch**: `codex/payment-service-spec`

**Created**: 2026-08-17

**Status**: Approved — ready for planning; production implementation remains gated by approved
plan/tasks/contracts and an Accepted Purchase Saga ADR

**Input**: User description: "Align the supplied Payment Service Stripe Checkout draft with the
Flash Sale repository, canonical topic/event names, Order-owned Saga orchestration, Clean/Hexagonal
boundaries, reconciliation, and a PCI-DSS-aware boundary."

**Linked Business Requirements**: Complete the payment participant needed after the verified Order
Service core while preserving durable, idempotent purchase processing.

**Business Owner**: Project owner

**Required Reviewers**: Project owner, architecture reviewer, security reviewer

## Problem and Scope

### Problem Statement

`payment-service` is currently only a Spring Boot scaffold. The supplied Stripe Checkout draft has
strong reliability ideas, but several names and flows conflict with the repository's current target
architecture:

- it treats `OrderCreated.v1` as an instruction for Payment to act;
- it uses `order.lifecycle.v1` and `payment.lifecycle.v1`, which are not the repository topic
  families;
- it keys payment results by `paymentId`, while the Order-owned Saga requires `orderId` ordering;
- it adds `PaymentExpired.v1` and consumes `OrderExpired.v1` even though neither interaction belongs
  to the current target Payment catalog;
- it permits direct external ingress to Payment when Gateway forwarding is inconvenient;
- it mixes requirements with DDL, package trees, dependency choices, polling intervals, retry
  schedules, and retention values that belong in an approved implementation plan.

The project needs a canonical Payment specification that keeps the draft's hosted Checkout,
idempotency, webhook, outbox, and recovery guarantees while making Payment a participant in the
existing Order-owned orchestration rather than a choreography consumer that starts work from an
Order fact.

### In Scope

- Define Payment as a participant in the Order-owned purchase Saga.
- Promote the candidate `PaymentRequested.v1`, `PaymentSucceeded.v1`, and `PaymentFailed.v1`
  contracts with their canonical topics and `orderId` partition key.
- Create or replay one logical Payment from a valid `PaymentRequested.v1` command.
- Let an authenticated owner create or resume a Stripe-hosted, card-only Checkout Session without
  supplying commercial values or provider configuration.
- Keep one durable logical Payment per Order and at most one unresolved/active Checkout attempt per
  Payment.
- Verify Stripe webhooks, durably deduplicate provider deliveries, and make provider outcomes drive
  Payment state.
- Reconcile ambiguous Session creation, missing/delayed webhooks, stale provider-processing state,
  and deadline/session-expiration races.
- Publish required Payment outcomes reliably after local durable state commits.
- Expose owner-scoped Payment query endpoints through API Gateway.
- Define the no-card-data and secret-handling boundary for Stripe-hosted Checkout.
- Add the Payment-owned service configuration, schema migrations, tests, and the shared local
  infrastructure changes required to validate this slice.

### Out of Scope

- Using `OrderCreated.v1` as a Payment command.
- Implementing the Order-owned Saga state machine, producing `PaymentRequested.v1` from Order, or
  consuming Payment results in Order. Those are a separate Purchase Saga integration feature.
- Confirming or releasing the Flash Sale reservation.
- Choosing the final Order state after payment or reservation results.
- Refunds, partial refunds, disputes, chargebacks, payouts, settlement-file reconciliation, or a
  financial ledger.
- Saved cards, subscriptions, recurring billing, Stripe Connect, multi-provider failover, custom
  card forms, or accepting raw payment-method/card data.
- Delayed payment methods; the initial provider scope is hosted Checkout with `card` only.
- Redis as a correctness or idempotency dependency.
- Automatic deletion of Payment, Inbox, idempotency, provider-receipt, or Outbox evidence.
- Formal PCI-DSS certification or a claim that the project is PCI-DSS compliant.
- Direct public exposure of `payment-service` that bypasses API Gateway.

### Non-goals

- This feature does not implement a generic distributed transaction, two-phase commit, a global
  Saga service, or a distributed lock.
- This feature does not make browser success/cancel navigation authoritative for financial state.
- This feature does not copy a tutorial's monolithic Order/Payment repositories or provider call
  transaction boundaries.
- This feature does not optimize Payment with Redis before PostgreSQL/provider profiling proves a
  need.

## Clarifications

### Session 2026-08-17

- Q: Should the MVP use automatic capture or authorize first? → A: Use automatic capture at
  successful Checkout completion.
- Q: How should a late successful payment be handled after Order/reservation expiry or release has
  advanced? → A: Prefer forward recovery; if safe completion is impossible, move to manual
  review. Automatic refund is out of scope.
- Q: How many sequential Checkout attempts may one Payment have? → A: At most three attempts
  before the immutable payment deadline, with at most one active or unresolved attempt.

## Baseline References

| Reference | Current authority | Feature 021 alignment |
|-----------|-------------------|-----------------------|
| [Saga messaging guide](../../docs/architecture/saga-messaging-reliability.md) | Purchase/payment uses an Order-owned orchestrated Saga | Payment consumes an explicit command and publishes facts; it does not react to `OrderCreated.v1` as a hidden command |
| [End-to-end flow](../../docs/architecture/flash-sale-end-to-end-flow.md) | Order owns workflow state and next-step decisions | Payment owns payment attempts/provider truth only |
| [Kafka topic/message catalog](../../docs/kafka/04-topic-message-catalog.md) | Canonical candidate topics, messages, ownership, and `orderId` key | Feature 021 promotes only the Payment command/result family it owns or consumes |
| [Feature 020 Order spec](../020-order-service-mvp/spec.md) | `OrderCreated.v1` is an approved fact and not payment authorization | Its topic/schema remain unchanged and Payment does not consume it for business action |
| [OrderCreated contract](../020-order-service-mvp/contracts/order-created-kafka.md) | Approved immutable Order fact | Supplies design context only; `PaymentRequested.v1` is a separate command contract |
| [HTTP response v1](../018-http-response-standardization/contracts/http-response-v1.md) | Shared success/error envelope and trace-header rules | Payment public JSON uses shared envelopes and echoes `X-Trace-Id` |
| [Stripe Checkout Session create](https://docs.stripe.com/api/checkout/sessions/create) | Provider rules for hosted Checkout and Session expiry | Internal five-minute business deadline cannot rely on Stripe's 30-minute minimum custom Session expiry |
| [Stripe webhook guide](https://docs.stripe.com/webhooks) | Raw-body signature verification, duplicate/out-of-order delivery, retries, and fast acknowledgement | Gateway and Payment must preserve raw bytes, verify signatures, deduplicate, and process asynchronously |
| [Stripe idempotent requests](https://docs.stripe.com/api/idempotent_requests) | Provider idempotency behavior and API v1 retention window | A PaymentAttempt uses a stable provider key; recovery does not assume provider retention forever |
| [Stripe integration security](https://docs.stripe.com/security/guide) | Hosted/low-risk integration reduces direct card-data exposure but compliance is shared | The project documents a PCI-DSS-aware boundary without claiming certification |

## Requirement Delta from the Supplied Draft

### ADDED

- An explicit `PaymentRequested.v1` command as the only Kafka trigger that creates/reuses a Payment.
- Order-owned Saga participation and a separate future Order/Flash Sale integration scope.
- A rule that all public user and Stripe webhook traffic enters through API Gateway.
- A provider-idempotency recovery boundary that accounts for Stripe API v1 key pruning after at
  least 24 hours.
- A manual-review outcome for provider truth that cannot be established safely; unresolved money is
  never guessed into success or failure.

### MODIFIED

- **Before**: Payment consumes `OrderCreated.v1` from `order.lifecycle.v1`.
  **After**: Payment consumes `PaymentRequested.v1` from
  `flashsale.payment.commands.v1`; `OrderCreated.v1` stays a fact on
  `flashsale.order.events.v1`.
- **Before**: Payment publishes to `payment.lifecycle.v1`, keyed by `paymentId`.
  **After**: Payment publishes `PaymentSucceeded.v1` and `PaymentFailed.v1` to
  `flashsale.payment.events.v1`, keyed by `orderId` so the orchestrator receives per-Order ordering.
- **Before**: Payment consumes `OrderExpired.v1` to discover the deadline.
  **After**: Order supplies an immutable `paymentDeadline` in `PaymentRequested.v1`; Payment enforces
  that deadline locally and reports an established terminal outcome to Order.
- **Before**: `PaymentExpired.v1` is a separate integration event.
  **After**: no `PaymentExpired.v1` is introduced in this slice. Deadline expiry is represented by
  Payment's internal `EXPIRED` state and a terminal `PaymentFailed.v1` outcome code.
- **Before**: successful payment leads directly to `Order -> PAID`.
  **After**: `PaymentSucceeded.v1` is a fact for the Order orchestrator; the later Saga must confirm
  the reservation before choosing a final Order state.
- **Before**: webhook may bypass Gateway.
  **After**: Gateway owns external ingress and forwards the unmodified body/signature to Payment.

### REMOVED OR DEFERRED

- Fixed DDL, indexes, class names, package tree, framework dependencies, polling intervals, retry
  backoff, and retention periods are removed from this WHAT/WHY document and must be decided in
  `plan.md` without changing approved behavior.
- `payment.order.dlt.v1` is not accepted as a canonical name. The plan must name an operational DLT
  consistently with the approved source command and document operator replay.
- Delayed-method events are deferred while the provider is configured for `card` only.

## User Scenarios & Testing

### User Story 1 - Accept an Orchestrated Payment Request (Priority: P1)

As the Order-owned Saga, I want Payment to durably accept one explicit payment request per Order so
that payment work starts only from an intentional command and duplicate delivery cannot duplicate
financial state.

**Why this priority**: Every later Checkout, webhook, and recovery operation needs a trustworthy
Payment identity and immutable amount/deadline snapshot.

**Independent Test**: Deliver a valid `PaymentRequested.v1` command plus duplicate, concurrent, and
conflicting variants and prove exactly one equivalent Payment exists without any Stripe call.

**Use Case IDs**: `UC-PAY-001`

#### Use Case UC-PAY-001: Accept Payment Request

**Trigger**: `PaymentRequested.v1` arrives from Order Service.

**Preconditions**:

- The command contract version is supported.
- Order Service supplied an Order, shopper, exact amount/currency, and payment deadline snapshot.

**Main Flow**:

1. Validate the command envelope, key, trace metadata, and business snapshot.
2. Establish the command identity and logical Order identity durably.
3. Create one Payment in `PENDING`, preserving the exact immutable commercial snapshot.
4. Commit the Payment and command receipt atomically.
5. Acknowledge the command only after the local commit.

**Alternative Flows**:

- An equivalent duplicate command returns the previously established semantic result.
- A different command ID with equivalent Order data resolves to the same Payment.
- A valid command first processed after its deadline creates a non-payable expired Payment and one
  terminal outcome for the orchestrator; it never calls Stripe.

**Exceptions**:

- Unsupported/malformed input or contradictory reuse of a command/Order identity causes no Payment
  mutation and is retained for operator inspection under the approved DLT policy.
- Transient local storage failure is retried without acknowledging the Kafka record.

**Postconditions**:

- Success: exactly one logical Payment exists for the Order.
- Minimal failure guarantee: no provider operation is issued and no contradictory Payment is
  accepted.

**Acceptance Scenarios**:

1. **UC-PAY-001/AC-01** — **Given** a valid new `PaymentRequested.v1`, **When** Payment accepts it,
   **Then** exactly one `PENDING` Payment contains the same Order, shopper, amount, currency, and
   deadline and Stripe is not called.
2. **UC-PAY-001/AC-02** — **Given** an accepted command, **When** the same message is delivered 100
   times, **Then** one Payment and one semantic command receipt remain.
3. **UC-PAY-001/AC-03** — **Given** an existing Payment, **When** another command ID carries the same
   logical snapshot for that Order, **Then** it resolves to the same Payment without a conflict.
4. **UC-PAY-001/AC-04** — **Given** an existing Payment, **When** the same Order identity is reused
   with different shopper, amount, currency, or deadline, **Then** no existing state changes and the
   conflicting input becomes operator-visible.

---

### User Story 2 - Start or Resume Hosted Checkout Safely (Priority: P1)

As an authenticated shopper, I want to obtain a Stripe-hosted Checkout URL for my payable Order so
that I can enter card data on Stripe without duplicate Checkout workflows when I retry.

**Why this priority**: This is the customer action that begins external payment while keeping card
data outside the project.

**Independent Test**: With a fake provider, exercise new, duplicate, conflicting, concurrent, and
ambiguous requests and prove only one active logical attempt exists.

**Use Case IDs**: `UC-PAY-002`

#### Use Case UC-PAY-002: Start Payment Attempt

**Trigger**: The Payment owner requests a Checkout Session through API Gateway with an
`Idempotency-Key`.

**Preconditions**:

- The user JWT is valid at Gateway and Payment Service.
- The Payment belongs to the JWT subject, is payable, and is before its deadline.
- The idempotency key is syntactically valid.

**Main Flow**:

1. Claim the client operation and one active PaymentAttempt durably.
2. Commit the attempt identity and stable provider idempotency identity before any network call.
3. Ask Stripe to create one hosted, card-only Checkout Session using server-owned amount, currency,
   references, and return URLs.
4. Persist the provider Session identity and replayable redirect result.
5. Return the Checkout result to the same authorized owner; Payment remains non-successful until
   provider truth is established.

**Alternative Flows**:

- Same client key and same canonical request replays or resumes the same logical result.
- Different client keys racing on the same Payment select one active attempt.
- An active `OPEN` Session may be resumed rather than replaced.
- An ambiguous provider response leaves the attempt unresolved, blocks a new attempt, and schedules
  recovery instead of guessing failure.

**Exceptions**:

- Missing/invalid key is rejected before Stripe is called.
- Same key with different canonical request is a conflict.
- Foreign and absent Payment identities return the same `404` behavior.
- A deadline-passed or terminal Payment cannot create a Checkout Session.

**Postconditions**:

- Success: one active PaymentAttempt maps to one Stripe Checkout Session and an authorized owner can
  receive its redirect URL.
- Minimal failure guarantee: no second unresolved provider operation is issued for the same logical
  attempt.

**Acceptance Scenarios**:

1. **UC-PAY-002/AC-01** — **Given** a payable owner Payment, **When** the owner supplies a new valid
   idempotency key, **Then** one hosted Checkout attempt is created and the response does not mark
   Payment successful.
2. **UC-PAY-002/AC-02** — **Given** a completed client operation, **When** the same key and request
   are replayed, **Then** the same attempt/Session result is returned without another logical
   provider creation.
3. **UC-PAY-002/AC-03** — **Given** a key used for one canonical request, **When** it is reused for a
   different Payment or user, **Then** the service returns a conflict and does not call Stripe.
4. **UC-PAY-002/AC-04** — **Given** one payable Payment, **When** 100 concurrent requests use
   different keys, **Then** at most one unresolved/active attempt and one logical Stripe Session win.
5. **UC-PAY-002/AC-05** — **Given** Stripe may have created a Session but the response is lost,
   **When** the client retries, **Then** the original attempt is recovered with the same provider
   idempotency identity and no new attempt is opened.
6. **UC-PAY-002/AC-06** — **Given** the Payment is absent or belongs to another user, **When** the
   caller requests Checkout, **Then** the same safe `404` shape is returned and Stripe is not called.
7. **UC-PAY-002/AC-07** — **Given** three terminal or unusable Checkout attempts already exist for
   one Payment, **When** its owner requests another attempt before the deadline, **Then** the fourth
   attempt is rejected and Stripe is not called.

---

### User Story 3 - Establish Provider Truth and Publish the Outcome (Priority: P1)

As the Order-owned Saga, I want Payment to recognize verified Stripe outcomes exactly once so that
the orchestrator can continue without trusting a browser redirect or losing a committed success.

**Why this priority**: A Checkout URL alone has no business value unless a real provider outcome is
durably observed and propagated.

**Independent Test**: Submit valid, invalid, duplicate, concurrent, and out-of-order Stripe events
and prove one monotonic Payment transition and one stable outbox outcome.

**Use Case IDs**: `UC-PAY-003`

#### Use Case UC-PAY-003: Process Stripe Checkout Event

**Trigger**: Stripe posts an event to the Payment webhook path through API Gateway.

**Preconditions**:

- Gateway preserved the raw request body and `Stripe-Signature` header.
- Payment Service owns the endpoint secret for this environment.

**Main Flow**:

1. Verify the signature against the exact raw body before trusting event data.
2. Durably record the provider event identity and safe correlation fields.
3. Return a successful acknowledgement after durable receipt, before long processing.
4. Resolve the PaymentAttempt and current provider state.
5. Apply a monotonic, idempotent Payment transition.
6. Commit the Payment change and required `PaymentSucceeded.v1` or `PaymentFailed.v1` outbox message
   atomically.
7. Publish the stable outbox message to Kafka for the Order orchestrator.

**Alternative Flows**:

- A duplicate provider event is acknowledged without a duplicate transition.
- Separate provider events that describe the same final Session outcome still create one semantic
  Payment result.
- An out-of-order or contradictory event triggers Session retrieval before any terminal regression.
- An event received before Session correlation is locally visible remains durably pending for retry.

**Exceptions**:

- Invalid signatures are rejected and cause no trusted receipt or Payment mutation.
- A valid event that cannot be durably recorded receives a non-success response so provider retry
  remains possible.
- Kafka outage leaves the committed Payment result in a retryable outbox state.

**Postconditions**:

- Success: provider truth is represented once in Payment and one stable result identity is durably
  available for publication.
- Minimal failure guarantee: a confirmed provider success is neither overwritten nor silently lost.

**Acceptance Scenarios**:

1. **UC-PAY-003/AC-01** — **Given** a valid completed Checkout Session whose payment is paid,
   **When** its verified event is processed, **Then** Payment and attempt become `SUCCEEDED` once and
   one `PaymentSucceeded.v1` outbox identity exists.
2. **UC-PAY-003/AC-02** — **Given** an invalid signature, **When** the webhook is called, **Then** it
   returns a safe `400` and creates no trusted receipt or Payment change.
3. **UC-PAY-003/AC-03** — **Given** one provider event, **When** it is delivered repeatedly or
   concurrently, **Then** it causes at most one semantic transition and one business outcome.
4. **UC-PAY-003/AC-04** — **Given** events arrive out of order, **When** their described state
   conflicts with current state, **Then** Payment never regresses and provider truth is retrieved
   where needed.
5. **UC-PAY-003/AC-05** — **Given** Payment succeeds while Kafka is unavailable, **When** the local
   transaction commits, **Then** Payment stays `SUCCEEDED` and the same outcome is published after
   Kafka recovers.
6. **UC-PAY-003/AC-06** — **Given** the browser visits a success or cancel URL, **When** no verified
   provider evidence has been processed, **Then** Payment state remains unchanged.

---

### User Story 4 - Recover Ambiguous and Deadline-Bound Payments (Priority: P1)

As an operator and shopper, I want unresolved payments to converge from provider truth or remain
explicitly actionable so that timeouts, missed webhooks, and the five-minute business deadline do
not create duplicate charges or silent losses.

**Why this priority**: External payment calls and webhook delivery cannot share a transaction with
the project database; reconciliation is part of correctness, not an optional optimization.

**Independent Test**: Inject response loss, webhook delay, process crash, provider outage, and the
deadline/payment race and verify deterministic convergence or visible manual review.

**Use Case IDs**: `UC-PAY-004`

#### Use Case UC-PAY-004: Reconcile Provider and Deadline State

**Trigger**: A durable unresolved/stale PaymentAttempt or a reached internal payment deadline becomes
eligible for recovery.

**Preconditions**:

- The Payment/attempt identity and provider correlation or stable provider idempotency identity are
  durable.

**Main Flow**:

1. Claim eligible recovery work safely across service instances.
2. Retrieve the Checkout Session when its provider identity is known.
3. If Session creation may have completed without a saved Session identity, retry only the same
   create operation with the same parameters and provider idempotency identity while provider
   replay is still safe.
4. Apply the same monotonic state rules used for webhook processing.
5. When the internal deadline is reached, prevent new attempts and request expiration of an open
   Session before declaring an unpaid terminal outcome.
6. Publish a terminal outcome only after provider success has been excluded safely.
7. Keep unresolved money in an explicit recovery/manual-review state rather than inventing a
   failure.

**Alternative Flows**:

- A paid Session converges to `SUCCEEDED`, even if local expiry was requested.
- A confirmed expired/unpaid Session at or after the deadline converges to internal `EXPIRED` and
  `PaymentFailed.v1` with a deadline outcome.
- An expired Session before the business deadline may allow another attempt only when fewer than
  three Checkout attempts have been created and no attempt remains active or unresolved.
- A creation response lost beyond the provider's safe idempotency replay window does not trigger a
  blind create; it remains operator-visible.

**Exceptions**:

- Provider unavailability advances bounded recovery scheduling but does not change financial truth.
- An unknown/unrecognized provider state remains unresolved and observable.

**Postconditions**:

- Success: Payment converges to an established provider/deadline outcome exactly once.
- Minimal failure guarantee: the same uncertain provider operation is not replaced by a fresh one
  and no success is silently overwritten.

**Acceptance Scenarios**:

1. **UC-PAY-004/AC-01** — **Given** Stripe created a Session and the response was lost before local
   persistence, **When** recovery runs inside the safe provider replay window, **Then** it reuses the
   same attempt, parameters, and provider idempotency identity.
2. **UC-PAY-004/AC-02** — **Given** a missing or delayed webhook for a Session with established
   provider success, **When** reconciliation retrieves it, **Then** the same success transaction and
   outbox outcome are produced once.
3. **UC-PAY-004/AC-03** — **Given** the business deadline arrives while a Session appears open,
   **When** Payment expires/retrieves the Session, **Then** it publishes failure only after confirming
   the Session is not paid.
4. **UC-PAY-004/AC-04** — **Given** Session expiration races with successful payment, **When** Stripe
   reports paid, **Then** Payment remains/becomes `SUCCEEDED` and is not forced to `EXPIRED`.
5. **UC-PAY-004/AC-05** — **Given** verified provider success arrives after Order/reservation expiry
   or release has advanced, **When** Payment publishes the success fact, **Then** the downstream Saga
   policy is forward recovery first and explicit manual review when safe completion is impossible;
   Payment does not initiate an automatic refund.
6. **UC-PAY-004/AC-06** — **Given** provider truth cannot be established safely, **When** automatic
   recovery is exhausted, **Then** Payment remains explicit and operator-visible rather than being
   guessed into a terminal state.

---

### User Story 5 - Query My Payment Safely (Priority: P2)

As an authenticated shopper, I want to query Payment by Payment or Order identity so that the UI can
show pending, confirming, successful, failed, expired, or unresolved status without trusting the
Stripe redirect.

**Why this priority**: The redirect can arrive before or without webhook processing; an owner query
is the safe UI read path.

**Independent Test**: Query owner, foreign, absent, and provider/Kafka outage cases through Gateway
and verify stable envelopes and indistinguishable foreign/absent behavior.

**Use Case IDs**: `UC-PAY-005`

**Acceptance Scenarios**:

1. **UC-PAY-005/AC-01** — **Given** an owner Payment, **When** its owner queries by Payment ID or
   Order ID, **Then** the current durable status and non-sensitive summary are returned.
2. **UC-PAY-005/AC-02** — **Given** a foreign or absent Payment, **When** a shopper queries it,
   **Then** both cases return the same safe `404` response.
3. **UC-PAY-005/AC-03** — **Given** Kafka or Stripe is unavailable while PostgreSQL remains
   available, **When** an owner queries Payment, **Then** the durable read still succeeds.
4. **UC-PAY-005/AC-04** — **Given** a redirect has occurred but provider outcome is still pending,
   **When** the owner queries, **Then** the response reports the non-terminal status and does not
   imply success.

### Edge Cases

- A webhook arrives before the transaction that stores its Session correlation becomes visible.
- Stripe sends the same Event ID more than once or separate Event IDs for the same Session outcome.
- A provider event describes an older state than the current terminal Payment state.
- Two service instances claim the same command, client key, provider event, outbox row, or recovery
  item concurrently.
- The service crashes after provider success but before persisting the response.
- The service crashes after publishing Kafka but before marking the outbox record delivered.
- An HTTP idempotency key is reused by another user or for another Payment.
- A Session naturally expires before the internal deadline or remains open past the internal
  deadline because Stripe's custom expiry cannot be shorter than 30 minutes.
- The provider idempotency replay window passes before a missing create response can be resolved.
- The stored currency/amount cannot be represented safely for the configured provider/currency.
- Gateway or another filter mutates the webhook body or removes `Stripe-Signature`.
- PostgreSQL is unavailable before a provider attempt is durably claimed.
- Provider success is established after Order has already advanced its expiry/release workflow.

## Requirements

### Functional Requirements

- **FR-001**: Payment Service MUST participate in the Order-owned Saga and MUST NOT own final Order
  or Flash Sale reservation state.
- **FR-002**: Only `PaymentRequested.v1` on `flashsale.payment.commands.v1` MAY create/reuse a
  Payment through Kafka; `OrderCreated.v1` MUST NOT be interpreted as authorization to pay.
- **FR-003**: A command MUST carry an immutable Order-owned snapshot containing at least command,
  correlation/causation, Order, shopper, exact amount/currency, and payment-deadline identities.
- **FR-004**: Payment MUST durably deduplicate physical command delivery and enforce at most one
  logical Payment per Order, including concurrent and different-command-ID delivery.
- **FR-005**: Equivalent duplicate commands MUST resolve to the same Payment; contradictory reuse of
  command or Order identity MUST NOT mutate established state and MUST become operator-visible.
- **FR-006**: Accepting `PaymentRequested.v1` MUST NOT call Stripe; provider interaction begins only
  when the authenticated owner starts a PaymentAttempt.
- **FR-007**: Payment amount, currency, shopper, Order identity, and deadline MUST be immutable and
  MUST come from the command rather than the public Checkout request.
- **FR-008**: The public Checkout operation MUST require a valid owner JWT and `Idempotency-Key` and
  MUST accept no client amount, currency, Order/user identity, return URL, provider metadata, or
  card/payment-method data.
- **FR-009**: Same client key plus the same canonical operation MUST replay or resume the same
  logical result; the same key plus a different operation MUST return a conflict without another
  provider call.
- **FR-010**: Concurrent requests with different client keys for one Payment MUST create at most one
  unresolved/active PaymentAttempt.
- **FR-011**: One PaymentAttempt MUST represent one Stripe Checkout Session and MUST keep one stable
  provider idempotency identity for every retry of that same provider create operation.
- **FR-012**: A new provider operation MUST NOT replace an unresolved attempt until provider truth
  or an approved operator decision makes the previous attempt terminal.
- **FR-013**: External provider calls MUST occur outside a long-running local database transaction,
  after the provider operation identity is durably established.
- **FR-014**: The initial Checkout MUST be hosted, one-time, and card-only; Stripe MUST own card-data
  collection and authentication.
- **FR-015**: The MVP MUST use automatic capture at successful hosted Checkout completion. It MUST
  NOT introduce a separate authorize-then-capture command/result flow in Feature 021.
- **FR-016**: Server configuration MUST own success/cancel URLs, provider metadata, exact amount,
  currency, and line-item description; public callers MUST NOT override them.
- **FR-017**: Creating/opening a Checkout Session MUST NOT mark Payment successful, and visiting a
  success/cancel URL MUST NOT mutate financial state.
- **FR-018**: All external Payment and Stripe webhook traffic MUST enter through API Gateway; the
  webhook route MAY omit user JWT but MUST preserve raw request bytes and `Stripe-Signature`.
- **FR-019**: Payment MUST verify the Stripe signature against the unmodified raw body before a
  provider event is trusted or allowed to affect Payment.
- **FR-020**: A valid provider event MUST be durably recorded and deduplicated before fast success
  acknowledgement; a valid event that cannot be recorded MUST receive a non-success response.
- **FR-021**: Provider processing MUST tolerate duplicate, concurrent, and out-of-order events and
  MUST retrieve provider state before applying a contradictory or insufficient terminal signal.
- **FR-022**: A paid completed Checkout Session MUST transition the Payment/attempt to `SUCCEEDED`
  at most once and atomically create one stable `PaymentSucceeded.v1` outbox identity.
- **FR-023**: A browser redirect, an `OPEN` Session, or an unverified provider payload MUST NOT be
  treated as payment success.
- **FR-024**: A terminal unpaid outcome MUST produce one stable `PaymentFailed.v1` only after
  provider success has been excluded safely; a transient technical failure MUST NOT be published as
  a business failure.
- **FR-025**: Internal deadline expiry MUST use internal `EXPIRED` state plus an explicit
  `PaymentFailed.v1` reason; this feature MUST NOT add `PaymentExpired.v1`.
- **FR-026**: Payment MUST prevent new attempts at/after the command deadline and MUST expire or
  retrieve an open Checkout Session before declaring an unpaid deadline outcome.
- **FR-027**: A provider-paid result racing with expiry MUST win inside Payment. When Order or
  reservation expiry/release has already advanced, the approved downstream Saga policy MUST prefer
  forward recovery and MUST use an explicit manual-review outcome when safe completion is
  impossible. Feature 021 MUST publish provider success without initiating an automatic refund or
  directly mutating Order/reservation state.
- **FR-028**: A Payment MAY create at most three sequential Checkout attempts, only before its
  immutable deadline and only after the previous attempt is terminal or unusable; at most one
  attempt may be active or unresolved at any time. Card retries inside one hosted Stripe Checkout
  Session MUST NOT consume another PaymentAttempt.
- **FR-029**: A reconciliation capability MUST recover stale/ambiguous attempts, missing/delayed
  webhook outcomes, unresolved expiry requests, and Session-create response loss.
- **FR-030**: Recovery MUST reuse the same provider operation only while provider idempotent replay
  is safe; after that window it MUST NOT blindly create another Session and MUST expose manual
  review.
- **FR-031**: Unknown/unrecognized provider state MUST remain explicit and observable; retry
  exhaustion alone MUST NOT invent success or failure.
- **FR-032**: Payment state and every required Kafka result MUST commit atomically through a local
  outbox; Kafka outage MUST NOT roll back or discard established provider truth.
- **FR-033**: `PaymentSucceeded.v1` and `PaymentFailed.v1` MUST be published on
  `flashsale.payment.events.v1` with Kafka key `orderId`, stable message identity, correlation,
  causation, aggregate version/time, and W3C trace context in headers.
- **FR-034**: Payment results MUST be facts for Order Service and MUST NOT directly mutate another
  service or imply that the reservation was confirmed/released.
- **FR-035**: Authenticated owners MUST be able to query Payment by Payment ID and Order ID; absent
  and foreign resources MUST be indistinguishable.
- **FR-036**: Successful public JSON responses MUST use `ApiResponse<T>`, failures MUST use
  `ApiErrorResponse`, and `X-Trace-Id` MUST be propagated/echoed without adding a body `traceId`.
  A response containing a Checkout redirect URL MUST include `Cache-Control: no-store`.
- **FR-037**: Stripe webhook acknowledgement MUST use a successful empty response after durable
  receipt; it MUST NOT use the user-facing response envelope.
- **FR-038**: Payment APIs, persistence, logs, metrics, traces, events, and test fixtures MUST NOT
  accept, persist, log, or publish PAN, CVV/CVC, PIN, track data, secrets, raw JWTs, or full webhook
  bodies. A Checkout redirect URL MAY appear only in the authenticated owner's successful
  start/resume Checkout response; it MUST NOT appear in errors, query responses, persistence,
  events, logs, metrics, or traces.
- **FR-039**: Provider secrets MUST be environment-specific, externalized, redacted, and absent from
  Git and Payment durable business records.
- **FR-040**: PostgreSQL MUST be Payment's durable source of truth; Redis MUST NOT be required for
  correctness or initial idempotency.
- **FR-041**: MVP MUST retain command/provider/idempotency/outbox evidence without an automated
  deletion job until a later approved retention policy exists.
- **FR-042**: Payment Service MUST expose liveness, readiness, Prometheus metrics, and trace-linked
  structured diagnostics without coupling business code to a concrete metrics registry.

### Non-Functional Requirements

- **NFR-REL-001**: Duplicate command, HTTP, provider-event, reconciliation, and outbox execution MUST
  produce at most one semantic financial effect for each approved identity.
- **NFR-REL-002**: Under injected database, provider, process, Kafka, and Schema Registry failures,
  every accepted operation MUST either converge to one established outcome or remain durably
  visible for retry/manual review.
- **NFR-REL-003**: Multi-instance correctness MUST rely on durable identity/state transitions rather
  than JVM-local or distributed locks.
- **NFR-PERF-001**: At least 95% of owner Payment queries MUST complete within 200 ms under the
  approved local baseline when PostgreSQL is healthy.
- **NFR-PERF-002**: Excluding measured provider latency, at least 95% of successful Checkout-start
  operations MUST add no more than 150 ms of service-local processing under the approved baseline.
- **NFR-PERF-003**: A test of 100 concurrent different-key requests for one payable Payment MUST
  produce at most one active provider Session workflow.
- **NFR-SEC-001**: User endpoints MUST validate the existing JWT/JWKS trust contract at Gateway and
  Payment Service and enforce owner authorization.
- **NFR-SEC-002**: Webhook trust MUST come from current provider signature verification and replay
  protection, never user JWT or a caller-supplied producer field.
- **NFR-SEC-003**: The feature MAY be described as a PCI-DSS-aware hosted-Checkout boundary but MUST
  NOT claim PCI-DSS compliance without formal assessment and required attestation.
- **NFR-OBS-001**: Logs, metrics, and traces MUST allow an operator to correlate command ID, Saga
  correlation ID, Order ID, Payment ID, attempt ID, provider Session/request/event IDs, outbox ID,
  state transition, and safe failure class without high-cardinality IDs as metric labels.
- **NFR-COMPAT-001**: Kafka values MUST use approved generated versioned contracts and the existing
  subject/compatibility governance; no JSON-string, JPA, provider SDK, or domain model may become the
  shared wire contract.

### Contract and Naming Alignment

| Direction | Topic | Message | Kafka key | Meaning |
|-----------|-------|---------|-----------|---------|
| Order -> Payment | `flashsale.payment.commands.v1` | `PaymentRequested.v1` / `PaymentRequestedV1` | `orderId` | Explicit Order-owned Saga command to establish/pay one Order snapshot |
| Payment -> Order | `flashsale.payment.events.v1` | `PaymentSucceeded.v1` / `PaymentSucceededV1` | `orderId` | Committed provider-paid fact; not reservation confirmation |
| Payment -> Order | `flashsale.payment.events.v1` | `PaymentFailed.v1` / `PaymentFailedV1` | `orderId` | Established terminal unpaid/deadline fact; not a transient technical error |
| Order -> observers | `flashsale.order.events.v1` | `OrderCreated.v1` / `OrderCreatedV1` | `orderId` | Existing fact; never a Payment command |

The v1 Payment contracts must use the repository envelope conventions: stable message ID,
type/version, producer, aggregate identity/version, correlation ID, causation ID, UTC occurrence
time, message-specific data, and W3C trace headers. Command and result retries preserve the same
wire identity and payload.

`PaymentRequested.v1` data must contain at least the immutable `orderId`, `userId`, exact amount,
currency, and `paymentDeadline`. `PaymentSucceeded.v1` and `PaymentFailed.v1` must contain at least
`paymentId`, `orderId`, established outcome time, exact amount/currency, and safe provider/outcome
references needed by the orchestrator and audit. Exact optional/defaulted fields, namespaces,
subjects, and rollout order belong in the contract and plan.

No `PaymentExpired.v1`, `order.lifecycle.v1`, `payment.lifecycle.v1`, or `payment.order.dlt.v1`
business contract is approved by this feature.

### Public HTTP Contract Boundary

| Operation | Route | Authentication | Success semantics |
|-----------|-------|----------------|-------------------|
| Get Payment | `GET /api/v1/payments/{paymentId}` | Owner JWT | `200 ApiResponse<PaymentDetails>` |
| Get Payment by Order | `GET /api/v1/payments/by-order/{orderId}` | Owner JWT | `200 ApiResponse<PaymentDetails>` |
| Start/resume Checkout | `POST /api/v1/payments/{paymentId}/checkout-sessions` | Owner JWT + `Idempotency-Key` | `201` new result, `200` replay/resume, or `202` durable ambiguous recovery |
| Receive Stripe event | `POST /webhooks/v1/payments/stripe` through Gateway | No user JWT; verified `Stripe-Signature` in Payment | `204` after durable provider receipt |

- The Checkout request has no business body in v1.
- Every user-facing JSON response uses the shared response contract and echoes `X-Trace-Id`.
- A response containing a Checkout redirect URL is owner-authorized and uses
  `Cache-Control: no-store`; Payment never exposes that URL through a query or error response.
- Webhook `400` means invalid signature/payload; a valid event that cannot be durably accepted uses
  a safe non-`2xx` response so Stripe can retry.
- Payment owns stable `PAYMENT_*` error codes; errors reveal no provider, database, security, or
  secret detail.

### Key Entities

- **Payment**: One logical obligation for one Order. Owns immutable shopper, amount, currency,
  deadline, provider choice, lifecycle, and the invariant that success happens at most once.
- **PaymentAttempt**: One externally initiated Checkout Session workflow. Owns stable attempt and
  provider idempotency identities, provider correlations, status, and outcome timestamps.
- **Payment Command Receipt**: Durable evidence that one Kafka command identity and canonical
  business fingerprint was accepted, replayed, or rejected as conflicting.
- **Client Idempotency Record**: Durable owner/operation/key fingerprint and replayable outcome for
  starting/resuming Checkout.
- **Provider Event Receipt**: Durable verified Stripe event identity, correlation, processing state,
  and safe failure information used for acknowledgement, deduplication, and recovery.
- **Payment Outbox Message**: Immutable stable Payment result awaiting/recording Kafka publication.
- **Recovery Work**: Durable eligibility and progress for ambiguous Session creation, stale provider
  state, deadline expiration, or provider-event correlation. It does not represent settlement or a
  financial ledger.

### Domain Model Delta

Added Payment lifecycle language:

- `PENDING`: payable but not provider-confirmed.
- `PROCESSING`: provider reports non-terminal processing.
- `UNKNOWN`: provider outcome cannot yet be established safely.
- `SUCCEEDED`: provider-paid terminal state and pivot fact.
- `FAILED`: established terminal unpaid failure not caused by a transient technical error.
- `EXPIRED`: internal payment deadline reached after provider success was safely excluded.

Added PaymentAttempt lifecycle language:

- `CREATING`: durable provider operation identity exists; Session response not yet established.
- `OPEN`: hosted Checkout Session can still be used.
- `PROCESSING`: Checkout completed but provider payment is not final.
- `UNKNOWN`: create/expire/retrieve outcome is ambiguous.
- `SUCCEEDED`, `FAILED`, `EXPIRED`: terminal attempt outcomes.

Core invariants:

- One Order has at most one logical Payment.
- One Payment has at most one unresolved/active attempt.
- One attempt maps to at most one provider Session and one stable provider create identity.
- Amount, currency, shopper, Order identity, and deadline do not change after command acceptance.
- `SUCCEEDED` never regresses, and no terminal transition is inferred from retry exhaustion alone.
- Provider success, client navigation, Order state, and reservation state are distinct facts.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Replaying one valid Payment command 100 times produces exactly one logical Payment and
  no provider call.
- **SC-002**: Sending 100 concurrent Checkout-start requests with different keys for one Payment
  produces at most one active logical provider Session workflow.
- **SC-003**: Replaying or concurrently delivering a provider event produces exactly one semantic
  Payment transition and one stable business result.
- **SC-004**: Every injected response-loss, webhook-delay, process-crash, provider-outage, and
  Kafka-outage scenario either converges to one established result after recovery or remains
  durably visible for manual review; none silently disappears.
- **SC-005**: All established provider-paid outcomes survive Kafka outage and are eventually
  delivered with the original Order key, message identity, and correlation context.
- **SC-006**: Owner and foreign/absent query tests pass through Gateway, with foreign and absent
  resources indistinguishable and 95% of healthy local owner reads below 200 ms.
- **SC-007**: Automated scans and contract tests find zero PAN, CVV/CVC, PIN, track data, provider
  secrets, raw JWTs, or full webhook bodies in APIs, persistence, events, logs, and test fixtures;
  Checkout URLs appear only in authorized start/resume responses with `Cache-Control: no-store` and
  never in errors, queries, persistence, events, logs, metrics, or traces.
- **SC-008**: Payment remains correct with Redis unavailable because no MVP correctness path depends
  on Redis.
- **SC-009**: Payment module verification, cross-module contract tests, Gateway route/security
  tests, provider-adapter tests, failure matrix, and one Stripe test-mode/CLI E2E complete with no
  required failure before the feature is marked Verified.

## Dependencies and Compatibility

- Feature 020's `OrderCreatedV1` schema and producer remain unchanged.
- A later Purchase Saga integration feature must add the Order producer for `PaymentRequested.v1`,
  Order consumers for Payment results, and reservation confirm/release flow; Feature 021 may use
  contract fixtures for independent Payment validation.
- The feature promotes two candidate Kafka topic families from the repository catalog; exact Avro
  schemas, subjects, compatibility, controlled registration, provisioning, consumer group, DLT,
  replay, and rollout order must be approved before implementation.
- Gateway needs one authenticated Payment route family and one narrowly permitted Stripe webhook
  route that preserves the body/signature. Payment Service still performs its own JWT/signature
  validation.
- Payment's new production dependencies, including the pinned Stripe Java SDK, persistence,
  security, Kafka, Avro/Registry, and test infrastructure, must be justified in `plan.md` before
  production code changes.
- A Purchase Saga ADR is required before production implementation because the existing architecture
  guides are target guidance rather than an Accepted ADR that promotes these candidate contracts.

## Assumptions

- The initial business/provider validation slice supports VND and hosted Checkout with `card` only;
  adding currency or delayed-method support requires contract and behavior review.
- The Order-owned Saga selects and transmits the authoritative payment deadline. The existing Flash
  Sale reservation currently expires five minutes after acceptance; Stripe's Session expiry does
  not replace that internal deadline.
- Provider Session retrieval and verified webhooks are valid sources of provider truth. Browser
  navigation is not.
- Stripe API v1 idempotency keys may be pruned after they are at least 24 hours old; safe recovery
  after that point cannot rely on replay alone.
- Existing Authentication JWT/JWKS and `libs/common-web` contracts are reused.
- No automated Payment evidence retention/cleanup runs in MVP; a later approved policy may add it.
- High-volume tests use a deterministic fake provider; the Stripe test API is used only for bounded
  contract/E2E validation.

## Constitutional Constraints

- **Service ownership**: Payment Service owns Payment, attempts, client idempotency, command/provider
  receipts, recovery state, and Payment outbox in `payment_db`. It never reads Order, Product, Flash
  Sale, Inventory, or Stripe-owned storage directly.
- **External ingress**: User and webhook traffic enter through API Gateway. A direct Payment ingress
  exception would require an Accepted ADR and is not approved here.
- **API/event contracts**: Public HTTP and three versioned Kafka contracts must be documented and
  approved before implementation; existing `OrderCreated.v1` is unchanged and not reinterpreted.
- **Durable and hot-path data**: PostgreSQL is Payment's durable truth. Redis is unnecessary for MVP
  correctness and no Redis Lua stock operation belongs to Payment.
- **Messaging reliability**: Command consumers and provider processing are idempotent. State plus
  required result publication uses one local atomic persistence capability and transactional outbox.
  Ordering uses `orderId`; retry/DLT/replay/reconciliation behavior must be explicit in the plan.
- **Root infrastructure ownership**: Shared Compose, Kafka/Registry bootstrap, Gateway topology,
  monitoring, and future Kubernetes changes belong under root `infra/`; Payment migrations and
  runtime configuration stay in `services/payment-service`.
- **Observability**: Use Spring Boot Actuator auto-configuration, the runtime Prometheus registry,
  declarative configuration, safe structured diagnostics, and HTTP/Kafka trace propagation. Domain
  and application code must not construct or depend on a Prometheus registry implementation.
- **Verification**: Pure domain/application tests, HTTP/security tests, PostgreSQL migration and
  concurrency integration tests, Avro compatibility tests, Kafka/Registry and outbox recovery tests,
  fake-provider contract/failure tests, bounded Stripe test-mode E2E, architecture tests, and load
  tests apply. No required layer may be omitted without plan rationale.
- **Architecture decisions**: An Accepted Purchase Saga ADR is required before production code
  promotes the candidate Payment command/event communication style. No service-boundary or direct
  ingress exception is approved.

## Approval and History

- 2026-08-17 — Draft aligned from the supplied Stripe Checkout specification with the verified
  repository architecture, Feature 020 contracts, and current official Stripe provider guidance.
- 2026-08-17 — Project owner selected automatic capture, forward recovery with manual-review
  fallback, and a maximum of three Checkout attempts per Payment.
- 2026-08-17 — Architecture review approved the Order-owned Saga boundary, Payment data ownership,
  versioned command/result contracts, transactional outbox, reconciliation ownership, and
  Clean/Hexagonal separation for planning. Production implementation remains gated by the Purchase
  Saga ADR and approved downstream artifacts.
- 2026-08-17 — Security review approved the hosted-Checkout boundary for planning after limiting
  Checkout URL disclosure to an owner-authorized `no-store` response, retaining raw-body Stripe
  signature verification, safe durable receipt, secret redaction, and the no-PCI-certification
  claim boundary.
- 2026-08-17 — Project owner authorized completion of the clarified specification and progression
  to `/speckit-plan`.
