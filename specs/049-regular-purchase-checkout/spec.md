# Feature Specification: Regular Purchase Checkout

**Feature Branch**: `codex/regular-purchase-checkout`

**Created**: 2026-09-03

**Status**: Approved for planning — owner decisions A/A/A/A/A recorded 2026-09-03

**Input**: User description: "Allow shoppers to buy a normal product immediately or checkout their Cart, then continue through the existing Order and Stripe Payment flow without changing the flash-sale purchase flow."

## Problem and Scope

### Problem Statement

The platform can complete a flash-sale reservation through Order and Payment, and it can preserve a
shopper's normal product intent in Cart. It cannot yet turn a normal product or the current Cart into
an Order. This leaves shoppers unable to buy products outside a flash-sale campaign and leaves the
frontend without a supported Cart checkout action.

### In Scope

- Let an authenticated shopper start a normal purchase for one currently sellable variant without
  first adding it to Cart ("Buy Now").
- Let an authenticated shopper submit the current Cart for regular checkout.
- Revalidate Product-owned eligibility and authoritative price before accepting a regular purchase.
- Reserve, confirm, or release Inventory-owned regular stock as the Order and Payment lifecycle
  advances.
- Reuse the existing Order-owned purchase workflow and Stripe-hosted payment experience after a
  regular Order is accepted.
- Make duplicate submissions, delayed outcomes, failures, and Cart changes safe and observable.
- Preserve strict ownership so a shopper can purchase only from their own Cart and see only their
  own resulting Orders and Payments.

### Out of Scope

- Changing flash-sale admission, quota, campaign price, or Redis hot-path behavior.
- Guest checkout, saved addresses, shipping calculation, tax calculation, coupons, wishlists, or
  seller settlement.
- Multiple currencies in one checkout, split tender, alternative payment providers, refunds, or
  automatic late-payment refunds.
- Notification delivery or Notification Service work.
- Direct browser calls to internal service operations or direct access to another service's data.

### Non-goals

- Cart does not become the owner of price, stock, Order, Payment, or checkout orchestration.
- Adding an item to Cart does not reserve stock or guarantee a displayed price.
- Buy Now does not silently add the chosen variant to Cart.
- This feature does not replace or merge the existing flash-sale purchase entry point.

## Clarifications

### Session 2026-09-03

- Q: How should Cart checkout behave when any submitted item is invalid, unavailable, or lacks
  sufficient stock? → A: Use all-or-nothing checkout; reject the whole submission, keep Cart
  unchanged, and create no Order or Payment.
- Q: How should accepted items be grouped into Orders and Payments? → A: One Buy Now or Cart
  checkout submission creates one Order and one Payment; accepted variants are line items in that
  Order.
- Q: What happens when an authoritative Product price differs from the price most recently
  confirmed by the shopper? → A: Reject checkout before creating an Order, Payment, or lasting
  stock hold; return the current price so the shopper can review and resubmit.
- Q: How should Cart edits and cleanup behave while a submitted checkout is awaiting payment? → A:
  Keep Cart editable, reconcile against an immutable checkout snapshot only after confirmed
  payment, remove only entries whose variant and quantity still match, and preserve every later
  edit; an unpaid outcome leaves Cart unchanged.
- Q: Which regular stock-hold duration and authenticated purchase-key policy should apply? → A:
  Hold stock for 5 minutes, set the Payment deadline 30 seconds earlier, scope each client-supplied
  idempotency key to the authenticated shopper, replay the original result for the same payload,
  and reject reuse of the key with a different payload.

### Session 2026-09-04

- Q: How many distinct Cart lines may one regular checkout submit? → A: A regular checkout and its
  Product purchase-quote batch may contain at most 20 distinct variant lines. This bounds
  synchronous decision work while preserving the existing per-variant quantity limit of 10.

## Baseline References

- `specs/048-cart-mvp/spec.md` — Cart is authenticated saved intent and explicitly has no checkout,
  stock reservation, Order, or Payment behavior.
