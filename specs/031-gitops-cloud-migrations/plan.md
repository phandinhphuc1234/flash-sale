# Implementation Plan: Cloud Database Migration Gates

**Branch**: `codex/gitops-phase16-cloud-migrations` | **Date**: 2026-08-21 | **Spec**: [spec.md](spec.md)

**Status**: Approved

## Summary

Create a migration-only Kustomize overlay and an operator-controlled PowerShell runner. The overlay
contains one Job for each of the seven cloud services that own PostgreSQL schemas; API Gateway is
stateless and intentionally has no migration Job. The runner validates the platform and Phase 15 Secret names,
then applies and waits for Jobs only when `-Apply` is explicitly provided.

## Design

### Migration boundary

`infra/k8s/overlays/cloud-migrations/` is intentionally separate from
`infra/k8s/overlays/cloud/`. Applying the application overlay cannot accidentally start migration
Jobs, and the operator can inspect migration evidence before rolling out Deployments.

### Job behavior

Each Job uses the service's ECR `initial` image, the common runtime ConfigMap, the service runtime
ConfigMap, and the service Secret. It runs:

```text
--spring.main.web-application-type=none
--spring.main.keep-alive=false
--spring.liquibase.enabled=true
--spring.kafka.listener.auto-startup=false
--spring.task.scheduling.enabled=false
```

Service-specific environment flags additionally disable Order/Payment consumers and publishers. Jobs
are single-shot with a bounded backoff and one-day retention for inspection.

### Runner behavior

`phase16-migrations.ps1` validates Kustomize, checks the namespace, platform StatefulSets, and all
Phase 15 Secret names without reading Secret values. Default mode is read-only. `-Apply` creates the
eight Jobs and waits for `condition=complete`; a failed Job stops the run. Existing Jobs cause a
failure unless `-ForceRerun` is explicitly supplied, in which case the named migration Jobs are
deleted and recreated.

## Constitution Check

- Service-owned schemas and images remain owned by their services.
- No cross-service database access is introduced.
- No Java, HTTP, Kafka, or migration SQL changes are made.
- Kubernetes assets remain under root `infra/`.
- Secret values are never committed or printed.
- Validation includes Kustomize, client dry-run, prerequisite checks, and failure preservation.

## Project structure

```text
infra/
├── k8s/overlays/cloud-migrations/
│   ├── kustomization.yaml
│   └── *-migration-job.yaml
└── scripts/gitops/phase16-migrations.ps1
specs/031-gitops-cloud-migrations/
└── validation.md
```

## Complexity tracking

| Decision | Reason | Simpler alternative rejected |
|---|---|---|
| Separate migration overlay | Prevent application rollout from racing schema creation | Add Jobs to cloud overlay would start them on every app sync |
| Explicit `-ForceRerun` | Preserve failed Job evidence by default | Always deleting Jobs would destroy diagnostics |
| Seven Jobs | Keep schema ownership and credentials isolated; Gateway has no database | One superuser migration Job violates service ownership |
