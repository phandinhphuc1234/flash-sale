# Phase 16 validation evidence

**Date:** 2026-08-21
**Branch:** `codex/gitops-phase16-cloud-migrations`

| Check | Command/scope | Result |
|---|---|---|
| Migration render | `kubectl kustomize infra/k8s/overlays/cloud-migrations` | PASS; exactly 7 Jobs render |
| Client dry-run | `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud-migrations` | PASS; no live Job changed |
| Platform dry-run | `kubectl apply --dry-run=client -n flash-sale -k infra/k8s/overlays/cloud/platform` | PASS; PostgreSQL, Redis, Kafka, and Schema Registry resources render |
| Kafka PVC permission guard | rendered `kafka` StatefulSet | PASS; initContainer and `fsGroup=1000` allow the Kafka `appuser` to write the gp2 volume |
| ConfigMap dry-run | cloud ConfigMap overlay plus `flash-sale-config.yaml` | PASS; common and service runtime ConfigMaps render |
| Secret/config boundary scan | rendered migration overlay and runner | PASS; service-specific Secret/ConfigMap refs only; no legacy `flash-sale-secrets` |
| Migration safety flags | rendered Job args/env | PASS; Liquibase enabled only for Jobs; listeners/schedulers/consumers disabled |
| Prerequisite guard | `phase16-migrations.ps1` on current cluster | PASS as a negative guard; stops because Phase 14 `postgres` StatefulSet is not applied yet |
| Formatting | `git diff --check` | PASS; only normal Git line-ending warnings |

## Live execution status

- No migration Job was created by the agent.
- The current EKS cluster still has the Product pilot resources, not the Phase 14 cloud platform
  StatefulSets (`postgres`, `redis`, `kafka`, `schema-registry`).
- After the platform is applied and ready, rerun the validation script, then explicitly use
  `-Apply` to create and wait for the seven Jobs.
