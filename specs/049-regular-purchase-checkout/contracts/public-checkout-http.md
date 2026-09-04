# Public HTTP Contract: Regular Purchase Checkout

**Owner**: Order Service
**Ingress**: API Gateway only
**Authentication**: shopper JWT; owner is always the JWT `sub`
**Response envelopes**: existing `ApiResponse` / `ApiErrorResponse` shape

## Common headers

Every command requires:

```http
Authorization: Bearer <shopper-access-token>
Idempotency-Key: <opaque 1..128 character value>
Content-Type: application/json
```

- The key is scoped to the authenticated shopper and endpoint canonical purchase content.
- Do not place shopper data, card data, timestamps, or secrets in the key.
- Missing/blank/oversized keys return `400 INVALID_IDEMPOTENCY_KEY`.
- Same shopper/key/same canonical body returns the original result with HTTP `200` and
  `Idempotency-Replayed: true`.
- Same shopper/key/different canonical body returns `409 IDEMPOTENCY_KEY_REUSED`.
- New acceptance returns HTTP `201`, `Location: /api/v1/orders/{orderId}`, and
  `Idempotency-Replayed: false`.
- `X-Trace-Id` is returned and propagated. Responses use `Cache-Control: no-store`.

Prices are JSON decimal numbers, not floating-point calculations in the client. The frontend must
submit the exact decimal/currency last shown and confirmed by the shopper.

## POST `/api/v1/orders/buy-now`

Starts a regular purchase without reading or changing Cart.

### Request

```json
{
  "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
  "quantity": 1,
  "expectedUnitPrice": 179000.0000,
  "currency": "VND"
}
```

Rules:

- `variantId` is required.
- `quantity` is an integer from 1 through 10.
- `expectedUnitPrice` is positive with no more than four decimal places.
- `currency` is exactly three uppercase ASCII letters.
- Cart is never read, reserved, or reconciled for this endpoint.

