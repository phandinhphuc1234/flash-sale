# Research: Regular Purchase Checkout

**Feature**: 049 Regular Purchase Checkout
**Date**: 2026-09-03
**Status**: Complete; all decisions resolved for planning

## Existing-system findings

- Cart currently owns durable saved intent and enriches display data through a Cart-only Product
  endpoint. It has no stock, Order, Payment, Kafka, or checkout ownership.
- Inventory stores durable `on_hand_quantity` and campaign allocations in PostgreSQL. It has no
  regular-stock hold model and currently has no Kafka runtime dependency.
- Order currently creates a one-line Flash Sale Order from `PurchaseAcceptedV1`; its database
  already has an `order_lines` table and its `OrderCreatedV1` contract already models an item array,
  but the Java aggregate enforces one line and the Order/Saga columns require Flash Sale IDs.
- Order already owns the durable Purchase Saga, transactional outbox, participant inbox, Payment
  deadline calculation, Payment result handling, late-success/manual-review behavior, and terminal
  Order transitions.
- `PaymentRequestedV1` contains only Order, shopper, amount, currency, and deadline. It does not
  contain campaign or reservation fields, so it can be reused without semantic distortion.
- Existing Order lifecycle V1 facts require Flash Sale `campaignId`/`reservationId`; regular
  purchase facts therefore need additive V2 records rather than fake UUIDs or nullable violations.
- API Gateway already routes `/api/v1/orders/**`; the new public endpoints need no new external
  service route, only security/CORS regression coverage.

## Decision 1 — Order remains the workflow owner

**Decision**: Implement Buy Now and Cart checkout as an Order-owned `regularpurchase` feature.

**Rationale**: ADR 0018 already assigns payment intent, deadline, recovery, and terminal workflow
decisions to Order. Moving orchestration to Cart would make mutable saved intent responsible for
Product, stock, Order, and Payment coordination and would create a second Saga owner.

**Alternatives rejected**:

- Cart calls Product, Inventory, Order, and Payment: violates the approved Cart boundary.
- Gateway orchestrates checkout: makes an edge router own durable business state.
- New Checkout service: changes service decomposition without sufficient value for this scope.
- Browser calls each service: exposes internal APIs and cannot provide durable recovery.

## Decision 2 — HTTP before acceptance, Kafka after acceptance

**Decision**: Use bounded synchronous HTTP for Cart snapshot, Product quote, and Inventory hold
creation; use Kafka outbox/inbox for Payment and post-acceptance participant finalization.

**Rationale**: The public caller needs an accepted/rejected answer within five seconds. Cart/price
validation and all-or-nothing stock acquisition are decision prerequisites, so asynchronous-only
coordination would require a new pending-submission API. After acceptance, provider and participant
work is long-running and at-least-once; durable commands/facts match the existing Saga.

**Rules**:

- Order never holds a local database transaction across an HTTP call.
- HTTP clients have bounded connect/read timeouts and no generic retry of non-idempotent operations.
- Inventory hold creation is explicitly idempotent; retry uses the same purchase request ID and
  fingerprint.
- A dependency timeout returns a retryable service-unavailable response, not a partial Order.

**Alternatives rejected**:

- Kafka for initial Cart/Product validation: cannot meet the simple synchronous acceptance contract.
- Synchronous Payment or hold finalization: couples provider latency/availability to Order request.
- Distributed transaction/2PC: unavailable across Kafka, PostgreSQL, and Stripe.

## Decision 3 — Durable intake checkpoints close the HTTP crash gap

**Decision**: Order persists a `regular_purchase_requests` intake row before downstream work. It is
unique by `(shopper_id, idempotency_key)` and stores canonical payload fingerprint, stable generated
IDs, phase, response/rejection metadata, and timing.

**Rationale**: A request can fail after Inventory commits a hold but before Order commits. A purely
in-memory orchestration would lose the hold identity, and reissuing could reserve stock twice. The
intake row makes retry resumable without holding a DB transaction during network calls.

**Canonical identity rules**:

- Buy Now canonical content: source, variant ID, quantity, expected unit price, currency.
- Cart canonical content: source, Cart ID/version, and items sorted by variant ID containing
  variant ID, quantity, item version, expected unit price, and currency.
