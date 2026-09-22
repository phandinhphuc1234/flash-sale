# Flash Sale web frontend

QuickCart is the Next.js storefront for the Flash Sale backend. It talks to the
public API Gateway only; it never calls a microservice directly and never holds
Stripe secrets.

## Local setup

```powershell
cd "flash-sale frontend\QuickCart"
npm install
Copy-Item .env.example .env.local
npm run dev
```

The repository's local Docker smoke environment exposes the Gateway on
`http://localhost:18080`. If your Gateway uses another port, change
`NEXT_PUBLIC_API_BASE_URL` in `.env.local`. The cloud edge is:
`https://api.flashsale123.tech`.

Run a production build before handing the frontend to another environment:

```powershell
npm run build
npm start
```

## Connected user flows

### Authentication

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/logout-all`

The access token stays in the browser session context. Every request adds an
`X-Trace-Id`; a single `401` refreshes the session once and retries the request.

### Catalog and Cart

- Catalog pages read `GET /api/v1/catalog/products` and categories through the Gateway. The
  catalog page sends search/category/sort/page state to the server; it does not filter a full
  product array in the browser.
- Product detail reads `GET /api/v1/catalog/products/{slug}`.
- Cart uses `GET /api/v1/cart`, absolute quantity `PUT /api/v1/cart/items/{variantId}`,
  `DELETE /api/v1/cart/items/{variantId}`, and `DELETE /api/v1/cart`.
- Cart is only a snapshot. It does not lock price or stock.
- Checkout is all-or-nothing: the UI blocks submission while any Cart line is
  unavailable, so the submitted snapshot always matches the backend Cart.

### Checkout

- **Cart checkout:** `POST /api/v1/orders/cart-checkouts` with the current
  `cartVersion`, each `itemVersion`, displayed price/currency, and a stable
  `Idempotency-Key`.
- **Buy now:** `POST /api/v1/orders/buy-now`; this does not mutate the Cart.
- Both paths navigate to `/orders/{orderId}`. The Order page polls the Payment
  created asynchronously and starts Stripe Checkout with
  `POST /api/v1/payments/{paymentId}/checkout-sessions`.
- The success page verifies Payment and Order. It checks Reservation only for
  Flash Sale orders; regular Cart/Buy Now orders do not have a reservation.

### Flash Sale

The product page submits `POST /api/v1/flash-sales/{campaignId}/reservations`
and then polls the reservation/order flow. The public backend currently has no
campaign discovery endpoint, so the UI accepts a campaign ID (or
`NEXT_PUBLIC_DEFAULT_CAMPAIGN_ID`) instead of inventing a catalog API.

### Admin screens

The seller screens call the documented catalog, campaign, and inventory admin
endpoints. They require an authenticated admin token. The seller Orders page is
intentionally informational because no public admin-order endpoint is present
in the current backend contract.

## API contract source of truth

See the monorepo documents:

- `docs/api/frontend-integration-guide.md`
- `docs/api/endpoint-registry.md`

When an endpoint or response changes, update those documents and this README
before changing frontend behavior.
