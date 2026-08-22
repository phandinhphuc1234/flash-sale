# Tasks: Terraform Safety and Drift Gate

**Input**: Design documents from `/specs/038-gitops-terraform-safety-gate/`

**Tests**: PowerShell parse, Terraform fmt/validate/plan, AWS identity, negative input, and diff check.

## Phase 1: Setup

- [x] T001 Create Phase 23 Spec Kit artifacts under `specs/038-gitops-terraform-safety-gate/`.
- [x] T002 Update `.specify/feature.json` to the Phase 23 feature directory.

## Phase 2: Foundational

- [x] T003 Implement bounded Terraform/AWS command execution in `infra/scripts/gitops/phase23-terraform-gate.ps1`.
- [x] T004 Validate the local CIDR input, optional `-AutoDetectPublicIp`, AWS profile/region, and Terraform working directory in `infra/scripts/gitops/phase23-terraform-gate.ps1`.

## Phase 3: User Story 1 - Review infrastructure changes safely (Priority: P1)

**Independent Test**: Run the gate with a valid CIDR and observe formatting, validation, identity, and plan classification without apply.

- [x] T005 [US1] Run Terraform fmt/validate and detailed plan with safe exit-code classification in `infra/scripts/gitops/phase23-terraform-gate.ps1`.
- [x] T006 [US1] Document Phase 23 commands and no-apply boundary in `infra/scripts/gitops/README.md` and `specs/038-gitops-terraform-safety-gate/quickstart.md`.
- [x] T007 [US1] Run static parser, Terraform checks, negative CIDR test, and `git diff --check`.
- [x] T008 [US1] Run the live read-only gate and record evidence in `specs/038-gitops-terraform-safety-gate/validation.md`.

## Phase 4: Polish

- [x] T009 Update feature status/tasks and audit output for credential/state leakage.

## Dependencies & Execution Order

- Setup precedes Foundational.
- Foundational precedes User Story 1.
- T005 and T006 can proceed after T004; T007-T009 require the complete helper.
