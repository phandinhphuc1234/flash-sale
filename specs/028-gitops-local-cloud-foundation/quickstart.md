# Quickstart: Validate the Local/Cloud Foundation

Run these commands from the repository root in PowerShell.

## 1. Validate the environment contract

```powershell
.\infra\scripts\gitops\phase13-environment-contract.ps1 -Environment local
.\infra\scripts\gitops\phase13-environment-contract.ps1 -Environment cloud
```

The script prints paths and secret **names** only. It must never print values from `.env` or PEM
files.

## 2. Render and dry-run the cloud overlay

```powershell
kubectl kustomize infra/k8s/overlays/cloud
kubectl apply --dry-run=client -k infra/k8s/overlays/cloud
```

The output should contain the `flash-sale` namespace, eight application Deployments, and eight
application Services. A live apply is intentionally not part of this phase.

## 3. Verify the unsupported environment guard

```powershell
.\infra\scripts\gitops\phase13-environment-contract.ps1 -Environment product
```

This command must fail with a non-zero exit code and explain that only `local` and `cloud` are
supported.

## Later phases

- Phase 14 adds single-node cloud PostgreSQL, Redis, Kafka, and Schema Registry.
- Phase 15 creates operator-managed Kubernetes Secrets and service-specific configuration.
- Later phases deploy services, switch Argo CD from the pilot overlay, expose the Gateway, and run
  end-to-end tests.
