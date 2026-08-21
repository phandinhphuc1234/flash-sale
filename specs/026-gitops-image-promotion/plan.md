# Implementation Plan: Product Pilot Image Promotion

**Branch**: `codex/gitops-phase11-image-promotion` | **Date**: 2026-08-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/026-gitops-image-promotion/spec.md`

## Summary

Add a Product-only GitHub Actions delivery workflow and a local PowerShell rehearsal script. The
workflow verifies the Product Maven reactor, authenticates to the existing ECR repository through
GitHub OIDC, publishes a commit-derived immutable image, and opens a protected-branch PR containing
only the `dev-pilot` image tag change. Argo CD remains the sole Kubernetes reconciler.

## Technical Context

**Language/Version**: GitHub Actions YAML, Bash on `ubuntu-latest`, PowerShell 5.1+/7, Java 21

**Primary Dependencies**: Existing Maven wrapper, Docker CLI, `actions/checkout@v6`,
`actions/setup-java@v4`, `aws-actions/configure-aws-credentials@v4`,
`aws-actions/amazon-ecr-login@v2`, GitHub CLI on hosted runners

**Storage**: Existing ECR repository; no new database or Kubernetes storage

**Testing**: Product Maven verify, workflow YAML/static checks, Docker build rehearsal, ECR push
rehearsal, Kustomize render, Argo Application sync/health evidence

**Target Platform**: GitHub-hosted Ubuntu runner, AWS ECR, existing EKS/Argo CD dev pilot

**Project Type**: Monorepo delivery/infrastructure workflow

**Performance Goals**: Complete the Product delivery job within 20 minutes under normal runner and
registry conditions; no deployment latency SLO is changed.

**Constraints**: No long-lived cloud keys, no direct protected-branch push, no `kubectl apply`, no
credential values in logs or files, immutable ECR tags only.

**Scale/Scope**: One Product Service image and one six-resource `dev-pilot` Application.

## Constitution Check

*GATE: PASS before Phase 0 research; re-evaluate after Phase 1 design.*

- **Specification traceability**: Requirements map to three independently testable user stories.
- **Service ownership**: Only Product Service build context is used; no database or domain ownership changes.
- **Communication**: No HTTP, Kafka, discovery, or ingress changes.
- **Data and messaging**: No PostgreSQL, Redis, Kafka, or outbox changes.
- **Root infrastructure ownership**: Workflow and helper are under `.github/` and `infra/`; no
  service-local shared orchestration is added.
- **Observability**: Workflow, ECR, GitHub PR, and Argo statuses provide the required evidence.
- **Contracts and dependencies**: Workflow and image contracts are documented; no Java dependency is added.
- **Validation**: Maven, PowerShell, Kustomize, workflow branch behavior, ECR, and Argo checks are listed.

## Phase 0 Research Summary

See [research.md](research.md) for OIDC, immutable tags, PR promotion, Product-only scope, and secret
boundary decisions.

## Phase 1 Design

### Workflow

`Product Pilot Delivery` triggers on `develop` pushes affecting Product/shared build inputs and on
manual dispatch. It runs Maven verification, assumes
`arn:aws:iam::090814040069:role/github-ci-role`, logs into ECR, builds and pushes
`flash-sale/product-service:pilot-${GITHUB_SHA}`, changes only the overlay tag on an automation
branch, and opens a PR to `develop`. It has `actions: write`, `id-token: write`, `contents: write`,
and `pull-requests: write`; after opening the PR it dispatches the existing `ci.yml` on the
automation branch because GitHub suppresses recursive events created by `GITHUB_TOKEN`. It never
runs `kubectl`.

### Local helper

`infra/scripts/gitops/phase11-product-delivery.ps1` reproduces the image build and optional ECR/tag
steps. No switches means validation-only; `-Push` enables ECR mutation and `-UpdateOverlay` enables
the local desired-state edit. It never commits or pushes Git changes.

### Source layout

```text
.github/workflows/product-pilot-delivery.yml
infra/scripts/gitops/phase11-product-delivery.ps1
specs/026-gitops-image-promotion/
docs/adr/0009-github-actions-ecr-argo-promotion.md
```

### Contracts

See [contracts/image-promotion.md](contracts/image-promotion.md).

## Complexity Tracking

No constitution violations. The workflow uses the existing AWS role, ECR repository, Argo Application,
and branch protection rather than introducing new platform components.
