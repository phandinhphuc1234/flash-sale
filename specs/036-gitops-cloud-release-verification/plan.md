# Implementation Plan: Cloud Release Artifact Verification

**Branch**: `codex/gitops-phase21-cloud-release-verification` | **Date**: 2026-08-22
**Spec**: `specs/036-gitops-cloud-release-verification/spec.md`
**Status**: Approved

## Summary

Add a read-only Phase 21 operator workflow that treats the existing EKS cloud environment as the
staging-equivalent release target. The workflow checks Argo ownership/health, all eight service
Deployment availability, ECR manifest digests versus running Pod image IDs, disabled Payment flags,
and the existing Gateway smoke contract. It must never rebuild, push, apply, or read Secret values.

## Technical Context

**Language/Version**: PowerShell 7; Java application code is unchanged

**Primary Dependencies**: Existing `kubectl`, AWS CLI/ECR, `pwsh`, and
`infra/scripts/gitops/phase18-gateway-smoke.ps1`; no new production dependency

**Storage**: N/A; observations are ephemeral and recorded in feature validation evidence

**Testing**: PowerShell AST/static checks, Kustomize render/client dry-run, bounded operator checks,
and live EKS/Argo/ECR/Gateway verification

**Target Platform**: AWS EKS `flash-sale-dev`, namespace `flash-sale`, Argo Application
`flash-sale-cloud`, ECR in `ap-southeast-2`

**Project Type**: Root infrastructure/operator automation in a Maven monorepo

**Performance Goals**: Verification completes within 10 minutes and reports all 8 services and their
image digests with bounded diagnostics

**Constraints**: Read-only live verification; localhost-only temporary Gateway port-forward; no
Secret reads; Payment remains disabled; cloud is the only staging-equivalent environment

**Scale/Scope**: Eight Deployments, one Argo Application, eight ECR repositories, seven Payment flags,
and three Gateway smoke assertions

## Constitution Check

- **Specification traceability**: PASS; FR-001 through FR-008 map to the operator workflow and evidence.
- **Service ownership**: PASS; no service source, persistence, migration, or domain model changes.
- **Communication**: PASS; existing Gateway smoke and Kubernetes Service/DNS are reused without new API/event contracts.
- **Data and messaging**: PASS; PostgreSQL, Redis, Kafka, Schema Registry, outbox, and consumers are not changed.
- **Root infrastructure ownership**: PASS; the verifier remains under root `infra/scripts/gitops`.
- **Observability**: PASS; existing health/readiness and smoke signals are checked, without new application instrumentation.
- **Contracts and dependencies**: PASS; no contract or production dependency changes.
- **Validation**: PASS; parser, dry-run, bounded operator, digest, Argo, and Gateway checks are defined.
- **Architecture decision**: PASS; no boundary, ingress, persistence, or ownership change requires an ADR.

## Phase 0 Research

See `research.md` for the following decisions:

1. Use ECR rather than GHCR because the existing cloud overlay and Terraform outputs use ECR and the
   GitHub OIDC role already authenticates to it.
2. Verify the currently selected desired-state tag and runtime digest instead of inventing a new release
   tag policy; the later release-build phase can introduce that policy explicitly.
3. Reuse Phase 18 Gateway smoke rather than exposing a public endpoint.
4. Keep verification separate from Argo reconciliation; this phase observes ownership and health only.

## Phase 1 Design

### Workflow

```text
preflight context/tools
        ↓
Argo Application status and revision
        ↓
8 Deployment availability + desired image refs
        ↓
ECR tag → manifest digest → Pod image ID comparison
        ↓
Payment flags remain false
        ↓
existing Phase 18 Gateway smoke
        ↓
bounded evidence summary
```

The new script is `infra/scripts/gitops/phase21-cloud-release-verify.ps1`. It uses bounded native
process execution, reports only image references/digests and status fields, and delegates temporary
port-forward cleanup to the existing smoke helper.

### Verification strategy

| Requirement | Static | Kubernetes/cloud | Live |
|---|---|---|---|
| FR-001 Argo state | Script parser | Kustomize dry-run | `flash-sale-cloud` status/revision |
| FR-002/004 services and digest | Service inventory guard | Overlay render | Deployment/Pod/ECR comparison |
| FR-005 Gateway | Existing script parser | Service remains ClusterIP | readiness/catalog/admin smoke |
| FR-006 Payment flags | Flag inventory guard | Rendered ConfigMap | Live ConfigMap values |
| FR-007/008 safety | No apply/delete/Secret paths | No resource mutation | bounded run and cleanup evidence |

### Failure and rollback

This phase has no persistent mutation and therefore no rollback operation. A failed check reports the
affected service or dependency and exits nonzero. Existing Phase 18 cleanup handles its temporary
port-forward. Operators rerun after fixing the underlying release or health issue.

## Project Structure

```text
infra/scripts/gitops/phase21-cloud-release-verify.ps1
infra/scripts/gitops/README.md
specs/036-gitops-cloud-release-verification/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── tasks.md
```

## Security and Secrets

The workflow uses AWS/EKS metadata and ECR image manifests only. It does not call `kubectl get secret`,
read `.env`, print credentials, or create a public endpoint. The existing smoke helper binds only to
localhost.

## Complexity Tracking

No constitutional departure is required.

## Post-Design Constitution Check

PASS. The plan observes the current cloud release without changing service behavior, contracts,
secrets, platform ownership, or production topology.
