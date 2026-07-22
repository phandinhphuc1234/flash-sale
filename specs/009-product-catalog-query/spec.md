# Feature Specification: Product Catalog Query

**Feature Branch**: `009-product-catalog-query`

**Created**: 2026-07-17

**Status**: Verified

**Input**: User description: "Create feature 009 first for product-service core read/query capability. Surface unclear constraints or planning issues and do not invent missing rules."

## Problem and Scope

### Problem Statement

The flash-sale system needs a first usable product catalog read slice so buyers and operators can view catalog data that already belongs to the product context. This feature establishes the observable catalog query behavior before write workflows, Kafka publication, campaign synchronization, stock handling, cart, or order processing are introduced.

### In Scope

- Browse product categories available to catalog readers.
- Browse products by catalog filters that are safe for the first read-only slice.
- View product detail with its active variants, categories, and media summary.
- Define the public visibility and response expectations needed before planning implementation.

### Out of Scope

- Creating, updating, publishing, deactivating, or archiving products.
- Product stock, reservation, purchase limit, campaign price, flash-sale price, cart, order, payment, or review behavior.
- Kafka publication or consumption.
- Search ranking, full-text search, recommendation, personalization, and admin back-office workflows.
- Binary media upload or media processing.
- Cross-service reads from another service database.

### Non-goals

- This feature does not establish the canonical product lifecycle commands.
- This feature does not decide product synchronization rules for campaign or flash-sale contexts.
- This feature does not change repository architecture or service ownership.

## Clarifications

### Session 2026-07-17

- Q: Should this first catalog query slice be public shopper access through the gateway, internal service-only access, or both? -> A: Public shopper access through api-gateway.
- Q: Should browse/detail responses omit price for now, show each eligible variant price only, or compute a product-level display price such as minimum active variant price? -> A: Show each eligible variant price only; do not compute product-level display price in this feature.
- Q: What exact rule makes a product visible to catalog readers, including product status, publish time, category state, variant state, and media requirements? -> A: A product is visible when it is ACTIVE, published, and has at least one active variant; category state and media presence do not participate in visibility for this feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse Visible Products (Priority: P1)

As a shopper, I want to browse products that are currently visible for display, so that I can discover catalog items before a campaign or purchase flow exists.

**Why this priority**: A read-only product list is the smallest useful catalog slice and lets the project validate catalog visibility, pagination, and response shape before adding write or event behavior.

**Independent Test**: Can be tested by preparing products with different visibility states and requesting a product listing, then verifying only visible products appear in a stable, bounded result set.

**Acceptance Scenarios**:

1. **Given** visible and non-visible products exist, **When** a shopper browses the catalog, **Then** only products that are ACTIVE, published, and have at least one active variant are returned.
2. **Given** more products exist than a single result set may contain, **When** a shopper browses the catalog, **Then** the response is bounded and provides enough information to request additional results.
3. **Given** multiple visible products qualify for the same request, **When** a shopper browses the catalog repeatedly without data changes, **Then** product ordering remains stable.

---

### User Story 2 - Browse Products by Category (Priority: P2)

As a shopper, I want to browse products in a category, so that I can narrow the catalog to the type of item I care about.

**Why this priority**: Category browsing is a core catalog behavior and is likely needed before campaign selection or storefront navigation can be useful.

**Independent Test**: Can be tested by assigning products to categories and verifying category-filtered browsing includes visible matching products and excludes non-matching products.

**Acceptance Scenarios**:

1. **Given** visible products belong to a selected category, **When** a shopper browses that category, **Then** the matching visible products are returned.
2. **Given** a category has no visible products, **When** a shopper browses that category, **Then** the shopper receives an empty result rather than unrelated products.
3. **Given** a selected category does not exist, **When** a shopper browses that category, **Then** the response clearly communicates that the category cannot be used.

---

### User Story 3 - View Product Detail (Priority: P3)

As a shopper, I want to view a product detail page, so that I can understand the product, its active variants, categories, and media before taking any later buying action.

**Why this priority**: Product detail is needed before write/admin and campaign behavior, but it depends on the visibility and variant rules established by the list behavior.

**Independent Test**: Can be tested by preparing one visible product with variants, categories, and media, then verifying the detail view returns only approved catalog information for that product.

**Acceptance Scenarios**:

1. **Given** a visible product exists, **When** a shopper views its detail, **Then** the product identity, display information, active variants with their own prices, categories, and media summary are returned.
2. **Given** a product exists but is not visible for display, **When** a shopper views its detail, **Then** the product is not exposed as a normal visible catalog item.
3. **Given** a product has no active variant, **When** a shopper browses or views that product, **Then** the product is hidden from normal catalog results.

