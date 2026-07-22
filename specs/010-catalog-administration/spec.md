# Feature Specification: Product Catalog Administration

**Feature Branch**: `010-catalog-administration`
**Created**: 2026-07-19
**Status**: Implementing — approved; US1 MVP stabilized, US2/US3 pending
**Owner**: Product/Catalog owner (user)
**Reviewers**: Business/domain owner, security owner, product-service technical owner
**Input**: User description: "Plan Feature 010 for product-service catalog administration, research the core capabilities, and ask before deciding any missing constraint."

> This feature uses the Flash Sale risk profile because it introduces privileged writes, changes
> shopper-visible catalog state, edits Variant Money, and must define concurrent-update behavior.
> The clarified specification, implementation plan, and task graph are approved. The US1 MVP is
> implemented and stabilized through T057 and T059-T065. Unchecked US2/US3 work remains
> outside this stabilization group and is not complete.

## Clarifications

### Session 2026-07-19

- Q: Which actor and enforcement boundary may use Catalog Administration? -> A: Option A - remote catalog administration uses a single `CATALOG_ADMIN` authority; `authentication-service` issues JWTs, `api-gateway` validates/routes admin traffic, and `product-service` revalidates the role and records actor/trace identity for privileged audit.
- Q: Which Product-owned capabilities belong in Feature 010? -> A: Option C - Product content, Variants/base VND prices, existing Category memberships, and Product/Variant media URL metadata are in scope; Category hierarchy administration remains out of scope.
- Q: What lifecycle policy should Feature 010 use? -> A: Option C - full lifecycle without hard delete: `DRAFT -> ACTIVE`, `ACTIVE -> INACTIVE`, `INACTIVE -> ACTIVE`, and any non-archived Product may become final `ARCHIVED`; published Product code, slug, and Variant SKU are immutable.
- Q: How are concurrent edits, multi-part command failure, and client retries handled? -> A: Option A - use optimistic locking with expected version, make each command atomic all-or-nothing, reject stale edits as conflicts, and use an idempotency key for create/lifecycle retry replay.
- Q: Which catalog changes require integration events in Feature 010? -> A: Option A - Feature 010 publishes no Kafka events and creates no outbox; Product DB remains the source of truth and catalog events/outbox are deferred to a later feature.
- Q: How long must admin idempotency keys remain replayable? -> A: Idempotency keys for create and lifecycle commands remain replayable for 7 days, then expire.

### Session 2026-07-20

- Q: May the same admin idempotency key be reused after its 7-day replay window expires? → A: Yes. After expiry, the stored outcome MUST NOT be replayed; the same actor may reuse the key for a fresh command, normal command validation and conflict rules apply, and prior audit history remains retained.

## Problem and Scope

**Business problem**: `product-service` can expose the shopper catalog created by Feature 009, but
there is no approved workflow for an operator to create, inspect, maintain, or control the lifecycle
of the catalog records that feed those reads.

**Goal**: Provide the smallest coherent administration slice that lets an authorized catalog
operator maintain Product-owned catalog data safely while preserving the public behavior already
approved in Feature 009.

**Approved in scope**:

- Browse and inspect Product records for administration, including non-public lifecycle states.
- Create a Product as a non-public draft.
- Edit Product content and existing Product-owned catalog metadata.
- Manage Product Variants and each Variant's base price.
- Assign existing Categories, choose at most one primary Category, and order memberships.
- Manage Product/Variant media URL metadata and display order; binary upload is excluded.
- Publish, unpublish/deactivate, reactivate, and archive through explicit lifecycle actions without
  hard deletion.
- Reject invalid, conflicting, or unauthorized changes with stable observable outcomes.

**Out of scope**:

