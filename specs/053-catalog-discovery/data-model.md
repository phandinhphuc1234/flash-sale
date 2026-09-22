# Catalog Discovery Data Model

## Existing data used

The MVP reuses the current Product Service model:

- `products`: published/archived state, name, slug, description, and timestamps.
- `categories`: hierarchical categories with `parent_id`, slug, and active state.
- `product_categories`: many-to-many product/category membership with primary flag.
- `variants`: price-bearing variants used for price sorting and summary output.

## Read-side request model

`CatalogProductQuery` is an application request model containing:

- optional normalized `q` (1–100 Unicode code points),
- optional `categorySlug`,
- optional non-negative `minPrice` and `maxPrice`,
- `sort` enum,
- zero-based `page`,
- bounded `size`.

No JPA entity is shared across services. Search predicates remain in the Product
Service persistence adapter and do not leak into the domain or web contract.

## Migration decision

No schema migration is required for the MVP. If an execution plan later shows that
indexes are needed, the index change must be proposed as a separate migration task
with measured evidence and rollback notes.