- `specs/044-order-purchase-saga/spec.md` — Order owns durable purchase orchestration and Payment
  outcome handling.
- `docs/adr/0018-order-owned-purchase-saga-payment-contracts.md` — accepted Order-owned Saga and
  Payment contract ownership.

## Requirement Delta

### ADDED

- A normal-product Buy Now entry point.
- A Cart checkout entry point that captures the shopper's current intent for purchase processing.
- A regular-stock lifecycle linked to the accepted Order and its Payment outcome.
- Safe Cart reconciliation after a submitted Cart purchase reaches its approved lifecycle point.

### MODIFIED

- **Before**: Cart can save and display product intent but cannot start an Order or Payment.
- **After**: an explicit checkout action may use a Cart snapshot to start an Order-owned regular
  purchase; ordinary Cart mutations still have no stock, Order, or Payment side effects.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Buy One Normal Product Now (Priority: P1)

As an authenticated shopper, I want to buy one normal product variant immediately so that I can
purchase it without waiting for a flash-sale campaign or managing a Cart first.

**Why this priority**: It is the smallest independently useful normal-purchase path and proves that
Product, Inventory, Order, and Payment responsibilities work outside the flash-sale path.

**Independent Test**: Select one sellable variant with available regular stock, submit Buy Now,
complete the hosted test payment, and verify exactly one confirmed Order and one confirmed stock
deduction for the authenticated shopper while Cart remains unchanged.

**Acceptance Scenarios**:

1. **Given** an authenticated shopper and a sellable variant with sufficient regular stock,
   **When** the shopper submits Buy Now and completes payment, **Then** one Order is confirmed for
   the authoritative amount and the reserved stock is confirmed exactly once.
2. **Given** the same shopper repeats the same Buy Now submission, **When** the original attempt has
   already been accepted, **Then** the shopper receives the original purchase result and no second
   Order, Payment, or stock deduction is created.
3. **Given** the variant is not sellable or lacks sufficient regular stock, **When** Buy Now is
   submitted, **Then** no Order or Payment is created and Cart is not changed.

---

### User Story 2 - Checkout My Current Cart (Priority: P1)

As an authenticated shopper, I want to checkout the products currently saved in my Cart so that I
can pay for a normal multi-item purchase through one deliberate action.

**Why this priority**: Cart is incomplete as a shopper journey until its saved intent can enter the
normal Order and Payment workflow.

**Independent Test**: Save at least two sellable variants in one shopper's Cart, submit checkout,
complete payment, and verify the accepted items, quantities, amount, stock outcomes, Order, Payment,
and Cart reconciliation without exposing or changing another shopper's Cart.

**Acceptance Scenarios**:

1. **Given** an authenticated shopper has multiple valid Cart items, **When** checkout is accepted
   and payment succeeds, **Then** exactly one Order and one Payment contain the approved item set
   and quantities at authoritative prices, and each stock reservation is confirmed exactly once.
2. **Given** another shopper owns a different Cart, **When** the first shopper checks out, **Then**
   the second shopper's Cart and purchases remain unchanged and undisclosed.
3. **Given** the Cart is empty, **When** checkout is requested, **Then** no Order, Payment, or stock
   reservation is created and the shopper receives a clear rejection.
4. **Given** a shopper changes the Cart while an earlier checkout is being processed, **When** the
   earlier purchase is confirmed, **Then** cleanup removes only entries whose variant and quantity
   still match the submitted snapshot and preserves every later Cart change.
5. **Given** any submitted Cart item is invalid, unavailable, or lacks sufficient stock, **When**
   checkout is evaluated, **Then** the entire checkout is rejected, the Cart remains unchanged, and
   no Order, Payment, or lasting stock hold is created.
6. **Given** an item's authoritative price differs from the price most recently confirmed by the
   shopper, **When** checkout is evaluated, **Then** the entire checkout is rejected before Order or
   Payment creation, current pricing is returned for review, and the shopper must explicitly
   resubmit.

---

### User Story 3 - Recover Safely Across Payment Outcomes (Priority: P1)