- Shopper catalog behavior already owned by Feature 009, except compatibility verification.
- Inventory quantity, stock reservation, oversell prevention, warehouse, or availability ledger.
- Campaign/flash-sale price, promotion, coupon, tax, cart, order, payment, or review behavior.
- Multi-currency or market/channel-specific price lists; the current schema supports VND only.
- Bulk import/export, bulk editing, approval workflow, scheduled publishing, or staged/current copies.
- Product types, attribute-schema administration, automatic Variant combination generation, bundles,
  collections, tags, advanced SEO, search ranking, or recommendation.
- Binary media upload, object storage, image resizing, CDN processing, or video transcoding.
- Hard deletion of Product, Variant, Category membership, or media history through lifecycle actions.
- Category tree creation, re-parenting, archival, or taxonomy administration.
- Kafka publication, outbox storage, or downstream catalog synchronization; catalog events are
  deferred to a later integration feature.

## Baseline References and Requirement Delta

| Reference | Governing content | Confirmed delta |
|-----------|-------------------|-----------------|
| [Feature 008](../008-product-catalog-schema/spec.md) | Product-owned tables, natural keys, VND Money, lifecycle values, restricted deletion, optimistic version fields | Add administration behavior over the approved schema; no schema delta is assumed |
| [Feature 009](../009-product-catalog-query/spec.md) | Public visibility is Product `ACTIVE`, published, and with at least one active Variant; Category state and media presence do not decide visibility | Add a separate privileged administration view and write workflow without weakening shopper visibility |
| [Constitution](../../.specify/memory/constitution.md) | Service/database ownership, gateway ingress, contract-first changes, outbox rule, observability, verification | Add Product-owned privileged commands and their contracts; event/outbox obligations are N/A because no event publication is included |

## User Scenarios & Testing

### User Story 1 - Create and inspect a catalog draft (Priority: P1)

As an authorized catalog operator, I want to create a Product draft and retrieve it in an
administration view so that I can prepare catalog content without exposing incomplete data to
shoppers.

**Why this priority**: A non-public draft is the smallest useful write slice and establishes
ownership, validation, and administration visibility before composition or publication is added.

**Independent Test**: Create one valid Product draft, retrieve it through the administration view,
and verify that the same record is absent from the public catalog.

**Use-case references**: UC-010-01 Create Product Draft; UC-010-02 Inspect Administrative Catalog

**Acceptance Scenarios**:

1. **Given** an authorized operator and unused Product identifiers, **When** the operator creates a
   valid Product, **Then** one Product is stored as `DRAFT`, returned with its identity and version,
   and remains hidden from shopper catalog results.
2. **Given** an existing Product uses the requested code or slug, **When** another draft is created
   with that value, **Then** the request is rejected clearly and no second Product is created.
3. **Given** Products exist in public and non-public states, **When** the operator browses the
   administration catalog, **Then** a bounded result can include every permitted state and exposes
   enough state information to select a Product for maintenance.
4. **Given** a missing Product identifier, **When** the operator requests its administration detail,
   **Then** the system reports that the Product does not exist without exposing another record.

---

### User Story 2 - Maintain Product composition (Priority: P2)

As an authorized catalog operator, I want to maintain the approved Product content, Variants,
Category memberships, and media metadata so that a draft can become a complete sellable catalog
entry.

**Why this priority**: A shopper selects a concrete Variant/SKU, while Categories and media make the
Product discoverable and understandable; these changes must preserve the schema invariants before
publication.

**Independent Test**: Starting from one draft, apply only the composition capabilities approved by
HD-002 and verify the resulting administration detail and all affected integrity rules without
publishing the Product.

**Use-case references**: UC-010-03 Maintain Product Composition

**Acceptance Scenarios**:

1. **Given** a Product draft, **When** the operator changes approved Product content, **Then** the
   administration detail shows the accepted values and a new observable version.
2. **Given** a Product and an unused SKU, **When** the operator adds a valid Variant with VND base
   price, **Then** that Variant belongs only to the selected Product and can be maintained through
   its Product.
3. **Given** existing Categories, **When** the operator changes Product memberships, **Then** every
   membership references an existing Category and no Product has more than one primary Category.
