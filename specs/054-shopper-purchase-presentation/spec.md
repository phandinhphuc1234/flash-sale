# Feature Specification: Shopper Purchase Presentation

**Feature Branch**: `codex/shopper-purchase-presentation`

**Created**: 2026-09-23

**Status**: Approved — owner confirmed scope, both Order-name decisions, plan, and tasks on 2026-09-23

**Input**: User selected item 4 of the storefront review: show shopper-friendly product and order names, readable states, truthful Flash Sale labels, and the price of the selected variant.

## Problem and Scope

The storefront exposes technical identifiers and raw state names to shoppers. The product card shows the first variant's price even when a product has multiple variants. Every product detail page claims to be Flash-sale ready, although the current public contract cannot establish campaign membership. These presentation issues make the otherwise implemented purchase flows harder to understand and can mislead a shopper.

### In Scope

- Human-readable product/variant identification in the owner-only Order detail view using Order-owned names captured at creation.
- Readable, accurate Order, Payment, and Reservation state labels and next-step guidance; canonical backend states remain unchanged.
- Product-list prices that do not present one arbitrary variant's price as the price of every variant.
- Remove an unverified Flash Sale badge from product details. Keep the existing campaign-ID reservation path visibly identified as a demo/manual path until a separate public campaign-discovery feature is approved.
- Preserve existing cart, Buy Now, Flash Sale reservation, Order, and payment behavior.

### Out of Scope

- Public campaign discovery, storefront sale scheduling, or changing campaign membership rules.
- Price calculation, discounts, stock checks, payment deadlines, order transitions, or idempotency changes.
- Wishlist/reviews, delivery fulfillment, notification service, or marketplace support.
- New shopper access to internal/admin Product endpoints.

## Clarifications

### Session 2026-09-23

- Q: Which product and variant names should an Order display after the catalog changes? → A: Capture the names when a new Order is created; older Orders without captured names show a neutral label and identifying code, never a guessed historical name.
- Q: How should a Flash Sale Order capture names when its PurchaseAccepted event has only `variantId`? → A: Try Product at Order creation; if unavailable, create the Order without names and show a neutral label. Never block Order saga creation for display data.

## Baseline References

- `flash-sale frontend/QuickCart/components/ProductCard.jsx`: first-variant price and fixed 4.5 rating.
- `flash-sale frontend/QuickCart/app/product/[id]/page.jsx`: selected variant price, universal Flash-sale-ready badge, and manual campaign ID input.
- `flash-sale frontend/QuickCart/app/orders/[id]/page.jsx`: Order line renders `variantId` and raw state names.
- `services/order-service/src/main/java/com/philia/flashsale/order/order/adapter/in/web/response/OrderItemResponse.java`: public Order line currently contains identifiers, quantity, and money, not names.
- `services/order-service/src/main/java/com/philia/flashsale/order/order/domain/model/OrderLine.java`: durable Order line currently has no product or variant display names.
- `specs/053-catalog-discovery/spec.md`: explicitly excludes replacement of product detail, checkout, and cart flows.

## Requirement Delta

### MODIFIED

- **Before**: Order detail shows `variantId` as the primary line label. **After**: a shopper sees a meaningful product and variant label where verified name data exists; older or unavailable names use a neutral, explicit fallback rather than an invented name.
- **Before**: raw machine states are displayed to shoppers. **After**: shopper-facing text explains the state and next available action without changing the underlying state.
- **Before**: product cards display only the first variant's base price. **After**: multi-variant products use an unambiguous starting-price label; selected-variant details continue showing the selected variant's authoritative catalog price.
- **Before**: every product detail shows `Flash-sale ready`. **After**: the product page does not imply sale eligibility that the public API has not established.

## User Scenarios & Testing

### User Story 1 - Understand a purchased item (Priority: P1)

As a shopper, I want to recognize the item in my Order detail even when I do not know its technical variant identifier.

**Why this priority**: It is the most misleading part of the current Order page and remains important after a product changes or disappears from the public catalog.

