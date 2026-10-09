# Feature Specification: P1 Shopper Experience

**Feature Branch**: `[057-storefront-p1-experience]`

**Created**: 2026-10-09

**Status**: Draft — one security decision required

**Input**: Consolidate the storefront P1 backlog into one specification covering purchase-progress
clarity, account recovery, and a real shopper Wishlist.

## Problem and Scope

The core purchase journey works, but shoppers must interpret technical status values, cannot recover
a forgotten password, and see product-save affordances that do not yet persist. These capabilities
improve confidence and repeat use without changing stock or payment truth.

### In Scope

- Friendly progress timeline using existing Order, Payment, and reservation states.
- Bounded status refresh and recovery guidance for pending purchases.
- Forgot-password request and password-reset completion.
- Real authenticated Wishlist: save, remove, list, and open current Product details.
- Consistent account navigation and accessible empty/error/loading states for these capabilities.

### Out of Scope

- New shipping, fulfillment, cancellation, refund, return, or compensation states.
- Guest Wishlist synchronization.
- Social sharing, Wishlist folders, price-drop alerts, or cross-user Wishlist visibility.
- Email marketing or multi-factor authentication.
- Product reviews, Notification inbox, or admin capabilities.

## Baseline References

- `flash-sale frontend/QuickCart/app/orders/[id]/page.jsx`: current Order detail.
- `flash-sale frontend/QuickCart/app/payments/success/page.jsx`: bounded Payment follow-up.
- `flash-sale frontend/QuickCart/app/reservations/[id]/page.jsx`: reservation follow-up.
- `flash-sale frontend/QuickCart/components/ProductCard.jsx`: current decorative save action.
- `services/authentication-service`: account and session owner.
- `docs/api/frontend-integration-guide.md`: current shopper HTTP contracts.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Understand purchase progress (Priority: P1)

As a shopper, I want a friendly timeline for my purchase so that I understand whether it is waiting,
paid, confirmed, failed, expired, or needs another action.

**Why this priority**: Purchase confidence is more important than adding another browsing feature.

**Independent Test**: Open representative regular-purchase and Flash Sale Orders in every existing
state and verify that the timeline, current step, and guidance match the authoritative backend data.

**Acceptance Scenarios**:

1. **Given** an Order is waiting for Payment, **when** its detail opens, **then** the current step and
   safe payment action are clear without exposing raw enum names.
2. **Given** Payment succeeds and the Order later confirms, **when** status refreshes, **then** the
   timeline advances without claiming confirmation early.
3. **Given** a reservation expires or Payment/Order fails, **when** the detail opens, **then** the
   shopper sees truthful recovery guidance rather than a success state.
4. **Given** bounded automatic refresh ends while processing continues, **when** no final result is
   available, **then** the page remains pending and provides manual refresh instead of declaring
   failure.

---

### User Story 2 - Recover a forgotten password (Priority: P1)

As a shopper, I want to reset a forgotten password securely so that I can regain access without
support and without revealing whether another person's account exists.

**Why this priority**: A login-only account journey is incomplete when credentials are forgotten.

**Independent Test**: Request recovery for an existing and non-existing email, use a valid reset
link once, then exercise expired, reused, malformed, and rate-limited cases.

**Acceptance Scenarios**:

1. **Given** any syntactically valid email, **when** recovery is requested, **then** the public
   response remains neutral and does not reveal account existence.
2. **Given** a valid unused reset authorization, **when** a compliant new password is submitted,
   **then** it can be used to sign in and the authorization cannot be replayed.
3. **Given** an expired, used, malformed, or revoked authorization, **when** reset is attempted,
   **then** the password remains unchanged and safe guidance is shown.
4. **Given** repeated recovery attempts exceed the approved limit, **when** another attempt occurs,
   **then** abuse protection applies without leaking account existence.

---

### User Story 3 - Save products for later (Priority: P2)

As an authenticated shopper, I want to save products and see them later so that the heart control
has durable, useful behavior.

**Why this priority**: It improves repeat browsing but is not required to complete a purchase.

**Independent Test**: Save products from listing/detail, reopen the account on another session, list
saved products, remove one, and verify deactivated/missing products are handled truthfully.

**Acceptance Scenarios**:

1. **Given** an authenticated shopper views a public product, **when** they save it, **then** repeated
   save attempts do not create duplicate visible entries.
2. **Given** saved products exist, **when** Wishlist opens, **then** each available item links to its
   current Product detail and current catalog presentation.
3. **Given** a saved product is no longer publicly available, **when** Wishlist opens, **then** the
   item is clearly unavailable and is never given invented current price or availability.
4. **Given** the shopper removes an item, **when** Wishlist refreshes, **then** it no longer appears
   while other saved items remain.
5. **Given** a guest selects a save control, **when** authentication is required, **then** the guest
   is directed to sign in without the UI claiming the item was already saved.

### Edge Cases

- Payment succeeds while the Order remains temporarily pending.
- A late Payment success follows an earlier timeout/failure indication.
- An Order has no Flash Sale reservation.
- A reset link is opened in two browser tabs.
- Recovery delivery is delayed beyond authorization expiry.
- The shopper changes password while other sessions are active.
- Wishlist save/remove requests are retried after ambiguous network timeouts.
- A product changes name, price, or visibility after being saved.
- Product presentation is temporarily unavailable while saved identity still exists.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Order detail MUST present existing Order, Payment, and reservation states as friendly
  labels, current progress, and next-step guidance without changing those state machines.
