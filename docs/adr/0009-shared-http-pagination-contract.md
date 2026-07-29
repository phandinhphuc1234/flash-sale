# ADR-0009: Use the shared HTTP pagination contract in Product Service

- Status: Accepted
- Date: 2026-07-27

## Context

`product-service` had separate `PageResponse`/`CatalogPageResponse` and
`AdminPageResponse`/`AdminCatalogPageResponse` types for two HTTP entry points.
The duplicated wrappers represented the same transport concern and could drift
over time. The repository already provides generic pagination types in
`libs/common-web`.

## Decision

- `libs/common-web` owns the generic HTTP pagination types `PageMeta` and
  `PageResponse<T>`.
- Their JSON shape remains the existing Product contract:
  `{"data": [...], "page": {"number", "size", "totalElements", "totalPages", "hasNext"}}`.
- Public catalog and admin catalog controllers use the shared `PageResponse<T>`.
- Product-specific DTOs, domain pagination models, persistence models, and
  business errors remain inside `product-service`; no domain or JPA type is
  shared.
- `ApiResponse<T>` is not adopted for existing Product endpoints in this
  refactor. Introducing an envelope would change the HTTP contract and must be
  handled by a separately approved API-versioning/contract feature.

## Consequences

The Product service depends on `common-web` for a small, stable transport
contract, while business logic stays service-owned. The duplicate Product page
wrapper classes can be removed without changing clients. Future services may
reuse the pagination types when their approved HTTP contract has the same shape;
different response shapes should use service-local DTOs or a new versioned
contract.
