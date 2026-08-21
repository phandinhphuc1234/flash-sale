# Tasks: Product Pilot GitOps Rollback Rehearsal

**Input**: Design documents from `/specs/027-gitops-rollback-rehearsal/`

**Prerequisites**: Approved `spec.md`, `plan.md`, `research.md`, `data-model.md`, and `quickstart.md`.

## Phase 1: Setup and preflight

- [ ] T001 Verify the current `dev-pilot` Argo health, Product rollout, current image tag, and previous ECR tag with the commands in `specs/027-gitops-rollback-rehearsal/quickstart.md`.
- [ ] T002 Confirm that no `.env`, Kubernetes Secret, application source, or ECR deletion is part of the rollback change.

## Phase 2: User Story 1 — Reviewed rollback (P1)

- [ ] T003 [US1] Create `codex/gitops-phase12-rollback` from `origin/develop` and revert merge commit `50d51ebca7d65907f4fd5e234cb25d9286a3de83`.
- [ ] T004 [US1] Validate `git diff --check`, the single-file diff, and `kubectl kustomize infra/k8s/overlays/dev-pilot` before pushing the rollback branch.
- [ ] T005 [US1] Push the rollback branch and open a PR targeting protected `develop`; record the required `Maven Verify` result.
- [ ] T006 [US1] Merge the approved rollback PR and verify Argo `Synced`/`Healthy`, Product rollout, and previous immutable image tag.

## Phase 3: User Story 2 — Restore forward state (P2)

- [ ] T007 [US2] Create a restoration branch that reverts the rollback merge commit and validate the single desired-state diff.
- [ ] T008 [US2] Open and merge the restoration PR after required CI passes.
- [ ] T009 [US2] Verify Argo and Product rollout return to `pilot-11cdf76ace24e6402eae0dbc1082d077766e3c72`.

## Phase 4: Evidence and closeout

- [ ] T010 Record rollback and restoration PRs, Git revisions, Argo revisions, image tags, rollout status, and command results in `specs/027-gitops-rollback-rehearsal/validation.md`.
- [ ] T011 Run `git diff --check` and review the final diff for secret-safe, desired-state-only changes.

## Dependencies

- T001–T002 precede all live changes.
- T003–T006 must complete before restoration begins.
- T007–T009 depend on the successful rollback observation.
- T010–T011 close the feature after both states are verified.

## MVP

The MVP is T001–T006: a reviewed rollback that returns Product to the previous known-good image.
