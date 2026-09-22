# Data Model: Inventory Admin List and Product Display Lookup

## InventoryListItemResult

Read-only application result owned by Inventory:

- `variantId: UUID` — stable Product variant reference
- `skuSnapshot: String` — Inventory-owned SKU snapshot
- `onHandQuantity: long`
- `campaignAllocatedQuantity: long`
- `availableQuantity: long`
- `updatedAt: Instant`

No Product name or JPA entity is stored in this result.

## AdminVariantDisplayResult

Read-only application result owned by Product:

- `variantId: UUID`
- `found: boolean`
- `productId: UUID?`
- `productName: String?`
- `variantName: String?`
- `sku: String?`
- `basePrice: String?`
- `currency: String?`
- `productStatus: String?`
- `variantStatus: String?`

Unknown or archived variants are represented without failing other results.

## Relationships

`InventoryListItemResult.variantId` joins to `AdminVariantDisplayResult.variantId` only at the
frontend read-composition boundary. This is an API relationship, not a database relationship across
services.
