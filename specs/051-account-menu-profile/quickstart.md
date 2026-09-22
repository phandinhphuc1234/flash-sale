# Quickstart: Account Profile and Session Menu

## Backend

```powershell
.\mvnw.cmd -pl services/authentication-service -am test `
  -Dtest=AccountProfileServiceTests,AccountProfileControllerContractTests `
  -Dsurefire.failIfNoSpecifiedTests=false
.\mvnw.cmd -pl services/authentication-service -am verify
```

## Frontend

```powershell
Set-Location '.\flash-sale frontend\QuickCart'
npm.cmd run build
```

## Manual checks

1. Sign in as a normal user; open the account menu and confirm Profile, Orders, Security, and Sign out are visible while Operations is absent.
2. Sign in as `ROLE_ADMIN`; confirm Operations opens `/seller`.
3. Open Security, cancel all-session logout, and verify the session remains usable.
4. Confirm all-session logout and verify the browser returns to login and refresh is not possible.
5. On Profile, edit `fullName`, `phone`, `address`, and (optionally) an unused username; save and
   reload the menu and `/api/v1/auth/me` to confirm the display label uses `fullName` while email
   remains unchanged. Try a duplicate username and confirm `409`; leave a contact field blank to
   clear it.
