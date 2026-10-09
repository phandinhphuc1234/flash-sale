# Feature Specification: Storefront Active Navigation

**Feature Branch**: `codex/navbar-active-navigation`

**Created**: 2026-10-09

**Status**: Approved — owner approved the specification and requested implementation on 2026-10-09

**Input**: User description: "Make the storefront navbar clearly show which page the shopper is on, using a polished effect that matches the existing theme and format."

## Problem and Scope

### Problem Statement

The storefront primary navigation does not distinguish the current destination from other links.
Shoppers can move between Home, Shop, Cart, product details, and account-owned purchase pages but
receive no persistent visual or accessible indication of their current section. Two visible entries,
Flash Sale and Help, currently point to Home even though they do not have real destinations, which
can make the location signal misleading.

### In Scope

- A clear selected state for real storefront destinations.
- Correct selected-state grouping for nested pages such as product details under Shop and Order
  details under the shopper's account area.
- Distinct hover, keyboard-focus, pressed, and selected states.
- An accessible current-page announcement for assistive technology.
- A compact navigation treatment on small screens that preserves access to the real top-level
  destinations.
- Removal of primary-navigation entries that currently have no real destination.

### Out of Scope

- Building public Flash Sale discovery or Help pages.
- Changing product, Cart, Order, Payment, Reservation, account, or authentication behavior.
- Changing seller Operations Console navigation.
- Adding a new icon library, animation library, design system, API, or backend endpoint.
- Redesigning the account menu or shopping Cart contents.

## Baseline References

- `flash-sale frontend/QuickCart/components/Navbar.jsx`: storefront navigation currently uses only
  hover color and does not read the current route.
- `flash-sale frontend/QuickCart/components/seller/Sidebar.jsx`: Operations Console already has a
  visually selected item, but exact-path matching does not define storefront behavior.
- `specs/053-catalog-discovery/spec.md`: public catalog discovery exists at `/all-products`, while
  public Flash Sale campaign discovery remains out of scope.
- `specs/054-shopper-purchase-presentation/spec.md`: shopper-facing presentation must remain truthful
  and must not imply unsupported Flash Sale availability.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Know the current storefront section (Priority: P1)

As a shopper, I want the navbar to show my current section so that I can understand where I am and
move elsewhere without guessing.

**Why this priority**: Current-location awareness is the requested usability problem and affects
every storefront visit.

**Independent Test**: Navigate between Home, Shop, product details, and Cart on a desktop viewport.
At each location, exactly one applicable top-level destination is visibly selected and announced as
the current page.

**Acceptance Scenarios**:

1. **Given** the shopper is on Home, **when** the navbar renders, **then** Home has the persistent
   selected treatment and other destinations do not.
2. **Given** the shopper opens Shop or a product detail, **when** the navbar renders, **then** Shop is
   selected because product detail belongs to that section.
3. **Given** the shopper opens Cart, **when** the navbar renders, **then** Cart is selected without
   hiding its item count.
4. **Given** a destination is selected, **when** assistive technology reads the navigation, **then**
   it can identify that destination as the current page.
5. **Given** the shopper hovers or focuses an unselected destination, **when** its interaction style
   appears, **then** it remains distinguishable from the persistent selected state.

---

### User Story 2 - Navigate clearly on a small screen (Priority: P2)

As a mobile shopper, I want the important destinations and current section to remain visible so that
the navigation does not disappear when the viewport becomes narrow.

**Why this priority**: The current primary text links are hidden below the desktop breakpoint,
leaving mobile shoppers without equivalent section navigation.

**Independent Test**: At a 375-pixel viewport, open Home, Shop, product detail, and Cart. Verify that
the real destinations remain reachable, the current destination is visible, labels do not wrap, and
the page has no horizontal overflow.

**Acceptance Scenarios**:

1. **Given** a small viewport, **when** the navbar renders, **then** Home and Shop remain reachable
   through a compact navigation treatment.
2. **Given** Cart is open on a small viewport, **when** the navbar renders, **then** its current state
   remains understandable and the Cart count remains readable.
