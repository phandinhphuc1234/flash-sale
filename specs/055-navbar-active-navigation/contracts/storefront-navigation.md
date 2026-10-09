# Storefront Navigation UI Contract

## Destinations

| Destination | Canonical href | Active route family |
|---|---|---|
| Home | `/` | `/` only |
| Shop | `/all-products` | `/all-products`, `/all-products/**`, `/product/**` |
| Cart | `/cart` | `/cart`, `/cart/**` |

Flash Sale and Help are not destinations in this contract because no real pages currently exist.

## Selected-state contract

For the active destination:

- render the persistent theme-consistent indicator;
- expose `aria-current="page"` on the actionable element;
- preserve its label and destination;
- keep keyboard focus separately visible.

For a route outside the table, no primary destination is selected.

## Compatibility

- Account menu, login, and Cart-count behavior remain unchanged.
- No API, backend route, payload, or authorization behavior changes.
- Seller Operations Console navigation is not affected.
