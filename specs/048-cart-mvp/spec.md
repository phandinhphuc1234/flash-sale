# Feature Specification: Authenticated Cart MVP

**Feature Branch**: `codex/cart-mvp`

**Created**: 2026-08-29

**Status**: Approved

**Input**: User description: "Build a basic interview-ready Cart Service in seven days. Cart is available only to authenticated shoppers, obtains current product display information from Product Service, and remains outside the flash-sale reservation hot path."

## Problem and Scope

Authenticated shoppers currently cannot keep a server-side selection of variants before deciding
whether to start the normal purchase or flash-sale reservation flow. The system needs a small Cart
MVP that preserves shopper intent while keeping catalog, price, stock, reservation, order, payment,
and identity ownership in their existing services.

### In Scope

- One cart per authenticated shopper.
- View the current cart.
- Add a variant or replace its desired quantity with one idempotent action.
- Remove one item or clear the whole cart.
- Return current Product-owned display information with cart items.
- Keep ownership isolation so one shopper cannot see or mutate another shopper's cart.
- Return stable, documented success and error contracts through the existing public gateway.

### Out of Scope

- Anonymous or guest carts and login-time cart merging.
- Coupons, wishlists, shipping estimates, tax calculations, and checkout orchestration.
- Reserving inventory, checking flash-sale admission, or guaranteeing displayed price or stock.
- Creating orders or payments directly from the cart.
- Redis caching, cart sharing, item-level notes, and multi-device conflict editing beyond idempotent
  replacement of a requested quantity.
- Cart events or integration with Notification Service.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Maintain My Cart (Priority: P1)

An authenticated shopper adds a catalog variant with a desired quantity, changes that quantity,
and removes the item without creating an order or reserving stock.

**Why this priority**: This is the smallest useful cart journey and supplies the main interview
demonstration of authenticated ownership, persistence, and idempotent mutation.

**Independent Test**: Authenticate as one shopper, set a variant quantity twice with the same
request, change it, remove it, and verify each resulting cart contains exactly the expected item and
quantity with no duplicate line.

**Acceptance Scenarios**:

1. **Given** an authenticated shopper with no cart item for a sellable variant, **When** the shopper
   sets its quantity to an allowed positive value, **Then** the cart contains exactly one item for
   that variant.
2. **Given** the item already exists, **When** the shopper sets a different allowed quantity,
   **Then** the existing item quantity is replaced and no duplicate item is created.
3. **Given** the same valid replacement request is repeated, **When** it is processed again,
   **Then** the observable cart state remains unchanged.
4. **Given** an item exists, **When** the shopper removes it, **Then** it is absent from the cart and
   removing it again leaves the cart unchanged.
5. **Given** a requested variant does not exist or is not currently sellable, **When** the shopper
   attempts to add or update it, **Then** the cart is not changed and the shopper receives a clear
   rejection.

---

### User Story 2 - View Current Product Details (Priority: P2)

An authenticated shopper views the cart and sees current Product-owned information for each saved
variant, rather than a Cart-owned price or catalog snapshot.

**Why this priority**: The frontend needs useful display data, while the project must demonstrate
that Cart does not become the source of truth for catalog or price.

**Independent Test**: Save a variant in the cart, change its Product-owned display information,
view the cart again, and verify the new information is returned without rewriting the shopper's
saved quantity.

**Acceptance Scenarios**:

1. **Given** a cart contains a valid variant, **When** the shopper views the cart, **Then** the item
   includes its saved quantity and current product, variant, price, currency, image, and sellability
   display information when those fields are available from the catalog owner.
2. **Given** Product-owned display information changes after the item was saved, **When** the cart
   is viewed again, **Then** the new display information is shown and the saved quantity is
   unchanged.
3. **Given** a cart item becomes not sellable, **When** the cart is viewed, **Then** the item remains
   visible as saved intent but is clearly marked unavailable and no stock or price guarantee is
   implied.
