# Product Campaign Validation HTTP Contract

**Owner**: Product Service  
**Caller**: Campaign Service only  
**Route**: not exposed by API Gateway

## Request

```http
POST /internal/v1/catalog/variants/campaign-validation
Authorization: Bearer <campaign-service-access-token>
X-Trace-Id: <trace-id>
Content-Type: application/json
```

```json
{
  "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57"
}
```

Token requirements: approved issuer/JWKS, subject `campaign-service`, audience
`flash-sale-internal-api`, and `SCOPE_catalog.read`.

## Success

```http
200 OK
```

```json
{
  "productId": "249887bf-1a20-4861-8f97-4226e880f40b",
  "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57",
  "sku": "IPHONE-16-128-BLACK",
  "productStatus": "ACTIVE",
  "variantStatus": "ACTIVE",
  "sellable": true,
  "basePrice": 22990000.0000,
  "currency": "VND"
}
```

Product evaluates its own current truth: Product ACTIVE/published/public, Variant ACTIVE and owned by
that Product, valid positive base price, and supported currency. Campaign does not share Product
entities or reproduce the Product visibility query.

## Failures and Campaign mapping

| Product response | Campaign response |
|---|---|
| 400 validation | 400 `CAMPAIGN_VALIDATION_FAILED` |
| 401/403 service-token rejection | 503 `PRODUCT_SERVICE_UNAVAILABLE` plus secure operational log/metric |
| 404 `PRODUCT_VARIANT_NOT_FOUND` | 404 `PRODUCT_VARIANT_NOT_FOUND` |
| 409 `PRODUCT_VARIANT_NOT_SELLABLE` | 409 `PRODUCT_VARIANT_NOT_SELLABLE` |
| timeout/connect/5xx | 503 `PRODUCT_SERVICE_UNAVAILABLE` |

The Product error body follows its approved local transport. Campaign maps by HTTP status and stable
error code, never by message text. No Product database or JPA/domain type is shared.
