# Endpoint registry

This is the stable registry link used by feature documents and tooling. The complete method/path
inventory, owner, access boundary, and internal-route warnings are maintained in
[`README.md`](README.md). Request and response payloads are maintained in
[`frontend-integration-guide.md`](frontend-integration-guide.md).

The current registry contains **55 unique business endpoints**:

- 44 Gateway-public endpoints for shoppers and administrators;
- 10 service-to-service endpoints that must not be exposed by the Gateway;
- 1 JWKS identity-trust endpoint.

Authentication also exposes `GET /api/v1/auth/me` as a no-store, authenticated account-summary
read. It contains only safe identity and authority fields; tokens, refresh credentials, passwords,
and session identifiers are not part of the response.
Authentication also exposes `PATCH /api/v1/auth/me` for the authenticated username only; email
changes remain outside this contract.
Authentication also exposes `PATCH /api/v1/auth/me/profile` for visible username and contact
profile fields; email, role, status, and security fields remain read-only.

The regular-purchase public commands are:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/orders/cart-checkouts` | Accept one immutable Cart snapshot as one regular Order. |
| `POST` | `/api/v1/orders/buy-now` | Accept one normal variant without mutating Cart. |

Those public commands coordinate the internal Cart snapshot, Product purchase quote, and Inventory
regular-hold contracts documented by Feature 049. They remain unreachable through Gateway.

The Gateway Swagger selector aggregates the eight service documents only when
`API_DOCS_ENABLED=true`; it never exposes `/internal/**` business routes.