4. **Given** Product Service cannot provide current display information, **When** the cart is
   viewed, **Then** the saved variant identities and quantities are still returned, each affected
   item is marked `detailsAvailable=false`, and no current product detail is presented as valid.

---

### User Story 3 - Clear and Isolate My Cart (Priority: P3)

An authenticated shopper clears all items and can never access another shopper's cart by changing
request input.

**Why this priority**: Clear-cart is basic usability, and strict ownership is a mandatory security
boundary.

**Independent Test**: Create carts for two shoppers, clear one shopper's cart, and verify the other
cart remains unchanged and cannot be addressed through the first shopper's requests.

**Acceptance Scenarios**:

1. **Given** a shopper has multiple cart items, **When** the shopper clears the cart, **Then** the
   shopper sees an empty cart and repeating clear remains successful.
2. **Given** two authenticated shoppers have different carts, **When** either shopper reads or
   mutates a cart, **Then** only the cart associated with that authenticated identity is affected.
3. **Given** an unauthenticated caller, **When** any cart operation is attempted, **Then** the call
   is rejected without exposing whether a cart exists.

### Edge Cases

- A quantity of zero, a negative quantity, a fractional quantity, or a value outside the allowed
  per-item range does not change the cart.
- Two identical replacement requests arriving concurrently produce one item with the requested
  quantity.
- Concurrent requests with different quantities may complete in either order, but the cart remains
  valid and contains only one item for the variant.
- Removing a missing item and clearing an empty cart are idempotent and do not create a cart item.
- Product information may change or disappear after an item is saved; Cart preserves the saved
  intent while reporting current availability.
- A failed Product lookup during a mutation leaves the prior cart state unchanged.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide exactly one logical cart for each authenticated shopper and
  derive ownership only from the authenticated identity.
- **FR-002**: Unauthenticated callers MUST be rejected for every cart operation.
- **FR-003**: A shopper MUST be able to view the current contents of their own cart.
- **FR-004**: A shopper MUST be able to set an absolute desired quantity for one variant using an
  idempotent operation.
- **FR-005**: A cart MUST contain at most one item for a given variant.
- **FR-006**: An accepted quantity MUST be a whole number from 1 through 10, inclusive.
- **FR-007**: Before accepting an add or quantity replacement, the system MUST verify with the
  catalog owner that the variant exists and is currently sellable.
- **FR-008**: Failure or rejection of the catalog verification MUST leave the existing cart state
  unchanged.
- **FR-009**: A shopper MUST be able to remove one variant from their cart, and repeating the
  removal MUST leave the same observable state.
- **FR-010**: A shopper MUST be able to clear all items, and repeating the clear operation MUST
  leave the same observable state.
- **FR-011**: Cart reads MUST combine the saved variant identity and quantity with current
  Product-owned display information; Cart MUST NOT claim ownership of catalog names, images,
  prices, currency, sellability, or stock.
- **FR-012**: An item that becomes unavailable after being saved MUST remain visible until the
  shopper removes it or clears the cart, and MUST be marked unavailable.
- **FR-013**: Cart operations MUST NOT reserve stock, perform flash-sale admission, create an order,
  create a payment, or guarantee the displayed price.
- **FR-014**: Cart data MUST remain isolated from every other shopper, including when callers submit
  forged cart or user identifiers.
- **FR-015**: The cart MUST persist without automatic expiration until the shopper removes items or
  clears it; expiry and retention cleanup are outside this MVP.
- **FR-016**: Every response and failure MUST use the project's documented response envelope,
  stable error code, and trace identifier conventions.
- **FR-017**: The public cart contract MUST be documented with frontend-ready request, response,
  authentication, and failure examples before implementation begins.

### Key Entities

- **Cart**: The authenticated shopper's mutable pre-order intent, identified by its owner and
  containing zero or more unique cart items plus creation and last-update information.
