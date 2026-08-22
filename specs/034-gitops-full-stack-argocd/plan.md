# Implementation Plan: Full-Stack Argo CD Ownership

**Branch**: codex/gitops-phase19-full-stack-argocd | **Date**: 2026-08-22
**Spec**: specs/034-gitops-full-stack-argocd/spec.md
**Status**: Approved

## Summary

Replace Product-only Argo reconciliation with one full-cloud Application. A guarded PowerShell
workflow validates desired state, suspends pilot self-healing, creates the cloud owner, waits for
Synced/Healthy and eight available Deployments, and only then removes the obsolete pilot
Application object. Automatic pruning remains disabled and a failed cutover restores the committed
pilot manifest.

## Technical Context

**Language/Version**: PowerShell 7 and Kubernetes YAML
**Primary Dependencies**: kubectl, built-in Kustomize, Argo CD Application CRD
**Storage**: No new storage; existing PostgreSQL, Redis, Kafka, PVCs, and Secrets remain unchanged
**Testing**: PowerShell syntax, Kustomize render/client dry-run, live Argo status, workload rollout,
image identity, and rollback guards
**Target Platform**: AWS EKS flash-sale-dev and the existing in-cluster Argo CD installation
**Project Type**: Root infrastructure and operator automation
**Performance Goals**: Cutover converges within 600 seconds
**Constraints**: No prune, no workload/PVC/Secret deletion, no migration rerun, no public ingress
**Scale/Scope**: One Argo Application, eight application Deployments, and four platform workloads

## Constitution Check

- **Specification traceability**: PASS; FR-001 through FR-009 map to desired-state ownership,
  recovery, image preservation, and validation tasks.
- **Service ownership**: PASS; no service code, schema, persistence, or migration changes.
- **Communication**: PASS; service discovery and Gateway behavior are unchanged.
- **Data and messaging**: PASS; no data, Redis, Kafka, outbox, or stock behavior changes.
- **Root infrastructure ownership**: PASS; Argo and scripts remain under root infra/.
- **Observability**: PASS; Argo sync/health, Kubernetes rollout, and image identity are recorded.
- **Contracts and dependencies**: PASS; no API/event contract or new production dependency.
- **Validation**: PASS; Kubernetes and Argo integration checks apply. Maven, contract, and load
  tests are omitted because application code and runtime contracts are unchanged.
- **Architecture decision**: PASS; ADR 0022 records the ownership and deployment-coupling change.

## Phase 0 Research

Research resolves four decisions:

1. Use a distinct flash-sale-cloud Application rather than retaining the misleading dev-pilot name.
2. Disable pilot auto-sync before the new owner reconciles overlapping Product resources.
3. Require both Synced and Healthy before removing the pilot Application object.
4. Keep prune disabled and migration Jobs outside the application overlay.

See research.md for rationale and alternatives.

## Phase 1 Design

### Desired-state manifest

infra/k8s/argocd/application-cloud.yaml targets develop and infra/k8s/overlays/cloud. It enables
self-heal but leaves prune disabled.

### Ownership transition runner

infra/scripts/gitops/phase19-argocd-cloud.ps1 defaults to validation. With -Apply it validates both
overlays and live prerequisites, refuses a cascading finalizer, suspends pilot auto-sync, applies
and refreshes flash-sale-cloud, waits for Synced/Healthy and all eight Deployments, verifies the
Product immutable image, and then deletes only the obsolete finalizer-free Application object. A
failure restores the pilot manifest.

### Image preservation

The cloud overlay adopts Product tag pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72, matching the
currently approved and running pilot image.

### Contract and data design

No external contract or durable data model is introduced. data-model.md documents only the
operator-visible ownership transition states. A contracts directory is intentionally omitted
because this is an internal deployment workflow.

## Project Structure

    docs/adr/0022-argocd-full-cloud-ownership.md
    infra/k8s/argocd/application-cloud.yaml
    infra/k8s/argocd/application-dev-pilot.yaml
    infra/k8s/argocd/kustomization.yaml
    infra/k8s/overlays/cloud/kustomization.yaml
    infra/scripts/gitops/phase19-argocd-cloud.ps1
    infra/scripts/gitops/README.md
    specs/034-gitops-full-stack-argocd/

## Post-Design Constitution Check

PASS. One GitOps owner removes conflicting deployment control. Prune remains disabled, migration
ownership remains service-scoped, shared assets remain under infra/, and ADR 0022 records the
deployment ownership change.

## Complexity Tracking

No constitutional departure is required.
