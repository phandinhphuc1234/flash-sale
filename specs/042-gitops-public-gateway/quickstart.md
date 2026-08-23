# Phase 23 Quickstart

This feature is intentionally gated. Do not apply a public Service until the operator confirms the
exposure mechanism and the ADR is Accepted.

## Current safe checks

```powershell
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
kubectl -n argocd get application flash-sale-cloud
kubectl -n flash-sale get svc
```

The expected baseline is Argo `Synced/Healthy`, `api-gateway` as `ClusterIP`, and all platform and
backend Services as `ClusterIP`.

## After approval

1. Apply the reviewed cloud overlay through the normal Git push → PR → merge → Argo flow.
2. Run the Phase 23 public smoke helper with its bounded timeout.
3. Record the generated AWS hostname and status codes without credentials.
4. If the smoke fails, revert the public-edge commit and wait for Argo to restore `ClusterIP`.
