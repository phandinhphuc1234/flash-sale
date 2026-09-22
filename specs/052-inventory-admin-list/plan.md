# Implementation Plan: Inventory Admin List and Product Display Lookup

**Branch**: `codex/inventory-admin-list` | **Date**: 2026-09-22 | **Spec**: [spec.md](spec.md)

## Summary

Add a bounded paginated Inventory admin collection and a Product admin batch display query. The
QuickCart Inventory page will compose one stock page with one batch metadata lookup, preserving
Product/Inventory ownership and avoiding N+1 requests.

## Technical Context

**Language/Version**: Java 21, Spring Boot 3.x, JavaScript/Next.js 15

**Primary Dependencies**: Spring MVC, Spring Data JPA, Liquibase, `common-web`, Next.js App Router

**Storage**: Inventory-owned PostgreSQL tables and Product-owned PostgreSQL tables; no cross-service joins

**Testing**: JUnit/Spring web contract tests, persistence/query tests, QuickCart lint/build/route smoke

**Target Platform**: Existing API Gateway, local QuickCart, independently deployable Spring services

**Project Type**: Multi-service web application with admin frontend

**Performance Goals**: One inventory page results in at most two browser API requests; server page size is capped at 100

**Constraints**: Zero-based page/size contract, deterministic ordering, shared response/error envelopes, admin authorization, trace propagation

**Scale/Scope**: Admin pages of initialized inventory rows; not the seckill purchase hot path

## Constitution Check

- **Specification traceability**: FR-001–FR-011 map to the two backend contracts and the frontend page.
- **Service ownership**: PASS. Inventory reads only `inventory_items`; Product reads only catalog tables.
- **Communication**: PASS. Frontend uses documented Gateway HTTP contracts; no direct database access.
- **Data and messaging**: PASS. No Redis, Kafka, outbox, payment, or stock deduction changes.
- **Root infrastructure ownership**: PASS. No infra changes.
- **Observability**: PASS. Existing `X-Trace-Id` boundary is preserved; no manual Prometheus registry.
- **Contracts and dependencies**: PASS. New HTTP contracts are documented before implementation; no new dependency.
- **Validation**: PASS. Backend unit/web tests, frontend build/route smoke, and docs verification are included.

## Design Decisions

### Pagination

Use the repository's existing zero-based `page`/`size` form. The adapter converts the application
query to `PageRequest` and maps it back to `PageResponse`; Spring `Pageable` never crosses the
application boundary. Default `size=20`, maximum `size=100`, stable ordering by `updatedAt DESC,
variantId ASC`.

### Product display composition

The Inventory collection returns live quantities and variant IDs. The Product admin batch query
returns names and lifecycle/price labels. QuickCart joins them by `variantId`. This avoids an
Inventory→Product synchronous dependency and avoids one Product call per row. Missing display data
does not hide valid inventory quantities.

### Package structure

Inventory extends its existing `stock` feature:

```text
services/inventory-service/src/main/java/com/philia/flashsale/inventory/stock/
├── application/query/ListInventoryQuery.java
├── application/result/InventoryListItemResult.java
├── application/result/InventoryPageResult.java
├── application/port/in/ListInventoryUseCase.java
├── application/port/out/ListInventoryPort.java
├── application/usecase/InventoryQueryService.java
└── adapter/in/web/
    ├── InventoryAdminController.java
    ├── mapper/InventoryWebMapper.java
    └── response/InventoryListItemResponse.java
```

Product extends `catalogadmin` with an admin-only display query and batch request/response. The
existing internal Cart display endpoint remains unchanged.

QuickCart changes only `app/seller/inventory/page.jsx` and small frontend helpers if needed.

## Runtime Flow

```text
QuickCart Inventory page
  ├─ GET /api/v1/admin/inventory?page=0&size=20
  │    └─ Inventory controller → use case → repository → inventory_items
  └─ POST /api/v1/admin/catalog/variants/display-details
       └─ Product controller → query → product/variant repositories
  └─ join by variantId → render table
```

Adjustment and movement history continue using their existing per-variant contracts.

## Compatibility and Security

- Existing `GET /api/v1/admin/inventory/{variantId}`, adjustment, and movement routes remain unchanged.
- Both new endpoints are administrator-only and use `ApiResponse`, `PageResponse`, `ApiErrorResponse`, and `X-Trace-Id` conventions.
- Batch input is limited to 100 IDs; unknown IDs become safe partial results.
- No credentials, JPA entities, SQL messages, or internal service addresses cross the frontend boundary.

## Validation Strategy

1. Add application query validation and pagination mapping tests in Inventory.
2. Add Inventory web contract tests for envelope, trace header, page bounds, and authorization boundary.
3. Add Product batch query/controller tests for bounded input and partial unknown results.
4. Run affected Maven module verification for Inventory and Product.
5. Update API registry/frontend guide and run the documentation verifier.
6. Replace the UUID-only QuickCart screen with a paginated table; run lint, production build, and
   route smoke. Authenticated browser inspection is attempted when an admin session is available.

## Migration Sequence

1. Add approved contract/spec artifacts.
2. Add Inventory collection read without changing schema or existing commands.
3. Add Product admin batch display read without changing Product lifecycle behavior.
4. Update QuickCart to use one request per bounded page and retain detail/adjustment/history actions.
5. Verify backend contracts and frontend behavior, then update evidence.

## Complexity Tracking

No constitution violations. A BFF, product metadata projection, cache, and cursor pagination are
intentionally deferred until measured admin traffic justifies them.
