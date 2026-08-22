# Phase 18 Validation Evidence

Date: 2026-08-22
Branch: `codex/gitops-phase18-gateway-smoke`

| Check | Result |
|---|---|
| `pwsh -NoProfile -File infra/scripts/gitops/phase18-gateway-smoke.ps1 -?` | PASS — script syntax/help loads |
| `infra/scripts/gitops/phase18-gateway-smoke.ps1` | PASS — cloud overlay and Gateway Deployment ready; no resources changed |
| Occupied-port guard on local Docker ports `18080` and `18081` | PASS — script rejects occupied ports before starting smoke assertions |
| `infra/scripts/gitops/phase18-gateway-smoke.ps1 -Run -LocalPort 28080 -TimeoutSeconds 120` | PASS — readiness `200`, catalog `200`, admin `401` through the EKS port-forward |
| Port ownership verification | PASS — listener on `127.0.0.1:28080` belonged to the exact kubectl child process started by the script |
| Gateway Service exposure | PASS — remains internal `ClusterIP`; only temporary localhost port-forward used |

## Smoke flow

The script validated the cloud Kustomize overlay, checked `api-gateway` readiness, started a
temporary `kubectl port-forward --address 127.0.0.1 service/api-gateway 28080:8080`, and sent three
GET requests:

1. `/actuator/health/readiness` returned `200`.
2. `/api/v1/catalog/products?page=0&size=1` returned `200` through Gateway to Product Service.
3. `/api/v1/admin/catalog/products?page=0&size=1` without credentials returned `401`.

The port-forward process was stopped by the script cleanup path. No Secret values, tokens, or
response bodies were committed.

## Environment isolation note

Local Docker Compose already publishes ports `18080` through `18089`. An early smoke attempt could
therefore reach a local container instead of EKS if it reused one of those ports. The final script
uses `28080`, rejects any occupied port, binds kubectl explicitly to `127.0.0.1`, and verifies that
the listening socket belongs to the kubectl process it created before checking application routes.
