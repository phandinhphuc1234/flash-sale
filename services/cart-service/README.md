# Cart Service

Cart Service owns a shopper's persistent purchase intent. It does not own sellability, final price,
stock, Order creation, or payment.

## Owned capabilities

- get the authenticated shopper's Cart;
- set an absolute item quantity (1–10), remove one item, or clear the Cart;
- retain `cartVersion` and per-item `itemVersion` for concurrency-safe checkout;
- enrich responses from Product display details through an internal service token;
- return an immutable checkout snapshot to Order;
- reconcile only unchanged purchased lines after a successful Cart checkout.

## Boundaries

| Direction | Contract |
|---|---|
| Browser via Gateway | `GET /api/v1/cart`, `PUT/DELETE /api/v1/cart/items/{variantId}`, `DELETE /api/v1/cart` |
| Order -> Cart | `POST /internal/v1/cart-checkout-snapshots` |
| Cart -> Product | `POST /internal/v1/catalog/variants/display-details` |
| Order -> Cart | `ReconcilePurchasedCartSnapshotV1` on `flashsale.cart.checkout.commands.v1` |

The shopper JWT subject is the owner. Clients never choose `ownerId` or `cartId`.

## Checkout model

```text
Cart intent + versions -> Order obtains internal snapshot
                       -> Product authoritative quote
                       -> Inventory hold
Payment success        -> Order emits reconciliation command
Cart consumes command  -> removes only lines whose variant/quantity/version still match
```

This prevents a late payment event from deleting items the shopper changed after checkout began.

## Data, configuration, and tests

Cart owns `cart_db` and its Liquibase changelog. Important settings include datasource credentials,
`CART_MAX_QUANTITY`, Product/OAuth URLs and client secret, JWT trust, Kafka/Schema Registry, and the
reconciliation topic/group/DLT switches.

```powershell
.\mvnw.cmd -pl services/cart-service -am verify
```

See [`docs/api/cart-frontend-ai-handoff.md`](../../docs/api/cart-frontend-ai-handoff.md) for the UI
contract and [`specs/049-regular-purchase-checkout/`](../../specs/049-regular-purchase-checkout/) for
regular Cart checkout decisions.

## Troubleshooting

- `503 CART_PRODUCT_UNAVAILABLE`: Cart persisted intent but Product enrichment failed; do not treat
  it as an authentication failure.
- `CART_CHANGED`: refresh Cart; versions no longer match the submitted checkout snapshot.
- A paid item remains in Cart: confirm the reconciliation consumer is enabled and inspect its DLT;
  unchanged-line matching is deliberately strict.
