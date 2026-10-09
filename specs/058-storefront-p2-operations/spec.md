# Feature Specification: P2 Commerce Operations

**Feature Branch**: `[058-storefront-p2-operations]`

**Created**: 2026-10-09

**Status**: Draft — two product decisions required

**Input**: Consolidate the storefront P2 backlog into one specification covering Category
administration, an in-app notification inbox, verified product reviews, read-only Order support,
and a bounded operational dashboard.

## Problem and Scope

The storefront and Operations Console cover the core catalog, stock, campaign, and checkout flows,
but secondary commerce workflows remain incomplete. Admins cannot manage category data through the
UI, support cannot inspect Orders through an approved admin view, shoppers cannot view durable
notifications or submit real reviews, and the dashboard lacks bounded operational summaries.

### In Scope

- Category create, rename, re-parent, activate, deactivate, browse, and select for Product composition.
- Shopper in-app notifications for Order and Payment outcomes, with unread count and mark-read.
- Shopper product ratings/reviews under an approved eligibility and edit policy.
- Read-only admin Order search, list, and detail for support.
- Bounded Operations Console summary counts for Orders and Campaigns over explicit time windows.
- Removal of fixed/decorative rating claims once real review presentation is available.

### Out of Scope

- Hard deletion of categories that have historical or active relationships.
- Email, SMS, push notification, WebSocket delivery, or notification preferences.
- Review comments, seller replies, images/video, votes, incentives, or automated moderation.
- Admin refund, cancellation, manual Order state change, compensation, or Payment mutation.
- Revenue recognition, accounting, tax, refund analytics, data warehouse, or arbitrary report builder.
- Customer administration or impersonation.

## Baseline References

- `flash-sale frontend/QuickCart/app/seller`: existing Operations Console.
- `flash-sale frontend/QuickCart/app/seller/orders/page.jsx`: current unavailable Order admin route.
- `flash-sale frontend/QuickCart/assets/productData.js` and current Product cards: fixed rating display.
- `services/product-service`: Product and Category owner.
- `services/order-service`: Order owner.
- `services/notification-service`: current notification service boundary.
- `docs/api/frontend-integration-guide.md`: current Category, Order, and frontend gaps.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage categories safely (Priority: P1)

As a catalog administrator, I want to manage and select categories without copying seeded UUIDs so
that Product composition is understandable and safe.

**Why this priority**: Category administration removes a concrete blocker in the existing Product
workflow and stays within an existing service owner.

**Independent Test**: Create parent/child categories, rename and re-parent one, use it in Product
composition, deactivate it, and verify cycle, duplicate, stale-version, and in-use behavior.

**Acceptance Scenarios**:

1. **Given** an authorized administrator, **when** a valid category is created, **then** it is
   discoverable in admin browse and selectable where Product composition permits it.
2. **Given** a category hierarchy, **when** a change would create a cycle, **then** the entire change
   is rejected without partial hierarchy mutation.
3. **Given** concurrent edits, **when** a stale update is submitted, **then** it is rejected rather
   than silently overwriting the newer state.
4. **Given** a category has relationships, **when** it is deactivated, **then** historical data is
   preserved and current Product behavior follows the approved visibility contract.

---

### User Story 2 - Read important account notifications (Priority: P2)

As a shopper, I want a small in-app inbox for important Order and Payment outcomes so that I can
return later and see what happened.

**Why this priority**: It makes asynchronous processing understandable without adding multiple
external delivery channels.

**Independent Test**: Produce representative Order/Payment outcomes, list the recipient's inbox,
observe the unread count, mark notifications read, retry delivery, and verify recipient isolation.

**Acceptance Scenarios**:

1. **Given** an eligible Order or Payment outcome, **when** notification processing completes,
   **then** the owning shopper receives at most one visible notification for that business event.
2. **Given** unread notifications, **when** the shopper opens the inbox, **then** they see a bounded
   newest-first list and an accurate unread count.
3. **Given** a shopper marks a notification read, **when** the request is retried, **then** the item
   remains read and no duplicate side effect occurs.
4. **Given** another shopper's notification identity, **when** access is attempted, **then** no
   existence or content is disclosed.

---

### User Story 3 - Submit a trustworthy product review (Priority: P3)

As a shopper, I want to rate and review an eligible purchased product so that future shoppers see
feedback backed by real purchase history.

**Why this priority**: Real feedback is useful, but it is not required for the purchase path and has
moderation/abuse implications.

**Independent Test**: Exercise the approved eligibility rule, create and update/delete behavior,
aggregate rating, hidden Product behavior, retries, and unauthorized access.

**Acceptance Scenarios**:

1. **Given** a shopper satisfies the approved review eligibility rule, **when** a valid rating and
   optional text are submitted, **then** the review is associated with that shopper and Product.
