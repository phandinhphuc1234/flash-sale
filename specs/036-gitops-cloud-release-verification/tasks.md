# Tasks: Cloud Release Artifact Verification

**Input**: Design documents from `specs/036-gitops-cloud-release-verification/`

**Status**: Approved

## Phase 1: Setup

- [x] T001 Confirm the Phase 21 feature pointer and approved scope in `.specify/feature.json` and `specs/036-gitops-cloud-release-verification/spec.md`.
- [x] T002 [P] Record the ECR digest comparison, read-only boundary, and cloud-as-staging decision in `specs/036-gitops-cloud-release-verification/research.md` and `plan.md`.

## Phase 2: Foundational

- [x] T003 [P] Add the eight-service and seven-Payment-flag inventories to `infra/scripts/gitops/phase21-cloud-release-verify.ps1` without reading Kubernetes Secrets.
- [x] T004 Add bounded `kubectl` and AWS CLI execution plus Argo/Deployment/ECR parsing to `infra/scripts/gitops/phase21-cloud-release-verify.ps1`.

## Phase 3: User Story 1 - Verify the cloud release artifact (Priority: P1)

**Goal**: Verify Argo health, eight available Deployments, matching ECR/runtime digests, disabled
Payment flags, and Gateway smoke outcomes without persistent mutation.

**Independent Test**: Run the Phase 21 script against `flash-sale-dev` and observe 8/8 digest matches,
`flash-sale-cloud` `Synced|Healthy`, 7/7 disabled flags, and readiness/catalog/admin `200/200/401`.

- [x] T005 [US1] Compare each Deployment image tag and Pod `imageID` with the ECR manifest digest in `infra/scripts/gitops/phase21-cloud-release-verify.ps1`.
- [x] T006 [US1] Verify Argo Application status, live revision, Deployment availability, and Payment ConfigMap flags in `infra/scripts/gitops/phase21-cloud-release-verify.ps1`.
- [x] T007 [US1] Invoke the existing Gateway smoke helper and preserve its localhost-only cleanup boundary in `infra/scripts/gitops/phase21-cloud-release-verify.ps1`.
- [x] T008 [US1] Document Phase 21 command, expected output, and safety boundary in `infra/scripts/gitops/README.md` and `specs/036-gitops-cloud-release-verification/quickstart.md`.

## Phase 4: Validation and evidence

- [x] T009 Run PowerShell AST/static checks, `git diff --check`, and `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`; record results in `specs/036-gitops-cloud-release-verification/validation.md`.
- [x] T010 Run the live Phase 21 verifier, record Argo revision, eight image tag/digest pairs, smoke results, and Payment flag results in `specs/036-gitops-cloud-release-verification/validation.md`.
- [x] T011 Mark T001-T010 complete and set `spec.md` to `Verified` only after all required checks pass.

## Dependencies and execution order

- T001-T002 establish the approved scope and design.
- T003-T004 provide the shared operator foundation.
- T005-T008 implement the independently testable P1 story.
- T009-T011 require the implementation and live cloud access.

## Parallel opportunities

- T002 and T003 can proceed in parallel after the spec is approved.
- T005 and T006 touch independent verification functions and can be developed in parallel before T007 integration.
- T009 static checks can run while the live environment remains unchanged.

## Implementation strategy

Deliver one read-only verifier as the MVP. Stop after the static checkpoint, review the script, then
run the live evidence command. Do not build images, modify the overlay, enable Payment, or introduce
production infrastructure in this phase.
