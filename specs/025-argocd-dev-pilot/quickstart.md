# Quickstart: Argo CD Dev Pilot Bootstrap

Run from the repository root with the EKS context selected.

## Validate without mutation

```powershell
.\infra\scripts\gitops\phase10-argocd-bootstrap.ps1
```

## Apply the pinned control plane and Application

```powershell
.\infra\scripts\gitops\phase10-argocd-bootstrap.ps1 -Apply
```

## Verify

```powershell
kubectl -n argocd get pods,svc
kubectl -n argocd get applications.argoproj.io dev-pilot
kubectl -n argocd describe applications.argoproj.io dev-pilot
kubectl -n flash-sale get pods,svc,pvc
```

## Local access

```powershell
kubectl -n argocd port-forward svc/argocd-server 8080:443
```

Open `https://localhost:8080`. Retrieve the initial admin password manually only when needed; do
not paste it into chat or commit it.
