# Inventory HTTP Contracts

This is the concise source contract for the Inventory core endpoints. With
`INVENTORY_API_DOCS_ENABLED=true`, Springdoc generates the machine-readable contract at
`/v3/api-docs` and Swagger UI at `/swagger-ui.html`. Update this contract and the controller
annotations before changing an externally observable route, request, response, or status code.

## Admin-through-Gateway

All `/api/v1/admin/inventory/**` endpoints require a valid JWT with `INVENTORY_ADMIN`. Gateway checks
the authority and `inventory-service` revalidates it. These endpoints are not shopper-facing.

### Adjust physical stock

```http
POST /api/v1/admin/inventory/{variantId}/adjustments
Idempotency: requestId in JSON body
```

```json
{
  "requestId": "uuid",
  "type": "INCREASE|DECREASE",
  "quantity": 1000,
  "reason": "Initial warehouse stock"
}
```

### Read inventory

```http
GET /api/v1/admin/inventory/{variantId}
```

Response fields: `variantId`, `skuSnapshot`, `onHandQuantity`, `campaignAllocatedQuantity`, and derived
`availableQuantity`.

### Read movement history

```http
GET /api/v1/admin/inventory/{variantId}/movements?page=0&size=20
```

Ordering is `createdAt DESC, id DESC`. The response uses shared `common-web` `PageResponse<T>` with
`PageMeta`; `size` must be between 1 and 100.

## Internal campaign allocation lifecycle

All internal lifecycle commands require a service-to-service JWT with `SCOPE_INVENTORY_WRITE`. They are
not public Gateway routes. Campaign Service owns release; Flash Sale Service owns reconciliation.

```http
POST /internal/v1/campaign-stock-allocations
POST /internal/v1/campaign-stock-allocations/{requestId}/release
POST /internal/v1/campaign-stock-allocations/{requestId}/reconcile
```

Every command includes `requestId`. Release includes a reason. Reconciliation includes
`soldQuantity` and `returnedQuantity` and must satisfy the exact allocation balance.

## Error mapping

| Condition | Status | Current error code |
|---|---:|
| Missing inventory/allocation | 404 | `INVENTORY_NOT_FOUND` |
| Invalid quantity/request | 400 | `VALIDATION_ERROR` |
| Insufficient stock, duplicate allocation, invalid lifecycle, idempotency conflict | 409 | `INVENTORY_OPERATION_REJECTED` |

Missing/invalid bearer tokens and insufficient authority are handled by Spring Security at the
boundary as `401` and `403` respectively. The Inventory exception handler owns the `400`, `404`, and
`409` envelopes above.