### Accepted response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Regular purchase accepted",
  "data": {
    "purchaseRequestId": "58d204d2-20b1-47b0-a58f-7a9b1d035588",
    "orderId": "cb52a787-d452-4e57-817b-ee3e5e972af9",
    "source": "BUY_NOW",
    "status": "PENDING_PAYMENT",
    "currency": "VND",
    "totalAmount": 179000.0000,
    "paymentDeadline": "2026-09-03T04:34:30Z",
    "stockHoldExpiresAt": "2026-09-03T04:35:00Z"
  },
  "timestamp": "2026-09-03T04:30:00Z"
}
```

The frontend next calls the existing Payment APIs:

1. `GET /api/v1/payments/by-order/{orderId}`
2. `POST /api/v1/payments/{paymentId}/checkout-sessions`
3. redirect to the returned hosted Checkout URL
4. poll `GET /api/v1/orders/{orderId}` and/or Payment status after return

## POST `/api/v1/orders/cart-checkouts`

Submits exactly the Cart snapshot the shopper reviewed. It does not lock the Cart.

### Request

```json
{
  "cartVersion": 12,
  "items": [
    {
      "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
      "quantity": 1,
      "itemVersion": 10,
      "expectedUnitPrice": 179000.0000,
      "currency": "VND"
    },
    {
      "variantId": "1a3cbb34-8d11-4a62-bf9c-d5065f76f185",
      "quantity": 2,
      "itemVersion": 12,
      "expectedUnitPrice": 49000.0000,
      "currency": "VND"
    }
  ]
}
```

Rules:

- `cartVersion` is non-negative and must equal the current owned Cart version at capture.
- `items` is non-empty and contains each variant exactly once.
- Every item matches the current Cart variant, quantity, and item version.
- Every quantity is 1 through 10 and all currencies match.
- Any missing/extra/changed Cart item rejects the whole submission as `CART_CHANGED`.
- The browser never submits `shopperId`, `cartId`, stock, Order ID, hold ID, or total.

### Accepted response

The response is the same as Buy Now with `source: "CART"` and the sum of all authoritative line
prices. One Order contains all accepted lines and one Payment covers the total.

## Additive Order read fields

`GET /api/v1/orders/{orderId}` retains every existing field and adds:

```json
{
  "purchaseSource": "CART",
  "stockParticipantType": "REGULAR_STOCK_HOLD",
  "stockReferenceId": "da29aa23-c4ae-4d62-9fd0-298c1ef952c4",
  "stockHoldExpiresAt": "2026-09-03T04:35:00Z"
}
```

- Legacy `reservationId` and `campaignId` remain populated for Flash Sale and are `null` for
  regular purchases during the compatibility period.
- The existing `items` array now truthfully returns one or more lines.
- Internal Saga retry/provider details remain absent.
- `GET /api/v1/orders` adds `purchaseSource` to each summary; existing fields remain.

## Price conflict response

HTTP `409` uses the standard error keys plus a typed `currentPrices` extension required for explicit
shopper review:

```json
{
  "success": false,
  "errorCode": "PRICE_CHANGED",
  "message": "One or more prices changed. Review current prices and submit a new request.",
  "errors": null,
  "currentPrices": [
    {
      "variantId": "7c71ef3a-f33e-4a0d-a169-851972afe842",
      "currentUnitPrice": 189000.0000,
      "currency": "VND"
    }
  ],
  "timestamp": "2026-09-03T04:30:00Z"
}
```

This response creates no Order, Payment command, or lasting hold. The frontend must update the
display and require a new explicit submission, normally with a new idempotency key.

## Error contract

| HTTP | `errorCode` | Meaning / retry |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Malformed field, duplicate variant, mixed currency, empty Cart submission. Correct input. |
| 400 | `INVALID_IDEMPOTENCY_KEY` | Missing/invalid key. Generate a stable key per user action. |
| 401 | `UNAUTHENTICATED` | Missing/invalid shopper token. Reauthenticate. |
| 403 | `ACCESS_DENIED` | Token is valid but cannot use the shopper endpoint. |
| 404 | `CART_NOT_FOUND` | Shopper has no Cart for Cart checkout. |
| 404 | `VARIANT_NOT_FOUND` | At least one Product variant does not exist. No partial acceptance. |
| 409 | `CART_CHANGED` | Current Cart no longer equals submitted versions/items. Reload Cart. |
| 409 | `PRICE_CHANGED` | Product price/currency differs; structured current prices are included. Review/resubmit. |
| 409 | `VARIANT_NOT_SELLABLE` | Product currently cannot be sold normally. |
| 409 | `INSUFFICIENT_STOCK` | Inventory rejected the atomic hold; shopper-safe variants/available quantities may be included. |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Same shopper/key was bound to different canonical content. Use a new key. |
| 409 | `PURCHASE_RECOVERY_REQUIRED` | Existing same-key request has an ambiguous durable checkpoint. Do not create another action; retry the same request or query support status. |
| 503 | `CHECKOUT_DEPENDENCY_UNAVAILABLE` | Cart/Product/Inventory unavailable before acceptance. Retry same key/body. |
| 503 | `REGULAR_PURCHASE_DISABLED` | Safe deployment/rollback feature flag is off. Do not assume acceptance. |

No error response includes another shopper's data, service token, provider details, raw downstream
body, Checkout URL, or secret.

## Frontend rules

- Generate one UUID-like idempotency key when the shopper clicks; retain it across network retries
  of the same unchanged body.
- Do not automatically reuse the key after Cart or price changes.
- Disable duplicate clicks visually, but rely on server idempotency for correctness.
- Treat Cart prices as display/confirmation values only.
- Never call Cart/Product/Inventory internal endpoints or the Stripe webhook.
- Buy Now does not add/remove Cart content. Cart checkout clears matching items only after the
  backend confirms payment and stock; the UI should refresh Cart after Order confirmation.
