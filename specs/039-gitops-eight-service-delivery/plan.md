# Implementation Plan: Eight-Service GitOps Image Delivery

**Branch**: `codex/gitops-phase21-eight-service-delivery` | **Date**: 2026-08-22 | **Spec**: [spec.md](spec.md)

## Summary

Replace the Product-only hosted delivery trigger with one matrix-based workflow for the eight
services in the cloud overlay. A detector maps changed paths to canonical service targets, selected
jobs run Maven verification and build/push immutable ECR images through GitHub OIDC, and one
promotion job updates only the selected image tags in the cloud Kustomize overlay before opening a
reviewable PR. Argo CD remains the only cluster reconciler.

## Technical Context

**Language/Version**: GitHub Actions YAML, Bash/Python on `ubuntu-latest`, Java 21, Docker

**Primary Dependencies**: Existing Maven wrapper, Docker CLI, `actions/checkout@v6`,
`actions/setup-java@v4`, `aws-actions/configure-aws-credentials@v4`,
`aws-actions/amazon-ecr-login@v2`, GitHub CLI, and existing `github-ci-role`

**Storage**: Existing ECR repositories and Git desired state; no new storage

**Testing**: YAML/static workflow checks, affected Maven reactor verification, Docker build path
validation, Kustomize render/dry-run, and live `phase21-cloud-release-verify.ps1`

**Target Platform**: GitHub-hosted Ubuntu runner, AWS ECR, AWS EKS `flash-sale-dev`, Argo CD
`flash-sale-cloud`

**Project Type**: Monorepo infrastructure/delivery workflow

**Performance Goals**: One selected-service run completes within 30 minutes on a hosted runner; a
shared change may build eight services concurrently within the same job timeout.

**Constraints**: No long-lived keys, no direct protected-branch push, no `kubectl`, no Secret values
in logs, immutable tags, one cloud overlay source of truth

**Scale/Scope**: Eight deployed services, eight ECR repositories, one cloud Kustomize overlay, one
promotion PR per delivery run

## Constitution Check

*GATE: PASS before implementation and re-check after design.*

- **Specification traceability**: FR-001–FR-011 map to detector, matrix, ECR, PR, and validation
  tasks.
- **Service ownership**: No application source or database ownership changes; each image uses its
  own service Dockerfile and Maven module.
- **Communication**: No HTTP, Kafka, discovery, or ingress change.
- **Data and messaging**: No PostgreSQL, Redis, Kafka, or outbox change.
- **Root infrastructure ownership**: Workflow, overlay, docs, and ADR remain under root `.github/`,
  `infra/`, and `docs/`.
- **Observability**: Delivery evidence uses GitHub Actions, ECR, Argo, Deployment readiness, and
  the existing release verification script; application metrics are unchanged.
- **Contracts/dependencies**: No new production dependency and no API/event contract delta.
- **Validation**: Static workflow checks, Maven verification, Kustomize dry-run, and live release
  verification are required.

## Research Decisions

1. **One generalized workflow** — Retain one owner for cloud promotion so Product changes do not
   create duplicate ECR pushes or competing PRs. The historical Product-only workflow is retired.
2. **Cloud overlay, not dev-pilot** — Phase 19 made `flash-sale-cloud` and
   `infra/k8s/overlays/cloud` the active source of truth. `dev-pilot` remains historical rollback
   evidence only.
3. **Matrix builds** — Verification and image publication are parallel per selected service; the
   promotion job waits for all selected targets, so a partial image set cannot create a PR.
4. **Full commit SHA tag** — `release-${GITHUB_SHA}` is deterministic and does not overwrite a
   different source revision. ECR immutability remains an account/repository control.
5. **Explicit Flash Sale mapping** — Deployment/image key `flash-sale-service` maps to
   `services/flashsale-service`; no directory rename is introduced.
6. **Shared-change fan-out** — Root Maven files, `libs/`, `contracts/`, Docker build inputs, and
   delivery workflow changes select all eight to avoid an untested shared dependency.

## Architecture and Flow

```text
develop push / manual dispatch
          |
          v
detect_targets (paths -> [{name,module,ecr}])
          |
          +--> verify_and_publish[service] x selected targets
          |       Maven reactor -> OIDC -> ECR login -> Docker build -> immutable push
          |
          v
promote_cloud (wait for every selected target)
          |
          +--> branch automation/cloud-image-promotion-...
          +--> update only selected newTag entries
          +--> PR -> develop
                          |
                          v
                 merge review -> Argo flash-sale-cloud -> EKS
```

The workflow never calls `kubectl`; Kubernetes state changes only after a reviewed Git merge and
Argo reconciliation.

## Target Mapping

| Deployment/image key | Maven module | Dockerfile | ECR repository | Cloud image key |
|---|---|---|---|---|
| api-gateway | `services/api-gateway` | `services/api-gateway/Dockerfile` | `flash-sale/api-gateway` | `api-gateway` |
| authentication-service | `services/authentication-service` | `services/authentication-service/Dockerfile` | `flash-sale/authentication-service` | `authentication-service` |
| product-service | `services/product-service` | `services/product-service/Dockerfile` | `flash-sale/product-service` | `product-service` |
| campaign-service | `services/campaign-service` | `services/campaign-service/Dockerfile` | `flash-sale/campaign-service` | `campaign-service` |
| flash-sale-service | `services/flashsale-service` | `services/flashsale-service/Dockerfile` | `flash-sale/flash-sale-service` | `flash-sale-service` |
| inventory-service | `services/inventory-service` | `services/inventory-service/Dockerfile` | `flash-sale/inventory-service` | `inventory-service` |
| order-service | `services/order-service` | `services/order-service/Dockerfile` | `flash-sale/order-service` | `order-service` |
| payment-service | `services/payment-service` | `services/payment-service/Dockerfile` | `flash-sale/payment-service` | `payment-service` |

## Failure and Safety Boundaries

- Detector failure stops the run before any matrix job.
- Any matrix failure prevents the promotion job through `needs` and leaves Git desired state intact.
- OIDC/ECR failure occurs before Docker push or overlay edit.
- Promotion changes are checked with `git diff --check` and an exact service-entry count.
- No empty promotion PR is created.
- Concurrency is not cancelled for `develop` delivery runs, preventing a newer run from leaving an
  earlier image set half-published.
- Existing Kubernetes Secret values and `.env` remain operator-managed and are never read by the
  workflow.

## Project Structure

```text
.github/workflows/service-delivery.yml                 # generalized hosted delivery
.github/workflows/product-pilot-delivery.yml           # retired compatibility stub or removed
docs/adr/0024-github-actions-eight-service-promotion.md
infra/k8s/overlays/cloud/kustomization.yaml            # only desired-state image tags changed
infra/scripts/gitops/README.md                         # operator runbook
specs/039-gitops-eight-service-delivery/
```

## Validation Strategy

1. Parse and inspect the workflow; assert eight targets, explicit Flash Sale mapping, OIDC, no
   `kubectl`, no Secret references, and cloud overlay path.
2. Run `git diff --check` and ensure the overlay still renders with
   `kubectl kustomize infra/k8s/overlays/cloud` and client-side dry-run.
3. Run `./mvnw clean verify` because this is a root delivery change that fans out to all modules.
4. On GitHub, run one service-local and one shared-change workflow; review the generated PR diff.
5. After merging the promotion PR, run `infra/scripts/gitops/phase21-cloud-release-verify.ps1`.

## Complexity Tracking

No constitutional violations. The matrix and detector replace duplicate service-specific workflow
files and reuse existing OIDC/ECR/Argo infrastructure.