As a shopper, I need an accepted regular purchase to converge to a consistent Order, Payment, stock,
and Cart result despite duplicate messages, timeouts, or a terminal payment outcome.

**Why this priority**: Normal checkout touches money and stock; a happy-path-only implementation
could charge without confirming an Order, oversell stock, or remove the wrong Cart items.

**Independent Test**: Exercise successful payment, terminal payment failure, expiry, duplicate
submission, duplicate outcome delivery, and delayed verified success, then verify every flow
converges without duplicate charge intent, duplicate stock effects, or destructive Cart cleanup.

**Acceptance Scenarios**:

1. **Given** an accepted regular purchase, **When** payment reaches a terminal unpaid outcome,
   **Then** all associated regular stock is released exactly once and the Order exposes the approved
   unpaid terminal result.
2. **Given** any purchase or payment outcome is delivered more than once, **When** it is replayed,
   **Then** no duplicate Order, Payment, stock effect, or Cart cleanup occurs.
3. **Given** a verified success arrives after an earlier unpaid result, **When** recovery is allowed
   by the existing Order-owned Saga policy, **Then** the system follows that policy without silently
   inventing a refund or contradicting durable payment evidence.
4. **Given** a downstream dependency is temporarily unavailable, **When** processing is retried,
   **Then** accepted durable facts remain recoverable and the shopper never receives a false
   confirmed result.
5. **Given** an accepted regular purchase remains unpaid at its 4-minute-30-second Payment deadline,
   **When** the 5-minute stock-hold window closes, **Then** no new Payment attempt is accepted and
   the held stock is released exactly once through the approved unpaid lifecycle.

### Edge Cases

- The Cart is empty between display and checkout submission.
- A product becomes unsellable, its authoritative price changes, or stock becomes insufficient
  after the shopper viewed it.
- Two checkout submissions race for the same Cart state.
- The shopper edits quantity, removes an item, or re-adds a variant after submitting checkout.
- Buy Now and Cart checkout concurrently request the final available units of one variant.
- A stock reservation succeeds for only part of a multi-item request before another dependency
  fails.
- Payment succeeds while stock confirmation or Cart reconciliation is temporarily unavailable.
- The same client submission or asynchronous outcome is replayed after a timeout.
- A caller tries to reference another shopper's Cart, Order, or Payment.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow an authenticated shopper to submit Buy Now for one normal,
  currently sellable variant and a permitted positive quantity.
- **FR-002**: The system MUST allow an authenticated shopper to submit their non-empty Cart as a
  regular purchase request.
- **FR-003**: Every purchase request MUST derive the shopper identity from the authenticated
  identity and MUST NOT accept a caller-selected Cart owner.
- **FR-004**: Buy Now MUST leave the shopper's Cart unchanged.
- **FR-005**: Ordinary Cart add, update, remove, read, and clear operations MUST remain saved-intent
  operations and MUST NOT reserve stock, create an Order, or create a Payment.
- **FR-006**: Cart checkout MUST be all-or-nothing. If any submitted item is invalid, unavailable,
  or lacks sufficient stock, the entire checkout MUST be rejected, the Cart MUST remain unchanged,
  and no Order, Payment, or lasting stock hold may remain.
- **FR-007**: Each accepted Buy Now or Cart checkout submission MUST create exactly one Order and
  one Payment. A multi-item Cart checkout MUST represent its accepted variants and quantities as
  line items within that single Order.
- **FR-008**: The system MUST use current Product-owned sellability, authoritative price, currency,
  and product identity when deciding whether to accept a normal purchase.
- **FR-009**: Cart display values MUST NOT be treated as a price guarantee or the source of the
  charged amount. If any authoritative price differs from the price most recently confirmed by the
  shopper, the entire checkout MUST be rejected before Order or Payment creation and MUST return
  current pricing for explicit review and resubmission.
- **FR-010**: The system MUST reserve sufficient Inventory-owned regular stock before exposing an
  accepted Order for payment and MUST NOT use the flash-sale stock path for a regular purchase.