### Edge Cases

- Products with no assigned category.
- Products assigned to more than one category.
- Products with variants in different sale states.
- Products with no media or inactive media.
- Requests for a missing category or missing product.
- Requested result size exceeds the maximum allowed bound.
- Multiple products share the same display timestamp or sort value.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow shoppers to browse a bounded list of products that are ACTIVE, published, and have at least one active variant.
- **FR-002**: The system MUST allow shoppers to browse a bounded list of visible products for a selected existing category; category state does not decide product visibility in this feature.
- **FR-003**: The system MUST allow shoppers to view details for a single product only when that product is ACTIVE, published, and has at least one active variant.
- **FR-004**: The system MUST return enough paging information for catalog readers to continue browsing without receiving an unbounded result set.
- **FR-005**: The system MUST return products in a deterministic order for the same request when catalog data has not changed.
- **FR-006**: The system MUST exclude internal-only fields and service implementation details from catalog reader responses.
- **FR-007**: The system MUST handle missing products and missing categories without exposing unrelated catalog records.
- **FR-008**: The system MUST expose this catalog query slice as public shopper access through api-gateway.
- **FR-009**: The system MUST show prices only on active variants returned in browse or detail responses and MUST NOT compute a product-level display price in this feature.
- **FR-010**: The system MUST treat category state and media presence as non-visibility factors for this feature.

### Key Entities

- **Catalog Reader**: A public shopper viewing product catalog information through api-gateway without modifying product state.
- **Product**: A catalog item owned by the product context and exposed only when it is ACTIVE, published, and has at least one active variant.
- **Variant**: A purchasable product option owned by the product context; this feature exposes active variant summary data and each active variant's own price but does not reserve or sell stock.
- **Category**: A catalog grouping used to organize product browsing.
- **Product Media**: Display metadata for product images or other media; this feature exposes only catalog-safe media summary information.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A catalog reader can browse the first page of visible products and understand whether more results are available.
- **SC-002**: A catalog reader can browse by an existing category and receive only visible products associated with that category.
- **SC-003**: A catalog reader can open a visible product detail and see its approved display information, active variants with their own prices, categories, and media summary.
- **SC-004**: Non-visible products and missing categories are not exposed as normal catalog results in any acceptance scenario.
- **SC-005**: The feature can be verified independently without cart, order, payment, campaign, stock, or Kafka behavior.

## Assumptions

- Existing product catalog schema from feature `008-product-catalog-schema` is the available data baseline.
- This is a read-only feature; it does not create, modify, or publish catalog records.
- Product-service remains the owner of product catalog data and migrations.
- The first slice should prefer stable, understandable catalog behavior over advanced search or ranking.
- Public shopper access, variant-level price presentation, and product visibility have been confirmed for this feature.

## Constitutional Constraints *(mandatory)*

- **Service ownership**: Product catalog query behavior belongs to product-service. No service may query another service database for this feature.
- **External ingress**: Public shopper traffic for this feature must enter through api-gateway.
- **API/event contracts**: Reader-facing HTTP contracts are expected for catalog queries. No Kafka event contract is in scope.
- **Durable and hot-path data**: PostgreSQL remains the durable source of truth. No Redis Lua hot-path behavior is in scope.
- **Messaging reliability**: No Kafka consumer, Kafka producer, or outbox behavior is in scope for this feature.
- **Root infrastructure ownership**: No shared Docker, Kubernetes, Helm, or monitoring asset changes are in scope. Service-owned configuration remains with product-service.
- **Observability**: Existing service liveness, readiness, and Prometheus-compatible Actuator endpoints must remain declarative. Manual Prometheus registry construction is not allowed. Trace ID propagation impact must be assessed in the plan for the public reader-facing flow.
- **Verification**: Query behavior requires requirement-level acceptance coverage and service-local verification. Contract tests may apply if a reader-facing HTTP contract is added. Load testing is not required for this first read-only slice unless the plan identifies a performance risk.
- **Architecture decisions**: No ADR is expected unless planning changes service boundaries, ingress policy, persistence ownership, communication style, or root infrastructure ownership.

## Approval and History

- 2026-07-17 - Draft created.
- 2026-07-17 - Clarified public shopper access, variant-level price presentation, and product visibility rule.
- 2026-07-17 - Approved for planning and implementation by user instruction in chat.
- 2026-07-17 - Verified locally with product-service and api-gateway module builds.
