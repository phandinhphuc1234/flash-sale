# Tasks: Cross-Service HTTP Response Standardization

**Input**: Design documents from `specs/018-http-response-standardization/`
**Prerequisites**: Approved `spec.md`, `plan.md`, contracts, and resolved trace-body decision.

## Phase 1: Setup

- [x] T001 Record the approved trace-body compatibility decision in `specs/018-http-response-standardization/spec.md` and `contracts/http-response-v1.md`.
- [x] T002 [P] Update the Feature 018 approval/history entries and active feature pointer in `.specify/feature.json`.

## Phase 2: Foundational

- [x] T003 Add shared-envelope contract assertions for `ApiResponse`, `ApiErrorResponse`, `FieldViolation`, and pagination in `libs/common-web/src/test/java/com/philia/flashsale/common/web/`.
- [x] T004 [P] Add the response/status/header/redaction matrix to `specs/018-http-response-standardization/contracts/http-response-v1.md` and `quickstart.md`.

## Phase 3: User Story 1 — Inventory baseline (P1)

- [x] T005 [P] [US1] Add Inventory web contract tests for success, validation, not-found, conflict, malformed body, and `X-Trace-Id` in `services/inventory-service/src/test/java/com/philia/flashsale/inventory/`.
- [x] T006 [US1] Replace message-parsing and unsafe Inventory mappings with typed service-owned error-code/status mappings in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/websupport/error/`.
- [x] T007 [US1] Ensure Inventory controllers return `ResponseEntity<ApiResponse<T>>` when status or headers matter in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/*/adapter/in/web/`.

## Phase 4: User Story 2 — Authentication and Campaign (P1)

- [x] T008 [P] [US2] Add Authentication success/error/security contract tests for standard envelopes, cookies, `no-store`, 401/403/429, and `X-Trace-Id` in `services/authentication-service/src/test/java/com/philia/flashsale/authentication/`.
- [x] T009 [US2] Migrate Authentication web exception and security handlers to `common-web` while preserving token/cookie semantics in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/websupport/`.
- [x] T010 [P] [US2] Add Campaign admin/internal contract tests for standard envelopes, field violations, ETag, Location, and `X-Trace-Id` in `services/campaign-service/src/test/java/com/philia/flashsale/campaign/contract/`.
- [x] T011 [US2] Migrate Campaign error handlers and response DTOs to `common-web` without changing Campaign error-code ownership in `services/campaign-service/src/main/java/com/philia/flashsale/campaign/websupport/error/`.

## Phase 5: User Story 3 — Product and Gateway compatibility (P2)

- [x] T012 [P] [US3] Add Product catalog/admin contract tests for shared success/error envelopes, pagination, validation, conflicts, and trace headers in `services/product-service/src/test/java/com/philia/flashsale/product/`.
- [x] T013 [US3] Migrate Product catalog/admin handlers and remove duplicate error wrappers only after all references use `common-web` in `services/product-service/src/main/java/com/philia/flashsale/product/*/adapter/in/web/`.
- [x] T014 [P] [US3] Update Gateway owned-error and downstream pass-through tests for the approved v1 response contract in `services/api-gateway/src/test/java/com/philia/flashsale/gateway/`.

## Phase 6: Polish and Verification

- [x] T015 [P] Update affected OpenAPI/HTTP documentation and current-system response tables in `docs/`.
- [x] T016 Run service verification for Authentication, Product, Campaign, and Inventory using `./mvnw -pl services/<service> -am verify` and record evidence in `specs/018-http-response-standardization/quickstart.md`.
- [x] T017 Run Gateway compatibility tests and `./mvnw clean verify`; do not mark the feature complete while a required test fails.

## Dependencies and Execution Order

- T001 blocks all implementation.
- T003–T004 are foundational and precede service migration.
- US1 can complete independently before US2.
- US2 can proceed Authentication and Campaign in parallel after the shared contract is approved.
- US3 Product and Gateway tests can run in parallel after the approved compatibility decision.
- T015–T017 follow all selected service migrations.

## MVP Scope

Complete T001–T007 first: approved contract plus Inventory migration and verification. Stop for review
before migrating Authentication, Campaign, or Product.
