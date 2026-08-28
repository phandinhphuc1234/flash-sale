# Tasks: Adaptive Seckill Capacity Gate

**Input**: Design documents from `/specs/046-seckill-capacity-gate/`

## Phase 1: Setup

- [x] T001 Create the Phase 26 feature artifacts and point `.specify/feature.json` to `specs/046-seckill-capacity-gate`.
- [x] T002 [P] Add Git-ignored result/token boundaries for the adaptive runner in `.gitignore`.

## Phase 2: Foundational

- [x] T003 [P] Define the invocation/result contract in `specs/046-seckill-capacity-gate/contracts/capacity-runner.md`.
- [x] T004 [P] Add the adaptive k6 profile in `load-tests/flashsale-service/adaptive-arrival-rate.js`.
- [x] T005 Add the PowerShell parameter/guardrail model in `infra/scripts/gitops/phase26-seckill-capacity.ps1`.

## Phase 3: User Story 1 — Safe capacity ceiling

- [x] T006 [US1] Implement bounded stage orchestration, cooldown, consecutive breach logic, and hard-cap outcome in `infra/scripts/gitops/phase26-seckill-capacity.ps1`.
- [x] T007 [US1] Implement k6 stage summary parsing and sanitized report output in `infra/scripts/gitops/phase26-seckill-capacity.ps1`.
- [x] T008 [P] [US1] Add parser/static safety tests in `infra/scripts/gitops/tests/phase26-seckill-capacity.tests.ps1`.

## Phase 4: User Story 2 — Correctness evidence

- [x] T009 [US2] Add winner/replay/sold-out/pending/unexpected and dropped-iteration metrics to `load-tests/flashsale-service/adaptive-arrival-rate.js`.
- [x] T010 [US2] Add immediate correctness/workload danger checks in `infra/scripts/gitops/phase26-seckill-capacity.ps1`; platform health remains a prerequisite from Phase 25.

## Phase 5: User Story 3 — Audit and cleanup

- [x] T011 [US3] Add bounded child-process cleanup and preserve the operator-owned ignored token file in `infra/scripts/gitops/phase26-seckill-capacity.ps1`.
- [x] T012 [US3] Document local-first and controlled cloud execution in `specs/046-seckill-capacity-gate/quickstart.md` and update `validation.md` with results.

## Phase 6: Polish and verification

- [x] T013 Run PowerShell parser/static tests and k6 profile inspection.
- [ ] T014 Run a disposable local adaptive test and record sanitized evidence.
- [x] T015 Run `git diff --check` and reconcile spec/plan/tasks/validation status.

## Dependencies

`T001–T005` → `T006–T010` → `T011–T012` → `T013–T015`.

Cloud execution is optional and must occur only after local evidence and explicit operator choice.
