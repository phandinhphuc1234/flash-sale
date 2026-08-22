# Tasks: Eight-Service GitOps Image Delivery

**Input**: Design documents from `/specs/039-gitops-eight-service-delivery/`

**Prerequisites**: Approved `spec.md`, `plan.md`, `research.md`, `data-model.md`, and
`contracts/image-promotion.md`.

## Phase 1: Setup and contracts

- [x] T001 Update `.specify/feature.json` to `specs/039-gitops-eight-service-delivery`.
- [x] T002 [P] Add the eight-service delivery contract in `specs/039-gitops-eight-service-delivery/contracts/image-promotion.md`.
- [x] T003 [P] Record the generalized cloud promotion decision in `docs/adr/0024-github-actions-eight-service-promotion.md`.

## Phase 2: Foundational delivery mapping

- [x] T004 Define the eight target mapping and shared-change rules in `.github/workflows/service-delivery.yml`.
- [x] T005 Add manual dispatch inputs and serialized `develop` concurrency in `.github/workflows/service-delivery.yml`.
- [x] T006 Retire the Product-only hosted trigger in `.github/workflows/product-pilot-delivery.yml` so Product changes have one cloud delivery owner.

## Phase 3: User Story 1 — Verify and publish selected images (P1)

- [x] T007 [US1] Add changed-path detection with service-local and shared fan-out behavior in `.github/workflows/service-delivery.yml`.
- [x] T008 [US1] Add matrix Maven reactor verification for the eight target modules in `.github/workflows/service-delivery.yml`.
- [x] T009 [US1] Add GitHub OIDC, ECR login, Docker build, and immutable push steps in `.github/workflows/service-delivery.yml`.
- [x] T010 [US1] Add failure gating so any matrix failure prevents the promotion job in `.github/workflows/service-delivery.yml`.

## Phase 4: User Story 2 — Promote through reviewed Git state (P1)

- [x] T011 [US2] Add exact selected-image `newTag` updates for `infra/k8s/overlays/cloud/kustomization.yaml` in `.github/workflows/service-delivery.yml`.
- [x] T012 [US2] Add automation branch push, single PR creation, diff guard, and CI dispatch in `.github/workflows/service-delivery.yml`.
- [x] T013 [US2] Ensure the workflow never runs `kubectl`, writes Secrets, pushes directly to `develop`, or auto-merges in `.github/workflows/service-delivery.yml`.

## Phase 5: User Story 3 — Operate and validate safely (P2)

- [x] T014 [US3] Document manual dispatch, PR review, Argo reconciliation, and Secret boundaries in `infra/scripts/gitops/README.md`.
- [x] T015 [US3] Update canonical roadmap status and Phase 21 evidence links in `docs/deployment/gitops-roadmap-status.md`.
- [x] T016 [US3] Add workflow static checks and target-mapping assertions under `specs/039-gitops-eight-service-delivery/validation.md`.

## Phase 6: Polish and evidence

- [x] T017 Run YAML/static workflow checks and `git diff --check`.
- [x] T018 Run `./mvnw clean verify` and `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`.
- [ ] T019 After hosted execution and PR merge, run `infra/scripts/gitops/phase21-cloud-release-verify.ps1` and record run/PR/Argo evidence in `specs/039-gitops-eight-service-delivery/validation.md`.

## Dependencies and execution order

- Setup/contracts (T001–T003) precede workflow changes.
- Mapping/foundation (T004–T006) precedes user stories.
- US1 (T007–T010) precedes US2 because promotion requires successful images.
- US3 (T014–T016) can be documented while US1/US2 are reviewed.
- Polish/evidence (T017–T019) depends on the hosted workflow and promotion PR.

## MVP scope

T001–T013 deliver the eight-service hosted image-to-Argo loop. T014–T019 complete operator
documentation and evidence.