4. **Given** Product or Variant media URL metadata, **When** the operator adds or reorders it, **Then**
   the media remains owned by that Product and cannot reference a Variant of another Product.
5. **Given** one invalid change in a requested operation, **When** the operator submits it, **Then**
   the entire command is rejected, no partial catalog mutation is persisted, and the current version
   remains unchanged.

---

### User Story 3 - Control shopper visibility (Priority: P3)

As an authorized catalog operator, I want explicit Product lifecycle actions so that only approved
catalog entries become visible to shoppers and retired entries can be hidden without destructive
deletion.

**Why this priority**: Publication changes externally observable behavior and may later trigger
downstream synchronization, so it follows safe draft maintenance and requires explicit invariants.

**Independent Test**: Move a prepared Product through `DRAFT -> ACTIVE`, `ACTIVE -> INACTIVE`,
`INACTIVE -> ACTIVE`, and a final archive action, then verify its administration state plus its
visibility under Feature 009 after every transition.

**Use-case references**: UC-010-04 Control Product Lifecycle

**Acceptance Scenarios**:

1. **Given** a `DRAFT` or `INACTIVE` Product with required content and at least one active Variant
   with a valid VND base price, **When** the operator publishes or reactivates it, **Then** it becomes
   `ACTIVE`, records or preserves the publication time according to the approved contract, and is
   shopper-visible only if the complete Feature 009 visibility rule is satisfied.
2. **Given** a Product without required content or without at least one active Variant with valid VND
   base price, **When** publication or reactivation is attempted, **Then** the command is rejected
   with the failed rule and the Product remains non-public.
3. **Given** an `ACTIVE` Product, **When** the operator unpublishes/deactivates it, **Then** it
   becomes `INACTIVE` and is no longer returned as a normal shopper catalog item.
4. **Given** any non-archived Product, **When** the operator archives it, **Then** it becomes final
   `ARCHIVED`, remains inspectable in administration, and is hidden from shopper catalog results.
5. **Given** an archived Product, **When** a transition, hard delete, or mutation is attempted,
   **Then** the system rejects it and preserves catalog history.

### Edge Cases and Failure Outcomes

- Code, slug, SKU, or optional barcode collides with an existing record.
- A Variant price is negative, has unsupported currency, or exceeds the accepted precision.
- A membership attempts a second primary Category or references a missing Category.
- Media references a Variant owned by another Product.
- A Product is published with no active Variant or with incomplete required content.
- A published Product code, slug, or Variant SKU change is attempted.
- Two operators update the same Product from the same starting version.
- A client retries a create or lifecycle command after losing the response.
- A client reuses an idempotency key after its 7-day replay window has expired.
- An operation targets an inactive or archived Product/Variant.
- A multi-part maintenance request contains both valid and invalid changes.
- Event publication failure is not a Feature 010 outcome because Kafka/outbox publication is deferred.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST limit administration reads and mutations to callers with the
  `CATALOG_ADMIN` authority. Remote administration traffic MUST enter through `api-gateway`, use a
  JWT issued by `authentication-service`, be validated/routed by the gateway, and be revalidated by
  `product-service` before any administration use case executes.
- **FR-002**: The system MUST provide an administration catalog view separate from the public
  shopper contract and MUST allow permitted Product states to be inspected in bounded results.
- **FR-003**: The system MUST create new Products as non-public `DRAFT` records and MUST NOT expose a
  draft through the Feature 009 shopper catalog.
- **FR-004**: The system MUST allow approved Product content fields to be maintained without using a
  public reader DTO as an administration or domain command model.
- **FR-005**: The system MUST implement only the approved Feature 010 administration boundary:
  Product content, Product Variants with base VND prices, existing Category memberships, and
  Product/Variant media URL metadata with display order. The feature MUST NOT include Category
  hierarchy administration, binary media upload, Product types, attribute-schema administration,
  bundles, non-VND price lists, or campaign/flash-sale pricing.
- **FR-006**: Every accepted mutation MUST preserve Feature 008 natural-key, Money, ownership,
  primary-Category, media, lifecycle-value, and referential-integrity invariants.
