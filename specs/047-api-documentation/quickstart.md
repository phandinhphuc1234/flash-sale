# Quickstart: Unified API Documentation

## 1. Validate documentation statically

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\docs\verify-api-documentation.ps1
```

Expected final line:

```text
API_DOCUMENTATION=PASS (40 supported endpoints; 7 service documents; defaults disabled)
```

## 2. Enable docs in local Docker Compose

In the ignored `infra/docker/.env`, set:

```dotenv
API_DOCS_ENABLED=true
```

Do not enable this variable in cloud ConfigMaps.

Start the application services using the repository's normal local prerequisites and Compose files:

```powershell
docker compose --env-file infra/docker/.env `
  -f infra/docker/compose.yml `
  -f infra/docker/compose.dev.yml `
  --profile apps up -d --build
```

## 3. Open the catalog

- Aggregated Swagger UI: `http://localhost:8080/swagger-ui.html`
- Gateway's own OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Select one of the seven named service definitions from Swagger UI.

Direct service Swagger UIs are also available through the development ports:

- Authentication: `http://localhost:18081/swagger-ui.html`
- Product: `http://localhost:18082/swagger-ui.html`
- Campaign: `http://localhost:18083/swagger-ui.html`
- Flash Sale: `http://localhost:18084/swagger-ui.html`
- Order: `http://localhost:18085/swagger-ui.html`
- Payment: `http://localhost:18086/swagger-ui.html`
- Inventory: `http://localhost:18088/swagger-ui.html`

## 4. Authorize protected calls

Use the Swagger **Authorize** button with a test JWT only. The catalog in `docs/api/README.md`
identifies the required boundary. Enabling Swagger does not bypass JWT authorities, owner checks,
internal service identity/scope checks, or Stripe webhook signature verification.

## 5. Verify the safe default

Set `API_DOCS_ENABLED=false` or remove the variable, restart Gateway and one service, then verify:

```powershell
curl.exe -s -o NUL -w "%{http_code}" http://localhost:8080/swagger-ui.html
curl.exe -s -o NUL -w "%{http_code}" http://localhost:18082/v3/api-docs
```

Expected: neither request returns `200`, while business and Actuator health endpoints retain their
normal behavior.

## 6. Build verification

```powershell
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/product-service,services/campaign-service,services/flashsale-service,services/inventory-service,services/order-service,services/payment-service -am verify
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml -f infra/docker/compose.dev.yml config
```
