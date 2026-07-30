# Inventory Campaign Allocation Compatibility Contract

**Owner**: Inventory Service  
**Caller**: Campaign Service only  
**Existing endpoint**: `POST /internal/v1/campaign-stock-allocations`

Feature 017 reuses the existing endpoint. It does not create a Campaign-specific duplicate.

## Request

```http
POST /internal/v1/campaign-stock-allocations
Authorization: Bearer <campaign-service-access-token>
X-Trace-Id: <trace-id>
Content-Type: application/json
```

```json
{
  "requestId": "a6f59a87-f967-4e88-bdd4-f5bbceae0e86",
  "campaignId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
  "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57",
  "quantity": 1000,
  "reason": "CAMPAIGN_SCHEDULE"
}
```

Token requirements change only for this allocation endpoint: approved issuer/JWKS, subject
`campaign-service`, audience `flash-sale-internal-api`, and
`SCOPE_inventory.campaign.allocate`. The broad `SCOPE_INVENTORY_WRITE` no longer authorizes this
operation after the compatibility rollout.

The `requestId` is created once with the Campaign schedule operation. Retrying/recovering never
creates a replacement ID.

## Existing success envelope

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Allocation accepted",
  "data": {
    "id": "21aa1d8a-3037-4f2c-aa0e-19ee4f2901ef",
    "requestId": "a6f59a87-f967-4e88-bdd4-f5bbceae0e86",
    "campaignId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
    "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57",
    "allocatedQuantity": 1000,
    "soldQuantity": 0,
    "returnedQuantity": 0,
    "status": "ACTIVE"
  },
  "timestamp": "2026-07-30T02:00:00Z"
}
```

Campaign's HTTP adapter maps `data.id` to its `inventoryAllocationId`. It verifies `requestId`,
Campaign ID, Variant ID, allocated quantity, and active status before finalization.

## Idempotency guarantees

- same `requestId` and same `(campaignId, variantId, quantity)` returns the same allocation;
- same `requestId` with different payload is rejected;
- `(campaignId, variantId)` has at most one active allocation according to Inventory's approved
  model;
- Inventory item lock, stock update, movement, allocation, and Inventory outbox remain in one
  Inventory transaction;
- Campaign never calculates available physical stock.

## Required stable Inventory errors

Feature 017 refines the existing generic message-based error classification so Campaign can map
failures without parsing text:

| Inventory response | Campaign response |
|---|---|
| 404 `INVENTORY_NOT_FOUND` | 409 `INVENTORY_ALLOCATION_REJECTED` |
| 409 `INVENTORY_INSUFFICIENT_STOCK` | 409 `INVENTORY_INSUFFICIENT_STOCK` |
| 409 `INVENTORY_ALLOCATION_REQUEST_CONFLICT` | 409 `INVENTORY_ALLOCATION_CONFLICT` |
| other 409 `INVENTORY_OPERATION_REJECTED` | 409 `INVENTORY_ALLOCATION_REJECTED` |
| 401/403 token rejection | 503 `INVENTORY_SERVICE_UNAVAILABLE` plus secure operational log/metric |
| timeout/connect/5xx | 503 `INVENTORY_SERVICE_UNAVAILABLE` |

Existing Inventory admin/release/reconcile behavior remains unchanged. The future release endpoint
will use `SCOPE_inventory.campaign.release` only when a release feature is separately approved.