- Same shopper/key/fingerprint returns or resumes the original result.
- Same shopper/key/different fingerprint returns `409 IDEMPOTENCY_KEY_REUSED`.
- Another shopper may independently use the same opaque key.

**Recovery**:

- Stable `purchaseRequestId`, proposed `orderId`, and proposed `holdId` are allocated once.
- Inventory creates/replays the same hold by `purchaseRequestId` and hold fingerprint.
- An orphan hold expires after five minutes even if Order never completes acceptance.
- A bounded recovery worker may resume non-terminal intake rows; it uses the same use case and IDs.

## Decision 4 — Product provides a dedicated purchase quote

**Decision**: Add an Order-only batch endpoint for authoritative purchase quotes instead of reusing
or broadening Cart's display-detail endpoint.

**Rationale**: Cart display data is explicitly not a price guarantee and its existing machine-token
contract is scoped to `cart-service`. Checkout needs a Product-owned decision contract with current
sellability, product/variant identity, SKU, price, currency, and version. A separate route and scope
preserve least privilege and make semantic use clear.

**Price rule**: Order compares every quote against the shopper-confirmed expected price/currency.
Any mismatch rejects the entire request before Order/Payment creation. Current Product prices are
returned in the rejection so the frontend can show them and require explicit resubmission.

## Decision 5 — Inventory owns one atomic multi-item hold

**Decision**: One regular checkout creates one Inventory hold aggregate containing all requested
lines in one transaction. Inventory locks matching `inventory_items` in ascending `variant_id`
order, verifies availability for every line, then records all hold items or none.

**Availability equation**:

```text
regularAvailable = onHandQuantity - campaignAllocatedQuantity - activeRegularHeldQuantity
```

Active held quantity is derived from durable unexpired `HELD` hold items. Confirmation changes the
hold to `CONFIRMED` and deducts `on_hand_quantity` once; release/expiry changes status without an
on-hand deduction. This keeps the existing meaning of `on_hand_quantity` and campaign allocation
constraints intact.

**Concurrency**:

- Sort variant IDs before acquiring pessimistic row locks to avoid inconsistent lock order.
- Verify the complete set and aggregate duplicate variants before any write.
- Unique `(hold_id, variant_id)` and unique `purchase_request_id` prevent duplicates.
- Same request/fingerprint replays the original hold; conflicting fingerprint is rejected.
- Confirmation/release/expiry are monotonic, idempotent state transitions with movement evidence.

**Alternatives rejected**:

- One HTTP hold per Cart line: can leave partial holds and requires compensation during acceptance.
- Redis Lua for normal purchases: Redis is reserved for Flash Sale admission and is not durable
  regular-stock truth.
- Deduct on-hand during hold then add it back: obscures physical stock semantics and increases
  compensating writes.

## Decision 6 — Generalize Order and Saga additively

**Decision**: Add `purchase_source` (`FLASH_SALE`, `BUY_NOW`, `CART`) and generic participant fields
to Order/Saga, generalize the domain to multiple lines, and retain legacy Flash Sale columns during
the compatibility window.

**Rationale**: Filling `campaign_id` with a zero UUID or calling an Inventory hold a Flash Sale
reservation would create false domain data. Dropping/renaming existing columns would break rollout
and rollback. Additive fields with backfill allow old data to retain its meaning.

**Migration direction**:

- Add and backfill generic `stock_participant_type` and `stock_reference_id` from legacy
  `reservation_id`; backfill `purchase_source=FLASH_SALE`.
- Add nullable Cart snapshot identity for Cart purchases only.
- Relax legacy Flash Sale-only null constraints only after backfill and under new cross-field check
  constraints.
- Keep old columns/indexes during this feature; no contract migration deletes historical fields.
- The feature flag stays disabled until the upgraded code is the only writer of regular rows.

## Decision 7 — Reuse Payment; version regular Order facts

**Decision**: Reuse `PaymentRequestedV1`, `PaymentSucceededV1`, `PaymentFailedV1`, Checkout API,
Stripe webhook, and Payment database unchanged. Publish additive `OrderCreatedV2`,
`OrderConfirmedV2`, `OrderCancelledV2`, and `OrderExpiredV2` for regular Orders while existing Flash
Sale flows continue publishing V1.

**Rationale**: Payment already receives every fact it needs and must not learn stock-source details.
Existing Order V1 facts require campaign/reservation data and cannot truthfully represent regular
purchases. TopicRecordNameStrategy permits separately versioned record-name subjects on the same
topic without reinterpreting V1.

