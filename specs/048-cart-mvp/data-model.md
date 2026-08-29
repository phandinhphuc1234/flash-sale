# Data Model: Authenticated Cart MVP

## Ownership

`cart-service` exclusively owns this model in `cart_db`. `product-service` remains the source of
truth for every Product display field. No database constraint crosses a service boundary.

## Aggregate: Cart

The Cart is the authenticated shopper's durable pre-order intent. The public contract does not
expose Cart ID or owner ID.

| Field | Type | Rule |
|-------|------|------|
| `id` | UUID | Generated Cart-owned identity; primary key. |
| `ownerId` | UUID | JWT subject converted to canonical UUID; required and globally unique. |
| `createdAt` | instant | Set once in UTC. |
| `updatedAt` | instant | Advances for set, remove, and clear operations that address this Cart. |

### Invariants

- One Cart row at most per authenticated owner.
- Cart ownership is never supplied by client path/query/body data.
- Cart can be logically empty or non-empty; no status column is required.
- Cart does not automatically expire.

## Entity: Cart Item

| Field | Type | Rule |
|-------|------|------|
| `cartId` | UUID | Required reference to the owning Cart; part of primary identity. |
| `variantId` | UUID | Required Product-owned identity value; no cross-database foreign key. |
| `quantity` | integer | Whole number from 1 through 10 inclusive. |
| `createdAt` | instant | Set once in UTC. |
| `updatedAt` | instant | Advances when quantity is replaced. |

### Invariants

- Unique `(cartId, variantId)`; one line per variant.
- Replacing quantity does not create a second line.
- Removing or clearing deletes Cart Item rows but may retain the Cart row.
- Product/price/image/stock/sellability fields are never persisted here.

## Read Model: Cart Product Detail

This is an ephemeral application result obtained from Product Service and is not a Cart table.

| Field | Type | Rule |
|-------|------|------|
| `variantId` | UUID | Must equal one requested Cart item identity. |
| `found` | boolean | Product definitively found or did not find the variant. |
| `sellable` | boolean | Product-owned current sellability; false when not found. |
| `productId` | UUID, nullable | Present when found. |
| `productSlug` | string, nullable | Current Product-owned slug. |
| `productName` | string, nullable | Current Product-owned name. |
| `variantName` | string, nullable | Current Product-owned variant name. |
| `sku` | string, nullable | Current Product-owned SKU. |
| `basePrice` | decimal string, nullable | Display only; never a purchase guarantee. |
| `currency` | string, nullable | Currency paired with current base price. |
| `primaryImageUrl` | URI string, nullable | First current active Product media item when available. |

Cart's web result adds `detailsAvailable`. It is false when the Product query could not be trusted
because of token/network/timeout/authorization/5xx/malformed-response failure. Product fields are
then null. A definitive Product not-found response is distinct from dependency failure.

## Relational Schema

```text
carts
├── id UUID PK
├── owner_id UUID NOT NULL UNIQUE
├── created_at TIMESTAMPTZ NOT NULL
└── updated_at TIMESTAMPTZ NOT NULL

cart_items
├── cart_id UUID NOT NULL FK -> carts(id) ON DELETE CASCADE
├── variant_id UUID NOT NULL
├── quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10)
├── created_at TIMESTAMPTZ NOT NULL
├── updated_at TIMESTAMPTZ NOT NULL
└── PK (cart_id, variant_id)
```

Required indexes are the Cart owner uniqueness index and the `cart_items` primary-key index. No
additional index is required for the MVP access paths.

## State Transitions

```text
No Cart row
  -- first valid set quantity --> Empty Cart row + one Cart Item

Item absent
  -- valid set quantity ------> Item(quantity=N)

Item(quantity=A)
  -- valid set quantity B ----> Item(quantity=B)
  -- remove ------------------> Item absent

Non-empty Cart
  -- clear -------------------> Empty Cart row
```

Product validation precedes every set transition and occurs outside the database transaction.
Remove and clear need no Product lookup.

## Concurrency

- Concurrent first writes for one owner converge on one Cart through `owner_id` uniqueness and
  atomic upsert.
- Concurrent writes for the same variant converge on one Cart Item through the composite primary
  key and atomic upsert; the last committed quantity is visible.
- Same-quantity replay is a semantic no-op even if `updatedAt` advances.
- Remove/clear/set interleavings may produce either valid serial outcome; constraints prevent
  duplicate or out-of-range rows.

## Migration and Rollback

The migration is additive and contains no data backfill. Liquibase records successful execution.
Application rollback retains the tables so a previous Cart shell image can start without data loss.
No automated cloud/local runner drops Cart data. Disposable developer databases may be recreated
only through an explicit user-owned environment reset.
