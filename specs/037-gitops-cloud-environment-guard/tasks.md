# Tasks: Cloud Environment and Configuration Guard

**Input**: Design documents from `/specs/037-gitops-cloud-environment-guard/`

**Tests**: Static PowerShell parsing, Kustomize dry-run, live read-only guard, and diff check are
required. Maven and load tests are not applicable because no Java or business behavior changes.

## Phase 1: Setup

- [x] T001 Create the Phase 22 Spec Kit artifacts under `specs/037-gitops-cloud-environment-guard/`.
- [x] T002 Update `.specify/feature.json` to the Phase 22 feature directory.

## Phase 2: Foundational

- [x] T003 Implement bounded native command execution in `infra/scripts/gitops/phase22-cloud-guard.ps1`.
- [x] T004 Add read-only EKS context, Kustomize, and Argo ownership checks in `infra/scripts/gitops/phase22-cloud-guard.ps1`.

## Phase 3: User Story 1 - Prove cloud ownership and configuration boundaries (Priority: P1)

**Independent Test**: Run the Phase 22 verifier and observe all guard sections PASS with no mutation.

- [x] T005 [US1] Verify application ConfigMap/Secret references and JWT mount in `infra/scripts/gitops/phase22-cloud-guard.ps1`.
- [x] T006 [US1] Verify platform Secret references, private Services, Kafka settings, and Payment flags in `infra/scripts/gitops/phase22-cloud-guard.ps1`.
- [x] T007 [US1] Document the Phase 22 command and safety boundary in `infra/scripts/gitops/README.md` and `specs/037-gitops-cloud-environment-guard/quickstart.md`.
- [x] T008 [US1] Run PowerShell parse, Kustomize render, client dry-run, and `git diff --check`.
- [x] T009 [US1] Run the live Phase 22 guard and record evidence in `specs/037-gitops-cloud-environment-guard/validation.md`.

## Phase 4: Polish

- [x] T010 Update feature status/tasks and review the final diff for Secret-value and mutation leaks.

## Dependencies & Execution Order

- Setup (T001-T002) precedes all implementation.
- Foundational (T003-T004) precedes User Story 1.
- T005-T006 can be implemented in parallel after T004; T007 follows the script contract.
- T008-T010 require the complete guard and evidence.
