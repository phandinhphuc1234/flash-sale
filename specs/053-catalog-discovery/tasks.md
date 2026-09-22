# Tasks: Catalog Discovery MVP

**Status**: Approved — implementation in progress.  
**Feature**: `053-catalog-discovery`

## Contract and test foundation

- [x] T001 Confirm the five human decisions in `spec.md` and record approval.
- [x] T002 Add/refresh the Product Service HTTP contract tests for the query
  parameters, defaults, validation errors, and backward-compatible response.
- [x] T003 Add frontend request/URL-state tests for search, category, sort, page,
  and size reset behavior.

## Product Service implementation

- [x] T004 Add the catalog discovery query model and validation in the application
  boundary; reject invalid ranges and unsupported sort values with typed errors.
- [x] T005 Extend `ProductCatalogController` and its mapper while preserving the
  existing response envelope.
- [x] T006 Implement the paged Product read port and persistence adapter query for
  published products, text search, descendant category membership, price bounds,
  and deterministic sorting.
- [x] T007 Add unit/integration coverage for empty query, search, category tree,
  each sort, price range, pagination boundaries, and inactive/missing category.

## QuickCart implementation

- [x] T008 Centralize catalog query serialization through the existing API client.
- [x] T009 Replace local category filtering with server-side query parameters.
- [x] T010 Add debounced search, sort controls, URL restoration, loading/empty/error
  states, and page reset behavior without changing checkout routes.
- [x] T011 Run frontend lint and production build; fix only feature-related issues.

## Documentation and verification

- [x] T012 Update `docs/api/endpoint-registry.md` and the frontend API guide with
  examples and error semantics.
- [x] T013 Run Product Service verification, frontend gates, and local Gateway smoke;
  record evidence in `specs/053-catalog-discovery/validation.md`.
- [ ] T014 Run `speckit-analyze` and `speckit-converge`; resolve any artifact drift.
- [ ] T015 Commit the coherent feature change and open a review PR after all gates
  pass.
