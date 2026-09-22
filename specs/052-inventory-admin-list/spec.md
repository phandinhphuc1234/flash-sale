# Feature Specification: Inventory Admin List and Product Display Lookup

**Feature Branch**: `codex/inventory-admin-list`

**Created**: 2026-09-22

**Status**: Approved

**Input**: User request to list inventory products in the admin frontend, preserve Product/Inventory ownership, and use bounded offset pagination with scale-aware batch lookup.

## User Scenarios & Testing

### User Story 1 - Browse stock as a paginated admin table (Priority: P1)

As an administrator, I want to see inventory rows in a paginated table so that I can review stock
without copying a Variant UUID into a form.

**Why this priority**: This is the minimum useful replacement for the current single-variant lookup
screen and gives operators a complete view of initialized inventory.

**Independent Test**: Request the inventory collection endpoint with page and size, then verify that
the response contains only Inventory-owned quantities, bounded metadata, and stable ordering.

**Acceptance Scenarios**:

1. **Given** initialized inventory rows, **When** an admin requests page 0 with size 20, **Then** the
   service returns at most 20 rows inside the shared paginated success envelope.
2. **Given** page or size is invalid, **When** an admin requests the collection, **Then** the service
   returns a safe 400 validation error and does not query an unbounded result.
3. **Given** stock changes while the table is open, **When** the admin refreshes a page, **Then** the
   row quantities come from Inventory Service and remain separate from catalog display metadata.

### User Story 2 - Resolve product labels in one bounded batch (Priority: P1)

As an administrator, I want inventory rows to show product name, variant name, and SKU so that the
table is understandable without exposing internal identifiers as the primary label.

**Why this priority**: Inventory owns quantity but not product identity; a documented batch display
lookup preserves the service boundary while avoiding one Product request per table row.

**Independent Test**: Submit a bounded set of variant IDs to the Product admin display lookup and
verify found, archived, and unknown variants are represented safely.

**Acceptance Scenarios**:

1. **Given** up to 100 variant IDs, **When** an authenticated admin submits one batch lookup, **Then**
   Product Service returns one display result per requested ID without exposing persistence details.
2. **Given** an unknown or archived variant, **When** it is included in the batch, **Then** the result
   identifies it as not currently sellable without failing the entire batch.
3. **Given** more than 100 IDs or duplicate/invalid IDs, **When** the request is submitted, **Then**
   the service returns a bounded validation error and does not perform an unbounded query.

### User Story 3 - Operate the inventory table without N+1 calls (Priority: P1)

As an administrator, I want the Inventory page to load one stock page and one product batch so that
the screen remains responsive as the catalog grows.

**Why this priority**: The frontend is the composition boundary for this read-only admin view; it
must not turn each table row into a separate service request.

**Independent Test**: Open the Inventory page with a page size of 20 and verify one Inventory request,
one Product batch request, stable pagination, and a useful fallback when Product metadata is delayed.

**Acceptance Scenarios**:

1. **Given** a page containing 20 inventory rows, **When** the admin opens that page, **Then** the
   browser performs no more than one inventory request and one Product batch request for the page.
2. **Given** Product display lookup is temporarily unavailable, **When** inventory data succeeds,
   **Then** quantities remain visible with SKU/variant fallback and a non-blocking warning.
3. **Given** the admin adjusts one row, **When** the existing adjustment succeeds, **Then** the row
   refreshes from Inventory Service and the current page position is preserved.

## Edge Cases

- An initialized Inventory row may refer to a variant that has been archived; show the row and mark
  the product display as archived/unavailable.
- Inventory may contain no rows; show an empty state with a link to the existing product/inventory
  initialization workflow.
- A page can become shorter after an adjustment or external change; clamp the requested page to the
  last available page after refresh.
- Product lookup can partially omit a requested ID; never discard the corresponding stock row.
- The first version does not define a low-stock threshold or arbitrary user-provided sort field; those
  are deferred until a business threshold and safe sort allow-list are approved.

## Requirements

### Functional Requirements

