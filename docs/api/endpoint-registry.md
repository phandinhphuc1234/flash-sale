# Endpoint registry

This is the stable registry link used by feature documents and tooling. The complete method/path
inventory, owner, access boundary, and internal-route warnings are maintained in
[`README.md`](README.md). Request and response payloads are maintained in
[`frontend-integration-guide.md`](frontend-integration-guide.md).

The current registry contains **47 unique business endpoints**:

- 39 Gateway-public endpoints for shoppers and administrators;
- 7 service-to-service endpoints that must not be exposed by the Gateway;
- 1 JWKS identity-trust endpoint.

The regular-purchase public commands are:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/orders/cart-checkouts` | Accept one immutable Cart snapshot as one regular Order. |
| `POST` | `/api/v1/orders/buy-now` | Accept one normal variant without mutating Cart. |

The Gateway Swagger selector aggregates the eight service documents only when
`API_DOCS_ENABLED=true`; it never exposes `/internal/**` business routes.
