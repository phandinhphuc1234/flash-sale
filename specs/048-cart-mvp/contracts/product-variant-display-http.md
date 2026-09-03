# HTTP Contract: Product Variant Display Batch Query

## Boundary

| Property | Value |
|----------|-------|
| Owner | `product-service` |
| Consumer | `cart-service` only for this version |
| Method/path | `POST /internal/v1/catalog/variants/display-details` |
| Authentication | OAuth2 client credentials |
| Required subject | `cart-service` |
| Required audience | `flash-sale-internal-api` |
| Required scope | `catalog.variant-display.read` |
| Success envelope | `ApiResponse<VariantDisplayBatchResponse>` |
| Failure envelope | `ApiErrorResponse` |
| Trace | `X-Trace-Id` and W3C trace context |
| Retry | None |

The endpoint is internal, is not routed by API Gateway, and queries only Product-owned data.

## Request

```json
{
  "variantIds": [
    "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
    "06a5de75-f305-49d1-b11e-5ef372bf20d3"
  ]
}
```

Rules:

- `variantIds` is required and may be empty.
- Each value must be a UUID.
- Duplicate IDs are normalized to one result, preserving the order of first appearance.
- No Cart owner, quantity, user token, stock, campaign, order, or payment data is accepted.

## Success — `200 OK`

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "variants": [
      {
        "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
        "found": true,
        "sellable": true,
        "productId": "0e9e1ff4-17b9-43f0-971b-88dfdae04aa3",
        "productSlug": "flash-sale-shirt",
        "productName": "Flash Sale Shirt",
        "variantName": "Black / M",
        "sku": "FSS-BLK-M",
        "basePrice": "299000.0000",
        "currency": "VND",
        "primaryImageUrl": "https://example.test/products/fss-black-m.jpg"
      },
      {
        "variantId": "06a5de75-f305-49d1-b11e-5ef372bf20d3",
        "found": false,
        "sellable": false,
        "productId": null,
        "productSlug": null,
        "productName": null,
        "variantName": null,
        "sku": null,
        "basePrice": null,
        "currency": null,
        "primaryImageUrl": null
      }
    ]
  },
  "timestamp": "2026-08-29T00:00:00Z"
}
```

The response contains exactly one result for every unique requested ID and preserves first-seen
request order. Missing variants are data outcomes inside `200`, allowing a batch to contain both
found and missing IDs. `sellable` is calculated by Product-owned lifecycle rules. No stock or
campaign availability claim is returned.

## Failures

| Status | Error code | Meaning |
|--------|------------|---------|
| 400 | `PRODUCT_VARIANT_DISPLAY_VALIDATION_ERROR` | Missing body, null/invalid list entry, or malformed JSON. |
| 401 | `UNAUTHENTICATED` | Missing or invalid internal token. |
| 403 | `CATALOG_VARIANT_DISPLAY_SCOPE_REQUIRED` | Wrong audience, subject, or scope. |
| 500 | `PRODUCT_INTERNAL_ERROR` | Safe unexpected Product failure. |

Product does not return 404 for an individual ID in a valid batch. Cart maps Product 401/403/5xx,
timeout, connection failure, and malformed/identity-mismatched success response to its approved
dependency-unavailable behavior.

## Compatibility

This is an additive v1 internal endpoint. It does not modify the existing Campaign validation or
public catalog contracts. Future fields may be added compatibly; removal, rename, type change,
scope change, or semantic change requires an approved contract migration.
