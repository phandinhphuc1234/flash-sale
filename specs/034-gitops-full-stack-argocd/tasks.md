# Tasks: Full-Stack Argo CD Ownership

**Input**: Design documents from specs/034-gitops-full-stack-argocd/
**Status**: Approved

## Phase 1: Setup

- [x] T001 Create approved specification and design artifacts in specs/034-gitops-full-stack-argocd/
- [x] T002 [P] Record the deployment ownership decision in docs/adr/0022-argocd-full-cloud-ownership.md
- [x] T003 [P] Point .specify/feature.json to specs/034-gitops-full-stack-argocd

## Phase 2: Foundational

- [x] T004 Preserve the approved Product immutable image in infra/k8s/overlays/cloud/kustomization.yaml
- [x] T005 Add the canonical cloud Application in infra/k8s/argocd/application-cloud.yaml and switch infra/k8s/argocd/kustomization.yaml to it

## Phase 3: User Story 1 — Full-cloud reconciliation

**Goal**: One Argo CD Application reconciles the canonical cloud environment.

**Independent Test**: flash-sale-cloud becomes Synced/Healthy and all eight application Deployments
are available.

- [x] T006 [US1] Implement validation-only and explicit ownership cutover in infra/scripts/gitops/phase19-argocd-cloud.ps1
- [x] T007 [P] [US1] Document Phase 19 operator commands in infra/scripts/gitops/README.md
- [x] T008 [US1] Validate both Kustomize targets and the Phase 19 script without changing live state

## Phase 4: User Story 2 — Safe recovery

**Goal**: A failed ownership transition restores pilot reconciliation without deleting workloads.

**Independent Test**: Safety guards reject cascading finalizers, prune stays disabled, and rollback
uses the committed historical manifest.

- [x] T009 [US2] Add finalizer, no-prune, single-owner, and rollback safeguards to infra/scripts/gitops/phase19-argocd-cloud.ps1
- [x] T010 [US2] Verify safety guards and repeatable validation behavior against the live Argo CD CRDs; live cutover remains T011

## Phase 5: Verification and evidence

- [x] T011 Run the live Phase 19 ownership transition and verify Argo, eight Deployments, Product image identity, and Phase 18 Gateway smoke
- [x] T012 Record commands and results in specs/034-gitops-full-stack-argocd/validation.md

## Dependencies and execution order

- T001-T003 establish the approved design and ADR.
- T004-T005 must complete before the transition script can be validated.
- T006-T008 implement and validate the normal transition.
- T009-T010 complete recovery safety before any live cutover.
- T011-T012 are the final live verification gate.

## Parallel opportunities

- T002 and T003 are independent documentation/configuration tasks.
- T007 can proceed while T006 is implemented because it changes a separate file.
- No live Argo mutation task is parallelized.

## Implementation strategy

Deliver one guarded environment-level cutover. Keep public ingress, pruning, pilot PVC cleanup,
migration reruns, and image-promotion workflow expansion in later explicit phases.
