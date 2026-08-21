# Tasks: Argo CD Dev Pilot Bootstrap

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Status**: Approved

## Phase 1: Bootstrap source and safety contract

- [x] T001 Confirm `SPECIFY_FEATURE_DIRECTORY` points to `specs/025-argocd-dev-pilot` and verify the Phase 9 `dev-pilot` overlay is merged and renders six resources.
- [x] T002 [P] Add ADR 0008 describing Argo CD source-of-truth, internal access, no-prune policy, and rollback.
- [x] T003 [P] Add the Argo CD Application contract in `specs/025-argocd-dev-pilot/contracts/argocd-application.md`.

## Phase 2: Application desired state (US2)

- [x] T004 [US2] Add `infra/k8s/argocd/application-dev-pilot.yaml` with the pinned repository URL, `develop` revision, exact pilot path, in-cluster destination, automated self-heal, and `prune: false`.
- [x] T005 [US2] Add an `infra/k8s/argocd/kustomization.yaml` that renders only the Application resource.
- [x] T006 [US2] Run `kubectl kustomize infra/k8s/argocd` and verify source/path/sync policy without Secret data. Evidence: `specs/025-argocd-dev-pilot/validation.md`.

## Phase 3: Argo CD bootstrap workflow (US1/US3)

- [x] T007 [US1] Add `infra/scripts/gitops/phase10-argocd-bootstrap.ps1` with pinned v3.4.2 URL, validation-by-default, explicit `-Apply`, readiness waits, internal Service check, and no password output.
- [x] T008 [US3] Run the script without `-Apply` and verify it performs no live mutation. Evidence: `specs/025-argocd-dev-pilot/validation.md`.
- [ ] T009 [US1] With explicit operator approval, run the script with `-Apply` to create/update only the `argocd` namespace and official Argo CD resources.
- [ ] T010 [US1] Verify Argo CD core Pods are Ready and `argocd-server` is `ClusterIP`; record no LoadBalancer/Ingress resources.

## Phase 4: GitOps reconciliation (US2)

- [ ] T011 [US2] Apply the source-controlled Application manifest after Argo CRDs are Ready.
- [ ] T012 [US2] Verify `dev-pilot` reports `Synced` and `Healthy` and manages only the six pilot resources.
- [ ] T013 [US2] Verify Product/PostgreSQL Pods remain Ready and the PVC remains Bound after first Argo sync.
- [ ] T014 [US3] Verify local UI/API access through `kubectl port-forward` without exposing a public Service.

## Phase 5: Evidence and handoff

- [ ] T015 Run parser, Kustomize render, default-script, Argo readiness, Service exposure, Application status, and pilot regression checks; record them in `specs/025-argocd-dev-pilot/validation.md`.
- [ ] T016 Run `git diff --check` and scan changed manifests/script output for Secret values or admin password material.
- [ ] T017 Update this ledger with command, scope, result, and PR reference; leave unrelated user changes unstaged.

## Dependencies and Order

T001–T003 establish the source contract. T004–T006 prepare the Application. T007–T010 install the
controller. T011–T014 create and verify reconciliation. T015–T017 are final evidence gates.

## Definition of Done

- Argo CD control plane is Ready and internal-only.
- `dev-pilot` is `Synced/Healthy` from `develop` and exact pilot path.
- Product/PostgreSQL remain Ready and PVC remains Bound.
- No public ingress, LoadBalancer, Secret value, or unrelated service is introduced.
