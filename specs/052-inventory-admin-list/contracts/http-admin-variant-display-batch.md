# HTTP Contract: Product Admin Variant Display Batch

## Request

```http
POST /api/v1/admin/catalog/variants/display-details
Authorization: Bearer <admin-token>
X-Trace-Id: inventory-admin-list-002
Content-Type: application/json

{
  "variantIds": [
    "00000000-0000-0000-0000-000000000001"
  ]
}
```

Rules:

- Between 1 and 100 IDs are accepted.
- Duplicate IDs are normalized before querying.
- Unknown or archived variants return `found=false` entries.

## Success: 200

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "variants": [
      {
        "variantId": "00000000-0000-0000-0000-000000000001",
        "found": true,
        "productId": "00000000-0000-0000-0000-000000000010",
        "productName": "Demo product",
        "variantName": "Standard",
        "sku": "SKU-001",
        "basePrice": "100000",
        "currency": "VND",
        "productStatus": "ACTIVE",
        "variantStatus": "ACTIVE"
      }
    ]
  },
  "timestamp": "2026-09-22T10:30:00Z"
}
```

Invalid body/too many IDs returns a safe 400. The endpoint requires the existing administrator
authorization and echoes `X-Trace-Id`.