2. **Given** an ineligible shopper, **when** submission is attempted, **then** no public rating or
   review is created.
3. **Given** a review changes under the approved edit policy, **when** Product ratings refresh, **then**
   the aggregate and visible review state become consistent without duplicate contribution.
4. **Given** no real reviews exist, **when** a Product is shown, **then** the UI does not fabricate a
   customer rating.

---

### User Story 4 - Inspect Orders for support (Priority: P3)

As an authorized support administrator, I want to search and inspect Orders without modifying them
so that I can help a shopper understand current state safely.

**Why this priority**: Read-only support provides value without introducing refund or compensation
risk.

**Independent Test**: Search bounded Order lists by approved identifiers/state/time, open details,
verify authorization and pagination, and confirm no mutation action is exposed.

**Acceptance Scenarios**:

1. **Given** an authorized support administrator, **when** they search using approved filters, **then**
   deterministic bounded results and safe summaries are returned.
2. **Given** an Order detail, **when** it is opened, **then** support sees the approved operational
   state and traceable identifiers without credentials or provider secrets.
3. **Given** an unauthorized user, **when** admin Order access is attempted, **then** access is denied
   without revealing Order existence.
4. **Given** this release is read-only, **when** the admin UI renders, **then** refund, cancellation,
   state mutation, and compensation controls are absent.

---

### User Story 5 - View bounded operational summaries (Priority: P4)

As an operator, I want a small dashboard of Order and Campaign counts so that I can spot workload
and lifecycle patterns without treating monitoring telemetry as business truth.

**Why this priority**: It improves demonstrations and daily orientation but depends on stable read
contracts and agreed definitions.

**Independent Test**: Select approved time windows and compare each displayed count with the owning
service's bounded query results for the same filters.

**Acceptance Scenarios**:

1. **Given** an explicit time window, **when** the dashboard opens, **then** Order counts by approved
   state and Campaign counts by lifecycle display their definition and last refresh time.
2. **Given** one summary source is unavailable, **when** the dashboard loads, **then** the affected
   card is unavailable without hiding other valid cards or substituting invented zeroes.
3. **Given** a user lacks the approved admin authority, **when** the dashboard is requested, **then**
   no operational count is disclosed.

### Edge Cases

- Category slugs collide after normalization or concurrent creation.
- A requested parent category is missing, inactive, or its new relationship would create a cycle.
- A notification business event is delivered more than once or arrives out of order.
- A notification points to an Order the shopper can no longer access.
- A review submission is retried after an ambiguous timeout.
- The reviewed Product becomes hidden or is archived.
- Rating aggregate update is temporarily delayed.
- Admin Order filters contain literal wildcard characters or exceed bounded sizes.
- One dashboard source times out while another succeeds.
- Time-window boundaries cross timezone or daylight-saving transitions.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Authorized administrators MUST be able to create, browse, rename, re-parent, activate,
  and deactivate categories without entering raw identifiers in normal Product composition.
- **FR-002**: Category hierarchy changes MUST reject cycles, missing parents, invalid/self parents,
  duplicate canonical slugs, stale versions, and partial mutation.
- **FR-003**: Category deactivation MUST preserve historical identity and MUST NOT silently hard-delete
  Product relationships.
- **FR-004**: Category admin lists MUST be bounded, deterministic, searchable, and filterable by
  approved state.
- **FR-005**: The in-app inbox MUST initially contain only approved Order and Payment outcome
  categories with stable, customer-safe wording and deep links the recipient is authorized to open.
- **FR-006**: Notification ingestion MUST deduplicate the same business event and tolerate redelivery
  without duplicate visible entries.
- **FR-007**: Shoppers MUST be able to list their own notifications, see an unread count, and mark
  one or all visible notifications read idempotently.
- **FR-008**: Notification content and identity MUST be ownership-isolated and MUST follow [NEEDS
  CLARIFICATION: select the in-app retention period and whether read notifications expire sooner].
- **FR-009**: Product review eligibility, contribution count, and edit/delete behavior MUST follow
  [NEEDS CLARIFICATION: define verified-purchase eligibility and whether a shopper may create one
  review per Product or one per purchased Order item, including later edit/delete rights].
- **FR-010**: Ratings MUST use a bounded scale and valid optional review text; raw markup, scripts,
  secrets, and unsupported content MUST not render as executable content.
- **FR-011**: Review create/change/remove retries MUST NOT create duplicate rating contributions.
- **FR-012**: Product presentation MUST show an aggregate derived from real visible reviews and MUST
  show an unrated state when none exist rather than a fixed fabricated rating.
- **FR-013**: Authorized support administrators MUST be able to browse/search bounded read-only Order
  summaries and inspect approved detail fields.