- **FR-011**: Competing regular purchases MUST NOT confirm more stock than the durable available
  quantity.
- **FR-012**: Order MUST remain the durable owner of purchase workflow state and Payment intent;
  Cart MUST NOT orchestrate Product, Inventory, Order, or Payment steps.
- **FR-013**: A retry of the same authenticated purchase submission MUST return the original
  purchase result and MUST NOT create duplicate Orders, Payments, or stock effects.
- **FR-014**: Duplicate or reordered downstream outcomes MUST be processed without duplicate or
  contradictory business effects.
- **FR-015**: Cart MUST remain editable while a submitted checkout is awaiting payment. Checkout
  MUST retain an immutable Cart snapshot, and confirmed-payment cleanup MUST remove only entries
  whose variant and quantity still match that snapshot. Later edits MUST be preserved, and an unpaid
  outcome MUST leave Cart unchanged.
- **FR-016**: A terminal unpaid outcome MUST release every stock hold associated with that purchase
  exactly once.
- **FR-017**: A verified successful payment MUST confirm the Order and its stock outcome according
  to the existing success-dominant Order Saga policy, including recoverable handling when a later
  step is temporarily unavailable.
- **FR-018**: The system MUST expose a shopper-safe status for an accepted regular purchase without
  exposing internal provider, retry, or infrastructure details.
- **FR-019**: Important regular purchase requests and outcomes MUST retain one trace identity across
  the public request and all downstream processing.
- **FR-020**: The existing flash-sale purchase entry point, campaign pricing, reservation rules, and
  tested Payment behavior MUST remain backward compatible.
- **FR-021**: A regular stock hold MUST expire 5 minutes after purchase acceptance, and the Payment
  deadline MUST be exactly 30 seconds earlier than that expiry.
- **FR-022**: Every Buy Now and Cart checkout submission MUST include a client-supplied idempotency
  key scoped to the authenticated shopper. Reusing the same key with the same canonical purchase
  content MUST return the original result; reusing it with different content MUST be rejected as a
  conflict without creating another business effect.

### Non-Functional Requirements

- **NFR-REL-001**: No single retry, duplicate delivery, or transient dependency outage may create
  more than one business effect for the same accepted purchase action.
- **NFR-SEC-001**: No response, log, metric, or trace may expose another shopper's Cart or purchase
  details, credentials, tokens, payment secrets, or raw provider payloads.
- **NFR-OBS-001**: Operators MUST be able to distinguish validation rejection, stock rejection,
  payment waiting, paid confirmation, unpaid release, and recovery-required outcomes without
  reading secret values.
- **NFR-COMPAT-001**: The old and new application versions MUST be able to coexist during rollout
  without destructive schema changes or reinterpretation of existing Cart, Order, Payment, or
  flash-sale data.

### Key Entities

- **Regular Purchase Request**: One authenticated shopper's explicit intent to buy either one normal
  variant immediately or the item set captured from their Cart; its identity is stable across
  retries.
- **Checkout Snapshot**: The immutable set of variant identities and quantities observed for one
  Cart checkout attempt, plus enough identity to reconcile later Cart changes safely; it does not
  own price or stock facts.
- **Regular Stock Hold**: Inventory-owned temporary claim for one accepted Order item, later
  confirmed or released exactly once.
- **Order**: The durable commercial record and owner of the regular purchase workflow, immutable
  charged amount, lifecycle, and Payment intent.
- **Payment**: The provider-facing payment lifecycle for one immutable Order.
- **Cart Reconciliation**: A conditional cleanup outcome that affects only the submitted shopper's
  matching saved intent at the approved lifecycle point.

### Domain Model Delta

- Add **Regular Purchase** as a purchase source distinct from **Flash-Sale Purchase** while both
  converge on the existing Order-owned Payment lifecycle.
- Add **Checkout Snapshot** so Cart intent used by a purchase can be distinguished from later Cart
  edits.
