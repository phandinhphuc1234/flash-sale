# Tasks: Cloud Secrets and Service Configuration

**Input**: Design documents from `/specs/030-gitops-cloud-secrets-config/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, accepted ADR

## Phase 1: Setup and inventory

- [x] T001 Add accepted per-boundary Secret ADR in `docs/adr/0020-cloud-secret-and-config-boundary.md`
- [x] T002 Add the repository-wide manual/deferred inventory in `infra/CONFIGURATION.md`
- [x] T003 Update `.specify/feature.json` to `specs/030-gitops-cloud-secrets-config`

## Phase 2: Cloud ConfigMaps

- [x] T004 [P] Add ConfigMap Kustomization in `infra/k8s/overlays/cloud/config/kustomization.yaml`
- [x] T005 [P] Add API Gateway ConfigMap in `infra/k8s/overlays/cloud/config/api-gateway-runtime-config.yaml`
- [x] T006 [P] Add Authentication ConfigMap in `infra/k8s/overlays/cloud/config/authentication-service-runtime-config.yaml`
- [x] T007 [P] Add Product ConfigMap in `infra/k8s/overlays/cloud/config/product-service-runtime-config.yaml`
- [x] T008 [P] Add Campaign ConfigMap in `infra/k8s/overlays/cloud/config/campaign-service-runtime-config.yaml`
- [x] T009 [P] Add Flash Sale ConfigMap in `infra/k8s/overlays/cloud/config/flash-sale-service-runtime-config.yaml`
- [x] T010 [P] Add Inventory ConfigMap in `infra/k8s/overlays/cloud/config/inventory-service-runtime-config.yaml`
- [x] T011 [P] Add Order ConfigMap in `infra/k8s/overlays/cloud/config/order-service-runtime-config.yaml`
- [x] T012 [P] Add Payment ConfigMap in `infra/k8s/overlays/cloud/config/payment-service-runtime-config.yaml`

## Phase 3: Secret boundary patches

- [x] T013 [P] Add API Gateway envFrom patch in `infra/k8s/overlays/cloud/patches/api-gateway-envfrom.yaml`
- [x] T014 [P] Add Authentication envFrom/JWT volume patch in `infra/k8s/overlays/cloud/patches/authentication-service-envfrom.yaml`
- [x] T015 [P] Add Product envFrom patch in `infra/k8s/overlays/cloud/patches/product-service-envfrom.yaml`
- [x] T016 [P] Add Campaign envFrom patch in `infra/k8s/overlays/cloud/patches/campaign-service-envfrom.yaml`
- [x] T017 [P] Add Flash Sale envFrom patch in `infra/k8s/overlays/cloud/patches/flash-sale-service-envfrom.yaml`
- [x] T018 [P] Add Inventory envFrom patch in `infra/k8s/overlays/cloud/patches/inventory-service-envfrom.yaml`
- [x] T019 [P] Add Order envFrom patch in `infra/k8s/overlays/cloud/patches/order-service-envfrom.yaml`
- [x] T020 [P] Add Payment envFrom patch in `infra/k8s/overlays/cloud/patches/payment-service-envfrom.yaml`
- [x] T021 Switch Phase 14 platform manifests to `platform-secrets` in
  `infra/k8s/overlays/cloud/platform/postgres-statefulset.yaml` and `redis-statefulset.yaml`
- [x] T022 Include config resources and patches from `infra/k8s/overlays/cloud/kustomization.yaml`

## Phase 4: User Story 1 - Manual inventory (P1)

**Independent test**: Validation-only script reports missing key names/files without values.

- [x] T023 [US1] Add validation-only input parser in `infra/scripts/gitops/phase15-secrets.ps1`
- [x] T024 [US1] Check required manual keys and JWT files in `infra/scripts/gitops/phase15-secrets.ps1`
- [x] T025 [US1] Add optional Stripe classification with `-EnableStripe`

## Phase 5: User Story 2 - Service ConfigMaps (P1)

**Independent test**: Rendered Deployments use common ConfigMap + service ConfigMap + service Secret.

- [x] T026 [US2] Assert all eight service ConfigMaps are rendered
- [x] T027 [US2] Assert no cloud Deployment references `flash-sale-secrets`
- [x] T028 [US2] Assert Gateway Flash Sale URL uses `flash-sale-service:8080`
- [x] T029 [US2] Assert Payment feature flags remain disabled

## Phase 6: User Story 3 - Opt-in Secret provisioning (P1)

**Independent test**: Default mode does not mutate; `-Apply` is explicit and output is value-safe.

- [x] T030 [US3] Add per-boundary Secret mapping and filtered temp files to
  `infra/scripts/gitops/phase15-secrets.ps1`
- [x] T031 [US3] Add `auth-jwt` file Secret provisioning to `infra/scripts/gitops/phase15-secrets.ps1`
- [x] T032 [US3] Ensure `-Apply` is never executed automatically and output excludes values

## Phase 7: Polish and evidence

- [x] T033 Run inventory/source comparison and record missing optional defaults
- [x] T034 Run `kubectl kustomize infra/k8s/overlays/cloud`
- [x] T035 Run `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`
- [x] T036 Run validation-only, missing-input negative, and secret-pattern tests
- [x] T037 Run `git diff --check` and record `validation.md`
- [x] T038 Mark tasks complete only after all checks pass

## Dependencies and Execution Order

- T001-T003 precede implementation.
- T004-T022 define ConfigMap/Secret boundaries before script assertions.
- T023-T025 can run after T002 and before apply behavior.
- T026-T029 depend on ConfigMap and patch resources.
- T030-T032 depend on the inventory and mapping decisions.
- T033-T038 are final gates.

## Implementation Strategy

1. Inventory and document first.
2. Add non-secret ConfigMaps and per-service patches.
3. Add validation-only Secret script.
4. Validate render/dry-run and safety.
5. Ask the operator to fill manual values; do not apply automatically.
