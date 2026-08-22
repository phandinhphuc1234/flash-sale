# Phase 18 Validation Evidence

Date: 2026-08-22
Branch: `codex/gitops-phase18-gateway-smoke`

| Check | Result |
|---|---|
| `pwsh -NoProfile -File infra/scripts/gitops/phase18-gateway-smoke.ps1 -?` | PASS — script syntax/help loads |
| `infra/scripts/gitops/phase18-gateway-smoke.ps1` | PASS — cloud overlay and Gateway Deployment ready; no resources changed |
| `infra/scripts/gitops/phase18-gateway-smoke.ps1 -Run -TimeoutSeconds 120` | PASS — readiness `200`, catalog `200`, admin `401` |
| Gateway Service exposure | PASS — remains internal `ClusterIP`; only temporary localhost port-forward used |

## Smoke flow

The script validated the cloud Kustomize overlay, checked `api-gateway` readiness, started a
temporary `kubectl port-forward service/api-gateway 18080:8080`, and sent three GET requests:

1. `/actuator/health/readiness` returned `200`.
2. `/api/v1/catalog/products?page=0&size=1` returned `200` through Gateway to Product Service.
3. `/api/v1/admin/catalog/products?page=0&size=1` without credentials returned `401`.

The port-forward process was stopped by the script cleanup path. No Secret values, tokens, or
response bodies were committed.