- **FR-007**: The system MUST expose explicit lifecycle actions rather than allowing an arbitrary
  status value to bypass transition rules. Allowed Product transitions are `DRAFT -> ACTIVE`,
  `ACTIVE -> INACTIVE`, `INACTIVE -> ACTIVE`, and `DRAFT|ACTIVE|INACTIVE -> ARCHIVED`. `ARCHIVED`
  is final, remains inspectable in administration, and cannot be mutated, reactivated, or hard
  deleted by Feature 010.
- **FR-008**: A successful publication or deactivation MUST remain compatible with the complete
  visibility rule in Feature 009; Category status and media presence MUST NOT silently become new
  visibility factors in this feature.
- **FR-009**: Missing targets, duplicate natural keys, invalid state transitions, invalid values,
  authorization failures, and concurrent conflicts MUST have distinct, stable outcomes in the
  approved administration contract.
- **FR-010**: Every accepted mutation MUST return enough identity and version information for an
  operator to understand which state was persisted.
- **FR-010A**: After a Product has first become `ACTIVE`, Product code, Product slug, and Variant SKU
  MUST be immutable. Product content, existing Category memberships, media URL metadata, Variant
  activation state, and Variant base VND price MAY be maintained for non-archived Products through
  approved administration commands.
- **FR-011**: Every administration mutation command MUST be atomic all-or-nothing. Update and
  lifecycle commands MUST include the caller's expected Product version; if the expected version is
  stale, the command MUST be rejected as a concurrency conflict without persisting any mutation.
  Create and lifecycle commands MUST support an idempotency key so a retried command can replay the
  original persisted outcome for 7 days instead of creating a duplicate effect. After 7 days, the
  key is expired and MUST NOT be used for replay. The same actor MAY then reuse that key for a fresh
  command; the prior response MUST NOT be returned, normal command validation and conflict rules
  MUST apply, and prior audit history MUST remain retained.
- **FR-012**: No accepted Feature 010 catalog mutation is incomplete because of Kafka, outbox, or
  downstream synchronization. Feature 010 MUST NOT publish Kafka events or create outbox records;
  catalog event contracts and outbox reliability MUST be specified in a later integration feature
  before any event publication is implemented.
- **FR-013**: No administration operation may read or write another service's database or introduce
  Product-owned stock, campaign, cart, order, or payment state.

### Non-Functional Requirements

- **NFR-001**: One hundred percent of unauthorized administration attempts MUST be rejected without
  changing Product-owned durable state.
- **NFR-002**: One hundred percent of accepted mutations MUST preserve the approved invariants and
  produce a deterministic success, idempotent replay, validation rejection, or stale-version conflict
  outcome under the approved concurrency policy.
- **NFR-003**: Every administration request MUST carry trace identity and authenticated actor
  identity through gateway and product-service; every privileged mutation outcome MUST be auditable
  by actor, command target, result, and trace identity.
- **NFR-004**: Administration list responses MUST be bounded; exact filters, limits, and response
  targets will be fixed in the approved HTTP contract and plan rather than inferred here.

### Business Rules and Invariants

- **INV-001**: A Product draft is never visible through the normal shopper catalog.
- **INV-002**: Product code and slug, Variant SKU, and a provided barcode remain unique within the
  Product catalog.
- **INV-003**: Variant base price is non-negative Money stored with currency `VND`; campaign or
  flash-sale prices are not Product base price.
- **INV-004**: A Variant belongs to exactly one Product; Product media belongs to exactly one Product
  and may reference only a Variant of that Product.
- **INV-005**: A Product has at most one primary Category membership.
- **INV-006**: Public visibility remains the conjunction approved in Feature 009: Product `ACTIVE`,
  published, and at least one active Variant.
- **INV-007**: No Feature 010 lifecycle action physically deletes catalog history. `ARCHIVED` is a
  final retained state, not a hard delete.