**Independent Test**: Open an owned Order containing a known product and compare its displayed line with the purchased product/variant. Open an older Order without a verified name and verify the fallback does not fabricate one.

**Acceptance Scenarios**:

1. **Given** an owned Order line with recorded product and variant names, **when** the shopper opens the Order, **then** those names appear as the primary line label and the quantity/line amount are unchanged.
2. **Given** an Order line without a recorded name, **when** the shopper opens the Order, **then** it shows a neutral item label and keeps an identifying code available as secondary information.
3. **Given** a shopper who does not own an Order, **when** they request it, **then** existing owner-only access is preserved.
4. **Given** a Flash Sale Order creation and Product is unavailable, **when** the accepted purchase is consumed, **then** Order creation and saga processing continue with absent names; the shopper sees a neutral item label.
5. **Given** a successfully captured name and a later rename, **when** the shopper reopens the Order, **then** the saved name remains unchanged.

### User Story 2 - Understand purchase progress (Priority: P1)

As a shopper, I want plain-language status and the next action so that asynchronous payment and reservation processing do not look like a failure.

**Why this priority**: Order, Payment, and Reservation finish at different times.

**Independent Test**: Render representative pending, confirmed, failed, cancelled, expired, and reservation states and verify labels and guidance without changing backend values or enabling an invalid payment action.

**Acceptance Scenarios**:

1. **Given** a pending Order or Payment, **when** the page renders, **then** the shopper sees a readable label and whether to pay, wait, or refresh.
2. **Given** a terminal Order or Reservation, **when** the page renders, **then** the message does not promise payment or reservation is still possible.
3. **Given** a state unknown to the current frontend, **when** the page renders, **then** it uses a neutral fallback without incorrectly calling the purchase successful.

### User Story 3 - See truthful offer pricing (Priority: P2)

As a shopper, I want the product card and detail page to describe the available prices and sale eligibility truthfully.

**Why this priority**: Misleading price or sale badges damage trust before checkout.

**Independent Test**: Display a product with one variant, multiple differently priced variants, and no usable variant; compare list and selected-detail labels. Open a product without public campaign data and verify no sale-eligibility claim appears.

**Acceptance Scenarios**:

1. **Given** multiple available variants with different catalog prices, **when** a product card renders, **then** it identifies its displayed minimum as a starting price, not the selected or final checkout price.
2. **Given** a shopper selects a variant, **when** product details render, **then** the visible price corresponds to that variant.
3. **Given** no verified public campaign association, **when** product details render, **then** no Flash Sale eligibility badge is shown; the existing manual campaign-ID reservation path remains explicitly identified as a demo/operator-assisted path.

### Edge Cases

- An Order line refers to a product that has since been renamed, unpublished, or archived.
- An old Order predates any newly recorded names.
- Product is unavailable during Flash Sale Order creation, or returns no trustworthy name.
- A PurchaseAccepted event is delivered again after Order creation; captured names must not be overwritten by a later catalog value.
- A product has zero active variants or a variant without a displayable price.
- Backend adds a new status unknown to the current frontend.
- A shopper returns from Stripe before Payment and Order have converged.

## Requirements

### Functional Requirements

