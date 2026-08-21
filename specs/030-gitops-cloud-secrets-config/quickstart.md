# Quickstart: Cloud Secrets and ConfigMaps

Run from the repository root in PowerShell. Values are never printed.

## 1. Validate local manual inputs without cluster mutation

```powershell
.\infra\scripts\gitops\phase15-secrets.ps1
```

Add any missing values to the ignored `infra/docker/.env`. The script also checks that
`AUTH_JWT_KEY_DIR\jwt-public.pem` and `jwt-private.pem` exist.

## 2. Validate the cloud overlay

```powershell
kubectl kustomize infra/k8s/overlays/cloud
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

## 3. Provision Secrets only after reviewing the inventory

```powershell
.\infra\scripts\gitops\phase15-secrets.ps1 -Apply
```

The script creates per-boundary Secrets and `auth-jwt`. It does not print values. Do not use
`-EnableStripe` yet; Stripe remains disabled in Phase 15.

## 4. Optional future Stripe enablement

Only after the Payment enablement spec is approved and the Stripe test values are present:

```powershell
.\infra\scripts\gitops\phase15-secrets.ps1 -EnableStripe -Apply
```

The Payment ConfigMap must also be intentionally changed to enable Stripe; adding keys alone does
not enable provider calls.
