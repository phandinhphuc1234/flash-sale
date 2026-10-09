# Quickstart: Validate P0 Storefront Completion

## Backend

```powershell
.\mvnw.cmd -pl services/campaign-service,services/api-gateway -am verify
```

Verify through Gateway (default local port):

```powershell
Invoke-RestMethod 'http://localhost:18080/api/v1/campaigns?page=0&size=12'
```

Expected: HTTP 200 shared success envelope. Draft/ended campaigns do not appear in discovery.

## Frontend

```powershell
Set-Location '.\flash-sale frontend\QuickCart'
npm test
npm run lint
npm run build
```

Manual routes: `/flash-sale`, `/help`, `/contact`, `/privacy`, and an unknown URL. Check 375, 768,
1024, and 1440 pixel widths and keyboard navigation. Confirm no Newsletter, Wishlist, address-save
control, raw internal error, or `href="#"` remains in supported storefront UI.