**Compatibility**:

- Preserve V1 schemas and producers for `FLASH_SALE` Orders.
- Register V2 subjects with BACKWARD_TRANSITIVE compatibility before enabling their producer.
- Consumers declare supported record types explicitly and send unsupported records to their own
  operational DLT; no silent cast/fallback.

## Decision 8 — Regular hold commands and outcomes

**Decision**: Order sends confirm/release commands on
`flashsale.inventory.regular-hold.commands.v1`; Inventory publishes confirmed/released/expired facts
on `flashsale.inventory.regular-hold.events.v1`. Every record is keyed by `orderId`.

**Rationale**: These are durable post-payment Saga effects. Stable `orderId` keying preserves one
purchase's sequence. Inventory owns the outcome fact and uses a command inbox plus outbox.

**Failure policy**:

- Same command ID/same fingerprint replays one result; conflicting reuse is non-retryable.
- Temporary database/broker errors retry with bounded backoff.
- Malformed, wrong-version, wrong-producer, or identity-conflict records go to the consumer-specific
  DLT with sanitized diagnostics.
- A higher-version verified Payment success can supersede an earlier failure. If the hold is still
  confirmable, forward recovery completes it; otherwise the existing manual-review rule applies.

## Decision 9 — Cart cleanup is conditional and asynchronous

**Decision**: Cart assigns monotonically increasing Cart and item revisions. A confirmed Cart Order
emits `ReconcilePurchasedCartSnapshotV1`, keyed by `cartId`. Cart removes an item only when cart
owner, variant ID, submitted quantity, and exact item revision still match.

**Rationale**: Comparing only variant and quantity is insufficient: a shopper could edit away and
then back to the same quantity while payment is pending. Exact item revision proves the row is the
same saved intent observed at checkout. Keying by Cart ID serializes cleanup with other cleanup
commands for that Cart; database locking handles concurrent HTTP edits.

**Lifecycle**:

- No cleanup is emitted for Buy Now or unpaid/expired regular purchases.
- Cleanup is emitted only after Inventory confirmation and Order confirmation are durable.
- Cart transaction stores inbox receipt and conditional mutations atomically.
- Later edits win and remain visible. Missing/nonmatching entries count as safe no-op, not failure.

## Decision 10 — Authentication and secret boundaries

**Decision**: Provision one `order-service` OAuth2 client with only:

- `cart.checkout-snapshot.read`
- `catalog.purchase-quote.read`
- `inventory.regular-hold.write`

Order stores its client secret only in ignored local environment/Kubernetes Secret boundaries.
Product, Cart, and Inventory validate exact `sub=order-service`, internal audience, token type, and
route-specific scope. Existing Cart and Campaign clients are not broadened.

## Decision 11 — Observability without sensitive payloads

**Decision**: Add counters/timers/gauges for intake outcome/latency, price and stock rejection,
active/expired holds, participant transition/retry/DLT, outbox lag, Cart reconciliation applied/no-op,
and recovery/manual-review. Propagate W3C trace context through HTTP and outbox payload metadata.

Labels must use bounded enums only. They must not contain shopper ID, Cart ID, order ID, variant ID,
idempotency key, price, secret, provider payload, or Checkout URL. Diagnostics may log stable trace
and internal event IDs according to existing sanitization policy.

## Decision 12 — Rollout and rollback are expand-first

**Decision**: Provision contracts and run migrations before enabling producers. Deploy consumers
before Order intake. Keep regular intake disabled by default and enable it through reviewed GitOps
only after all affected workloads are healthy.

**Rollback boundary**:

- Disable new intake first.
- Drain or record accepted Saga/outbox/hold work.
- Restore only images proven able to read every persisted enum/column value.
- Do not reverse Liquibase changes, delete regular Orders/holds, shrink constraints, or remove
  Kafka subjects/topics.
- After the first real regular Order exists, an older Flash-Sale-only Order image is not considered
  compatible; use a forward fix unless a compatibility rehearsal proves otherwise.

## Resolved unknowns

There are no remaining technical `NEEDS CLARIFICATION` items. Task generation may expose sizing or
file-level refinements, but it may not change the accepted financial, stock, idempotency, Cart
cleanup, deadline, or service-ownership rules without returning to the specification.