- **INV-008**: Publication or reactivation requires required Product content and at least one active
  Variant with valid VND base price. Category status and media presence remain non-prerequisites for
  Feature 009 shopper visibility.

### Key Entities and Domain Model Delta

- **Catalog Operator**: A privileged authenticated actor holding the `CATALOG_ADMIN` authority and
  allowed to inspect or mutate catalog administration data through the gateway-mediated admin API.
- **Product**: The administration lifecycle owner and aggregate candidate for Product content,
  Variant ownership, Category memberships, media ownership, and publication invariants.
- **Product Variant**: A concrete SKU and the owner of Product base Money; it is not inventory or a
  campaign price.
- **Category Membership**: The ordered association between a Product and an existing Category,
  including its optional primary designation.
- **Product Media Metadata**: URL and descriptive/display metadata owned by a Product and optionally
  associated with one of that Product's Variants.
- **Added domain terms**: `Catalog Operator`, `Product Draft`, `Publish`, `Unpublish/Deactivate`, and
  `Archive`; do not use generic `save everything` or `set status` as business operations.
- **Changed states or transitions**: Product lifecycle transitions are explicit and limited to
  `DRAFT -> ACTIVE`, `ACTIVE -> INACTIVE`, `INACTIVE -> ACTIVE`, and
  `DRAFT|ACTIVE|INACTIVE -> ARCHIVED`; `ARCHIVED` is final and retained.

## Distributed-System Risk Decisions

| Risk area | Decision and required behavior | Requirement/scenario reference |
|-----------|--------------------------------|--------------------------------|
| Money/payment | Product base Money remains non-negative VND on Variant; payment and campaign price are out of scope. Published Product code, Product slug, and Variant SKU are immutable; Variant base VND price may be maintained for non-archived Products. | FR-005, FR-006, FR-010A, INV-003 |
| Inventory/oversell | N/A: product-service owns no stock or reservation state in Feature 010. | FR-013 |
| Concurrency | Optimistic locking with caller-provided expected Product version is required for update and lifecycle commands. Stale versions are rejected as conflicts; last-write-wins is not allowed. | FR-009, FR-011, HD-004 |
| Idempotency/deduplication | Create and lifecycle commands use an idempotency key. A retry with the same key replays the original persisted outcome for 7 days rather than creating a duplicate effect. After expiry, the old outcome is not replayed and the same actor may reuse the key for a fresh command under normal validation/conflict rules. | FR-011, HD-004, HD-006 |
| Consistency/ordering | PostgreSQL remains Product source of truth. No Feature 010 mutation requires ordered downstream event publication or catalog synchronization. | FR-012, HD-005 |
| Retry/timeout/compensation | Each command is atomic all-or-nothing. Client retry for create/lifecycle uses idempotency replay. Event recovery is N/A in Feature 010 because Kafka/outbox is deferred. | FR-011, FR-012, HD-004/005 |
| Security/authorization | Remote administration uses JWT-based `CATALOG_ADMIN` authority. `api-gateway` validates and routes admin traffic; `product-service` revalidates the role and records actor/trace identity for privileged mutation audit. Login/token issuance remains owned by `authentication-service`, not Feature 010. | FR-001, NFR-001, NFR-003 |
| TTL/quota/retention | No Product TTL or quota is introduced. Product hard delete is out of scope; `ARCHIVED` retains catalog history. Admin idempotency replay retention is 7 days. An expired key may be reused as a fresh command, while prior audit history remains retained. | INV-007, FR-011, HD-003/004/006 |

## Dependencies and Compatibility

- **Upstream dependencies**: `authentication-service` owns login/token issuance for an authenticated
  actor with `CATALOG_ADMIN`; `api-gateway` owns public admin ingress, JWT validation/routing,
  preservation of the bearer token/trace, and removal of caller actor headers. Feature 010 does not
  implement login or token issuance, but its plan
  must account for the required gateway and product-service authorization contract.
