# Implementation Plan: Catalog Discovery MVP

**Status**: Approved  
**Feature**: `053-catalog-discovery`

## Technical context

- Java 21, Spring Boot 3.x, Product Service package-by-feature architecture.
- PostgreSQL remains the catalog source of truth.
- QuickCart is a Next.js frontend using the Gateway boundary at
  `NEXT_PUBLIC_API_BASE_URL`.
- Existing catalog requests and response DTOs must remain backward compatible.
- No new production dependency is required.

## Constitution check

- **Boundaries**: Product Service owns catalog search; no service reads another
  service's database.
- **External traffic**: browser continues to use api-gateway.
- **Contracts**: HTTP contract and endpoint registry are updated before code.
- **Persistence**: query predicates stay in Product Service's persistence adapter.
- **Observability**: existing trace/request headers and actuator behavior remain.
- **No migration by default**: current schema is sufficient; indexes require a
  separate measured task.

## Design

### Backend

1. Add a typed query object and validator in the catalog application boundary.
2. Extend `ProductCatalogController`'s existing list endpoint with the additive
   query parameters in the contract file.
3. Add a read port for the paged discovery query and implement it in the Product
   persistence adapter using the existing product/category/variant tables.
4. Preserve the current public response envelope and map validation failures to
   the standard error codes.
5. Add controller, application, persistence, and contract tests for empty query,
   text search, descendant categories, price range, each sort, page bounds, and
   deterministic ties.

### Frontend

1. Keep category data in the existing category query, but serialize `q`,
   `categorySlug`, `sort`, `page`, and `size` in the catalog URL.
2. Replace local `products.filter(...)` category logic with a request that sends
   the server-side query.
3. Debounce text input, reset page when search/filter/sort changes, and preserve
   loading, empty, retry, and error states.
4. Render price and pagination from the server response without changing the
   existing product-card or checkout routes.

### Documentation and validation

1. Update the canonical endpoint registry and frontend API guide with examples.
2. Run Product Service module verification and the frontend lint/build gates.
3. Run a local Gateway smoke for anonymous catalog search and category navigation.
4. Record commands and outcomes in `validation.md`.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| A category query becomes expensive | Bound page size; measure; add an explicitly reviewed index migration only if needed |
| Frontend and API disagree on page semantics | Contract tests plus URL examples |
| Search looks like a marketplace feature | Keep seller/store concerns explicitly deferred |
| Inventory availability is stale or expensive | Do not add an availability filter in this MVP |

## Rollback

The endpoint remains backward compatible. A frontend rollback can stop sending the
new query parameters; the backend retains old defaults. No destructive migration is
planned.
