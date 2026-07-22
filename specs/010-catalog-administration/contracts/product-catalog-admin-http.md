# HTTP Contract: Product Catalog Administration

Base path exposed through api-gateway:

```text
/api/v1/admin/catalog
```

All endpoints require a valid JWT with `CATALOG_ADMIN`. Gateway validates and routes the request;
product-service revalidates the authority before executing a use case. Actor identity comes from the
verified JWT security context. Caller-supplied actor headers are not an identity source and are
ignored or removed at the edge.

Runtime JWT/JWKS trust is a prerequisite owned by `authentication-service`; Feature 010 does not
implement login, token issuance, signing keys, or key rotation. The blocked prerequisite and the
minimum future decisions are recorded in
[authentication-jwt-jwks-prerequisite.md](authentication-jwt-jwks-prerequisite.md). Until that
separate Authentication feature is approved and implemented, real external admin E2E remains
blocked and this contract is verified with approved test-only JWT support.

Required headers for administration requests:

| Header | Required | Meaning |
|--------|----------|---------|
| `Authorization` | yes | Bearer JWT with `CATALOG_ADMIN` |
| `X-Trace-Id` | yes for every admin endpoint | Non-blank trace/correlation identity, maximum 128 characters |
| `Idempotency-Key` | yes for create/lifecycle, no for list/detail/composition | Replay key retained for 7 days |
| `If-Match` | yes for update/lifecycle, no for create/list/detail | Expected Product version |

Within the 7-day replay window, the same actor, key, command, and canonical request payload replay
the original stored outcome. Reusing the same actor/key for a different request within that window
returns `IDEMPOTENCY_KEY_REUSED`. At or after expiry, the old outcome is not replayed and the same
actor may reuse the key for a fresh command; normal validation and conflict rules apply, and prior
audit history remains retained.

Responses that return a Product include `version`.

## GET `/api/v1/admin/catalog/products`

Browse Products for administration, including public and non-public states.

### Query Parameters

| Name | Required | Meaning |
|------|----------|---------|
| `status` | no | `DRAFT`, `ACTIVE`, `INACTIVE`, or `ARCHIVED` |
| `q` | no | Trimmed, case-insensitive literal substring search across code, slug, name, and Variant SKU; `%` and `_` are ordinary characters, not caller-controlled wildcards |
| `page` | no | Zero-based page number. Default `0` |
| `size` | no | Page size from `1` to `100`. Default `20` |

Results are ordered by Product `updated_at` descending, then Product `id` ascending. The second key
makes paging deterministic when multiple Products have the same update timestamp.

### 200 Response

