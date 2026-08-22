# Phase 16 quickstart

Run from the repository root after Phase 15 has provisioned Secrets.

The migration Jobs consume the platform and ConfigMaps, but do not apply the application overlay.
Prepare those non-secret resources first:

```powershell
kubectl apply -n flash-sale -k infra/k8s/overlays/cloud/platform
kubectl apply -n flash-sale -f infra/k8s/overlays/cloud/flash-sale-config.yaml
kubectl apply -k infra/k8s/overlays/cloud/config
```

Wait until `postgres`, `redis`, `kafka`, and `schema-registry` are available before continuing.

## Validate only

```powershell
.\infra\scripts\gitops\phase16-migrations.ps1
```

This checks the current EKS context, namespace, PostgreSQL/Redis/Kafka/Schema Registry resources,
Phase 15 Secret/ConfigMap names, and the migration overlay. It does not create Jobs.

## Apply migrations

Only after reviewing the rendered Jobs:

```powershell
.\infra\scripts\gitops\phase16-migrations.ps1 -Apply
```

The runner applies the seven database migration Jobs and waits for completion. API Gateway is
stateless and has no migration Job. If one fails, inspect it before changing
anything:

```powershell
kubectl -n flash-sale get jobs,pods
kubectl -n flash-sale describe job/<failed-job>
kubectl -n flash-sale logs job/<failed-job>
```

To intentionally rerun after inspection:

```powershell
.\infra\scripts\gitops\phase16-migrations.ps1 -Apply -ForceRerun
```

After all eight Jobs complete, the next phase can apply the application overlay. Do not enable Stripe
or add Cart/Notification in this phase.
