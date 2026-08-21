# Implementation Plan: Product Pilot GitOps Rollback Rehearsal

**Branch**: `codex/gitops-phase12-rollback` | **Date**: 2026-08-21 | **Spec**: [spec.md](spec.md)

## Summary

Rehearse the existing ADR 0009 rollback path by reverting the merged Product image-promotion commit
through a protected-branch PR, observing Argo CD reconcile the previous immutable image, and then
reverting the rollback so `dev-pilot` returns to the promoted image. The change is Git-only; no
application source, `.env`, Secret, ECR deletion, or direct cluster mutation is introduced.

## Technical Context

**Language/Version**: Git/Kustomize operational workflow; no application language change

**Primary Dependencies**: GitHub branch protection, GitHub Actions CI, Amazon ECR, Argo CD, kubectl

**Storage**: Git desired state and existing ECR image metadata; no new storage

**Testing**: `git diff --check`, `kubectl kustomize`, required GitHub CI, Argo status, and rollout
verification

**Target Platform**: AWS EKS `flash-sale-dev`, namespace `flash-sale`, Argo application `dev-pilot`

**Project Type**: GitOps operational rehearsal for a monorepo infrastructure overlay

**Performance Goals**: Each post-merge Product rollout reaches one available replica within 180 seconds

**Constraints**: Only `infra/k8s/overlays/dev-pilot/kustomization.yaml` may change; no direct `kubectl apply`

**Scale/Scope**: One Product Deployment, one StatefulSet left untouched, one development overlay

## Constitution Check

- Specification traceability: PASS; this approved spec defines the rollback and restoration paths.
- Service ownership: PASS; no service source, schema, JPA, or database ownership changes.
- Communication: PASS; no API, Kafka, or discovery change.
- Data and messaging: PASS; PostgreSQL, Redis, Kafka, and outbox behavior are untouched.
- Root infrastructure ownership: PASS; the only desired-state path is under `infra/k8s/`.
- Observability: PASS; existing Argo/Kubernetes health and rollout evidence is used.
- Contracts and dependencies: PASS; no new production dependency or contract is added.
- Validation: PASS; the plan includes Kustomize, CI, Argo, and rollout checks.

## Project Structure

```text
specs/027-gitops-rollback-rehearsal/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md
└── checklists/requirements.md

infra/k8s/overlays/dev-pilot/kustomization.yaml  # the only desired-state file allowed to change
```

**Structure Decision**: Keep rollback in the existing GitOps overlay and use the existing CI and
Argo boundaries. No helper script or application code is required for a one-time rehearsal.

## Complexity Tracking

No constitutional violations or new architectural complexity are introduced.