- **Downstream consumers**: Feature 009 shopper catalog immediately consumes Product-owned state
  through Product-owned reads. `campaign-service` and `flashsale-service` are not event consumers in
  Feature 010; event-driven synchronization must be introduced by a later approved feature.
- **Compatibility promise**: Feature 010 adds a separate administration contract and MUST NOT
  weaken or repurpose the public `/api/v1/catalog` contract. Any change to Feature 009 behavior must
  be specified as an explicit compatibility delta before planning.

## Success Criteria

### Measurable Outcomes

- **SC-001**: An approved catalog operator can create and retrieve a valid Product draft while that
  Product appears in zero shopper catalog results.
- **SC-002**: Every acceptance test for duplicate identifiers, invalid Money, ownership mismatch,
  primary Category conflict, invalid lifecycle transition, and stale update produces the approved
  deterministic outcome without corrupting existing catalog state.
- **SC-003**: After every approved lifecycle action, shopper visibility agrees with Feature 009 in
  all tested Product/Variant state combinations.
- **SC-004**: The administration slice can be verified without inventory, campaign, flash-sale,
  cart, order, payment, binary-media, or cross-service database behavior.

## Assumptions

- Feature 008 is the current durable schema baseline; a new migration is needed only if approved
  audit, idempotency, or new catalog data cannot be represented by that schema.
- Media administration means URL metadata only for the first slice.
- Category master/taxonomy administration is a separable capability and is excluded from Feature
  010.
- Advanced commerce capabilities found in larger platforms are deliberately deferred to keep the
  first administration slice learnable and independently testable.

## Human Decisions Required

| Priority | Question | Options/trade-off | Owner | Decision deadline | Resolution |
|----------|----------|-------------------|-------|-------------------|------------|
| RESOLVED HD-001 | Which actor and enforcement boundary may use Catalog Administration? | One Catalog Admin role; separate Editor/Publisher permissions; trusted internal identity; or no remote API yet | Product owner + security owner | Before approving US1/FR-001 | RESOLVED: single `CATALOG_ADMIN` authority through JWT-authenticated gateway traffic, with product-service revalidation and actor/trace audit |
| RESOLVED HD-002 | Which Product-owned capabilities belong in Feature 010? | Product only; Product+Variants; add existing Category/media associations; or also full Category hierarchy | Product owner | Before approving US2/FR-005 | RESOLVED: Product content, Variants/base VND prices, existing Category memberships, and Product/Variant media URL metadata are in scope; Category hierarchy administration is out of scope |
| RESOLVED HD-003 | What are the allowed lifecycle transitions, publication prerequisites, reactivation, archival, and deletion rules? | Draft-only; publish/deactivate; or full explicit lifecycle without/with hard delete | Product owner | Before approving US3/FR-007 | RESOLVED: full lifecycle without hard delete; `DRAFT -> ACTIVE`, `ACTIVE -> INACTIVE`, `INACTIVE -> ACTIVE`, and non-archived to final `ARCHIVED`; published code/slug/SKU immutable |
| RESOLVED HD-004 | How are concurrent edits, multi-part command failure, and client retries handled? | Reject stale version and keep commands atomic; last-write-wins; serialized writes; or custom merge/partial policy | Product owner + technical reviewer | Before planning persistence/contracts | RESOLVED: optimistic locking with expected version; commands are atomic all-or-nothing; stale edits return conflict; create/lifecycle retries use idempotency key replay |
| RESOLVED HD-005 | Which catalog changes, if any, require integration events? | No events in 010; visibility lifecycle events only; or all material catalog changes | Product owner + downstream owners | Before planning persistence/contracts | RESOLVED: no Kafka events or outbox in Feature 010; Product DB remains source of truth and catalog events are deferred to a later feature |
| RESOLVED HD-006 | May an expired admin idempotency key be reused? | Reuse as a fresh command after expiry, or reserve the key permanently | Product owner | Before US1 stabilization implementation | RESOLVED: after the 7-day replay window, the old outcome is not replayed and the same actor may reuse the key for a fresh command; normal validation/conflict rules apply and prior audit history remains retained |