3. **Given** a keyboard user navigates the header at any supported width, **when** focus moves between
   controls, **then** every actionable control has a visible focus indicator.

### Edge Cases

- The current route contains a query string or fragment.
- The shopper opens a nested product route or an Order/Payment/Reservation follow-up route.
- The route is unknown to the navbar.
- The shopper is signed out and opens a public destination.
- The Cart count changes while Cart is selected.
- A label approaches the available width on a narrow screen.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The storefront MUST show one persistent selected state for the current applicable
  top-level destination.
- **FR-002**: Home MUST be selected only on the Home route, not on every route that begins with `/`.
- **FR-003**: Shop MUST be selected on catalog listing and product-detail routes.
- **FR-004**: Cart MUST be selected on the Cart route while preserving the current Cart count.
- **FR-005**: Nested shopper purchase/account pages MUST retain truthful navigation context without
  falsely selecting Home or Shop.
- **FR-006**: The selected state MUST use more than a transient hover effect and MUST remain clear
  without relying on color alone.
- **FR-007**: The current link MUST expose a machine-readable current-page state to assistive
  technology.
- **FR-008**: Keyboard focus MUST be visible and visually distinct from the selected state.
- **FR-009**: Real primary destinations MUST remain usable at desktop and small-screen widths without
  clipped or wrapped labels.
- **FR-010**: Flash Sale and Help MUST NOT appear as working primary destinations while they only
  redirect to Home; adding those destinations later requires their own approved behavior.
- **FR-011**: Existing navigation destinations, account visibility, authentication, and Cart behavior
  MUST remain unchanged except for the presentation and removal of false destinations described
  here.
- **FR-012**: Motion MUST be subtle and MUST respect the user's reduced-motion preference.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In every reviewed Home, Shop, product-detail, and Cart state, exactly one applicable
  primary destination has the persistent selected treatment.
- **SC-002**: All selected destinations expose their current-page state to accessibility inspection,
  and every navbar action has a visible keyboard focus state.
- **SC-003**: At 375, 768, 1024, and 1440 pixel widths, the navbar has no unintended horizontal page
  overflow, clipped actionable control, or wrapped destination label.
- **SC-004**: No visible primary navigation item routes to Home while claiming to be Flash Sale or
  Help.
- **SC-005**: Existing Cart count, login/account menu, and destination navigation checks continue to
  pass after the visual change.

## Assumptions

- The existing white, slate, and orange visual language remains authoritative.
- A thin orange active indicator, stronger label weight, and restrained tinted background are an
  appropriate persistent treatment; the implementation plan will choose exact utility classes.
- Product details are part of Shop; Cart is its own destination.
- Order, Payment, Reservation, Profile, and Security pages belong to account-owned follow-up flows
  and should not falsely select another top-level destination.
- Flash Sale and Help return only when real, independently approved destinations exist.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Frontend-only presentation; no service or database ownership changes.
- **External ingress**: Existing Gateway use remains unchanged.
- **API/event contracts**: No HTTP or Kafka contract changes.
- **Durable and hot-path data**: No PostgreSQL, Redis, stock, Order, or Payment behavior changes.
- **Messaging reliability**: No Kafka, outbox, idempotency, retry, or recovery changes.
- **Root infrastructure ownership**: No infrastructure or deployment changes.
- **Observability**: No health, metrics, logging, or trace behavior changes.
- **Verification**: Focused navigation tests, frontend production build, desktop/mobile browser review,
  accessibility-state inspection, and `git diff --check` apply. Backend, load, contract, and
  Kubernetes tests are omitted because no backend, traffic, contract, or manifest behavior changes.
- **Architecture decisions**: No architecture decision or ADR is required; this is a bounded
  storefront presentation change with no dependency addition.

## Approval and History

- 2026-10-09 — Draft created from the project owner's request for researched, theme-consistent
  current-page navigation feedback.
- 2026-10-09 — Project owner approved the specification and requested full implementation.
