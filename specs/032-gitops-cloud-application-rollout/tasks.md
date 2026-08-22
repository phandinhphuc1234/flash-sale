# Tasks: Cloud Application Rollout

**Input**: Design documents from `specs/032-gitops-cloud-application-rollout/`

**Status**: Approved

## Phase 1: Foundation

- [x] T001 Create Phase 17 approved spec, plan, research, data model, and quickstart in `specs/032-gitops-cloud-application-rollout/`.
- [x] T002 [P] Point `.specify/feature.json` to `specs/032-gitops-cloud-application-rollout`.

## Phase 2: Payment probe safety (US2)

- [x] T003 [US2] Keep the Payment API security chain conditional while making the Actuator-safe default chain available in `services/payment-service/src/main/java/com/philia/flashsale/payment/security/PaymentSecurityConfiguration.java`.
- [x] T004 [US2] Add a focused security composition test in `services/payment-service/src/test/java/com/philia/flashsale/payment/security/PaymentSecurityConfigurationTests.java`.

## Phase 3: Cloud application rollout (US1)

- [x] T005 [US1] Add validation-only and explicit-apply rollout orchestration in `infra/scripts/gitops/phase17-application-rollout.ps1`.
- [x] T006 [US1] Document Phase 17 command order and failure inspection in `infra/scripts/gitops/README.md` and `specs/032-gitops-cloud-application-rollout/quickstart.md`.

## Phase 4: Verification

- [x] T007 Run Payment module verification and Kustomize client dry-run.
- [x] T008 Run Phase 17 validation and live cloud rollout; record evidence in `specs/032-gitops-cloud-application-rollout/validation.md`.

## Dependencies

T001-T002 precede implementation. T003-T004 are required before the application rollout. T005-T006
can proceed after the approved design. T007-T008 are final gates.
