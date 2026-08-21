# Tasks: Cloud Stateful Service Foundation

**Input**: Design documents from `/specs/029-gitops-cloud-stateful-foundation/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, accepted ADR

## Phase 1: Setup and architecture

- [x] T001 Add accepted single-node cloud stateful ADR in `docs/adr/0019-cloud-single-node-stateful-foundation.md`
- [x] T002 Update active feature pointer in `.specify/feature.json`

## Phase 2: Foundational platform manifests

- [x] T003 [P] Add platform Kustomization in `infra/k8s/overlays/cloud/platform/kustomization.yaml`
- [x] T004 [P] Add PostgreSQL init database ConfigMap in `infra/k8s/overlays/cloud/platform/postgres-init-configmap.yaml`
- [x] T005 [P] Add PostgreSQL StatefulSet with `gp2` PVC in `infra/k8s/overlays/cloud/platform/postgres-statefulset.yaml`
- [x] T006 [P] Add PostgreSQL internal Service in `infra/k8s/overlays/cloud/platform/postgres-service.yaml`
- [x] T007 [P] Add Redis StatefulSet with AOF and Secret reference in `infra/k8s/overlays/cloud/platform/redis-statefulset.yaml`
- [x] T008 [P] Add Redis internal Service in `infra/k8s/overlays/cloud/platform/redis-service.yaml`
- [x] T009 [P] Add Kafka KRaft StatefulSet in `infra/k8s/overlays/cloud/platform/kafka-statefulset.yaml`
- [x] T010 [P] Add Kafka internal Service in `infra/k8s/overlays/cloud/platform/kafka-service.yaml`
- [x] T011 [P] Add Schema Registry Deployment in `infra/k8s/overlays/cloud/platform/schema-registry-deployment.yaml`
- [x] T012 [P] Add Schema Registry internal Service in `infra/k8s/overlays/cloud/platform/schema-registry-service.yaml`
- [x] T013 Include the platform Kustomization from `infra/k8s/overlays/cloud/kustomization.yaml`

## Phase 3: User Story 1 - Start cloud platform dependencies (P1)

**Independent test**: Kustomize renders and client-side dry-run succeeds with all four platform
components and no public Service.

- [x] T014 [US1] Add cloud stateful preflight script in `infra/scripts/gitops/phase14-stateful-preflight.ps1`
- [x] T015 [US1] Validate platform resource counts and internal Service types in the preflight script

## Phase 4: User Story 2 - Preserve data and service discovery (P1)

**Independent test**: Rendered manifests contain stable DNS names, one replica, `gp2` PVC templates,
and protocol-specific probes.

- [x] T016 [US2] Add PVC and DNS assertions to `infra/scripts/gitops/phase14-stateful-preflight.ps1`
- [x] T017 [US2] Document platform endpoints and logical databases in
  `specs/029-gitops-cloud-stateful-foundation/data-model.md`

## Phase 5: User Story 3 - Keep credentials outside Git (P1)

**Independent test**: Strict preflight checks Secret existence without reading Secret data, and a
  secret-pattern scan finds no values.

- [x] T018 [US3] Add optional `-RequireSecrets` validation to `infra/scripts/gitops/phase14-stateful-preflight.ps1`
- [x] T019 [US3] Scan changed platform files and preflight output for credential/value leakage

## Phase 6: Polish and evidence

- [x] T020 Run `kubectl kustomize infra/k8s/overlays/cloud`
- [x] T021 Run `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`
- [x] T022 Run `git diff --check` and record results in
  `specs/029-gitops-cloud-stateful-foundation/validation.md`
- [x] T023 Mark completed tasks only after every required check passes

## Dependencies and Execution Order

- T001 must precede platform manifest implementation because it records the single-node exception.
- T003-T013 build the manifest foundation.
- T014-T015 depend on T003-T013.
- T016-T019 depend on the platform manifests and preflight script.
- T020-T023 are final gates.

## Implementation Strategy

1. Record the architecture decision.
2. Add manifests without Secret values.
3. Add preflight and safety checks.
4. Validate render/dry-run and stop before live apply.
5. Provision Secrets and service-specific config in Phase 15, then run the live readiness checks.
