# Quickstart: Cloud Stateful Foundation

Run these commands from the repository root in PowerShell. No command below prints Secret values.

## 1. Validate prerequisites and manifests

```powershell
.\infra\scripts\gitops\phase14-stateful-preflight.ps1
kubectl kustomize infra/k8s/overlays/cloud
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

The default preflight checks the current Kubernetes context, EBS CSI driver, `gp2` StorageClass,
and cloud manifest render. It reports that Secret provisioning is deferred.

## 2. Strict Secret check before a live apply

```powershell
.\infra\scripts\gitops\phase14-stateful-preflight.ps1 -RequireSecrets
```

This checks only that `flash-sale-secrets` exists in namespace `flash-sale`; it never reads the
Secret data. If it fails, stop and complete Phase 15's operator-only Secret provisioning.

## 3. Live apply (after Phase 15)

Do not run this during Phase 14 alone. After the Secret and cloud image/config prerequisites are
ready:

```powershell
kubectl apply -k infra/k8s/overlays/cloud
kubectl -n flash-sale get statefulset,pod,pvc,svc
kubectl -n flash-sale rollout status statefulset/postgres --timeout=180s
kubectl -n flash-sale rollout status statefulset/redis --timeout=180s
kubectl -n flash-sale rollout status statefulset/kafka --timeout=180s
kubectl -n flash-sale rollout status deployment/schema-registry --timeout=180s
```

The application Deployments should be started only after these readiness checks pass.