- **FR-014**: Admin Order access MUST be separately authorized, ownership-safe, audited, paginated,
  and free of provider credentials or secret values.
- **FR-015**: This release MUST NOT expose admin Order refund, cancel, state-change, retry,
  compensation, or Payment mutation actions.
- **FR-016**: The dashboard MUST show only explicitly defined bounded Order and Campaign counts over
  an explicit time window, with last-refresh and unavailable states.
- **FR-017**: Dashboard cards MUST NOT substitute zero for unavailable data and MUST NOT use
  infrastructure monitoring telemetry as authoritative commerce data.
- **FR-018**: All P2 lists MUST use bounded pagination or an explicitly bounded result window with
  deterministic ordering.
- **FR-019**: All new public/admin responses and failures MUST preserve approved envelopes,
  authorization, safe errors, and trace correlation.

### Key Entities

- **Category**: Product-owned hierarchical classification with stable identity, slug, name, state,
  parent relationship, and concurrency version.
- **Notification**: Recipient-owned durable message derived from one approved business event with
  category, safe content, target, creation time, and read state.
- **Product Review**: Shopper-authored rating and optional text governed by verified eligibility and
  one approved contribution rule.
- **Rating Summary**: Product-level count and aggregate derived from visible valid reviews.
- **Admin Order Summary**: Support-safe read model for bounded search and inspection.
- **Operational Summary**: Defined count for one owner, state grouping, time window, and refresh time.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An administrator can create a category and select it during Product composition without
  copying an identifier, while all tested cycle/stale/duplicate cases cause zero partial mutation.
- **SC-002**: Duplicate delivery of the same notification event results in exactly one visible inbox
  item in every tested redelivery scenario.
- **SC-003**: Ownership tests disclose zero notification, review-management, or Order detail data to
  an unauthorized account.
- **SC-004**: Every eligible review contributes at most once under the approved rule, and Product
  presentation shows no fabricated rating when the visible review count is zero.
- **SC-005**: Admin Order search and every new list remain within their approved bounds and return
  deterministic page ordering for repeated identical requests.
- **SC-006**: When one dashboard source fails, all unaffected cards remain usable and the failed card
  displays unavailable rather than zero in 100% of tested partial-failure scenarios.
- **SC-007**: At 375, 768, 1024, and 1440 pixels, shopper P2 pages and supported admin layouts retain
  reachable actions and no unintended horizontal overflow.

## Assumptions

- Product Service remains the Category owner.
- The first notification inbox is in-app and limited to Order/Payment outcomes.
- Admin Order is read-only for this release.
- Dashboard summaries are bounded operational counts, not accounting or revenue reports.
- Review moderation and richer review media remain future work.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Product owns Category; Order owns Orders; Notification owns its inbox read
  model. Review ownership must be selected in planning/ADR without sharing databases or JPA models.
- **External ingress**: Shopper and admin browser traffic enters only through API Gateway with
  explicit authorization.
- **API/event contracts**: Category, inbox, review, admin Order, and summary contracts MUST be
  documented before implementation. New event consumption requires versioned contracts.
- **Durable and hot-path data**: Each owner persists its own durable state. Redis Lua reservation
  behavior and stock truth are unchanged.
- **Messaging reliability**: Notification/review event consumers, if selected, MUST be idempotent;
  required state-plus-publication uses an outbox; retry, ordering, DLT, and recovery are planned.
- **Root infrastructure ownership**: Shared Kafka/monitoring/deployment changes belong under root
  `infra/`; service migrations/configuration remain service-owned.
- **Observability**: New commands/events propagate trace IDs and expose bounded outcome metrics;
  shopper PII, review text, and high-cardinality IDs are not metric labels.
- **Verification**: Affected module unit/integration/contract/security tests, consumer replay tests,
  frontend tests/build/browser review, relevant load tests, and full cross-module verification are
  required. Kubernetes dry-run applies when manifests change.
- **Architecture decisions**: Review ownership or any new cross-service communication style requires
  an approved ADR. No admin financial mutation is authorized by this feature.

## Human Decisions Required

| Priority | Decision | Options and trade-offs | Owner | Deadline |
|---|---|---|---|---|
| Blocking | Notification retention | Short retention reduces PII/storage; longer retention improves shopper history. Read and unread expiry may be equal or different. | Notification/product owner | Before plan approval |
| Blocking | Verified-review contribution policy | One per Product is simple; one per Order item reflects repeat purchases but complicates aggregate/edit behavior. Edit/delete rights also affect audit/moderation. | Product/review owner | Before plan approval |

## Approval and History

- 2026-10-09 — Draft created after the owner requested one consolidated P2 specification.
