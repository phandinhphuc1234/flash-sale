# Validation Evidence: Cloud Stateful Service Foundation

**Date**: 2026-08-21
**Branch**: `codex/gitops-phase14-cloud-stateful-foundation`

## Manifest and resource validation

| Check | Result |
|---|---|
| `kubectl kustomize infra/k8s/overlays/cloud` | PASS |
| `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` | PASS; no live resource changed |
| Rendered platform resources | PASS; 3 StatefulSets, 1 Schema Registry Deployment, 4 platform Services |
| Rendered PVC templates | PASS; 3 `gp2` claims: PostgreSQL 20Gi, Redis 4Gi, Kafka 8Gi |
| Platform Service exposure | PASS; all platform Services are internal ClusterIP/headless, no LoadBalancer/NodePort |
| Stable DNS names | PASS; `postgres`, `redis`, `kafka`, `schema-registry` |

## Preflight validation

| Command | Result |
|---|---|
| `phase14-stateful-preflight.ps1` | PASS; EKS context, EBS CSI, `gp2`, overlay render, and resource assertions passed |
| `phase14-stateful-preflight.ps1 -RequireSecrets` | PASS; `flash-sale/flash-sale-secrets` exists; data was not read |

## Safety checks

| Check | Result |
|---|---|
| Secret-value pattern scan | PASS; no provider key, webhook secret, password value, or PEM body found |
| `git diff --check` | PASS; no whitespace errors |

## Scope confirmation

- No live `kubectl apply` was performed.
- No Secret values were created, read, printed, or committed.
- No Java source, migration, HTTP contract, Kafka event contract, or Terraform resource changed.
- Phase 15 must verify Secret keys and service-specific configuration before a live cloud apply.
