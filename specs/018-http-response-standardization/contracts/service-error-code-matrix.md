# Service Error Code Ownership Matrix

| Service | Owner | Examples |
|---|---|---|
| Authentication | Authentication bounded context | `AUTH_INVALID_CREDENTIALS`, `AUTH_REFRESH_TOKEN_INVALID` |
| Product | Product catalog/admin features | `PRODUCT_NOT_FOUND`, `DUPLICATE_PRODUCT_CODE`, `INVALID_ADMIN_REQUEST` |
| Campaign | Campaign bounded context | `CAMPAIGN_VALIDATION_FAILED`, `CAMPAIGN_NOT_FOUND` |
| Inventory | Inventory stock/allocation features | `INVENTORY_NOT_FOUND`, `INVENTORY_OPERATION_REJECTED` |
| Gateway | Gateway edge failures | `UNAUTHENTICATED`, `RATE_LIMIT_EXCEEDED`, `DOWNSTREAM_UNAVAILABLE` |

Codes stay local to the owner. `common-web` contains no business-code enum.
