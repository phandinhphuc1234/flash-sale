# Tasks: Cloud Database Migration Gates

## Phase 1: Artifacts

- [x] T001 Add approved migration spec and plan.
- [x] T002 Add migration ADR documenting the separate overlay and operator gate.

## Phase 2: Migration overlay

- [x] T003 Add migration Kustomization with seven database Jobs; explicitly exclude stateless Gateway.
- [x] T004 [P] Add Authentication migration Job.
- [x] T005 [P] Add Product migration Job.
- [x] T006 [P] Add Campaign migration Job.
- [x] T007 [P] Add Flash Sale migration Job.
- [x] T008 [P] Add Inventory migration Job.
- [x] T009 [P] Add Order migration Job.
- [x] T010 [P] Add Payment migration Job.

## Phase 3: Operator runner

- [x] T011 Add validation-only prerequisite checks.
- [x] T012 Add explicit `-Apply` Job creation and completion waits.
- [x] T013 Add safe `-ForceRerun` behavior and failure preservation.

## Phase 4: Verification

- [x] T014 Run Kustomize render and client dry-run.
- [x] T015 Run validation-only script and missing-resource negative test.
- [x] T016 Assert seven Jobs, service-specific Secrets, disabled listeners, and no legacy Secret.
- [x] T017 Record validation evidence and complete the task ledger.

## Dependencies

T001-T002 precede implementation. T003-T010 precede T011-T013. T014-T017 are final gates. No live
`-Apply` is run by the agent.
