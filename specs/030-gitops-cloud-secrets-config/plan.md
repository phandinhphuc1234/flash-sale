# Implementation Plan: Cloud Secrets and Service Configuration

**Branch**: `codex/gitops-phase15-cloud-secrets-config` | **Date**: 2026-08-21 | **Spec**:
[spec.md](spec.md)

**Status**: Approved

## Summary

Replace the cloud overlay's shared runtime Secret with per-boundary Secrets, add explicit service
ConfigMaps, mount Authentication JWT files, and provide a validation-only/opt-in provisioning script.
The script consumes the ignored local `.env` and JWT directory but never prints or commits values.
Stripe and migrations remain disabled for this phase.

## Technical Context

**Language/Version**: Kubernetes YAML and PowerShell 7-compatible script

**Primary Dependencies**: Existing `kubectl`, Kustomize, PowerShell, and the local ignored `.env`

**Storage**: No database/storage change; existing Phase 14 platform resources are referenced

**Testing**: Inventory comparison, script validation/negative tests, Kustomize render, client-side
dry-run, Secret pattern scan, and output redaction review

**Target Platform**: AWS EKS namespace `flash-sale`; local workstation supplies Secret input

**Project Type**: Root infrastructure configuration for eight Spring Boot services

**Performance Goals**: Validation-only completes within 10 seconds and never contacts a provider API

**Constraints**: No values in Git/output, validation-only by default, `-Apply` required for mutation,
no live application rollout, no migration/Stripe enablement

**Scale/Scope**: Eight application ConfigMaps and ten Secret boundaries including JWT files

## Constitution Check

- **Specification traceability**: FR-001–FR-011 map to inventory, ConfigMap, patch, script, and scan
  tasks.
- **Service ownership**: Each datasource URL points to the service's own logical DB; no migration is
  moved into infrastructure.
- **Communication**: Internal service names use Kubernetes DNS; Gateway remains the ingress.
- **Data and messaging**: Redis/Kafka/Schema Registry endpoints match Phase 14; no behavior changes.
- **Root infrastructure ownership**: All new artifacts remain under root `infra/` plus ADR/spec docs.
- **Observability**: Existing probes and Actuator config are preserved.
- **Contracts and dependencies**: No HTTP/Kafka contract or production dependency changes.
- **Validation**: Secret inventory, script tests, Kustomize, dry-run, and leak scan apply.
- **Architecture**: ADR `docs/adr/0020-cloud-secret-and-config-boundary.md` is accepted.

## Design

### Deployment environment injection

Each cloud application Pod receives:

```text
flash-sale-runtime-config
        +
<service>-runtime-config
        +
<service>-secrets
```

Authentication additionally receives `auth-jwt` as a mounted file Secret. Platform StatefulSets use
`platform-secrets`.

### ConfigMap ownership

ConfigMaps are committed because they contain no credentials. Datasource URLs, Kafka topics, internal
DNS, JWT issuer/JWK locations, CORS, and feature flags are explicit. Optional tuning properties remain
at application defaults unless a later approved task changes them.

### Secret provisioning

`phase15-secrets.ps1` has two modes:

- default validation: parse local `.env`, check required keys/files, verify namespace, print names only;
- `-Apply`: write short-lived filtered env files in the OS temp directory, run `kubectl create secret
  generic --dry-run=client -o yaml | kubectl apply -f -`, then delete the files.

The script never calls `kubectl get secret -o yaml` and never emits values. `-EnableStripe` is opt-in.

### Migration boundary

All service ConfigMaps set the service's Liquibase flag to false (Product is already hard-coded false).
A later phase will add one-off migration Jobs per service so migrations run in service-owned images
before application readiness is enabled.

## Project Structure

```text
infra/
├── CONFIGURATION.md
├── ENVIRONMENTS.md
├── k8s/overlays/cloud/
│   ├── config/
│   │   ├── kustomization.yaml
│   │   └── <eight service ConfigMaps>.yaml
│   ├── patches/
│   │   └── <eight envFrom/auth-jwt patches>.yaml
│   └── kustomization.yaml
└── scripts/gitops/phase15-secrets.ps1
docs/adr/0020-cloud-secret-and-config-boundary.md
```

**Structure Decision**: Keep non-secret runtime config in cloud overlay-owned ConfigMaps and keep
operator secret creation outside Git manifests. The old global `flash-sale-secrets` remains only for
legacy overlays and is not referenced by the canonical cloud overlay.

## Complexity Tracking

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| Nine Secret boundaries | Prevent Stripe/JWT/OAuth exposure across Pods | One global Secret violates least privilege |
| Eight ConfigMaps | Avoid datasource URL collisions and make ownership reviewable | One global ConfigMap cannot represent per-service settings |
| Temp filtered env files in `-Apply` | Avoid passing Secret values as command arguments or committing files | Direct `--from-literal` arguments risk process/log exposure |

## Implementation Order

1. Accept ADR and write complete inventory.
2. Add ConfigMaps and Kustomize patches.
3. Switch Phase 14 platform Secret references to `platform-secrets`.
4. Add validation-only/opt-in Secret script.
5. Run inventory, render, dry-run, negative, and leak checks.
6. Ask the operator to fill missing manual values; do not run `-Apply` automatically.
