# Phase 22 Quickstart: Internal Authenticated Cloud Smoke

## Prerequisites

- The Phase 21 release verifier passes and `flash-sale-cloud` is `Synced/Healthy`.
- The current `kubectl` context is `flash-sale-dev`.
- An existing Authentication account with `ROLE_ADMIN`/`CATALOG_ADMIN`/`INVENTORY_ADMIN`/
  `CAMPAIGN_ADMIN` is available. Do not put its password in `.env` or a file.
- The Inventory image running in EKS contains the approved fixture adapter after its promotion PR
  has been merged and Argo has reconciled it.
- PowerShell 7+, `kubectl`, Java/Maven, and the repository checkout are available.

## Validate without mutation

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-internal-e2e.ps1
```

Expected: context/Argo/Gateway/configuration checks pass, but no Product, Campaign, Inventory, or
shopper state changes.

## Run the authenticated smoke

```powershell
pwsh -NoLogo -NoProfile -File .\infra\scripts\gitops\phase22-internal-e2e.ps1 `
  -Run `
  -AdminLogin "existing-admin-login"
```

The script prompts for the admin password using `Read-Host -AsSecureString`. It creates unique
shopper/Product/Campaign identities, initializes Inventory through the temporary Inventory-owned Job,
submits one reservation, replays the key, and polls the owner Order list. It deletes the Job in all
exit paths; Product/Campaign IDs are reported for manual archive/cleanup because no delete contract
exists.

## Verification commands

```powershell
.\mvnw.cmd -pl services/inventory-service -am verify
.\mvnw.cmd clean verify
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

Record the command, commit/Argo revision, sanitized IDs, statuses, and exit code in
`validation.md`. Never paste passwords, JWTs, cookies, Secret values, or full request bodies.
