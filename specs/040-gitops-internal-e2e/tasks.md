# Tasks: Internal Authenticated Cloud End-to-End Smoke

**Input**: Design documents from `/specs/040-gitops-internal-e2e/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`, and
`quickstart.md` are approved for implementation.

## Phase 1: Setup

- [x] T001 Create the Phase 22 validation ledger at `specs/040-gitops-internal-e2e/validation.md` with sanitized evidence columns and pending live-run rows.
- [x] T002 [P] Add the runner/operator contract references to `docs/deployment/gitops-roadmap-status.md` without changing public exposure or Payment status.

## Phase 2: Foundational

- [x] T003 Add the opt-in Inventory fixture configuration properties in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/configuration/InventoryFixtureProperties.java` with UUID, SKU, positive quantity, reason, and enabled validation.
- [x] T004 Add the driving CLI adapter in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/adapter/in/fixture/InventoryFixtureCommandLineRunner.java` to delegate only to `InitializeInventoryUseCase`, exit after one command, and never use JDBC/JPA or external clients.
- [x] T005 [P] Add `InventoryFixtureCommandLineRunnerTest` in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/fixture/InventoryFixtureCommandLineRunnerTest.java` for property validation, successful delegation, duplicate/failure exit, and disabled-normal-runtime behavior.
- [x] T006 [P] Update `services/inventory-service/src/main/resources/application.yml` with disabled-by-default fixture properties and non-web Job-safe defaults; do not alter normal Deployment behavior.
- [x] T007 Run `./mvnw.cmd -pl services/inventory-service -am verify` and `git diff --check`; record the result in `validation.md`.

## Phase 3: User Story 1 — Safe cloud preflight (P1)

**Goal**: Verify intended cluster, Argo ownership, Gateway reachability, and disabled Payment before
any business fixture is created.

**Independent Test**: Validation mode exits zero on the healthy EKS context and performs no business
or Kubernetes resource mutation.

- [x] T008 [P] [US1] Add bounded native-command, HTTP, JSON-envelope, and sanitized-error helpers in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T009 [US1] Implement context/Argo/Kustomize/Deployment/Payment-flag preflight in `infra/scripts/gitops/phase22-internal-e2e.ps1`, reusing no direct database or broker commands.
- [x] T010 [US1] Implement loopback-only Gateway port-forward lifecycle and anonymous admin `401/403` probe in `infra/scripts/gitops/phase22-internal-e2e.ps1` with `try/finally` cleanup.
- [x] T011 [P] [US1] Add PowerShell AST/static safety checks for `infra/scripts/gitops/phase22-internal-e2e.ps1` under `infra/scripts/gitops/tests/phase22-internal-e2e.tests.ps1`.

## Phase 4: User Story 2 — Catalog, Inventory, and Campaign fixture (P1)

**Goal**: Use the supported admin HTTP contracts and the Inventory-owned Job to prepare one active
Campaign without cross-service database access.

**Independent Test**: A run with an existing admin credential creates a unique Product/Variant,
initializes its Inventory item through the Job, and schedules/activates a Campaign.

- [x] T012 [US2] Implement secure admin login and authority checks in `infra/scripts/gitops/phase22-internal-e2e.ps1` using `Read-Host -AsSecureString`; never accept or print a raw password.
- [x] T013 [US2] Implement unique shopper registration/login and in-memory token handling in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T014 [US2] Implement Product draft, server-owned Variant composition/read-back, ETag/version extraction, and publication calls in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T015 [US2] Implement temporary Inventory fixture Job manifest rendering, apply, bounded wait, sanitized diagnostics, and default deletion in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T016 [US2] Implement Campaign create/item/schedule/activate calls with required If-Match, idempotency, and trace headers in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T017 [P] [US2] Add contract/static tests for fixture Job activation and no-secret output in `infra/scripts/gitops/tests/phase22-internal-e2e.tests.ps1`.

## Phase 5: User Story 3 — Reservation replay and Order convergence (P1)

**Goal**: Prove one authenticated reservation, same-key identity replay, owner visibility, and the
matching Order created by the `PurchaseAcceptedV1` consumer.

**Independent Test**: The script fails if the reservation is not 202, replay identities differ,
owner query mismatches, or no matching Order appears before the bounded deadline.

- [x] T018 [US3] Implement reservation submission with trace/idempotency headers and strict 202/identity extraction in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T019 [US3] Implement same-key replay comparison and owner reservation polling in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T020 [US3] Implement owner Order list polling followed by detail lookup and exact purchase/reservation/campaign/variant identity matching with expected `PENDING_PAYMENT` status in `infra/scripts/gitops/phase22-internal-e2e.ps1`.
- [x] T021 [US3] Emit sanitized evidence summary (context, Argo revision, fixture IDs, statuses, trace label, elapsed time) and fail non-zero for any missing convergence in `infra/scripts/gitops/phase22-internal-e2e.ps1`.

## Phase 6: Polish and validation

- [x] T022 [P] Add the Phase 22 operator quickstart and troubleshooting notes to `specs/040-gitops-internal-e2e/quickstart.md` and `docs/deployment/gitops-roadmap-status.md`.
- [x] T023 Run `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud`, PowerShell parser/static checks, and full `./mvnw.cmd clean verify`; record all results in `specs/040-gitops-internal-e2e/validation.md`. The default reactor run exposed a local concurrency timeout in an existing Order integration test; the complete reactor passed with a test-only Hikari wait override, with no production configuration change.
- [ ] T024 Run the live authenticated smoke with an operator-supplied admin account, record sanitized IDs and Argo revision in `specs/040-gitops-internal-e2e/validation.md`, and verify no secrets appear in logs.
- [x] T025 Run `speckit-analyze` prerequisites and resolve all CRITICAL/HIGH consistency findings before marking Phase 22 complete. No unresolved findings remain.

## Dependencies and Execution Order

- Setup (T001–T002) precedes all other work.
- Foundational Inventory adapter work (T003–T007) blocks the live runner and its Job.
- US1 preflight (T008–T011) blocks fixture creation.
- US2 (T012–T017) blocks reservation/order assertions.
- US3 (T018–T021) blocks final evidence.
- Polish/validation (T022–T025) is last; live smoke requires merged Inventory image promotion and Argo reconciliation.

## Parallel Opportunities

- T005, T006, and T008 can proceed in parallel after the spec/plan are approved.
- T011 and T017 are static-test work and can proceed after the runner skeleton exists.
- Documentation T002/T022 can proceed in parallel with Inventory unit tests.

## Implementation Strategy

1. Complete the Inventory adapter and unit tests first; verify the module.
2. Implement and validate read-only preflight mode.
3. Add the admin/fixture stages and verify the temporary Job separately.
4. Add reservation replay and Order convergence; run the full smoke only after all prior stages pass.
5. Record evidence, run full reactor/Kustomize gates, then update the roadmap status.