- Add **Regular Stock Hold**; it is not a flash-sale reservation and does not use campaign quota.
- Preserve **Cart** as mutable shopper intent rather than a price, stock, Order, or Payment owner.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A shopper can start either Buy Now or Cart checkout and receive an accepted purchase
  result or an actionable rejection within 5 seconds under the approved representative test load,
  excluding time spent on the hosted payment page.
- **SC-002**: Successful test payments produce one confirmed Order and exactly one confirmed stock
  deduction for every accepted item in 100% of approved happy-path scenarios.
- **SC-003**: One hundred replays of the same purchase submission and downstream outcomes produce
  one Order, one Payment intent, one stock result per item, and at most one Cart reconciliation.
- **SC-004**: Approved concurrency tests never confirm more units than durable regular stock and
  leave no accepted purchase with an unexplained partial stock outcome.
- **SC-005**: Terminal unpaid scenarios release all associated stock holds and do not remove later
  Cart intent in 100% of approved failure and expiry scenarios.
- **SC-006**: Existing flash-sale purchase, Payment, Cart CRUD, authentication, and public gateway
  regression suites remain green.
- **SC-007**: A frontend implementer can complete Buy Now, Cart checkout, payment redirect, and
  purchase-status handling using documented public contracts without calling an internal route.

## Dependencies and Compatibility

- Existing Authentication establishes shopper identity.
- Existing Product behavior remains authoritative for identity, sellability, price, and currency.
- Existing Inventory remains authoritative for durable regular stock.
- Existing Order-owned Saga and Payment contracts remain the purchase workflow baseline.
- Existing Cart records remain readable during rollout; schema evolution must be additive until old
  application versions are no longer active.
- New or changed public and asynchronous contracts require compatibility review before
  implementation.

## Assumptions

- The first release supports authenticated shoppers only.
- All items accepted into one payment use one supported currency.
- The first release uses the existing hosted Stripe test-mode payment experience.
- A five-minute regular stock hold provides the same bounded purchase window already exercised by
  the existing purchase workflow; Payment stops accepting new attempts 30 seconds before expiry.
- Shipping, tax, discounts, refunds, notifications, and seller-specific fulfillment remain outside
  this feature.
- Product and Inventory capabilities needed for regular checkout may be extended only through
  documented service-owned contracts; their databases are never shared.

## Human Decisions Required

- **HD-001 — Cart acceptance (decided 2026-09-03, Option A)**: Checkout is all-or-nothing. Any
  invalid, unavailable, or insufficient-stock item rejects the entire submission; Cart remains
  unchanged and no Order or Payment is created.
- **HD-002 — Purchase grouping (decided 2026-09-03, Option A)**: Each accepted Buy Now or Cart
  checkout submission creates one Order and one Payment; a Cart's accepted variants are line items
  inside that Order.
- **HD-003A — Price change (decided 2026-09-03, Option A)**: Reject the entire checkout before Order,
  Payment, or lasting stock-hold creation; return current Product-owned pricing for explicit shopper
  review and resubmission.
- **HD-003B — Cart editing and cleanup (decided 2026-09-03, Option A)**: Cart remains editable;
  confirmed-payment cleanup removes only variant-and-quantity entries still matching the immutable
  checkout snapshot, preserves later edits, and does nothing on an unpaid outcome.
- **HD-003C — Hold and idempotency (decided 2026-09-03, Option A)**: Hold regular stock for 5
  minutes, set the Payment deadline 30 seconds earlier, scope each client-supplied idempotency key
  to the authenticated shopper, replay the original result for equivalent content, and reject the
  same key with different content.
- **HD-004 — Cart checkout line cap (decided 2026-09-04)**: One Cart checkout and its Product
  purchase-quote batch may contain at most 20 distinct variant lines. This is separate from the
  existing 1–10 quantity bound for each variant.

