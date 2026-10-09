# UI State Model: Storefront Active Navigation

No durable business entity or persistence change is introduced.

## Navigation destination

| Field | Meaning | Values |
|---|---|---|
| `key` | Stable presentation identity | `home`, `shop`, `cart` |
| `href` | Real storefront destination | `/`, `/all-products`, `/cart` |
| `label` | Shopper-facing short label | `Home`, `Shop`, `Cart` |
| `active` | Whether the current route belongs to the destination | boolean |

## Route classification

```text
pathname -> classifyStorefrontRoute(pathname) -> destination key or null
```

Invariants:

- at most one destination is active;
- Home matches only `/`;
- prefixes match only complete route segments;
- unknown/account/purchase-follow-up/seller routes return `null`;
- visual active state and `aria-current` derive from the same result.
