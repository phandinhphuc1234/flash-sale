# HTTP Contract: Authenticated Cart API

## Boundary

| Property | Value |
|----------|-------|
| Owner | `cart-service` |
| Public prefix | `/api/v1/cart` |
| Ingress | API Gateway only |
| Authentication | Public shopper bearer JWT; issuer/signature/expiry/audience/type/subject revalidated by Cart |
| Success envelope | `ApiResponse<T>` except `204 No Content` |
| Failure envelope | `ApiErrorResponse` |
| Trace | Request/response `X-Trace-Id`; W3C trace context remains propagatable |
| Content type | `application/json` where a body exists |

Cart owner and Cart ID are never accepted from request input and are never exposed in response data.

## Response models

### CartResponse

```json
{
  "items": [],
  "distinctItemCount": 0,
  "totalQuantity": 0,
  "updatedAt": null
}
```

`updatedAt` is null when no durable Cart exists. Items are returned in stable `updatedAt` descending,
then `variantId` ascending order.

### CartItemResponse

```json
{
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "quantity": 2,
  "detailsAvailable": true,
  "sellable": true,
  "unavailableReason": null,
  "productId": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
  "productSlug": "flash-sale-shirt",
  "productName": "Flash Sale Shirt",
  "variantName": "Black / M",
  "sku": "FSS-BLK-M",
  "basePrice": "299000.0000",
  "currency": "VND",
  "primaryImageUrl": "https://example.test/products/fss-black-m.jpg",
  "updatedAt": "2026-08-29T00:00:00Z"
}
```

Rules:

- `basePrice` is display-only and is never a purchase guarantee.
- `detailsAvailable=false` means Product could not be trusted/reached; every Product-owned field,
  including `sellable`, is null and `unavailableReason=PRODUCT_DETAILS_UNAVAILABLE`.
- A definitive missing Product result uses `detailsAvailable=false`, `sellable=false`, Product fields
  null, and `unavailableReason=PRODUCT_NOT_FOUND`; unlike a dependency failure, this is a trusted
  Product-owned absence decision.
- A found but non-sellable variant uses current Product fields, `sellable=false`, and
  `unavailableReason=PRODUCT_NOT_SELLABLE`.

## GET `/api/v1/cart`

Returns the authenticated shopper's Cart. An absent Cart is a successful empty Cart and does not
create durable state.

### Success — `200 OK`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "items": [],
    "distinctItemCount": 0,
    "totalQuantity": 0,
    "updatedAt": null
  },
  "timestamp": "2026-08-29T00:00:00Z"
}
```

Product dependency failure does not change this status; affected item details degrade according to
the response rules above.

## PUT `/api/v1/cart/items/{variantId}`

Creates or replaces one desired quantity. This operation is idempotent by absolute replacement.

### Request

```json
{
  "quantity": 2
}
```

`variantId` must be a canonical UUID. `quantity` is required and must be an integer from 1 through
10 inclusive. Zero does not mean delete; clients use DELETE.

### Success — `200 OK`

Returns `ApiResponse<CartItemResponse>` for the persisted item using the Product result verified
before mutation.

### Failures

| Status | Error code | Meaning |
|--------|------------|---------|
| 400 | `CART_VALIDATION_ERROR` | Invalid UUID/body/quantity or malformed JSON. |
| 401 | `UNAUTHENTICATED` | Missing, invalid, expired, wrong-audience, wrong-type, or subjectless shopper token. |
| 404 | `CART_VARIANT_NOT_FOUND` | Product definitively reports the variant absent; Cart is unchanged. |
| 409 | `CART_VARIANT_NOT_SELLABLE` | Product reports the variant cannot currently be sold; Cart is unchanged. |
| 503 | `CART_PRODUCT_DEPENDENCY_UNAVAILABLE` | Product token/network/timeout/auth/5xx/malformed response failure; Cart is unchanged. |
| 500 | `CART_INTERNAL_ERROR` | Safe unexpected Cart failure; internal detail is not exposed. |

## DELETE `/api/v1/cart/items/{variantId}`

Removes the matching item owned by the authenticated shopper. Missing items are successful no-ops.

### Success — `204 No Content`

The response body is empty.

### Failures

`400 CART_VALIDATION_ERROR`, `401 UNAUTHENTICATED`, and safe `500 CART_INTERNAL_ERROR` apply.

## DELETE `/api/v1/cart`

Clears all items owned by the authenticated shopper. An absent or empty Cart is a successful no-op.

### Success — `204 No Content`

The response body is empty.

### Failures

`401 UNAUTHENTICATED` and safe `500 CART_INTERNAL_ERROR` apply.

## Security and caching

- Cart responses use `Cache-Control: no-store` because they are user-specific mutable data.
- The service never accepts an owner/user ID parameter and never returns another shopper's state.
- Authorization headers, JWT claims beyond safe subject correlation, Product tokens, and secrets are
  never returned or logged.
