# HTTP Contract: Inventory Admin Collection

## Request

```http
GET /api/v1/admin/inventory?page=0&size=20
Authorization: Bearer <admin-token>
X-Trace-Id: inventory-admin-list-001
```

Rules:

- `page` is zero-based and defaults to `0`.
- `size` defaults to `20` and must be between `1` and `100`.
- Results are ordered by `updatedAt DESC, variantId ASC`.

## Success: 200

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "data": [
      {
        "variantId": "00000000-0000-0000-0000-000000000001",
        "skuSnapshot": "SKU-001",
        "onHandQuantity": 100,
        "campaignAllocatedQuantity": 20,
        "availableQuantity": 80,
        "updatedAt": "2026-09-22T10:30:00Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "timestamp": "2026-09-22T10:30:00Z"
}
```

## Validation and authorization

- Missing/invalid authentication: existing 401 boundary.
- Authenticated non-admin: existing 403 boundary.
- Invalid page or size: 400 with the Inventory-owned validation error code.
- Response echoes `X-Trace-Id` and never exposes persistence internals.