- **FR-002**: Purchase progress MUST distinguish pending, successful, terminal failure, expiry, and
  recoverable retry conditions.
- **FR-003**: Automatic status refresh MUST be bounded, cancellable when the shopper leaves, and
  replaceable by manual refresh after the budget is exhausted.
- **FR-004**: Client redirect or Payment-provider success alone MUST NOT be treated as authoritative
  Order confirmation.
- **FR-005**: The recovery-request response MUST NOT reveal whether the submitted account exists.
- **FR-006**: Password-reset authorization MUST be random, confidential, single-use, expiring, and
  invalid after successful use.
- **FR-007**: The system MUST rate-limit recovery requests and reset attempts using a bounded policy
  that does not reveal account existence.
- **FR-008**: New passwords MUST satisfy the existing approved password policy and MUST NOT be
  logged, returned, or included in URLs.
- **FR-009**: Successful password reset MUST follow [NEEDS CLARIFICATION: choose the reset-token
  lifetime and whether success revokes all existing sessions or preserves them].
- **FR-010**: Recovery audit evidence MUST record bounded outcome categories and trace correlation
  without storing the raw reset authorization or password.
- **FR-011**: An authenticated shopper MUST be able to save, list, and remove Product identities in
  an ownership-isolated Wishlist.
- **FR-012**: Wishlist save and remove behavior MUST be idempotent under client retry.
- **FR-013**: Wishlist presentation MUST obtain current customer-facing Product data from the Product
  contract and MUST NOT persist copied current price or availability as truth.
- **FR-014**: Missing, hidden, or temporarily unavailable products MUST be represented truthfully and
  remain removable from Wishlist.
- **FR-015**: Guests MUST authenticate before durable Wishlist mutation; the storefront MUST NOT
  claim an unauthenticated save succeeded.
- **FR-016**: Wishlist listing MUST be bounded, deterministically ordered, and usable across the
  shopper's authenticated sessions.
- **FR-017**: All new account and Wishlist failures MUST use safe messages, trace correlation, and
  accessible recovery actions.

### Key Entities

- **Purchase Progress**: Shopper-facing interpretation of authoritative Order, Payment, and optional
  reservation states.
- **Recovery Request**: Neutral request to initiate account recovery.
- **Reset Authorization**: Confidential, single-use, expiring permission to change one account's
  password.
- **Saved Product**: Ownership association between a shopper and Product identity, independent of
  current Product presentation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every tested existing purchase-state combination, the displayed step and guidance
  match the authoritative backend state with zero false confirmations.
- **SC-002**: Automatic refresh stops within its approved budget in 100% of tested pending scenarios
  and leaves a usable manual refresh action.
- **SC-003**: Existing-account and non-existing-account recovery requests produce indistinguishable
  public status and message shape.
- **SC-004**: Valid reset authorization succeeds once; expired, reused, malformed, and revoked
  authorization succeeds zero times.
- **SC-005**: A shopper can save a product, find it after a new authenticated session, and remove it
  in no more than three interactions per action.
- **SC-006**: Retried save/remove mutations produce no duplicate saved entries or contradictory
  visible state in all tested retry scenarios.
- **SC-007**: At 375, 768, 1024, and 1440 pixels, the new timeline, recovery, and Wishlist journeys
  have no clipped primary action or unintended horizontal overflow.

## Assumptions

- Existing Order, Payment, and reservation states remain unchanged.
- The first timeline is purchase/payment progress, not physical shipment tracking.
- Recovery uses the account's existing email identity and a generic public response.
- Wishlist stores Product identity and resolves current Product presentation when read.
- Wishlist is account-owned; guest-only local favorites are not part of this release.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Authentication owns credentials, recovery, and sessions; Order/Payment/
  Flash Sale retain their state ownership; Wishlist ownership must be selected in the approved plan
  without cross-service database access.
- **External ingress**: All browser traffic enters through API Gateway.
- **API/event contracts**: Recovery and Wishlist HTTP contracts MUST be documented before
  implementation. Existing purchase contracts change only if missing timestamps are explicitly
  approved and versioned.
- **Durable and hot-path data**: Credential/recovery and Wishlist state are durable in their owning
  service. Redis Lua stock behavior is unaffected.
- **Messaging reliability**: No new event is required by this specification; any later notification
  event follows versioning, idempotency, and outbox requirements.
- **Root infrastructure ownership**: Shared mail-sink/provider or deployment assets belong under
  root `infra/`; service configuration and migrations remain with their owner.
- **Observability**: Recovery and Wishlist propagate trace IDs and expose bounded outcome metrics;
  secrets, email addresses, reset authorization, and passwords are not metric labels or logs.
- **Verification**: Authentication/Wishlist unit, integration, contract, authorization, expiry,
  replay, enumeration, rate-limit, and frontend browser tests are required. Existing purchase E2E
  regression is required; load tests apply to bounded read/mutation targets selected in planning.
- **Architecture decisions**: Wishlist service ownership requires an ADR if it changes a service
  boundary or communication style.

## Human Decisions Required

| Priority | Decision | Options and trade-offs | Owner | Deadline |
|---|---|---|---|---|
| Blocking | Reset authorization lifetime and session behavior after success | Shorter TTL reduces exposure; longer TTL improves deliverability. Revoking all sessions is safer after credential reset; preserving sessions reduces disruption. | Authentication/security owner | Before plan approval |

## Approval and History

- 2026-10-09 — Draft created after the owner requested one consolidated P1 specification.
