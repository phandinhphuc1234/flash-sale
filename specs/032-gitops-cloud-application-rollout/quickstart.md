# Phase 17 Quickstart

Prerequisites: `kubectl` points to the intended EKS cluster; Phase 14 platform, Phase 15 Secrets,
and Phase 16 migrations have completed; ECR `initial` images exist.

```powershell
cd C:\Users\MSi\flash-sale
.\infra\scripts\gitops\phase17-application-rollout.ps1
.\infra\scripts\gitops\phase17-application-rollout.ps1 -Apply
kubectl -n flash-sale get deployments,pods
kubectl -n flash-sale rollout status deployment/payment-service --timeout=180s
```

Expected result: validation passes without changing resources, then all eight Deployments become
available. Payment acceptance, Stripe, consumers, and recovery remain disabled. If a rollout fails,
inspect `kubectl -n flash-sale describe pod <pod>` and `kubectl -n flash-sale logs <pod>`; do not
delete migration Jobs or PVCs as a first response.
