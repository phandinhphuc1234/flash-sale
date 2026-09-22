# HTTP Contract: Catalog Discovery

## Public endpoint

`GET /api/v1/catalog/products`

### Query parameters

| Name | Type | Required | Default | Rules |
|---|---|---:|---|---|
| `q` | string | no | absent | Trimmed, case-insensitive, max 100 Unicode code points |
| `categorySlug` | string | no | absent | URL-safe category slug; descendants included |
| `minPrice` | decimal | no | absent | `>= 0`, same VND unit used by existing catalog prices |
| `maxPrice` | decimal | no | absent | `>= minPrice` when both are present |
| `sort` | enum | no | `NEWEST` | `RELEVANCE`, `NEWEST`, `PRICE_ASC`, `PRICE_DESC` |
| `page` | integer | no | `0` | `>= 0`, zero-based |
| `size` | integer | no | `20` | `1..50` |

`RELEVANCE` requires a non-empty `q`. If it is requested without `q`, the server
returns `INVALID_CATALOG_REQUEST` rather than silently claiming relevance.

### Examples

```http
GET /api/v1/catalog/products?q=headphones&categorySlug=audio&sort=PRICE_ASC&page=0&size=20
```

```http
GET /api/v1/catalog/products?minPrice=100000&maxPrice=500000&sort=NEWEST&page=1&size=20
```

### Response

The existing paged catalog response envelope and existing product summary fields
remain unchanged. The server may add optional metadata only after updating the
canonical API documentation. Older clients must ignore unknown fields.

### Errors

- `400 INVALID_CATALOG_REQUEST`: malformed, out-of-range, or contradictory query
  parameters.
- `404 CATEGORY_NOT_FOUND`: the requested category is absent or inactive.
- `401/403`: only where the existing route/security policy already requires it;
  public catalog browsing remains anonymous.

Errors use the repository's standard API error envelope and never expose SQL or
stack traces.

## Existing category endpoint

`GET /api/v1/catalog/categories?parentId=<optional-uuid>` remains compatible. The
frontend uses it to render the category tree and links the selected slug back to
the products endpoint.

