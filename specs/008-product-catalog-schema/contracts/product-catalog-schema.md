# Product Catalog Database Contract

This contract is the review boundary for the first durable schema owned by `product-service`. It is a database contract, not an HTTP or Kafka contract.

## Ownership and execution

- Logical database: `product_db`
- PostgreSQL schema: `public`
- Owner: `product-service` only
- Master changelog: `classpath:/db/changelog/db.changelog-master.yaml`
- Changeset: `philia:001-create-product-catalog-schema`
- Changeset execution: transactional and PostgreSQL-only
- Normal application replicas: `SPRING_LIQUIBASE_ENABLED=false`
- Migration process: one-off Product process with `SPRING_LIQUIBASE_ENABLED=true`

No other service may read or write these tables directly, and no foreign key may reference another service database.

## Business tables

The changeset creates exactly these five business tables:

1. `categories`
2. `products`
3. `product_variants`
4. `product_categories`
5. `product_media`

Liquibase additionally owns `databasechangelog` and `databasechangeloglock` as migration metadata.

## Stable natural keys

- Category `code` is unique.
- Category `slug` is unique.
- Product `code` is unique.
- Product `slug` is unique.
- Variant `sku` is globally unique.
- Variant `barcode` is unique when present.

Blank values are rejected for required codes, slugs, names, SKU, and media URL. A provided barcode cannot be blank.

## Money contract

`product_variants.base_price` and `product_variants.currency` form one Money value on the same row.

- `base_price`: `NUMERIC(19,4)`, non-negative
- `currency`: `CHAR(3)`, initially restricted to `VND`

This schema does not contain Product-level currency, campaign price, dated price, discount, tax, inventory, or reserved stock.

## Ownership relationships

- A Category may reference one optional parent Category.
- A Variant belongs to exactly one Product.
- A Product/category membership belongs to one Product and one Category.
- Media belongs to exactly one Product.
- Media may optionally reference a Variant belonging to that same Product.

The media ownership invariant is database-enforced by:

```text
product_variants UNIQUE (product_id, id)
product_media FOREIGN KEY (product_id, variant_id)
  -> product_variants (product_id, id)
```

All foreign keys use restricted deletion. There is no implicit cascade that deletes catalog data.

## Required indexes

- `idx_categories_parent_sort` on `(parent_id, sort_order, id)`
- `idx_products_status_published` on `(status, published_at DESC, id)`
- `idx_product_variants_product_sort` on `(product_id, sort_order, id)`
- `idx_product_categories_category_sort_product` on `(category_id, sort_order, product_id)`
- `uq_product_categories_one_primary` as a unique partial integrity index on `(product_id) WHERE is_primary = TRUE`
- `idx_product_media_product_variant_sort` on `(product_id, variant_id, sort_order, id)`

The primary-category partial index exists only to enforce at most one primary membership; it is not a speculative browsing index.

## Integrity rules

- Category, membership, Variant, and Media sort order is non-negative.
- Category status is one of `ACTIVE`, `INACTIVE`, `ARCHIVED`.
- Product status is one of `DRAFT`, `ACTIVE`, `INACTIVE`, `ARCHIVED`.
- Variant and Media status is one of `ACTIVE`, `INACTIVE`, `ARCHIVED`.
- Media type is `IMAGE` or `VIDEO`.
- Optional Variant weight is positive when present.
- Optional Media width, height, and file size are positive when present.
- Product and Variant `attributes` values are JSON objects.
- At most one category membership per Product has `is_primary = TRUE`.
- A non-null Media Variant must belong to the Media Product.

## Migration behavior

- The changeset does not use `IF NOT EXISTS`; an unmanaged name collision must fail visibly.
- The master changelog explicitly includes the versioned changeset and does not use `includeAll`.
- A successful second run records no additional execution and creates no duplicate object.
- `databasechangeloglock.locked` must be false after a completed run.
- Rollback is documented in reverse dependency order and does not use `CASCADE`.

## Explicitly prohibited in this feature

- Product HTTP endpoints, DTOs, mappers, domain classes, use cases, entities, or repositories
- JPA or Hibernate schema generation
- Redis keys or Lua scripts
- Kafka events, consumers, producers, or Outbox tables
- Inventory, campaign, Cart, Order, Payment, or identity state
- Cross-service database access or foreign keys
