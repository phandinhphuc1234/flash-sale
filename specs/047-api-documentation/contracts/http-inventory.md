# HTTP Endpoint Inventory Contract

This feature documents existing supported behavior; it does not authorize a method, path, body,
status, header, or security change. One endpoint is one unique `HTTP method + normalized path` pair.

## Counting rules

- Included: supported business HTTP endpoints, approved internal service contracts, Stripe webhook,
  JWKS trust publication, and the approved OAuth client-credentials token endpoint.
- Excluded: Actuator, Swagger/OpenAPI, `/error`, CORS preflight, and unsupported framework handlers.
- `Gateway-public` means a matching route exists in API Gateway; it does not mean authentication is
  optional.
- `Internal` endpoints must never be added as public Gateway business routes.
- Conditional endpoints remain implemented contracts even if a runtime feature flag currently
  disables their controller.

## Supported endpoint catalog

| ID | Owner | Boundary | Method | Path | Access | Purpose |
|---:|---|---|---|---|---|---|
| API-001 | Authentication | Gateway-public | POST | `/api/v1/auth/register` | Anonymous | Register a shopper account |
| API-002 | Authentication | Gateway-public | POST | `/api/v1/auth/login` | Anonymous | Create a user session and access token |
| API-003 | Authentication | Gateway-public | POST | `/api/v1/auth/refresh` | Refresh credential | Rotate/refresh a session |
| API-004 | Authentication | Gateway-public | POST | `/api/v1/auth/logout` | Refresh credential | Revoke the current session |
| API-005 | Authentication | Gateway-public | POST | `/api/v1/auth/logout-all` | Authenticated user | Revoke all sessions owned by the user |
| API-006 | Authentication | Identity trust | GET | `/.well-known/jwks.json` | Public key material only | Publish RSA public keys for JWT verification |
| API-007 | Authentication | Internal | POST | `/oauth2/token` | Client ID/secret and approved scope | Issue service client-credentials token |
| API-008 | Product | Gateway-public | GET | `/api/v1/catalog/categories` | Anonymous | Browse visible categories |
| API-009 | Product | Gateway-public | GET | `/api/v1/catalog/products` | Anonymous | Browse visible products with pagination/filtering |
| API-010 | Product | Gateway-public | GET | `/api/v1/catalog/products/{slug}` | Anonymous | Read visible product detail by slug |
| API-011 | Product | Gateway-public | POST | `/api/v1/admin/catalog/products` | `CATALOG_ADMIN` | Create a catalog product draft |
| API-012 | Product | Gateway-public | GET | `/api/v1/admin/catalog/products` | `CATALOG_ADMIN` | List administrative product views |
| API-013 | Product | Gateway-public | GET | `/api/v1/admin/catalog/products/{productId}` | `CATALOG_ADMIN` | Read administrative product detail |
| API-014 | Product | Gateway-public | PUT | `/api/v1/admin/catalog/products/{productId}/composition` | `CATALOG_ADMIN`, `If-Match` | Replace product/category/variant composition |
| API-015 | Product | Gateway-public | POST | `/api/v1/admin/catalog/products/{productId}/publish` | `CATALOG_ADMIN`, `If-Match` | Publish a product |
| API-016 | Product | Gateway-public | POST | `/api/v1/admin/catalog/products/{productId}/deactivate` | `CATALOG_ADMIN`, `If-Match` | Deactivate a product |
| API-017 | Product | Gateway-public | POST | `/api/v1/admin/catalog/products/{productId}/archive` | `CATALOG_ADMIN`, `If-Match` | Archive a product |
| API-018 | Product | Internal | POST | `/internal/v1/catalog/variants/campaign-validation` | Approved service JWT subject/scope | Validate a variant snapshot for Campaign |
| API-019 | Campaign | Gateway-public | POST | `/api/v1/admin/campaigns` | `SCOPE_CAMPAIGN_ADMIN` | Create a campaign draft |
| API-020 | Campaign | Gateway-public | PATCH | `/api/v1/admin/campaigns/{campaignId}` | `SCOPE_CAMPAIGN_ADMIN`, `If-Match` | Replace campaign metadata |
| API-021 | Campaign | Gateway-public | PUT | `/api/v1/admin/campaigns/{campaignId}/item` | `SCOPE_CAMPAIGN_ADMIN`, `If-Match` | Replace the campaign item snapshot |
| API-022 | Campaign | Gateway-public | GET | `/api/v1/admin/campaigns/{campaignId}` | `SCOPE_CAMPAIGN_ADMIN` | Read campaign detail |
| API-023 | Campaign | Gateway-public | POST | `/api/v1/admin/campaigns/{campaignId}/schedule` | `SCOPE_CAMPAIGN_ADMIN`, `If-Match`, idempotency key | Schedule the campaign |
| API-024 | Campaign | Gateway-public | POST | `/api/v1/admin/campaigns/{campaignId}/activate` | `SCOPE_CAMPAIGN_ADMIN`, `If-Match` | Activate a scheduled campaign |
| API-025 | Campaign | Gateway-public | POST | `/api/v1/admin/campaigns/{campaignId}/outbox-events/{eventId}/requeue` | `SCOPE_CAMPAIGN_ADMIN` | Requeue an owned terminally failed outbox event |
| API-026 | Campaign | Internal | GET | `/internal/v1/campaigns/{campaignId}/snapshot` | Flash Sale service subject/scope | Read a campaign recovery snapshot |
| API-027 | Inventory | Gateway-public | GET | `/api/v1/admin/inventory/{variantId}` | `INVENTORY_ADMIN` | Read durable inventory state |
| API-028 | Inventory | Gateway-public | POST | `/api/v1/admin/inventory/{variantId}/adjustments` | `INVENTORY_ADMIN` | Apply an idempotent stock adjustment |
| API-029 | Inventory | Gateway-public | GET | `/api/v1/admin/inventory/{variantId}/movements` | `INVENTORY_ADMIN` | Browse immutable stock movements |
| API-030 | Inventory | Internal | POST | `/internal/v1/campaign-stock-allocations` | `SCOPE_INVENTORY_WRITE` | Allocate stock to a campaign request |
| API-031 | Inventory | Internal | POST | `/internal/v1/campaign-stock-allocations/{requestId}/release` | `SCOPE_INVENTORY_WRITE` | Release an active allocation |
| API-032 | Inventory | Internal | POST | `/internal/v1/campaign-stock-allocations/{requestId}/reconcile` | `SCOPE_INVENTORY_WRITE` | Reconcile an allocation against durable inventory |
| API-033 | Flash Sale | Gateway-public | GET | `/api/v1/flash-sales/reservations/{reservationId}` | Authenticated owner | Read an owned reservation |
| API-034 | Flash Sale | Gateway-public | POST | `/api/v1/flash-sales/{campaignId}/reservations` | Authenticated shopper, idempotency key | Reserve one campaign item |
| API-035 | Order | Gateway-public | GET | `/api/v1/orders/{orderId}` | Authenticated owner | Read one owned order |
| API-036 | Order | Gateway-public | GET | `/api/v1/orders` | Authenticated owner | List owned orders |
| API-037 | Payment | Gateway-public | POST | `/api/v1/payments/{paymentId}/checkout-sessions` | Authenticated owner, idempotency key | Create or replay a hosted Checkout Session |
| API-038 | Payment | Gateway-public | GET | `/api/v1/payments/{paymentId}` | Authenticated owner | Read one owned payment |
| API-039 | Payment | Gateway-public | GET | `/api/v1/payments/by-order/{orderId}` | Authenticated owner | Read an owned payment by order identity |
| API-040 | Payment | Gateway-public | POST | `/webhooks/v1/payments/stripe` | Valid Stripe signature | Accept a raw Stripe webhook receipt |
| API-041 | Cart | Gateway-public | GET | `/api/v1/cart` | Authenticated shopper | Read the authenticated shopper's Cart |
| API-042 | Cart | Gateway-public | PUT | `/api/v1/cart/items/{variantId}` | Authenticated shopper | Set or replace one desired variant quantity |
| API-043 | Cart | Gateway-public | DELETE | `/api/v1/cart/items/{variantId}` | Authenticated shopper | Remove one owned Cart item idempotently |
| API-044 | Cart | Gateway-public | DELETE | `/api/v1/cart` | Authenticated shopper | Clear all owned Cart items idempotently |
| API-045 | Product | Internal | POST | `/internal/v1/catalog/variants/display-details` | Cart service subject/scope | Batch-read current Product display details for Cart |
| API-046 | Order | Gateway-public | POST | `/api/v1/orders/cart-checkouts` | Authenticated shopper, idempotency key | Validate and accept one immutable Cart snapshot as one regular Order |
| API-047 | Order | Gateway-public | POST | `/api/v1/orders/buy-now` | Authenticated shopper, idempotency key | Accept one sellable normal variant without changing Cart |

## Totals by owner

| Owner | Gateway-public | Internal | Identity trust | Total |
|---|---:|---:|---:|---:|
| API Gateway | 0 | 0 | 0 | 0 (routing/edge owner only) |
| Authentication | 5 | 1 | 1 | 7 |
| Product | 10 | 2 | 0 | 12 |
| Campaign | 7 | 1 | 0 | 8 |
| Inventory | 3 | 3 | 0 | 6 |
| Flash Sale | 2 | 0 | 0 | 2 |
| Order | 2 | 0 | 0 | 2 |
| Payment | 4 | 0 | 0 | 4 |
| Cart | 4 | 0 | 0 | 4 |
| Notification | 0 | 0 | 0 | 0 |
| **Total** | **39** | **7** | **1** | **47** |

## Documentation endpoints (excluded from the 45)

When explicitly enabled, each documented service owns:

- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- OpenAPI YAML: `/v3/api-docs.yaml`

Gateway additionally owns exact local document proxy paths under `/openapi/{service-name}`. These
paths are documentation infrastructure and are not business endpoints.