```json
{
  "data": [
    {
      "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
      "code": "PROD-001",
      "slug": "iphone-15",
      "name": "iPhone 15",
      "status": "DRAFT",
      "publishedAt": null,
      "version": 0
    }
  ],
  "page": {
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

## GET `/api/v1/admin/catalog/products/{productId}`

View full Product administration detail.

### 200 Response

```json
{
  "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
  "code": "PROD-001",
  "slug": "iphone-15",
  "name": "iPhone 15",
  "shortDescription": "Apple smartphone",
  "description": "Long description",
  "status": "DRAFT",
  "publishedAt": null,
  "version": 0,
  "variants": [
    {
      "id": "3370ff93-577c-4559-ac66-797a9f4bb499",
      "sku": "IPHONE-15-BLACK",
      "barcode": null,
      "name": "Black",
      "basePrice": "19990000.0000",
      "currency": "VND",
      "status": "ACTIVE",
      "sortOrder": 0
    }
  ],
  "categories": [
    {
      "id": "b0be67d2-dabb-40ef-a239-75d6ff0be6cc",
      "primary": true,
      "sortOrder": 0
    }
  ],
  "media": [
    {
      "id": "e6ffef17-82c1-4c0c-a110-b26aac2f3036",
      "variantId": null,
      "mediaType": "IMAGE",
      "url": "https://cdn.example.test/iphone.webp",
      "altText": "iPhone 15",
      "sortOrder": 0,
      "status": "ACTIVE"
    }
  ]
}
```

## POST `/api/v1/admin/catalog/products`

Create a non-public Product draft.

Headers:

```text
Idempotency-Key: create-product-20260719-001
X-Trace-Id: trace-001
```

### Request

```json
{
  "code": "PROD-001",
  "slug": "iphone-15",
  "name": "iPhone 15",
  "shortDescription": "Apple smartphone",
  "description": "Long description"
}
```

### 201 Response

```json
{
  "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
  "status": "DRAFT",
  "version": 0
}
```

Retry with the same actor, idempotency key, and request body within 7 days returns the original
stored outcome. Same actor/key with a different body returns `IDEMPOTENCY_KEY_REUSED`.

## PUT `/api/v1/admin/catalog/products/{productId}/composition`

Replace the approved Product composition atomically: Product content, Variants/base VND prices,
existing Category memberships, and media URL metadata.

Headers:

```text
If-Match: 3
X-Trace-Id: trace-002
```

### Request

```json
{
  "name": "iPhone 15",
  "shortDescription": "Apple smartphone",
  "description": "Long description",
  "variants": [
    {
      "id": "3370ff93-577c-4559-ac66-797a9f4bb499",
      "sku": "IPHONE-15-BLACK",
      "barcode": null,
      "name": "Black",
      "basePrice": "19990000.0000",
      "currency": "VND",
      "status": "ACTIVE",
      "sortOrder": 0
    }
  ],
  "categories": [
    {
      "id": "b0be67d2-dabb-40ef-a239-75d6ff0be6cc",
      "primary": true,
      "sortOrder": 0
    }
  ],
  "media": [
    {
      "id": "e6ffef17-82c1-4c0c-a110-b26aac2f3036",
      "variantId": null,
      "mediaType": "IMAGE",
      "url": "https://cdn.example.test/iphone.webp",
      "altText": "iPhone 15",
      "sortOrder": 0,
      "status": "ACTIVE"
    }
  ]
}
```

### 200 Response

```json
{
  "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
  "version": 4
}
```

One invalid item rejects the entire command.

## POST `/api/v1/admin/catalog/products/{productId}/publish`

Publish a `DRAFT` or reactivate an `INACTIVE` Product as `ACTIVE`.

Headers:

```text
If-Match: 4
Idempotency-Key: publish-product-001
X-Trace-Id: trace-003
```

### 200 Response

```json
{
  "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
  "status": "ACTIVE",
  "publishedAt": "2026-07-19T10:15:30Z",
  "version": 5
}
```

Publication requires required Product content and at least one active Variant with valid VND base
price. Category status and media presence are not publication prerequisites.

## POST `/api/v1/admin/catalog/products/{productId}/deactivate`

Move an `ACTIVE` Product to `INACTIVE`.

Headers:

```text
If-Match: 5
Idempotency-Key: deactivate-product-001
X-Trace-Id: trace-004
```

### 200 Response

```json
{
  "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
  "status": "INACTIVE",
  "version": 6
}
```

## POST `/api/v1/admin/catalog/products/{productId}/archive`

Move a non-archived Product to final `ARCHIVED`.

Headers:

```text
If-Match: 6
Idempotency-Key: archive-product-001
X-Trace-Id: trace-005
```

### 200 Response

```json
{
  "id": "7d1fb8fd-8a65-4fd7-a729-9f6fe7d5c4f7",
  "status": "ARCHIVED",
  "version": 7
}
```

Archived Products remain visible to admin detail/list endpoints and hidden from shopper catalog
results. They cannot be mutated, reactivated, or hard deleted by Feature 010.

## Error Responses

All errors use a stable body:

```json
{
  "code": "STALE_PRODUCT_VERSION",
  "message": "Product version is stale",
  "traceId": "trace-003"
}
```

| HTTP | Code | Meaning |
|------|------|---------|
| 400 | `INVALID_ADMIN_REQUEST` | Request shape, paging, enum, money, or validation failure |
| 401 | `UNAUTHENTICATED` | Missing or invalid authentication |
| 403 | `CATALOG_ADMIN_REQUIRED` | Authenticated caller lacks `CATALOG_ADMIN` |
| 404 | `PRODUCT_NOT_FOUND` | Product target missing |
| 404 | `CATEGORY_NOT_FOUND` | Referenced existing Category missing |
| 409 | `DUPLICATE_PRODUCT_CODE` | Product code already exists |
| 409 | `DUPLICATE_PRODUCT_SLUG` | Product slug already exists |
| 409 | `DUPLICATE_VARIANT_SKU` | Variant SKU already exists |
| 409 | `DUPLICATE_BARCODE` | Barcode already exists |
| 409 | `STALE_PRODUCT_VERSION` | `If-Match` does not match current Product version |
| 409 | `INVALID_LIFECYCLE_TRANSITION` | Transition is not allowed |
| 409 | `IMMUTABLE_PUBLISHED_IDENTIFIER` | Published Product code/slug/SKU change attempted |
| 409 | `ARCHIVED_PRODUCT_IMMUTABLE` | Archived Product mutation attempted |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Same actor/key used with a different request hash |

## Compatibility

This is the first admin contract. It must not change the public shopper contract in
`../009-product-catalog-query/contracts/product-catalog-http.md`. Breaking admin changes require a
versioned path or explicit compatibility update.
