# Tasks: One-Time Authentication Admin Bootstrap

**Input**: Design documents from `/specs/041-authentication-admin-bootstrap/`

**Prerequisites**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/admin-bootstrap-operation.md`

## Phase 1: Foundational domain and application

- [x] T001 [P] Add `Account.bootstrapAdmin` factory and pure role/status tests in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/domain/Account.java` and `services/authentication-service/src/test/java/com/philia/flashsale/authentication/account/domain/AccountTests.java`.
- [x] T002 Add `AdminBootstrapAccountPort`, command, result, use-case interface, and
  `BootstrapAdminAccountService` with validation, idempotent same-admin behavior, and fail-closed
  collisions under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/application/bootstrap/`.
- [x] T003 Add application unit coverage for creation, existing-admin retry, user collision,
  mismatched identity, password policy, and password non-replacement in
  `services/authentication-service/src/test/java/com/philia/flashsale/authentication/account/application/bootstrap/BootstrapAdminAccountServiceTests.java`.

## Phase 2: Authentication adapters and configuration

- [x] T004 [P] Extend `JpaAccountPersistenceAdapter` to implement the bootstrap port without moving
  JPA types outside `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/adapter/out/persistence/`.
- [x] T005 Add `AdminBootstrapProperties`, enable it at the Authentication composition root, and add
  disabled-by-default `flashsale.authentication.admin-bootstrap` bindings in
  `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/`
  and `services/authentication-service/src/main/resources/application.yml`.
- [x] T006 Add the opt-in `ApplicationRunner` inbound adapter under
  `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/adapter/in/bootstrap/`;
  output only sanitized created/existing status and subject metadata.

## Phase 3: One-time cloud operation (P1)

- [x] T007 [US1] Implement `infra/scripts/gitops/phase22-admin-bootstrap.ps1` with validation-only
  default, SecureString password prompt, current Authentication image discovery, temporary Secret,
  generated Job client dry-run, bounded wait, sanitized diagnostics, and Secret/Job cleanup.
- [x] T008 [US1] Add script tests in `infra/scripts/gitops/tests/phase22-admin-bootstrap.tests.ps1`
  covering no-apply behavior, required flags, cleanup, and absence of raw password handling.
- [x] T009 [US1] Update `specs/041-authentication-admin-bootstrap/quickstart.md` and
  `specs/041-authentication-admin-bootstrap/contracts/admin-bootstrap-operation.md` with exact
  operator commands and cleanup expectations.

## Phase 4: Verification and evidence

- [x] T010 Run `./mvnw.cmd -pl services/authentication-service -am verify` and record the result in
  `specs/041-authentication-admin-bootstrap/validation.md`.
- [x] T011 Run `kubectl apply --dry-run=client -k infra/k8s/overlays/cloud` and the script validation
  mode; record sanitized command/output evidence in `validation.md`.
- [ ] T012 Run the one-time Job and Phase 22 authenticated smoke, record only sanitized subject,
  statuses, Argo revision, and exit codes, then mark the implementation tasks complete.

## Dependencies and Execution Order

- T001 → T002 → T003.
- T002 → T004, T005, T006.
- T004–T006 → T007.
- T007 → T008–T009.
- T001–T009 → T010–T012.

## Implementation Strategy

1. Complete domain/application and unit tests.
2. Wire the runner and persistence adapter while keeping bootstrap disabled by default.
3. Add the disposable launcher and script tests.
4. Verify the module, dry-run the cloud overlay, then run the one-time Job and Phase 22 smoke.