## Constitutional Constraints

- **Service ownership**: `product-service` exclusively owns catalog administration behavior, data,
  and migrations. No cross-service database access or shared persistence/domain entity is allowed.
- **External ingress**: Remote operator traffic must enter through `api-gateway`; gateway validates
  JWT, routes only the approved administration contract, preserves the bearer token and trace
  identity, and removes caller-supplied actor headers. `product-service` revalidates
  `CATALOG_ADMIN` and derives the actor from its verified JWT context before executing use cases.
- **API/event contracts**: A versioned administration HTTP contract is required before code. No Kafka
  contract is required for Feature 010 because event publication is out of scope.
- **Durable and hot-path data**: PostgreSQL remains the durable Product source of truth. No Redis Lua
  or flash-sale hot-path state is in scope.
- **Messaging reliability**: Kafka/outbox reliability is N/A for Feature 010 because no durable
  mutation requires event publication. A later feature that introduces catalog events must define
  outbox-compatible atomic publication, ordering, idempotency, retry, and recovery before code.
- **Root infrastructure ownership**: No shared Docker, Kubernetes, Helm, or monitoring change is
  assumed. Product runtime configuration/migrations remain service-owned; gateway configuration
  remains owned by `api-gateway`.
- **Observability**: Existing declarative liveness/readiness/Prometheus exposure must remain intact;
  no manual Prometheus registry is allowed. Trace identity, authenticated actor identity, command
  target, and mutation result must be preserved for privileged-action audit.
- **Verification**: Domain/application unit tests, Product persistence integration tests against
  PostgreSQL, administration HTTP/security contract tests, concurrency tests, gateway route tests,
  and module verification apply where selected scope requires them. Kafka/outbox tests are N/A for
  Feature 010; migration/load/Kubernetes tests require explicit plan inclusion or a justified N/A.
- **Architecture decisions**: An ADR is required only if planning changes service ownership,
  communication style, ingress/security trust boundary, or infrastructure ownership. Adding the
  administration capability within current Product ownership alone does not require an ADR.

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-07-19 | Initial research-backed risk-profile draft created; five decisions remain open | Codex | Product owner | Draft |
| 2026-07-19 | HD-001 access model resolved as Option A: JWT-authenticated `CATALOG_ADMIN` through gateway with product-service revalidation | Codex | Product owner | Draft |
| 2026-07-19 | HD-002 scope resolved as Option C: Product, Variants/base VND prices, existing Category memberships, and media URL metadata | Codex | Product owner | Draft |
| 2026-07-19 | HD-003 lifecycle resolved as Option C: full lifecycle without hard delete and immutable published identifiers | Codex | Product owner | Draft |
| 2026-07-19 | HD-004 concurrency resolved as Option A: optimistic locking, atomic commands, stale conflicts, and idempotency-key replay for create/lifecycle retries | Codex | Product owner | Draft |
| 2026-07-19 | HD-005 event policy resolved as Option A: no Kafka/outbox in Feature 010; event integration deferred | Codex | Product owner | Draft |
| 2026-07-19 | Idempotency replay retention clarified as 7 days for admin create/lifecycle commands | Codex | Product owner | Draft |
| 2026-07-20 | HD-006 idempotency expiry reuse clarified: expired outcomes are not replayed; the same actor may reuse the key as a fresh command while audit history remains retained | Codex | Product owner | Draft |
| 2026-07-20 | Reconciled prior owner approvals across the clarified spec, technical plan, and task graph; recorded US1 as implemented with convergence stabilization still in progress | Codex | Product owner | Implementing |
| 2026-07-20 | Completed and verified the approved T059-T065 US1 stabilization group; US2/US3 and T066-T067 remained pending | Codex | Product owner | Implementing |
| 2026-07-21 | Recorded T066 authentication prerequisite as blocked pending a separate Authentication feature; US2/US3 and T067 remain pending | Codex | Product owner | Implementing |
