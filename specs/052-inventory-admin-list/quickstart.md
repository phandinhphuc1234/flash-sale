# Quickstart: Inventory Admin List

## Prerequisites

- Authentication, Product, Inventory, Gateway, PostgreSQL, and QuickCart are running.
- An administrator account is available.
- At least one inventory item has been initialized.

## Backend checks

```powershell
./mvnw.cmd -pl services/inventory-service -am verify
./mvnw.cmd -pl services/product-service -am verify
```

Use an admin token to call:

```text
GET /api/v1/admin/inventory?page=0&size=20
POST /api/v1/admin/catalog/variants/display-details
```

Verify that the first response contains page metadata and live quantity fields, and the second
contains one result per requested variant ID.

## Frontend checks

```powershell
cd "flash-sale frontend/QuickCart"
npm.cmd run lint
npm.cmd run build
```

Open `/seller/inventory`, confirm one inventory request and one batch display request per page, move
between pages, adjust a row, and open movement history.
