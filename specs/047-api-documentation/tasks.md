# Tasks: Unified API Documentation

**Status**: Completed and verified (2026-08-29)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[contracts/http-inventory.md](contracts/http-inventory.md), and [quickstart.md](quickstart.md).

## Phase 1: Setup and approved artifacts

- [x] T001 Create and approve the behavior scope in `specs/047-api-documentation/spec.md`.
- [x] T002 Validate requirements in `specs/047-api-documentation/checklists/requirements.md`.
- [x] T003 Record design, dependency, security, and verification decisions in
  `specs/047-api-documentation/plan.md` and `specs/047-api-documentation/research.md`.
- [x] T004 Record all supported method/path pairs in
  `specs/047-api-documentation/contracts/http-inventory.md`.

## Phase 2: Foundational documentation runtime

- [x] T005 Add the centrally managed Springdoc version to `pom.xml` and replace Inventory's literal
  version in `services/inventory-service/pom.xml` (NFR-MAINT-001).
- [x] T006 [P] Add the WebFlux Springdoc UI dependency to `services/api-gateway/pom.xml`.
- [x] T007 [P] Add the WebMVC Springdoc UI dependency to
  `services/authentication-service/pom.xml`, `services/product-service/pom.xml`,
  `services/campaign-service/pom.xml`, `services/flashsale-service/pom.xml`,
  `services/order-service/pom.xml`, and `services/payment-service/pom.xml`.

## Phase 3: User Story 1 — Read the complete API catalog (P1)

**Goal**: A reader can identify all 40 supported endpoints and their boundaries from one document.

**Independent test**: Run the static validation and confirm 40 unique rows with totals 33/6/1 and
zero endpoints for Cart/Notification.

- [x] T008 [US1] Publish counting rules, all endpoint rows, totals, access guidance, and operational
  exclusions in `docs/api/README.md` (FR-001–FR-004, FR-010–FR-011).
- [x] T009 [US1] Add catalog uniqueness/count/owner checks in
  `infra/scripts/docs/verify-api-documentation.ps1` (FR-012).
- [x] T010 [US1] Run `infra/scripts/docs/verify-api-documentation.ps1` and record the result in
  `specs/047-api-documentation/validation.md`.

## Phase 4: User Story 2 — Explore APIs with Swagger locally (P2)

**Goal**: One local Gateway Swagger UI loads seven service-owned OpenAPI documents.

**Independent test**: Enable `API_DOCS_ENABLED`, render Compose, and verify all seven document
definitions and direct service paths are configured.

- [x] T011 [P] [US2] Add opt-in Springdoc properties to
  `services/authentication-service/src/main/resources/application.yml`,
  `services/product-service/src/main/resources/application.yml`,
  `services/campaign-service/src/main/resources/application.yml`,
  `services/flashsale-service/src/main/resources/application.yml`,
  `services/order-service/src/main/resources/application.yml`, and
  `services/payment-service/src/main/resources/application.yml` (FR-005).
- [x] T012 [P] [US2] Preserve Inventory's service-specific flag while accepting the shared opt-in in
  `services/inventory-service/src/main/resources/application.yml` (FR-005).
- [x] T013 [P] [US2] Add service title/version/security-scheme OpenAPI beans under each owning
  service's `src/main/java/**/configuration/*OpenApiConfiguration.java`, including explicit
  `POST /oauth2/token` documentation for Authentication (FR-005).
- [x] T014 [P] [US2] Permit generated documentation paths without changing business path rules in
  Authentication, Product, Campaign, Flash Sale, Order, and Payment security configuration classes
  under `services/*/src/main/java/**/security/` or `configuration/` (FR-008).
- [x] T015 [US2] Add seven exact OpenAPI proxy routes and Swagger UI definitions to
  `services/api-gateway/src/main/resources/application.yml` (FR-006, FR-009).
- [x] T016 [US2] Gate Gateway documentation paths with the opt-in flag in
  `services/api-gateway/src/main/java/com/philia/flashsale/gateway/security/GatewaySecurityConfiguration.java`
  and extend its security tests (FR-006–FR-009).
- [x] T017 [US2] Pass the shared opt-in only through local Compose in `infra/docker/compose.yml` and
  document it in `infra/docker/.env.example` (FR-006–FR-007).
- [x] T018 [US2] Extend `infra/scripts/docs/verify-api-documentation.ps1` to verify dependencies,
  service flags, seven Gateway definitions, and no Gateway `/internal/**` route (FR-005–FR-006,
  FR-009, FR-012).

## Phase 5: User Story 3 — Keep documentation off public cloud by default (P3)

**Goal**: Default and cloud configurations expose no documentation catalog.

**Independent test**: Static validation confirms all defaults are false, Gateway denies docs when
disabled, and cloud ConfigMaps do not enable the flag.

- [x] T019 [US3] Add default-disabled and opt-in Gateway security coverage in
  `services/api-gateway/src/test/java/com/philia/flashsale/gateway/security/GatewaySecurityConfigurationTests.java`
  (NFR-SEC-001).
- [x] T020 [US3] Add cloud-manifest/default checks to
  `infra/scripts/docs/verify-api-documentation.ps1` and explain the boundary in
  `docs/api/README.md` (FR-007–FR-009).

## Phase 6: Polish and verification

- [x] T021 Synchronize usage steps in `specs/047-api-documentation/quickstart.md` and Inventory's
  existing guide at `docs/inventory/README.md`.
- [x] T022 Run the static validation, affected-reactor Maven compile/Gateway verify, and Docker Compose render
  commands from `specs/047-api-documentation/plan.md`; record command/result evidence in
  `specs/047-api-documentation/validation.md`.
- [x] T023 Review `git diff --check`, confirm no business endpoint/contract changed, mark completed
  tasks, and update status/history in `specs/047-api-documentation/{spec.md,plan.md,tasks.md}`.

## Dependencies

```text
T001-T004
   -> T005-T007
      -> US1: T008 -> T009 -> T010
      -> US2: T011-T014 -> T015-T018
         -> US3: T019-T020
            -> T021-T023
```

US1 is independently useful as a complete human catalog. US2 depends on the common dependency
setup. US3 completes the security posture before final verification.

## Parallel opportunities

- T006 and T007 affect distinct POM files after T005 establishes the shared version.
- T011, T012, T013, and T014 affect separate service-owned files and may be reviewed in parallel,
  but implementation in a single worktree must avoid overlapping edits.

## Implementation strategy

1. Publish and validate the 40-endpoint catalog first.
2. Enable service-owned OpenAPI generation without changing controllers.
3. Add Gateway aggregation and safe-default enforcement.
4. Run cross-cutting verification once after the coherent feature is complete.
