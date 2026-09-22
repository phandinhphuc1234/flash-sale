# Tasks: Inventory Admin List and Product Display Lookup

**Input**: Design documents from `/specs/052-inventory-admin-list/`

**Prerequisites**: Approved spec.md, plan.md, research.md, data-model.md, contracts/

**Status**: Approved for implementation on 2026-09-22

## Phase 1: Inventory collection query (US1)

- [x] T001 [P] [US1] Add `ListInventoryQuery`, `InventoryListItemResult`, and `InventoryPageResult` under `services/inventory-service/src/main/java/com/philia/flashsale/inventory/stock/application/` with page/size validation (FR-001–FR-004).
- [x] T002 [P] [US1] Add `ListInventoryUseCase` and `ListInventoryPort` under `services/inventory-service/src/main/java/com/philia/flashsale/inventory/stock/application/port/`.
- [x] T003 [US1] Extend `InventoryItemJpaRepository` and `InventoryItemPersistenceAdapter` to page initialized inventory rows with deterministic `updatedAt DESC, variantId ASC` ordering under `services/inventory-service/src/main/java/com/philia/flashsale/inventory/stock/adapter/out/persistence/jpa/`.
- [x] T004 [US1] Implement the Inventory list application service, web mapper, response DTO, and `GET /api/v1/admin/inventory` in `services/inventory-service/src/main/java/com/philia/flashsale/inventory/stock/` using shared `ApiResponse<PageResponse<T>>` and trace/auth conventions.
- [x] T005 [P] [US1] Add application and persistence tests for page bounds, default size, deterministic ordering, and empty pages under `services/inventory-service/src/test/java/com/philia/flashsale/inventory/stock/`.
- [x] T006 [US1] Add web contract tests for the Inventory collection response, validation errors, trace header, and admin authorization under `services/inventory-service/src/test/java/com/philia/flashsale/inventory/stock/adapter/in/web/`.

## Phase 2: Product admin batch display query (US2)

- [x] T007 [P] [US2] Add bounded batch request/result/application types under `services/product-service/src/main/java/com/philia/flashsale/product/catalogadmin/application/` and `adapter/in/web/request|response/` (FR-006–FR-007).
- [x] T008 [US2] Implement the Product-owned batch display query using existing catalogadmin persistence boundaries, returning `found`, lifecycle, SKU, name, and price fields without exposing JPA types.
- [x] T009 [US2] Add `POST /api/v1/admin/catalog/variants/display-details` to `ProductAdminController` with administrator security, input bounds, shared envelopes, and `X-Trace-Id` handling.
- [x] T010 [P] [US2] Add Product application, persistence, and web contract tests for found, archived, unknown, duplicate, invalid, and over-limit variant IDs under `services/product-service/src/test/java/com/philia/flashsale/product/catalogadmin/`.

## Phase 3: Inventory admin frontend composition (US3)

- [x] T011 [US3] Replace the UUID-only screen in `flash-sale frontend/QuickCart/app/seller/inventory/page.jsx` with a paginated inventory table that calls one Inventory page endpoint and one Product batch endpoint per page (FR-008–FR-009).
- [x] T012 [US3] Add product/variant enrichment join, partial Product lookup fallback, page controls, empty state, and stable loading/error states in `flash-sale frontend/QuickCart/app/seller/inventory/page.jsx` without changing adjustment or movement-history payloads.
- [x] T013 [US3] Add frontend route checks and focused assertions for request count, pagination, partial lookup failure, and existing adjustment/history actions in `flash-sale frontend/QuickCart/`.

## Phase 4: Contract documentation and verification

- [x] T014 [P] Update `docs/api/frontend-integration-guide.md`, `docs/api/endpoint-registry.md`, and relevant OpenAPI annotations for both new contracts.
- [x] T015 [P] Run the API documentation verifier and record contract evidence in `specs/052-inventory-admin-list/validation.md`.
- [x] T016 Run `./mvnw.cmd -pl services/inventory-service -am verify` and `./mvnw.cmd -pl services/product-service -am verify`; record results in `validation.md`.
- [x] T017 Run QuickCart lint, production build, and `/seller/inventory` route smoke; record results in `validation.md`.

## Dependencies & Execution Order

- Phase 1 (US1) and Phase 2 (US2) can proceed in parallel after the approved contracts are present.
- Phase 3 (US3) depends on the response shapes from Phases 1 and 2.
- Phase 4 depends on the implemented endpoints and frontend composition.

## MVP Scope

The MVP is Phases 1–3: an admin can browse paginated stock rows, see Product labels through one
bounded batch request, and continue adjusting stock or viewing movements.

## Verification Notes

- No schema migration is expected for the first implementation because `inventory_items` already
  contains the required quantity and timestamp fields.
- No load test is required; the page size cap and two-request-per-page invariant are the applicable
  performance safeguards for this admin feature.
