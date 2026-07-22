# Data Model: Product Catalog Schema

All tables are owned exclusively by `product-service` in PostgreSQL database `product_db`, schema `public`. No foreign key crosses a service boundary.

## Category

Table: `categories`

| Column | Type | Null | Default | Rules |
|--------|------|------|---------|-------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `parent_id` | `UUID` | Yes | — | Self FK to `categories.id`, delete restricted |
| `code` | `VARCHAR(64)` | No | — | Unique, trimmed value cannot be blank |
| `slug` | `VARCHAR(200)` | No | — | Unique, trimmed value cannot be blank |
| `name` | `VARCHAR(255)` | No | — | Trimmed value cannot be blank |
| `description` | `TEXT` | Yes | — | Optional display description |
| `status` | `VARCHAR(20)` | No | `ACTIVE` | `ACTIVE`, `INACTIVE`, or `ARCHIVED` |
| `sort_order` | `INTEGER` | No | `0` | Must be non-negative |
| `created_at` | `TIMESTAMPTZ` | No | current timestamp | Creation time |
| `updated_at` | `TIMESTAMPTZ` | No | current timestamp | Application updates later |
| `version` | `BIGINT` | No | `0` | Must be non-negative |

Index: `idx_categories_parent_sort` on `(parent_id, sort_order, id)`.

Cycle prevention is not implemented in SQL; a later category-management domain feature owns it.

## Product

Table: `products`

| Column | Type | Null | Default | Rules |
|--------|------|------|---------|-------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `code` | `VARCHAR(64)` | No | — | Unique, trimmed value cannot be blank |
| `slug` | `VARCHAR(200)` | No | — | Unique, trimmed value cannot be blank |
| `name` | `VARCHAR(255)` | No | — | Trimmed value cannot be blank |
| `short_description` | `VARCHAR(500)` | Yes | — | Optional summary |
| `description` | `TEXT` | Yes | — | Optional full description |
| `attributes` | `JSONB` | No | empty object | Must contain a JSON object |
| `status` | `VARCHAR(20)` | No | `DRAFT` | `DRAFT`, `ACTIVE`, `INACTIVE`, or `ARCHIVED` |
| `published_at` | `TIMESTAMPTZ` | Yes | — | Optional publication time |
| `created_at` | `TIMESTAMPTZ` | No | current timestamp | Creation time |
| `updated_at` | `TIMESTAMPTZ` | No | current timestamp | Application updates later |
| `version` | `BIGINT` | No | `0` | Must be non-negative |

Index: `idx_products_status_published` on `(status, published_at DESC, id)`.

Product does not own stock, campaign price, a Cart, or an Order.

## Product Variant

Table: `product_variants`

| Column | Type | Null | Default | Rules |
|--------|------|------|---------|-------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `product_id` | `UUID` | No | — | FK to `products.id`, delete restricted |
| `sku` | `VARCHAR(100)` | No | — | Globally unique, non-blank |
| `barcode` | `VARCHAR(64)` | Yes | — | Unique when present, non-blank when present |
| `name` | `VARCHAR(255)` | No | — | Non-blank |
| `attributes` | `JSONB` | No | empty object | Must contain a JSON object |
| `base_price` | `NUMERIC(19,4)` | No | — | Must be non-negative |
| `currency` | `CHAR(3)` | No | `VND` | Must equal `VND` in this version |
| `status` | `VARCHAR(20)` | No | `ACTIVE` | `ACTIVE`, `INACTIVE`, or `ARCHIVED` |
| `weight_grams` | `INTEGER` | Yes | — | Must be positive when present |
| `sort_order` | `INTEGER` | No | `0` | Must be non-negative |
| `created_at` | `TIMESTAMPTZ` | No | current timestamp | Creation time |
| `updated_at` | `TIMESTAMPTZ` | No | current timestamp | Application updates later |
| `version` | `BIGINT` | No | `0` | Must be non-negative |

Constraints/indexes:

- Unique `(product_id, id)` to support composite media ownership.
- `idx_product_variants_product_sort` on `(product_id, sort_order, id)`.

Money is always read as the pair `base_price + currency` from this row.

## Product Category Membership

Table: `product_categories`

| Column | Type | Null | Default | Rules |
|--------|------|------|---------|-------|
| `product_id` | `UUID` | No | — | FK to `products.id`, delete restricted |
| `category_id` | `UUID` | No | — | FK to `categories.id`, delete restricted |
| `is_primary` | `BOOLEAN` | No | `FALSE` | At most one true membership per Product |
| `sort_order` | `INTEGER` | No | `0` | Must be non-negative |
| `created_at` | `TIMESTAMPTZ` | No | current timestamp | Membership creation time |

Primary key: `(product_id, category_id)`.

Indexes:

- `idx_product_categories_category_sort_product` on `(category_id, sort_order, product_id)`.
- Unique partial integrity index `uq_product_categories_one_primary` on `(product_id) WHERE is_primary = TRUE`.

The partial unique index enforces integrity; it is not the deferred primary-category browsing optimization.

## Product Media

Table: `product_media`

| Column | Type | Null | Default | Rules |
|--------|------|------|---------|-------|
| `id` | `UUID` | No | `gen_random_uuid()` | Primary key |
| `product_id` | `UUID` | No | — | Direct FK to `products.id`, delete restricted |
| `variant_id` | `UUID` | Yes | — | With `product_id`, composite FK to Variant owner |
| `media_type` | `VARCHAR(20)` | No | — | `IMAGE` or `VIDEO` |
| `url` | `VARCHAR(2048)` | No | — | Non-blank location |
| `alt_text` | `VARCHAR(500)` | Yes | — | Optional accessible description |
| `mime_type` | `VARCHAR(100)` | Yes | — | Optional file type |
| `width_px` | `INTEGER` | Yes | — | Positive when present |
| `height_px` | `INTEGER` | Yes | — | Positive when present |
| `file_size_bytes` | `BIGINT` | Yes | — | Positive when present |
| `sort_order` | `INTEGER` | No | `0` | Must be non-negative |
| `status` | `VARCHAR(20)` | No | `ACTIVE` | `ACTIVE`, `INACTIVE`, or `ARCHIVED` |
| `created_at` | `TIMESTAMPTZ` | No | current timestamp | Creation time |
| `updated_at` | `TIMESTAMPTZ` | No | current timestamp | Application updates later |

Index: `idx_product_media_product_variant_sort` on `(product_id, variant_id, sort_order, id)`.

`variant_id = NULL` represents Product-level media. A non-null Variant must belong to the same Product.

## Referential and Delete Policy

All foreign keys use restricted deletion. The initial lifecycle uses status fields, and no table silently cascades a Product, Category, Variant, or Media hard delete. A later deletion feature must explicitly remove dependents in an approved order or introduce an expand-and-contract policy.

## Rollback Order

```text
1. product_media
2. product_categories
3. product_variants
4. products
5. categories
```

The rollback never uses `CASCADE`.

## Deferred Data

- managed brands;
- multi-market/dated prices;
- inventory and stock reservation;
- campaign snapshots and sale prices;
- search documents;
- outbox and event payloads;
- Product API/JPA mappings;
- category cycle enforcement.