- **Cart Item**: A saved variant identity and desired whole-number quantity. It does not own a
  price, catalog description, stock count, or reservation.
- **Product Display Information**: Current read-only information supplied by the catalog owner for
  presentation, including product and variant identity, names, price, currency, image, and
  sellability when available.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An authenticated shopper can add, view, update, remove, and clear cart items through
  the documented frontend journey without creating an order or reservation.
- **SC-002**: Repeating the same add/update, remove, or clear action 100 times produces the same
  final cart as performing it once, with no duplicate variant line.
- **SC-003**: In ownership tests covering at least two shoppers and all cart operations, zero
  requests expose or mutate another shopper's cart.
- **SC-004**: At least 95% of cart reads under the agreed local MVP test profile complete within
  one second when the catalog owner is available.
- **SC-005**: Product-owned display changes are reflected on the next successful cart read in all
  contract and integration test cases.
- **SC-006**: All documented validation, unavailable-product, authentication, dependency-failure,
  and concurrent-update scenarios produce deterministic, documented outcomes.

## Assumptions

- The existing authentication system provides a stable shopper subject identifier.
- Product Service remains the source of truth for catalog identity, display fields, price,
  currency, and sellability.
- Inventory and Flash Sale remain the only owners of stock and reservation decisions.
- The normal purchase and flash-sale purchase flows continue to start outside Cart; this MVP does
  not add a Cart-to-Order or Cart-to-Flash-Sale checkout action.
- The frontend may use returned product details for display only and must re-enter the existing
  authoritative purchase flow before an order is created.
- Guest carts, promotional calculations, notification emission, and automatic checkout are
  intentionally deferred to protect the seven-day delivery window.
- If current Product information cannot be retrieved during a cart read, the frontend can still
  render the saved variant identity and quantity but MUST treat Product-owned fields as unavailable.

## Decisions

| Decision | Approved rule | Rationale |
|----------|---------------|-----------|
| Maximum quantity per variant | 10 | A small explicit bound protects the basic cart from accidental or abusive quantities without introducing inventory ownership. |
| Product dependency failure on cart read | Return saved identity and quantity with `detailsAvailable=false` | Preserves shopper intent and allows graceful frontend degradation without presenting stale Product data as current. |
| Automatic cart expiration | No automatic expiration in the MVP | Keeps the seven-day scope focused; expiry, retention cleanup, and their operational semantics require a later approved feature. |

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Cart Service owns cart intent and its schema. It MUST NOT access Product,
  Inventory, Flash Sale, Order, Payment, or Authentication databases.
- **External ingress**: All shopper operations MUST enter through API Gateway. The service itself
  is not directly public.
- **API/event contracts**: New public Cart contracts and the required internal Product lookup
  contract MUST be documented before implementation. No Kafka contract is introduced.
- **Durable and hot-path data**: Cart intent is durable Cart-owned data. No Redis Lua or flash-sale
  hot-path change is permitted.
- **Messaging reliability**: N/A; this MVP neither consumes nor publishes business events.
- **Root infrastructure ownership**: Shared local and cloud orchestration changes belong under
  root `infra/`; Cart runtime configuration and schema migrations remain service-owned. The
  accepted Cart boundary ADR remains authoritative.
- **Observability**: Existing liveness, readiness, and metrics behavior MUST remain available.
  Cart requests and the Product lookup MUST propagate the request trace identifier; business code
  MUST remain independent of a concrete metrics registry.
- **Verification**: Domain and application tests cover cart invariants and ownership; web contract
  tests cover the public API; persistence tests cover uniqueness and concurrency; Product client
  contract/integration tests cover lookup outcomes; a focused local end-to-end journey is required.
  A load test is limited to a small read/mutation profile because Cart is not the seckill hot path.
- **Architecture decisions**: ADR 0002 already approves the independent Cart boundary. No boundary
  change is introduced, so no new ADR is required unless planning changes ownership or the chosen
  communication style.
