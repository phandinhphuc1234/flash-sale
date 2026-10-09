# Tasks: P0 Storefront Completion

**Status**: Approved — the project owner requested full P0 implementation on 2026-10-09.

## Phase 1 — Contracts and backend query boundary

- [x] T001 Document public Campaign and storefront contracts before production changes.
- [x] T002 Add Campaign public query/result/port/use-case types with lifecycle derivation tests.
- [x] T003 Add a paginated Campaign persistence adapter/query with deterministic ordering tests.
- [x] T004 Add public Campaign HTTP controller, DTO mapper, OpenAPI operations, and MVC contract tests.
- [x] T005 Permit public Campaign reads in Campaign Security and add anonymous/denial tests.

## Phase 2 — Gateway exposure

- [x] T006 Add the `/api/v1/campaigns/**` Gateway route and anonymous security rule.
- [x] T007 Add Gateway routing/security tests proving public reads pass and admin mutation remains
  protected.

## Phase 3 — Storefront Flash Sale journey

- [x] T008 Add a reusable public Campaign API client/normalizer and unit tests.
- [x] T009 Build `/flash-sale` discovery with live/upcoming/empty/error states and pagination.
- [x] T010 Build `/flash-sale/[id]` detail with lifecycle reconciliation, sign-in return, and existing
  reservation API integration.
- [x] T011 Add Flash Sale to primary navigation and remove the manual campaign-ID reservation UI
  from regular product detail.

## Phase 4 — Resilience and trust surfaces

- [x] T012 Add branded global loading, error, and not-found boundaries.
- [x] T013 Add real Help, Contact, and Privacy pages and update Footer links.
- [x] T014 Remove Newsletter, Wishlist, and address-save controls/routes from supported storefront
  behavior.

## Phase 5 — Verification and evidence

- [x] T015 Run Campaign/Gateway module verify and record exact results.
- [x] T016 Run frontend unit tests, lint, production build, and placeholder audit.
- [x] T017 Perform responsive/accessibility/manual journey review where runtime data is available;
  record environmental limitations honestly.
- [x] T018 Update API/frontend documentation and complete `validation.md` with evidence.
