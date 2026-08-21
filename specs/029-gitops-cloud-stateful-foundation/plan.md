# Implementation Plan: Cloud Stateful Service Foundation

**Branch**: `codex/gitops-phase14-cloud-stateful-foundation` | **Date**: 2026-08-21 | **Spec**:
[spec.md](spec.md)

**Status**: Approved

## Summary

Add a cloud-only Kubernetes platform layer for one PostgreSQL, Redis, Kafka, and Schema Registry
instance. The resources use internal DNS, EBS-backed PVCs, readiness probes, and Secret references.
No value-bearing Secret or live apply is included; Phase 15 owns credential provisioning and
service-specific runtime configuration.

## Technical Context

**Language/Version**: Kubernetes YAML and PowerShell 7-compatible script

**Primary Dependencies**: Kubernetes API `v1`/`apps/v1`, existing AWS EBS CSI addon, `gp2`
StorageClass, PostgreSQL 17 Alpine, Redis 7.4 Alpine, Apache Kafka 4.0.0, Confluent Schema Registry
8.3.0

**Storage**: EBS-backed PVCs: PostgreSQL 20Gi, Redis 4Gi, Kafka 8Gi

**Testing**: Kustomize render, client-side dry-run, preflight checks, manifest secret scan, and live
readiness checks after Phase 15

**Target Platform**: AWS EKS `flash-sale-dev`, namespace `flash-sale`

**Project Type**: Root Kubernetes infrastructure for a Java microservice monorepo

**Performance Goals**: Platform readiness within 180 seconds after a valid Secret and available EBS
volume; no performance SLO is claimed for the single-node demo

**Constraints**: One replica each, internal-only Services, no live Secret values, no public ingress,
no production HA claim, no service code or contract changes

**Scale/Scope**: Four platform components serving eight application services

## Constitution Check

- **Specification traceability**: FR-001 through FR-010 map to manifest, preflight, and validation
  tasks.
- **Service ownership**: Infrastructure creates empty logical databases only; service migrations and
  schemas remain in their modules.
- **Communication**: Kubernetes DNS names are the only internal discovery mechanism.
- **Data and messaging**: PostgreSQL remains durable truth; Redis is atomic hot-path support; Kafka
  and Schema Registry are internal single-node demo dependencies.
- **Root infrastructure ownership**: All shared platform assets remain under `infra/k8s` and
  `infra/scripts`.
- **Observability**: Stateful probes and Schema Registry readiness are declarative; no service code
  or metrics registry changes.
- **Contracts and dependencies**: No HTTP/Kafka contract changes; images are already existing
  infrastructure dependencies and are recorded here.
- **Validation**: Kustomize, dry-run, preflight, secret scan, and post-Secret readiness checks apply.
- **Architecture**: ADR `docs/adr/0019-cloud-single-node-stateful-foundation.md` is accepted.

## Design

### Resource placement

```text
flash-sale namespace
├── postgres StatefulSet (1) ── PVC gp2 20Gi ── postgres:5432
├── redis StatefulSet (1) ───── PVC gp2 4Gi  ── redis:6379
├── kafka StatefulSet (1) ───── PVC gp2 8Gi  ── kafka:9092
└── schema-registry Deployment (1) ─────────── schema-registry:8081
```

### PostgreSQL bootstrap

An init ConfigMap creates the seven service-owned logical databases on a new volume. It does not
contain migrations or application tables. A single Secret supplies the bootstrap username/password.

### Redis

Redis runs with append-only persistence and `requirepass` from `flash-sale-secrets`. It is used only
for atomic flash-sale operations; durable acceptance remains in PostgreSQL.

### Kafka and Schema Registry

Kafka uses one KRaft broker/controller, replication factor one, and an internal advertised listener.
Its headless Service publishes the Pod address before readiness so the single KRaft controller can
resolve `kafka-0.kafka:9093` during bootstrap; the readiness probe still gates application startup.
Schema Registry uses Kafka's internal `_schemas` topic and exposes `/subjects` for readiness. Kafka UI
remains local-only.

### Secret boundary

The manifests reference `flash-sale-secrets` by key. The preflight strict mode checks existence only.
Phase 15 creates the Secret from operator-local values.

## Project Structure

```text
infra/k8s/overlays/cloud/
├── kustomization.yaml             # adds platform resources
└── platform/
    ├── kustomization.yaml
    ├── postgres-init-configmap.yaml
    ├── postgres-statefulset.yaml
    ├── postgres-service.yaml
    ├── redis-statefulset.yaml
    ├── redis-service.yaml
    ├── kafka-statefulset.yaml
    ├── kafka-service.yaml
    ├── schema-registry-deployment.yaml
    └── schema-registry-service.yaml
infra/scripts/gitops/
└── phase14-stateful-preflight.ps1
docs/adr/0019-cloud-single-node-stateful-foundation.md
```

**Structure Decision**: Platform manifests are cloud-overlay-owned because they rely on EBS `gp2`
and are not part of the local Compose source of truth. They remain under root `infra/` and do not
enter service modules.

## Complexity Tracking

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| Four in-cluster platform components | Demonstrate the full GitOps topology in EKS | External managed services add cost and a separate lifecycle |
| One PostgreSQL instance with seven logical DBs | Conserve node/PVC resources while preserving service DB boundaries | One database StatefulSet per service multiplies operational overhead |
| Single-node Kafka | Demo resource budget and existing Compose parity | Kafka HA requires quorum/replication/failure design outside scope |

## Implementation Order

1. Add and accept the architecture ADR.
2. Add platform manifests and connect them to the cloud overlay.
3. Add the non-secret preflight script.
4. Run render, dry-run, resource-count, secret-scan, and preflight checks.
5. Stop before live apply; Phase 15 supplies Secrets and service-specific configuration.
