# Internal HTTP Contracts: Regular Checkout Decisions

These routes are Kubernetes-internal and are never exposed through API Gateway. All requests use a
short-lived client-credentials JWT with internal audience/type, exact `sub=order-service`, and the
route-specific scope. W3C trace headers are propagated.

Common success/error envelopes follow `common-web`. Error bodies contain no token, secret, raw SQL,
stack trace, or another shopper's data.

## Cart checkout snapshot

**Owner**: Cart Service
**Consumer**: Order Service only
**Scope**: `cart.checkout-snapshot.read`

### `POST /internal/v1/cart-checkout-snapshots`

Request:

```json
{
  "shopperId": "8090d071-cce3-4384-9009-394ae9a6bb75"
}
```

Response `200`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Cart checkout snapshot retrieved",
  "data": {
    "cartId": "b5bfd90f-55b6-45f5-84e0-0f9ecb51fe21",
    "ownerId": "8090d071-cce3-4384-9009-394ae9a6bb75",
    "cartVersion": 12,
    "capturedAt": "2026-09-03T04:30:00Z",
    "items": [
      {
        "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
        "quantity": 1,
        "itemVersion": 10
      }
    ]
  },
  "timestamp": "2026-09-03T04:30:00Z"
}
```

Rules:

- Cart resolves by `ownerId`; it never accepts a Cart ID selected by the browser.
- Empty Cart returns `409 CART_EMPTY`; absent Cart returns `404 CART_NOT_FOUND`.
- The response is a point-in-time value, not a lock. Order compares it to the public request.
- Product display enrichment is not called and no price field appears.
- Wrong subject, audience, type, or scope returns `403`; invalid token returns `401`.

## Product authoritative purchase quote

**Owner**: Product Service
**Consumer**: Order Service only
**Scope**: `catalog.purchase-quote.read`

### `POST /internal/v1/catalog/variants/purchase-quotes`

Request:

```json
{
  "variantIds": [
    "7c71ef3a-f33e-4a0d-a169-851972afe842",
    "1a3cbb34-8d11-4a62-bf9c-d5065f76f185"
  ]
}
```

Response `200`:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Purchase quotes retrieved",
  "data": {
    "quotes": [
      {
        "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
        "found": true,
        "sellable": true,
        "unavailableReason": null,
        "productId": "53a97606-6200-46a8-859b-8d97290035fd",
        "sku": "PHONE-BLACK-128",
        "productName": "Phone",
        "variantName": "Black / 128 GB",
        "unitPrice": 179000.0000,
        "currency": "VND",
        "catalogVersion": 7
      }
    ]
  },
  "timestamp": "2026-09-03T04:30:00Z"
}
```

Rules:

- Input is non-empty, unique, and contains at most 20 variant IDs; Order sorts IDs before call.
- Exactly one result is returned for each requested ID in deterministic ID order.
- `found=false` uses null commercial fields and never leaks internal deletion/publication details.
- `unitPrice/currency/sellable` are the only acceptance source; Cart values are ignored.
- This endpoint does not reserve Product or Inventory state.
- Existing `/internal/v1/catalog/variants/display-details` remains Cart-only and unchanged.

## Inventory atomic regular-stock hold

**Owner**: Inventory Service
**Consumer**: Order Service only
**Scope**: `inventory.regular-hold.write`

### `POST /internal/v1/regular-stock-holds`

Request:

```json
{
  "holdId": "da29aa23-c4ae-4d62-9fd0-298c1ef952c4",
  "purchaseRequestId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
  "orderId": "cb52a787-d452-4e57-817b-ee3e5e972af9",
  "shopperId": "8090d071-cce3-4384-9009-394ae9a6bb75",
  "requestedAt": "2026-09-03T04:30:00Z",
  "items": [
    {
      "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
      "quantity": 1
    }
  ]
}
```

Order does not supply `expiresAt`; Inventory applies its configured, contract-tested five-minute
policy to its own current time. `requestedAt` is trace/audit input and must fall within an approved
clock-skew bound; it cannot extend the hold.

Response `201` (new) or `200` (equivalent replay):

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Regular stock held",
  "data": {
    "holdId": "da29aa23-c4ae-4d62-9fd0-298c1ef952c4",
    "purchaseRequestId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
    "orderId": "cb52a787-d452-4e57-817b-ee3e5e972af9",
    "status": "HELD",
    "expiresAt": "2026-09-03T04:35:00Z",
    "items": [
      {
        "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
        "quantity": 1
      }
    ]
  },
  "timestamp": "2026-09-03T04:30:00Z"
}
```

Atomic rejection `409 INSUFFICIENT_STOCK`:

```json
{
  "success": false,
  "errorCode": "INSUFFICIENT_STOCK",
  "message": "Regular stock is insufficient for the submitted purchase",
  "errors": [
    {
      "field": "items[0].quantity",
      "message": "Requested quantity exceeds current regular availability"
    }
  ],
  "timestamp": "2026-09-03T04:30:00Z"
}
```

Rules:

- Inventory aggregates duplicate variant IDs defensively, locks all Inventory rows in ascending
  variant order, validates all quantities, and commits all hold items or none.
- Unique `purchaseRequestId` plus canonical hold fingerprint is the idempotency boundary.
- Same request/fingerprint returns the original hold even if replay arrives later in its lifecycle;
  response status reflects the current durable hold state.
- Same request/different identity or items returns `409 HOLD_IDENTITY_CONFLICT` without mutation.
- Unknown Inventory variant returns `409 INVENTORY_ITEM_NOT_FOUND`; no partial hold remains.
- Order must not treat a timeout as rejection. It retries the same request/IDs or resumes from its
  durable intake state.

## Optional recovery read

### `GET /internal/v1/regular-stock-holds/by-purchase-request/{purchaseRequestId}`

This Order-only endpoint may be implemented for operator/recovery clarity. It returns the same hold
projection or `404 REGULAR_HOLD_NOT_FOUND`. Correctness does not depend on it because create is
idempotent; if implemented, it uses `inventory.regular-hold.write` in this MVP to avoid an unused
fourth scope.

## HTTP client policy

| Client | Connect timeout | Read timeout | Automatic retry |
|---|---:|---:|---|
| Order -> Cart snapshot | bounded by five-second public budget | bounded by remaining budget | None; use-case retry resumes same intake. |
| Order -> Product quote | bounded by five-second public budget | bounded by remaining budget | None; safe orchestration retry only. |
| Order -> Inventory hold | bounded by five-second public budget | bounded by remaining budget | No blind Feign retry; explicit same-ID retry/resume only. |

Circuit-breaker or new retry libraries are not introduced by this plan. Existing HTTP error mapping
must distinguish business 4xx from transient 5xx/timeout and never copy a raw downstream body into
the public response.