- **FR-001**: The Order detail MUST prefer verified product and variant names for an owned line and MUST NOT infer a historical purchased name from the current public catalog.
- **FR-002**: A new Order MUST retain available product and variant display names captured at Order creation; later catalog renames, unpublishing, or deletion MUST NOT change those captured names.
- **FR-003**: Older Orders with no verified name MUST remain readable without a destructive backfill or a fabricated historical name; the Order API MUST allow absent names for those lines.
- **FR-004**: Shopper-facing state text MUST be readable and preserve the canonical Order, Payment, and Reservation state meanings and permitted actions.
- **FR-005**: Product list cards MUST distinguish a starting price from a selected variant price when variants differ; checkout still uses the authoritative server result.
- **FR-006**: Product detail MUST display the currently selected variant's catalog price and MUST NOT show an unverified Flash Sale eligibility badge.
- **FR-007**: The manual campaign-ID reservation control MUST be clearly marked as a demo/manual path until public campaign discovery is separately approved; no reservation request or payload is changed here.
- **FR-008**: No new public/internal authorization route may be introduced solely to let browser code read technical Product data.
- **FR-009**: For Flash Sale Order creation, Order MUST attempt to obtain Product-owned display names using the existing internal Product contract. A lookup failure or missing trustworthy name MUST NOT block Order creation, saga progression, or payment; the line remains unnamed and the UI uses the neutral fallback.
- **FR-010**: Replayed purchase events and idempotent regular-purchase requests MUST preserve the originally stored name snapshot, including an absent snapshot, rather than replacing it with a later catalog value.

### Key Entities

- **Order line display identity**: Shopper-readable identification of what was purchased, separate from the line's immutable quantity and money snapshot.
- **Catalog variant price**: Display price for a specific sellable variant; not the final accepted Order price.
- **Purchase state**: Existing Order, Payment, and Reservation lifecycle states, presented without redefining them.

## Success Criteria

### Measurable Outcomes

- **SC-001**: In the defined test fixtures, every named new Order line displays the purchased product and variant names and no Order line uses a UUID as its primary label.
- **SC-002**: All documented Order, Payment, and Reservation states in scope have reviewed shopper-facing text; an unknown state never appears as a false success.
- **SC-003**: Multi-variant test products display a starting-price label on list cards, and changing the selected variant updates the product-detail price in every tested case.
- **SC-004**: No product detail shows an unverified Flash Sale eligibility claim in reviewed desktop and mobile views.

## Dependencies and Compatibility

- The historical-name decision requires an additive Order read contract and Order-owned durable fields. Existing clients must continue to accept their current response fields.
- The regular Buy Now/Cart Product quote already includes product and variant names; the Flash Sale `PurchaseAcceptedV1` event contains only `variantId`, so the chosen approach reuses the internal Product contract on the Order side without changing Kafka payloads.
- No Kafka schema, Product ownership, Order amount, or payment behavior may change as a side effect of presentation work.

## Assumptions

- The current storefront language is English; this feature improves wording in that language rather than introducing localization.
- A missing historical display name should be explicit, not silently replaced with the current catalog name.
- Public campaign discovery remains a separate feature; the manual reservation path is retained for demonstrations.

## Constitutional Constraints

- **Service ownership**: Order owns its Order-line history; Product owns current catalog data. Neither reads the other's database.
- **External ingress**: Existing Gateway routes remain the only browser boundary.
- **API/event contracts**: An Order response addition requires contract-first documentation and compatibility tests; no Kafka event change is assumed.
- **Durable and hot-path data**: Only the Order-name decision may add Order-owned durable data. No Redis Lua or stock behavior changes.
- **Messaging reliability**: Existing outbox, Kafka event, and replay semantics remain unchanged; name lookup failure cannot prevent or duplicate Order creation.
- **Root infrastructure ownership**: No shared infrastructure change is expected; any migration remains Order-owned.
- **Observability**: Existing trace IDs, health, readiness, and Prometheus setup remain unchanged.
- **Verification**: Frontend component/build/browser checks apply; an Order contract/migration path additionally requires Order module and compatibility validation. Load tests are not applicable because purchase traffic behavior is unchanged.
- **Architecture decisions**: No service boundary change is intended; an ADR is needed only if the approved plan reveals a broader ownership change.

## Approval and History

- 2026-09-23 — Project owner selected item 4 of the storefront review as the next implementation scope.
- 2026-09-23 — Project owner selected immutable Order-owned product/variant names for new Orders and neutral fallback for older unnamed Orders.
- 2026-09-23 — Project owner selected best-effort Product lookup for Flash Sale Order names, with neutral fallback and no saga blockage. These decisions approve the behavior for planning; plan/tasks still require review before production changes.
