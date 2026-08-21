# Implementation Plan: Local and Cloud Environment Foundation

**Branch**: `codex/gitops-phase13-cloud-environment-foundation` | **Date**: 2026-08-21 | **Spec**:
[spec.md](spec.md)

**Status**: Approved

## Summary

Create an explicit environment contract for the two supported runtimes, add a canonical full-stack
cloud Kustomize overlay, and add a value-safe PowerShell validator. Local remains Docker Compose;
cloud becomes the future EKS/Argo CD target. Existing pilot and legacy overlays remain untouched for
rollback compatibility.

## Technical Context

**Language/Version**: PowerShell 7-compatible scripts and Kubernetes YAML

**Primary Dependencies**: Existing `kubectl`/Kustomize client and Git; no new production dependency

**Storage**: No storage change in this phase

**Testing**: PowerShell validator checks, `kubectl kustomize`, `kubectl apply --dry-run=client -k`,
and `git diff --check`

**Target Platform**: Developer workstation for validation; AWS EKS for the later cloud rollout

**Project Type**: Root infrastructure and deployment documentation for a Java monorepo

**Performance Goals**: Environment validation completes in under 10 seconds on a developer machine

**Constraints**: No secret values in Git or command output; no live cloud apply; no business code or
contract changes

**Scale/Scope**: Eight independently deployable application services, one initial replica each

## Constitution Check

- **Specification traceability**: FR-001 through FR-009 map to the tasks below.
- **Service ownership**: Only root `infra/` metadata and overlays change; service source and schemas
  remain owned by their services.
- **Communication**: No HTTP/Kafka behavior changes; Gateway remains the public ingress boundary.
- **Data and messaging**: No PostgreSQL, Redis, Kafka, outbox, or consumer behavior changes.
- **Root infrastructure ownership**: All new files are under `infra/` or the feature documentation.
- **Observability**: Existing probes are reused by the base; no registry code is added.
- **Contracts and dependencies**: No contracts change and no production dependency is added.
- **Validation**: Validator, Kustomize render, client-side dry-run, and diff checks are required.

## Design

### Environment ownership

| Label | Source of truth | Runtime | Reconciler | Public endpoint in this phase |
|---|---|---|---|---|
| `local` | `infra/docker/` | Docker Compose | Manual Compose commands | No |
| `cloud` | `infra/k8s/overlays/cloud/` | AWS EKS | Argo CD in a later phase | No |

`dev-pilot` remains a historical Product-only overlay and is not renamed or deleted in this phase.

### Cloud overlay

`infra/k8s/overlays/cloud/kustomization.yaml` composes `infra/k8s/base/` and applies the existing ECR
repository names with the `initial` tag. It does not define Secrets, backing services, or public
ingress. Those concerns are separate tasks with separate validation.

### Secret boundary

`infra/ENVIRONMENTS.md` and the validator list key names and owners only. They never read `.env`, PEM,
or Kubernetes Secret values. Phase 15 will add the operator-only provisioning flow.

### Validation script

`infra/scripts/gitops/phase13-environment-contract.ps1` resolves the repository root from its own
location, validates `local` or `cloud`, checks the canonical source paths, verifies that
`infra/docker/.env` is ignored, and prints the non-sensitive secret inventory. It exits non-zero for
an unsupported environment or missing required path.

## Project Structure

```text
infra/
├── ENVIRONMENTS.md
├── docker/                         # local source of truth
├── k8s/
│   ├── base/                       # shared resources
│   └── overlays/
│       ├── cloud/                  # canonical full-stack cloud target
│       ├── dev/                    # compatibility overlay
│       └── dev-pilot/              # historical Product pilot
└── scripts/gitops/
    └── phase13-environment-contract.ps1
specs/028-gitops-local-cloud-foundation/
└── documentation and validation artifacts
```

**Structure Decision**: Keep shared resources in `infra/k8s/base`, introduce one named cloud overlay,
and leave local orchestration in `infra/docker`. No service-local infrastructure is added.

## Validation Matrix

| Check | Scope | Expected result |
|---|---|---|
| Validator local | Environment contract | Exit 0; Compose path and non-sensitive inventory shown |
| Validator cloud | Environment contract | Exit 0; cloud overlay path and inventory shown |
| Validator product | Negative guard | Non-zero; supported values explained |
| Kustomize render | Cloud overlay | Eight Deployments and eight Services rendered |
| Client dry-run | Cloud overlay | No API/schema errors; no live resource changed |
| Git diff check | Changed files | No whitespace errors |

## Complexity Tracking

No constitutional violation or new ADR is required. Keeping `dev` and `dev-pilot` during migration
is a reversible compatibility choice, not a second supported environment.

## Implementation Order

1. Add environment contract documentation and feature artifacts.
2. Add the canonical cloud overlay and common non-secret ConfigMap.
3. Add and run the value-safe validator.
4. Run render, dry-run, negative guard, and diff validations.
5. Record evidence and mark Phase 13 tasks complete.
