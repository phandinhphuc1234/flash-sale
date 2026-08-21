# Tasks: Product Pilot Image Promotion

**Input**: Design documents from `/specs/026-gitops-image-promotion/`

**Prerequisites**: Approved `spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/image-promotion.md`, and `quickstart.md`.

## Phase 1: Setup and contracts

- [x] T001 Confirm the active feature pointer and Phase 10 Argo `dev-pilot` baseline in `.specify/feature.json` and `specs/026-gitops-image-promotion/validation.md`.
- [x] T002 [P] Record the delivery ownership and rollback decision in `docs/adr/0009-github-actions-ecr-argo-promotion.md`.
- [x] T003 [P] Document workflow/image/Kustomize contracts in `specs/026-gitops-image-promotion/contracts/image-promotion.md`.

## Phase 2: Foundational delivery safety

- [x] T004 Add workflow permissions and path-trigger design to `.github/workflows/product-pilot-delivery.yml` without enabling direct `develop` pushes.
- [x] T005 [P] Add local validation/publish/update helper in `infra/scripts/gitops/phase11-product-delivery.ps1` with secret-safe output.
- [x] T006 [P] Document prerequisites and evidence commands in `specs/026-gitops-image-promotion/quickstart.md`.

## Phase 3: User Story 1 — Publish a verified immutable image (P1)

- [x] T007 [US1] Add Product Maven verification and GitHub OIDC/ECR login steps to `.github/workflows/product-pilot-delivery.yml`.
- [x] T008 [US1] Add commit-derived Docker build and immutable ECR push steps to `.github/workflows/product-pilot-delivery.yml`.
- [x] T009 [US1] Validate the local helper's Maven/build path; hosted ECR push remains an operator-run check in `infra/scripts/gitops/phase11-product-delivery.ps1`.

## Phase 4: User Story 2 — Promote through reviewed Git state (P1)

- [x] T010 [US2] Add single-file overlay update and no-empty-diff guard to `.github/workflows/product-pilot-delivery.yml`.
- [x] T011 [US2] Add automation branch push, pull-request creation, and explicit `ci.yml` dispatch to `.github/workflows/product-pilot-delivery.yml`.
- [ ] T012 [US2] Verify the workflow PR keeps `develop` protected and lets Argo reconcile `infra/k8s/overlays/dev-pilot` after merge.

## Phase 5: User Story 3 — Reproduce the flow locally (P2)

- [x] T013 [US3] Implement explicit `-Push` and `-UpdateOverlay` safeguards in `infra/scripts/gitops/phase11-product-delivery.ps1`.
- [x] T014 [US3] Validate the dev-pilot overlay rendering with `kubectl kustomize infra/k8s/overlays/dev-pilot`.

## Phase 6: Polish and evidence

- [x] T015 Run PowerShell parser, `git diff --check`, and a secret-value scan for changed files.
- [x] T016 Run `./mvnw --batch-mode --no-transfer-progress -pl services/product-service -am verify`.
- [ ] T017 Record local validation, hosted workflow, ECR, PR, and Argo evidence in
  `specs/026-gitops-image-promotion/validation.md`.
- [x] T018 Update `infra/scripts/gitops/README.md` with the Phase 11 command and safety behavior.

## Dependencies and execution order

- Setup and contracts (T001–T003) precede delivery implementation.
- Foundational safety (T004–T006) precedes all user stories.
- US1 (T007–T009) precedes US2 because a promotion PR needs an image.
- US3 (T013–T014) can be validated independently after T005.
- Polish/evidence (T015–T018) depends on all desired stories.

## MVP scope

T001–T012 deliver the hosted Product image-to-Argo loop. T013–T018 complete local rehearsal and
evidence.
