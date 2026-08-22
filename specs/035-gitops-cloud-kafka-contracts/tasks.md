# Tasks: Cloud Kafka Contract Provisioning

**Input**: Design documents from `specs/035-gitops-cloud-kafka-contracts/`

**Status**: Approved

## Phase 1: Setup

- [x] T001 Create and approve the Phase 20 specification, checklist, plan, research, data model, contract inventory, and quickstart under `specs/035-gitops-cloud-kafka-contracts/`
- [x] T002 [P] Record explicit root-owned cloud Kafka administration in `docs/adr/0023-cloud-kafka-contract-provisioning.md`
- [x] T003 [P] Point `.specify/feature.json` to `specs/035-gitops-cloud-kafka-contracts`

## Phase 2: Foundational

- [x] T004 Disable implicit user-topic creation in `infra/k8s/overlays/cloud/platform/kafka-statefulset.yaml` and validate the cloud overlay render/client dry-run
- [x] T005 [P] Add non-mutating `-CheckOnly` and exact-latest Git schema verification to `infra/docker/schema-registry/register-campaign-schemas.ps1`
- [x] T006 [P] Add exact-latest Git schema verification to `infra/docker/schema-registry/register-flashsale-schemas.ps1`
- [x] T007 [P] Register and verify both OrderCreated and the deferred PurchaseAccepted Order DLT subject in `infra/docker/schema-registry/register-order-schemas.ps1`
- [x] T008 [P] Add exact-latest Git schema verification to `infra/docker/schema-registry/register-payment-schemas.ps1`

## Phase 3: User Story 1 — Materialize approved cloud contracts (Priority: P1)

**Goal**: Seven topics and nine exact schema subjects can be materialized explicitly.

**Independent Test**: Run explicit apply after merge and verify every topic topology, subject record,
latest identity, and compatibility value.

- [x] T009 [US1] Implement EKS/Argo preflight, seven-topic create/empty-expansion, schema registration orchestration, and post-apply verification in `infra/scripts/gitops/phase20-kafka-contracts.ps1`
- [x] T010 [P] [US1] Document Phase 20 sequence and boundaries in `infra/scripts/gitops/README.md` and align the Order registrar description in `infra/docker/README.md`
- [x] T011 [US1] Run PowerShell parser checks, `./mvnw.cmd -pl contracts/kafka-avro-contracts -am verify`, cloud Kustomize render/client dry-run, and validation-only live inventory; record pre-merge results in `specs/035-gitops-cloud-kafka-contracts/validation.md`

## Phase 4: User Story 2 — Detect drift and rerun safely (Priority: P2)

**Goal**: Missing state is distinguishable from unsafe drift, and repeated apply is idempotent.

**Independent Test**: Pre-merge apply is rejected before mutation; after merge, two consecutive apply
runs leave topic topology and schema-version counts unchanged on the second run.

- [x] T012 [US2] Add origin/develop merge gating, no-delete drift refusal, zero-record partition-expansion guard, localhost port ownership validation, exact child-process cleanup, and bounded failure output to `infra/scripts/gitops/phase20-kafka-contracts.ps1`
- [x] T013 [US2] Verify pre-merge apply rejection leaves topics and subjects unchanged and record the result in `specs/035-gitops-cloud-kafka-contracts/validation.md`

## Phase 5: Live verification and evidence

- [ ] T014 Run Phase 20 apply after merge, verify `flash-sale-cloud` remains Synced/Healthy, seven topics and nine subjects are exact, then rerun apply to prove idempotence
- [ ] T015 Record live IDs/versions/topology/compatibility and update `specs/035-gitops-cloud-kafka-contracts/validation.md`, `tasks.md`, and `spec.md` to Verified

## Dependencies and execution order

- T001-T003 establish approved scope, ownership, and the active feature.
- T004-T008 are foundational and block the runner's complete verification path.
- T009-T011 implement and validate the primary provisioning flow.
- T012-T013 complete destructive-drift and pre-merge safety gates.
- T014-T015 require the reviewed Phase 20 branch to be merged into `develop`.

## Parallel opportunities

- T002 and T003 touch independent files.
- T005-T008 modify separate registration scripts and can run in parallel after T004's decision is
  recorded.
- T010 can proceed while T009 is implemented.
- Live broker/registry mutation is never parallelized.

## Implementation strategy

Deliver one reviewable, merge-gated provisioning workflow. Stop after pre-merge evidence and push the
branch for review. Run T014-T015 only after merge. Do not enable Payment, publish synthetic business
events, configure retention, install a Kafka operator, or delete broker/registry state.
