# Tasks: Local and Cloud Environment Foundation

**Input**: Design documents from `/specs/028-gitops-local-cloud-foundation/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`

## Phase 1: Setup

- [x] T001 Add the local/cloud environment contract to `infra/ENVIRONMENTS.md`
- [x] T002 Add the Phase 13 validation quickstart to `infra/README.md`

## Phase 2: Foundational environment target

- [x] T003 [P] Add the canonical full-stack cloud overlay in `infra/k8s/overlays/cloud/kustomization.yaml`
- [x] T004 [P] Add non-secret cloud runtime defaults in `infra/k8s/overlays/cloud/flash-sale-config.yaml`
- [x] T005 [P] Add the value-safe environment validator in `infra/scripts/gitops/phase13-environment-contract.ps1`

## Phase 3: User Story 1 - Identify the target environment (P1)

**Independent test**: The validator accepts `local` and `cloud`, rejects `product`, and documentation
maps each label to one source of truth.

- [x] T006 [US1] Validate local and cloud source paths in `infra/scripts/gitops/phase13-environment-contract.ps1`
- [x] T007 [US1] Validate unsupported environment handling in `infra/scripts/gitops/phase13-environment-contract.ps1`

## Phase 4: User Story 2 - Render the canonical cloud topology (P1)

**Independent test**: Kustomize render and client-side dry-run succeed and contain all eight
application Deployments and Services.

- [x] T008 [US2] Render `infra/k8s/overlays/cloud` with `kubectl kustomize`
- [x] T009 [US2] Run client-side dry-run for `infra/k8s/overlays/cloud`
- [x] T010 [US2] Confirm the eight-service image mapping in `infra/k8s/overlays/cloud/kustomization.yaml`

## Phase 5: User Story 3 - Preserve safe configuration boundaries (P2)

**Independent test**: Validator output contains secret names but no values, and `.env` is ignored.

- [x] T011 [US3] Add the non-sensitive secret inventory to `infra/ENVIRONMENTS.md`
- [x] T012 [US3] Verify ignored `.env` handling without reading its contents in `infra/scripts/gitops/phase13-environment-contract.ps1`
- [x] T013 [US3] Run a value-leak review over the validator output and changed files

## Phase 6: Polish and evidence

- [x] T014 Run `git diff --check` for the Phase 13 change set
- [x] T015 Record commands and results in `specs/028-gitops-local-cloud-foundation/validation.md`
- [x] T016 Update this task ledger only after all required validations pass

## Dependencies and Execution Order

- Setup (T001-T002) precedes the foundational environment target.
- Foundational tasks (T003-T005) precede user-story validation.
- T006-T007 and T011-T013 depend on T005.
- T008-T010 depend on T003-T004.
- T014-T016 are final gates.

## Implementation Strategy

1. Establish documentation and the cloud overlay.
2. Add the validator without reading secret values.
3. Validate both positive environments and the negative `product` case.
4. Record evidence, then stop before stateful services and secret provisioning.