- **FR-001**: Inventory Service MUST expose an authenticated admin collection read at
  `GET /api/v1/admin/inventory` using zero-based `page` and bounded `size` parameters.
- **FR-002**: Inventory collection responses MUST use `ApiResponse<PageResponse<InventoryListItem>>`
  and MUST include `variantId`, `skuSnapshot`, `onHandQuantity`, `campaignAllocatedQuantity`,
  `availableQuantity`, and `updatedAt`.
- **FR-003**: Inventory collection queries MUST reject page values below zero and sizes outside
  1–100; the default size MUST be 20.
- **FR-004**: Inventory collection ordering MUST be deterministic: `updatedAt DESC` followed by
  `variantId ASC`; clients MUST NOT provide arbitrary database sort expressions in this version.
- **FR-005**: Inventory Service MUST query only its own persistence model and MUST NOT read Product
  Service tables or import Product domain types.
- **FR-006**: Product Service MUST expose an authenticated admin batch display lookup for 1–100
  variant IDs, returning product/variant labels, SKU, price, lifecycle status, and a `found` marker.
- **FR-007**: The Product batch lookup MUST return partial results for unknown or archived variants,
  while invalid input remains a safe validation error.
- **FR-008**: The frontend MUST issue at most one Inventory collection request and one Product batch
  request per visible page; it MUST NOT issue one Product request per row.
- **FR-009**: The frontend MUST preserve existing per-variant inventory detail, adjustment, and
  movement-history contracts.
- **FR-010**: Admin endpoints MUST require the existing administrator authorization boundary, echo
  `X-Trace-Id`, and use the shared safe error envelope without credentials or persistence internals.
- **FR-011**: The feature MUST not change seckill Redis Lua operations, Kafka contracts, payment flow,
  or inventory stock deduction semantics.

## Key Entities

- **InventoryListItem**: A read-only Inventory-owned stock projection keyed by `variantId`.
- **AdminVariantDisplay**: A Product-owned read-only display projection for one variant ID.
- **PageResponse**: The shared zero-based page payload with bounded metadata.

## Success Criteria

### Measurable Outcomes

- **SC-001**: An admin can view the first 20 initialized stock rows without entering a UUID.
- **SC-002**: One Inventory page requires no more than two browser API requests, regardless of whether
  it contains 1 or 20 rows.
- **SC-003**: Invalid page sizes never cause an unbounded database read and return a documented 400.
- **SC-004**: If Product metadata is unavailable, stock quantities remain visible and actionable.
- **SC-005**: Existing single-variant read, adjustment, and movement-history tests remain green.

## Assumptions

- Inventory rows are created by the existing initialization/fixture workflow; this feature does not
  invent a new stock initialization policy.
- Product display metadata is allowed to be eventually consistent with inventory quantities.
- The existing Gateway routes admin Product and Inventory paths; no new public ingress route is needed.
- Page-based offset pagination is sufficient for the current admin scale; cursor pagination is deferred.

## Constitutional Constraints

- **Service ownership**: Inventory owns stock queries and Product owns catalog display queries; no
  cross-service database access.
- **External ingress**: Admin frontend traffic enters through the existing api-gateway routes.
- **API/event contracts**: Two documented HTTP read contracts are added; no Kafka contract changes.
- **Durable and hot-path data**: PostgreSQL remains the source of truth; no Redis Lua or seckill change.
- **Messaging reliability**: No Kafka consumers, producers, or outbox changes.
- **Root infrastructure ownership**: No infrastructure change or ADR exception.
- **Observability**: Existing trace filters and actuator/Prometheus configuration remain unchanged;
  new endpoints echo `X-Trace-Id`.
- **Verification**: Inventory and Product unit/web contract tests, QuickCart lint/build/route smoke,
  and documentation verification apply; load testing is not required for this admin read feature.
- **Architecture decisions**: No service-boundary change; the batch Product read is a documented
  existing-boundary query.

## Approval and History

- 2026-09-22 — Owner requested implementation after reviewing the scale-aware Product/Inventory
  design; this specification is approved for implementation.
