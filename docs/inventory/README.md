# Inventory service API guide

This folder documents the HTTP boundary currently implemented by `inventory-service`.

## Live Swagger UI

The service generates its OpenAPI document and Swagger UI with Springdoc. API documentation is
disabled by default, including in a VPS deployment, so an operator must explicitly enable it.

For local development, start Inventory Service with:

```powershell
$env:INVENTORY_API_DOCS_ENABLED = "true"
.\mvnw.cmd -pl services/inventory-service -am spring-boot:run
```

The shared local switch `API_DOCS_ENABLED=true` also enables Inventory and is the recommended option
when using the unified Gateway catalog. `INVENTORY_API_DOCS_ENABLED` remains available as a
service-only override.

Then open:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

Swagger UI has an **Authorize** button. Paste a JWT that has `INVENTORY_ADMIN` for admin API calls
or `SCOPE_INVENTORY_WRITE` for internal allocation calls. The documentation endpoints are public
only while `INVENTORY_API_DOCS_ENABLED=true`; the actual API routes keep their normal JWT checks.

Swagger annotations are separated from runtime controller logic:

```text
stock/adapter/in/web/
├── InventoryAdminApi.java           # OpenAPI operations and response documentation
├── InventoryAdminController.java    # Spring MVC routing and use-case calls
└── request/response records          # Admin HTTP-only contract models

allocation/adapter/in/web/
├── InventoryInternalApi.java        # Internal OpenAPI operations and responses
├── InventoryInternalController.java # Internal routing and use-case calls
└── request/response records          # Internal HTTP-only contract models
```

The controllers implement their matching API interfaces. This keeps documentation readable while
leaving domain and application packages free of Swagger dependencies.

## API ownership and access

| API group | Path | Intended caller | Required JWT authority |
|---|---|---|---|
| Admin inventory | `/api/v1/admin/inventory/**` | Admin UI through `api-gateway` | `INVENTORY_ADMIN` |
| Campaign allocation lifecycle | `/internal/v1/campaign-stock-allocations/**` | Campaign/Flash Sale services only | `SCOPE_INVENTORY_WRITE` |

The internal endpoints are deliberately not public Gateway routes. Do not call them from a browser
or shopper client.

## Response contract

Successful responses use the shared `common-web` envelope:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "2026-07-28T10:00:00Z"
}
```

Known Inventory validation and business failures use the shared error envelope:

```json
{
  "success": false,
  "errorCode": "INVENTORY_OPERATION_REJECTED",
  "message": "client-safe explanation",
  "errors": [{"field": "quantity", "message": "must be greater than 0"}],
  "timestamp": "2026-07-28T10:00:00Z"
}
```

The currently mapped status codes are `400` for invalid input, `404` for missing inventory or
allocation, and `409` for rejected lifecycle/stock operations or incompatible idempotent reuse.
Authentication failures (`401`/`403`) are produced by Spring Security at the service boundary.

## Related source-of-truth files

- [Unified 40-endpoint API catalog](../api/README.md)
- [Feature 016 HTTP contract](../../specs/016-inventory-service/contracts/http.md)
- [Feature 016 specification](../../specs/016-inventory-service/spec.md)
- [Shared response types](../../libs/common-web/src/main/java/com/philia/flashsale/common/web/)
