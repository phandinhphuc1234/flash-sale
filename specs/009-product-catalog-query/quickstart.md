# Quickstart: Product Catalog Query

## Verify active feature

```json
{
  "feature_directory": "specs/009-product-catalog-query"
}
```

## Run service verification

```powershell
.\mvnw.cmd -pl services/product-service -am verify
.\mvnw.cmd -pl services/api-gateway -am verify
```

Expected:

- product-service tests pass with Testcontainers PostgreSQL
- api-gateway context test passes with the catalog route configured

## Local manual run

Start backing services:

```powershell
docker compose -f infra/docker/compose.yml -f infra/docker/compose.dev.yml up -d postgres
```

Run product-service with a migrated product database. For a fresh local database, enable Liquibase for product-service before starting the app:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/product_db"
$env:SPRING_DATASOURCE_USERNAME="flashsale"
$env:SPRING_DATASOURCE_PASSWORD="flashsale_dev_password"
$env:SPRING_LIQUIBASE_ENABLED="true"
$env:SERVER_PORT="18082"
.\mvnw.cmd -pl services/product-service spring-boot:run
```

Run api-gateway in another terminal:

```powershell
$env:PRODUCT_SERVICE_URL="http://localhost:18082"
$env:SERVER_PORT="8080"
.\mvnw.cmd -pl services/api-gateway spring-boot:run
```

Manual checks after seeding catalog data:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/catalog/categories
Invoke-RestMethod "http://localhost:8080/api/v1/catalog/products?page=0&size=20"
Invoke-RestMethod http://localhost:8080/api/v1/catalog/products/<slug>
```

Expected:

- Only visible products are returned.
- Active variant prices appear under variants.
- No product-level display price appears.
- Missing or hidden product slugs return `PRODUCT_NOT_FOUND`.
- Missing category slugs return `CATEGORY_NOT_FOUND`.
