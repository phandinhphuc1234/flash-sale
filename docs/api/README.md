# Flash Sale HTTP API catalog

This is the reader-facing catalog for the HTTP surface currently supported by the monorepo.
There are **45 unique endpoints**, counted by `HTTP method + normalized path`.

Frontend developers should use the Vietnamese
[`frontend-integration-guide.md`](frontend-integration-guide.md), which adds complete request/response
payload examples, authentication/session rules, shopper/admin flows, polling guidance, and known
contract gaps.

## What is counted

- Included: supported business APIs, approved internal service APIs, the Stripe webhook, JWKS, and
  the OAuth client-credentials token endpoint.
- Excluded: Actuator, Swagger/OpenAPI infrastructure, `/error`, CORS preflight, and unsupported
  framework routes.
- `Gateway-public` means API Gateway has a route for the endpoint. Authentication and authorization
  still apply.
- `Internal` endpoints are service-to-service contracts and must not be exposed through Gateway.
- Cart Service owns four authenticated shopper endpoints. Notification Service currently owns no
  supported HTTP endpoints.

## Summary

| Owner | Gateway-public | Internal | Identity trust | Total |
|---|---:|---:|---:|---:|
| API Gateway | 0 | 0 | 0 | 0 (routing only) |
| Authentication | 5 | 1 | 1 | 7 |
| Product | 10 | 2 | 0 | 12 |
| Campaign | 7 | 1 | 0 | 8 |
| Inventory | 3 | 3 | 0 | 6 |
| Flash Sale | 2 | 0 | 0 | 2 |
| Order | 2 | 0 | 0 | 2 |
| Payment | 4 | 0 | 0 | 4 |
| Cart | 4 | 0 | 0 | 4 |
| Notification | 0 | 0 | 0 | 0 |
| **Total** | **37** | **7** | **1** | **45** |

## Endpoint inventory

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

### Cart contract notes

API-041 through API-044 require a shopper JWT. The authenticated JWT subject is the only owner
selector; clients must not send `ownerId` or `cartId`. Cart responses carry `Cache-Control:
no-store`. PUT uses absolute quantity replacement (1–10) and is safe to retry with the same body;
both DELETE operations are idempotent and return `204 No Content`.

| Status | Cart error codes | Meaning |
|---:|---|---|
| 400 | `CART_VALIDATION_ERROR` | Invalid UUID, body, or quantity |
| 401 | `UNAUTHENTICATED` | Missing or invalid shopper JWT |
| 404 | `CART_VARIANT_NOT_FOUND` | Product variant does not exist |
| 409 | `CART_VARIANT_NOT_SELLABLE` | Variant is not currently sellable |
| 503 | `CART_PRODUCT_UNAVAILABLE` | Product display dependency unavailable |
| 500 | `CART_INTERNAL_ERROR` | Unexpected Cart failure |

API-045 is not a frontend endpoint. Cart calls it with a service token whose subject is
`cart-service`, audience is `flash-sale-internal-api`, and scope is
`catalog.variant-display.read`; missing/invalid credentials map to `401`/`403`, malformed input to
`400`, and Product returns ordered display results with explicit missing/non-sellable entries.

## Read the APIs in Swagger UI

Swagger is intentionally **disabled by default** and must remain disabled on the public cloud edge.
For local Docker Compose only:

1. In the ignored `infra/docker/.env`, set:

   ```dotenv
   API_DOCS_ENABLED=true
   ```

2. Build/start the application profile:

   ```powershell
   docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up -d --build
   ```

3. Open `http://localhost:8080/swagger-ui.html` and select a service from the top-right document
   selector.

The Gateway catalog contains eight service-owned documents: Authentication, Product, Campaign,
Flash Sale, Inventory, Order, Payment, and Cart. Notification is not listed because it does not
currently own a supported HTTP controller.

### Direct service documents

When a service runs directly on its local host port with `API_DOCS_ENABLED=true`, use:

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| Authentication | `http://localhost:18081/swagger-ui.html` | `http://localhost:18081/v3/api-docs` |
| Product | `http://localhost:18082/swagger-ui.html` | `http://localhost:18082/v3/api-docs` |
| Campaign | `http://localhost:18083/swagger-ui.html` | `http://localhost:18083/v3/api-docs` |
| Flash Sale | `http://localhost:18084/swagger-ui.html` | `http://localhost:18084/v3/api-docs` |
| Order | `http://localhost:18085/swagger-ui.html` | `http://localhost:18085/v3/api-docs` |
| Payment | `http://localhost:18086/swagger-ui.html` | `http://localhost:18086/v3/api-docs` |
| Inventory | `http://localhost:18088/swagger-ui.html` | `http://localhost:18088/v3/api-docs` |
| Cart | `http://localhost:18089/swagger-ui.html` | `http://localhost:18089/v3/api-docs` |

Swagger's **Authorize** button accepts a bearer JWT for secured APIs. Internal APIs additionally
require the approved service identity/scope and are not reachable through Gateway. The OAuth token
endpoint uses HTTP Basic client credentials. The Stripe webhook must be called by Stripe with a
valid signature; Swagger cannot manufacture a valid provider signature.

## Source and verification

- Canonical feature contract: [`specs/047-api-documentation/contracts/http-inventory.md`](../../specs/047-api-documentation/contracts/http-inventory.md)
- Static verification: `pwsh -NoLogo -NoProfile -File infra/scripts/docs/verify-api-documentation.ps1`
- Existing Inventory detail guide: [`docs/inventory/README.md`](../inventory/README.md)

The static verifier checks the catalog totals, duplicate endpoint keys, Springdoc wiring, exact
Gateway document routes, safe defaults, and the absence of a public `/internal/**` Gateway route.
