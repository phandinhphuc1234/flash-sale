# Feature Specification: Catalog Discovery MVP

**Feature ID**: `053-catalog-discovery`  
**Status**: Approved  
**Created**: 2026-09-22  
**Owner**: Flash Sale platform team

## 1. Problem and context

The storefront can display catalog data, but discovery is currently split between a
single broad product request and client-side filtering. This makes category links
unreliable, requires the browser to fetch more data than it needs, and will not
scale as the catalog grows. The Product Service already owns hierarchical
categories and product/variant data, so the first improvement should keep catalog
discovery inside that bounded context.

This feature deliberately does **not** turn the application into a marketplace.
There is one storefront and one Product Service catalog. Seller/store profiles,
seller isolation, commissions, and marketplace search are deferred until a separate
approved feature changes the service boundaries.

## 2. Goals

1. Let a shopper search published products by a short text query.
2. Let a shopper browse a category and its descendants through a URL-safe slug.
3. Let a shopper sort and paginate results without downloading the whole catalog.
4. Keep the existing catalog response shape and existing requests backward
   compatible.
5. Make the frontend URL the source of truth for discovery state so refresh and
   sharing preserve the result set.

## 3. Non-goals

- Multi-seller shops, seller storefronts, commissions, seller ratings, or seller
  isolation.
- Search across orders, inventory, payment, or private admin data.
- An availability/stock filter that would require a cross-service lookup or a
  denormalized inventory projection.
- Recommendation, typo-tolerant full-text search, autocomplete, or relevance
  ranking beyond the bounded Product Service query.
- Replacing the existing product detail, flash-sale, checkout, or cart flows.

## 4. User stories and acceptance criteria

### US1 — Search and browse catalog (Priority P1)

As a shopper, I can enter a search query and see only matching published products.

**Acceptance criteria**

- Given no query, the endpoint returns the same published catalog contract as today.
- Given `q=phone`, matching is case-insensitive and ignores leading/trailing
  whitespace.
- Empty or whitespace-only `q` behaves as absent; a query longer than 100 Unicode
  code points is rejected with a typed 400 error.
- Results contain only products that are published and not archived.
- The response includes the existing page metadata and product summary fields.

### US2 — Category, sort, and pagination (Priority P1)

As a shopper, I can choose a category, sort order, page, and page size.

**Acceptance criteria**

- `categorySlug` matches the selected category and its descendants, unless the
  request explicitly asks for an exact category in a future version.
- Supported sort values are `RELEVANCE`, `NEWEST`, `PRICE_ASC`, and `PRICE_DESC`.
- `RELEVANCE` is allowed only when `q` is present; without `q`, the server treats
  the default as `NEWEST`.
- `page` is zero-based and `size` is between 1 and 50 inclusive. Invalid values
  return a typed 400 error instead of silently being coerced.
- Ordering is deterministic: ties are resolved by product id ascending.
- The frontend sends these values to the server and does not apply a second
  category filter to the returned page.

### US3 — Category navigation (Priority P2)

As a shopper, I can load categories and navigate to a category result URL.

**Acceptance criteria**

- The existing categories endpoint remains backward compatible.
- The frontend displays the existing category hierarchy and links to the catalog
  route with `categorySlug` in the query string.
- A missing or inactive category returns a typed 404 from Product Service.

### US4 — Safe evolution (Priority P2)

As an operator, I can deploy this feature without breaking older clients.

**Acceptance criteria**

- Existing `GET /api/v1/catalog/products?page=0&size=20` callers continue to work.
- No database migration is required for the MVP; the current category and product
  schema is sufficient.
- API, OpenAPI, frontend behavior, and validation documentation are updated in the
  same change.

## 5. Functional requirements

- **FR-001** Product Service MUST own catalog search, category filtering, sorting,
  and pagination.
- **FR-002** The public catalog endpoint MUST accept the query parameters defined in
  `contracts/http-catalog-discovery.md`.
- **FR-003** The endpoint MUST validate query parameters before executing a database
  query and return the standard API error envelope.
- **FR-004** Category filtering MUST use Product Service's category relationship;
  the browser MUST NOT call Inventory Service to decide catalog membership.
- **FR-005** The default result set MUST exclude unpublished and archived products.
- **FR-006** The frontend MUST serialize discovery state in the URL and debounce
  text search to avoid a request on every keystroke.
- **FR-007** The frontend MUST preserve the current Gateway boundary and call the
  catalog through `NEXT_PUBLIC_API_BASE_URL` (local default `http://localhost:18080`).
- **FR-008** The API documentation and endpoint registry MUST describe the new
  parameters, validation rules, and examples.

## 6. Non-functional requirements

- The normal catalog request should complete within 1 second at the supported page
  size in the local/dev dataset; performance claims beyond that require a measured
  load test.
- The server MUST cap `size` at 50 to bound database and response work.
- Search and category queries MUST be read-only and must not mutate cart, stock,
  reservation, order, or payment state.
- Error responses MUST not expose SQL, stack traces, or internal service URLs.
- Trace/request identifiers must continue to propagate through Gateway and Product
  Service.

## 7. Human decisions required before implementation

The following choices are intentionally explicit so the API contract is not
silently changed:

| Decision | Proposed default | Why |
|---|---|---|
| Category semantics | Include descendants | Matches shopper expectations for a storefront tree |
| Default sort | `NEWEST` | Stable and useful without inventing relevance scoring |
| Maximum page size | 50 | Bounds response and query work |
| Price range currency | VND minor/whole units already used by catalog | Avoids introducing a second money representation |
| Marketplace/store model | Deferred | Avoids changing Product–Inventory–Order–Payment ownership now |

## 8. Success criteria

- The same catalog page can be reproduced from a copied URL containing `q`,
  `categorySlug`, `sort`, `page`, and `size`.
- A category result page does not fetch the entire catalog first.
- Existing anonymous catalog browsing, product detail, flash sale, cart, and order
  flows continue to pass their existing checks.
- The API registry and frontend integration guide show the new contract.