| Priority | Why it blocks | Options and trade-offs | Owner | Decision deadline |
|----------|---------------|------------------------|-------|-------------------|
| Decided | Partial acceptance changes stock compensation, totals, frontend confirmation, and test invariants. | Option A selected: all-or-nothing rejection with unchanged Cart and no Order or Payment. | Project owner | Decided 2026-09-03 |
| Decided | Grouping determines Order, Payment, amount, failure, and status semantics. | Option A selected: one submission creates one Order and one Payment, with multiple line items when applicable. | Project owner | Decided 2026-09-03 |
| Decided | Price changes must not silently alter the amount the shopper agreed to pay. | Option A selected: reject before Order/Payment creation and require explicit resubmission against current pricing. | Project owner | Decided 2026-09-03 |
| Decided | Concurrent Cart edits and cleanup must not delete later shopper intent. | Option A selected: immutable snapshot, no Cart lock, match-only cleanup after confirmed payment, and no cleanup for unpaid outcomes. | Project owner | Decided 2026-09-03 |
| Decided | Stock-hold expiry and duplicate-request scope determine availability, payment deadlines, conflict behavior, and recovery tests. | Option A selected: 5-minute hold, 4-minute-30-second Payment deadline, shopper-scoped client key, equivalent replay, and conflicting-payload rejection. | Project owner | Decided 2026-09-03 |
| Decided | The number of lines bounds synchronous Product/Inventory decision work and must agree across Cart checkout contracts. | Project owner selected a maximum of 20 distinct variant lines; each line retains the existing quantity bound of 1–10. | Project owner | Decided 2026-09-04 |

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Cart owns mutable saved intent, Product owns catalog and price, Inventory
  owns regular stock and holds, Order owns purchase workflow and commercial state, and Payment owns
  provider interaction. No service may access another service's database.
- **External ingress**: Shopper traffic enters only through `api-gateway`; internal capabilities are
  not exposed to browsers.
- **API/event contracts**: Every new or changed synchronous and asynchronous boundary must be
  documented and versioned before implementation, with backward compatibility and rollout order.
- **Durable and hot-path data**: PostgreSQL remains the durable truth for regular purchase state and
  regular stock. The flash-sale Redis Lua hot path is unchanged.
- **Messaging reliability**: Durable state followed by required publication uses the approved
  outbox strategy; consumers are idempotent with explicit ordering, deduplication, recovery, and
  dead-letter behavior defined during planning.
- **Root infrastructure ownership**: Shared local or cloud orchestration belongs under root
  `infra/`; service-owned runtime configuration and migrations stay within the owning service.
- **Observability**: Affected services retain liveness, readiness, and Prometheus-compatible
  endpoints through declarative configuration; important requests/events propagate trace identity
  without business code constructing a registry implementation.
- **Verification**: Planning must map unit, integration, HTTP/event contract, migration,
  idempotency, concurrency, end-to-end, rollback, and regression evidence. Load scope may not be
  omitted for stock correctness.
- **Architecture decisions**: ADR 0018 remains authoritative for Order-owned orchestration. Planning
  must determine whether the new regular-stock contract or Cart reconciliation changes an
  architecture boundary enough to require an additional ADR before production code.

## Approval and History

- 2026-09-03 — Draft created from the approved request to add normal Buy Now and Cart checkout.
- 2026-09-03 — HD-001 Option A approved: Cart checkout is all-or-nothing.
- 2026-09-03 — HD-002 Option A approved: one submission creates one Order and one Payment.
- 2026-09-03 — HD-003A Option A approved: authoritative price changes require shopper review and
  resubmission.
- 2026-09-03 — HD-003B Option A approved: Cart remains editable and cleanup preserves later intent.
- 2026-09-03 — HD-003C Option A approved: five-minute stock hold and shopper-scoped idempotency.
- 2026-09-04 — HD-004 approved: Cart checkout and Product purchase-quote batch are limited to 20 distinct variant lines.
- 2026-09-03 — Project owner approved the clarified specification for planning.
- 2026-09-03 — Project owner approved the implementation plan and authorized task generation.
- 2026-09-03 — Project owner approved the generated task ledger and authorized Phase 1 implementation.
