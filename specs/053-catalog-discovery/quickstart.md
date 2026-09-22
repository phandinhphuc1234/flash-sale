# Catalog Discovery MVP Quickstart

## Local validation

1. Start the local backing services and Product Service stack.
2. Verify the Gateway is reachable at `http://localhost:18080`.
3. Call the backward-compatible default:

```powershell
Invoke-RestMethod 'http://localhost:18080/api/v1/catalog/products?page=0&size=20'
```

4. Call a filtered page:

```powershell
Invoke-RestMethod 'http://localhost:18080/api/v1/catalog/products?q=headphones&sort=PRICE_ASC&page=0&size=20'
```

5. Open QuickCart's catalog page, change search/category/sort, refresh the browser,
   and confirm the URL reproduces the same result.

## Required gates after approval

```powershell
./mvnw -pl services/product-service -am verify
```

```powershell
cd 'flash-sale frontend/QuickCart'
npm.cmd run lint
npm.cmd run build
```

```powershell
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

