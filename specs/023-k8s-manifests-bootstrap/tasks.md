# Tasks: Kubernetes Manifest Bootstrap

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Status**: Approved

## Phase 1: Existing source baseline

- [x] T001 Confirm the active feature pointer targets this feature and preserve the user-owned deletion
  of infra/k8s/README.md outside this task. Evidence: git status.
- [x] T002 [P] Review the existing product-service base files against the runtime manifest contract.
  Files: infra/k8s/base/product-service/*.
- [x] T003 Update the base kustomization to declare exactly the namespace and eight service bases.
  File: infra/k8s/base/kustomization.yaml.

## Phase 2: Development overlay

- [x] T004 Update the dev overlay to map all eight image placeholders to their existing ECR
  repositories with tag initial. File: infra/k8s/overlays/dev/kustomization.yaml.
- [x] T005 Confirm the dev ConfigMap contains only non-secret shared runtime values. File:
  infra/k8s/overlays/dev/flash-sale-config.yaml.

## Phase 3: Workloads

- [x] T006 [US1] Add the api-gateway Deployment, ClusterIP Service, and service kustomization.
  Files: infra/k8s/base/api-gateway/deployment.yaml, service.yaml, kustomization.yaml.
- [x] T007 [P] [US1] Add the authentication-service workload files. Files:
  infra/k8s/base/authentication-service/deployment.yaml, service.yaml, kustomization.yaml.
- [x] T008 [P] [US1] Add the campaign-service workload files. Files:
  infra/k8s/base/campaign-service/deployment.yaml, service.yaml, kustomization.yaml.
- [x] T009 [P] [US1] Add the flash-sale-service workload files. Files:
  infra/k8s/base/flash-sale-service/deployment.yaml, service.yaml, kustomization.yaml.
- [x] T010 [P] [US1] Add the inventory-service workload files. Files:
  infra/k8s/base/inventory-service/deployment.yaml, service.yaml, kustomization.yaml.
- [x] T011 [P] [US1] Add the order-service workload files. Files:
  infra/k8s/base/order-service/deployment.yaml, service.yaml, kustomization.yaml.
- [x] T012 [P] [US1] Add the payment-service workload files. Files:
  infra/k8s/base/payment-service/deployment.yaml, service.yaml, kustomization.yaml.

## Phase 4: Validation and delivery evidence

- [x] T013 [US2] Render the dev overlay and verify 18 resources. Evidence:
  kubectl kustomize infra/k8s/overlays/dev.
- [x] T014 [US2] Validate the dev overlay without live cluster mutation. Evidence:
  kubectl apply --dry-run=client -k infra/k8s/overlays/dev.
- [x] T015 [US2] Run a whitespace and secret-safety review. Evidence: git diff --check plus
  a manifest scan showing no kind: Secret.
- [x] T016 Record final validation results in this task ledger and leave all changes unstaged for
  user review. No commit or push occurs unless the user explicitly requests it.

## Dependencies and Order

T001–T005 establish the source baseline. T006–T012 can then be implemented as one coherent
workload group. T013–T016 run only after all workload files and the overlay are complete.

## Validation Evidence

| Task | Command / check | Scope | Result |
|---|---|---|---|
| T013 | `kubectl kustomize infra/k8s/overlays/dev` | Render | PASS — 1 Namespace, 1 ConfigMap, 8 Deployments, 8 Services (2026-08-20) |
| T014 | `kubectl apply --dry-run=client -k infra/k8s/overlays/dev` | Client dry-run | PASS — all 18 resources accepted, no live mutation (2026-08-20) |
| T015 | `git diff --check`; trailing-whitespace and Secret-kind scans | Diff and secret safety | PASS — no trailing whitespace; no Kubernetes Secret resource (2026-08-20) |
