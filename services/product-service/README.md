# Product Service

Product Service owns catalog structure, product lifecycle, sellability, and the authoritative normal
purchase quote. It never reserves stock and never accepts payment.

## Owned capabilities

- anonymous category/product browsing;
- administrator create, composition replacement, publish, deactivate, and archive operations;
- optimistic concurrency through `If-Match`/version checks;
- campaign variant validation snapshots;
- Cart display-detail enrichment;
- authoritative batch purchase quotes for Order regular checkout.

## HTTP boundaries

| Boundary | Routes |
|---|---|
| Public | `GET /api/v1/catalog/categories`, `/products`, `/products/{slug}` |
| Admin | `/api/v1/admin/catalog/products/**` |
| Campaign internal | `POST /internal/v1/catalog/variants/campaign-validation` |
| Cart internal | `POST /internal/v1/catalog/variants/display-details` |
| Order internal | `POST /internal/v1/catalog/variants/purchase-quotes` |

The three internal routes require different approved service subjects/scopes. They are not exposed
through Gateway.

## Data and architecture

Product owns `product_db` and its Liquibase changelog. Domain/application packages are isolated from
Spring MVC and JPA adapters. A purchase quote is a read decision based on current Product state; Cart
display prices are not trusted for checkout acceptance.

## Configuration and verification

Core runtime settings are the PostgreSQL datasource, `SPRING_LIQUIBASE_ENABLED`, JWT issuer/JWKS/
audience, approved `CART_CLIENT_ID` and `ORDER_CLIENT_ID`, and `API_DOCS_ENABLED`.

```powershell
.\mvnw.cmd -pl services/product-service -am verify
```

Use the explicit migration procedure in [`infra/docker/README.md`](../../infra/docker/README.md).
For payloads and error codes, see [`docs/api/README.md`](../../docs/api/README.md) and the
[frontend guide](../../docs/api/frontend-integration-guide.md).

## Troubleshooting

- `412`/version failure: refresh the admin representation and send the latest `If-Match` value.
- Cart shows an unavailable item: inspect Product display-detail output; Cart intentionally keeps
  shopper intent while marking current availability.
- Regular checkout reports price/sellability change: the Product quote is authoritative; refresh
  the UI instead of forcing the stale Cart value.
