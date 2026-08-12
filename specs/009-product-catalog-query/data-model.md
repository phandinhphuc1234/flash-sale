# Data Model: Product Catalog Query

This feature reads the schema created by feature `008-product-catalog-schema`. It does not add or modify tables.

## Read Concepts

### Product

- **Source table**: `products`
- **Identity**: `id`
- **Catalog identity**: `slug`, `code`
- **Display fields**: `name`, `short_description`, `description`
- **Visibility rule**: visible when `status = 'ACTIVE'`, `published_at` is present and not in the future, and at least one related variant has `status = 'ACTIVE'`.
- **Ordering**: product browse sorts by `published_at DESC, id DESC`.

### Variant

- **Source table**: `product_variants`
- **Identity**: `id`
- **Catalog identity**: `sku`
- **Display fields**: `name`, `attributes`
- **Price fields**: `base_price`, `currency`
- **Eligibility rule**: returned only when `status = 'ACTIVE'`.
- **Ordering**: `sort_order ASC, id ASC`.
- **Important rule**: price is shown only at variant level; no product-level display price is computed.

### Category

- **Source table**: `categories`
- **Identity**: `id`
- **Catalog identity**: `slug`, `code`
- **Display fields**: `name`, `description`
- **Relationships**: `parent_id` for hierarchy, `product_categories` for product membership.
- **Feature rule**: category state does not decide product visibility in this feature.

### Product Category Membership

- **Source table**: `product_categories`
- **Identity**: `(product_id, category_id)`
- **Display fields**: `is_primary`, `sort_order`
- **Usage**: category browse and detail category summaries.

### Product Media

- **Source table**: `product_media`
- **Identity**: `id`
- **Display fields**: `media_type`, `url`, `alt_text`, dimensions, sort order
- **Eligibility rule**: only `status = 'ACTIVE'` media is returned.
- **Feature rule**: media presence does not decide product visibility.

## Response Models

### CategoryResponse

- `id`
- `parentId`
- `slug`
- `name`
- `sortOrder`

### ProductSummaryResponse

- `id`
- `code`
- `slug`
- `name`
- `shortDescription`
- `variants`: active variant price summaries

### ProductDetailResponse

- `id`
- `code`
- `slug`
- `name`
- `shortDescription`
- `description`
- `variants`: active variants with prices
- `categories`: category summaries
- `media`: active media summaries

### PageMetadata

- `number`
- `size`
- `totalElements`
- `totalPages`
- `hasNext`

## Validation Rules

- `page` must be zero or greater.
- `size` must be between 1 and 100.
- Missing category slug returns not found.
- Missing product slug and non-visible product slug return not found.
