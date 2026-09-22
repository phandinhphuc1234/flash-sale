# Tasks: Account Profile and Session Menu

**Input**: Design documents from `/specs/051-account-menu-profile/`
**Prerequisites**: Approved spec, plan, research, data-model, contract, and quickstart
**Status**: Approved for implementation on 2026-09-18

## Phase 1: Backend account-summary contract

- [x] T001 [P] [US1] Add `AccountProfile` application result, `LoadAccountProfilePort`, `LoadAccountProfileUseCase`, and `LoadAccountProfileService` under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/application/profile/`; map username/email fallback and authorities without framework types.
- [x] T002 [P] [US1] Add `JpaAccountProfileAdapter` under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/adapter/out/persistence/` using the existing Authentication-owned repository and domain mapper.
- [x] T003 [US1] Wire the profile use case in `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationApplicationConfiguration.java`.
- [x] T004 [P] [US1] Add `AccountProfileResponse`, web mapper, and authenticated `GET /api/v1/auth/me` controller under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/adapter/in/web/`; preserve shared envelope, trace, and no-store behavior.
- [x] T005 [P] [US1] Add `AccountProfileServiceTests` under `services/authentication-service/src/test/java/com/philia/flashsale/authentication/account/application/profile/` for mapping, fallback display name, missing account, and authority projection.
- [x] T006 [P] [US1] Add `AccountProfileControllerContractTests` under `services/authentication-service/src/test/java/com/philia/flashsale/authentication/account/adapter/in/web/` for success body, trace header, no-store, sensitive-field absence, and subject extraction.
- [x] T007 [US1] Run the focused Authentication tests and record evidence in `specs/051-account-menu-profile/validation.md`.

## Phase 2: Frontend account and security experience

- [x] T008 [P] [US1] Add safe account-profile parsing/loading to `flash-sale frontend/QuickCart/context/AppContext.jsx` and `lib/accountProfile.js`, retaining login fallback when `/me` is temporarily unavailable.
- [x] T009 [P] [US1] Add `AccountMenu` and confirmation dialog components under `flash-sale frontend/QuickCart/components/account/`; place current logout at the menu boundary and all-session logout behind confirmation.
- [x] T010 [US1] Replace persistent logout buttons in `flash-sale frontend/QuickCart/components/Navbar.jsx` with the account trigger/menu; keep Operations visible only for `ROLE_ADMIN`.
- [x] T011 [P] [US2] Add read-only profile and security pages at `flash-sale frontend/QuickCart/app/account/profile/page.jsx` and `flash-sale frontend/QuickCart/app/account/security/page.jsx`.
- [x] T012 [US1] Preserve the existing `/seller` admin guard and update the admin shell link/copy if needed without changing admin API contracts.

## Phase 3: Verification and documentation

- [x] T013 [P] [US1] Update `specs/051-account-menu-profile/validation.md` with contract, module, frontend build, and manual role/session checks.
- [x] T014 [US1] Run `./mvnw.cmd -pl services/authentication-service -am verify` and `npm.cmd run build` in QuickCart; record exit status and warnings.
- [x] T015 [US1] Run local route smoke for `/`, `/account/profile`, `/account/security`, and `/seller`; verify no `.env` or secret file is staged.

## Dependencies and Execution Order

Phase 1 must complete before the frontend consumes `/me`. Within Phase 1, T005/T006 can be written
in parallel after the application result shape is agreed; T007 is the checkpoint. Phase 2 then
implements the menu/pages. Phase 3 is final validation and evidence.

## Phase 4: Profile username update and OpenAPI coverage

- [x] T016 [P] [US4] Add the username update command, outbound port, use-case interface, and application service under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/application/profile/`; preserve the authenticated subject, email, role, status, and password hash while enforcing normalized username uniqueness.
- [x] T017 [US4] Add the domain username mutation and persistence adapter wiring under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/domain/Account.java`, `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/adapter/out/persistence/JpaAccountProfileAdapter.java`, and `services/authentication-service/src/main/java/com/philia/flashsale/authentication/configuration/AuthenticationApplicationConfiguration.java`.
- [x] T018 [US4] Add `PATCH /api/v1/auth/me` with a validated request DTO, shared success/error envelope, `Cache-Control: no-store`, `X-Trace-Id`, and explicit Swagger `@Tag`, `@Operation`, `@ApiResponses`, and security metadata under `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/adapter/in/web/`.
- [x] T019 [P] [US4] Add application and MockMvc contract tests for username update success, email immutability, duplicate conflict, validation, trace/no-store headers, and sensitive-field absence under `services/authentication-service/src/test/java/com/philia/flashsale/authentication/account/`.
- [x] T020 [P] [US1] Audit all REST controller routes for generated OpenAPI visibility; add missing operation/tag metadata without changing route behavior, and update `docs/api/frontend-integration-guide.md`, `docs/api/endpoint-registry.md`, and `infra/scripts/docs/verify-api-documentation.ps1` for API-052.
- [x] T021 [US4] Run focused Authentication tests, module verification, frontend build/route smoke, and API documentation verification; record evidence in `specs/051-account-menu-profile/validation.md` and mark this phase complete.

## Phase 5: Editable contact profile

- [x] T022 [P] [US4] Add the additive Liquibase migration and Authentication-owned persistence/domain fields for nullable `fullName`, `phone`, and `address`, preserving legacy accounts and constructors.
- [x] T023 [US4] Add `UpdateAccountDetailsCommand`, use case/service, validated request DTO, and `PATCH /api/v1/auth/me/profile` with safe envelope, no-store, trace, uniqueness, and unknown-field rejection.
- [x] T024 [P] [US4] Extend the account profile read model and Swagger/API contract with `fullName`, `phone`, and `address`; add focused application and MockMvc tests.
- [x] T025 [US1] Replace technical profile fields on QuickCart's profile page with editable full name, phone, address, and username controls; keep email read-only and update the account menu label from the saved profile.
- [x] T026 [US4] Run Authentication verification, API documentation verification, QuickCart build, and route smoke; record evidence in `validation.md`.

## Phase 6: Approved campaign creation UX follow-up

Approved by the owner's 2026-09-22 implementation request for the proposed campaign-code UX.

- [x] T027 [US5] Add bounded name/date-based code suggestions in `flash-sale frontend/QuickCart/lib/campaignCode.js` for FR-018.
- [x] T028 [US5] Update `flash-sale frontend/QuickCart/app/seller/campaigns/page.jsx` with name-first layout, suggested/custom code, help text, inline conflict feedback, and unchanged draft creation contract (FR-017–FR-020).
- [x] T029 [US5] Verify suggestion edge cases, focused lint, production build, and local route/browser checks; record the exact scope and limitations in `specs/051-account-menu-profile/validation.md` before completing T027–T029.

## Phase 7: Approved campaign detail workflow follow-up

Approved by the owner's 2026-09-22 request after review of the current campaign detail page.

- [x] T030 [US6] Refactor `flash-sale frontend/QuickCart/app/seller/campaigns/[id]/page.jsx` into the ordered details → offer → launch workflow with a status progress summary and draft-only editing (FR-021, FR-024).
- [x] T031 [US6] Replace raw variant UUID entry with existing catalog-backed product/variant selectors, price/quantity feedback, local datetime handling, and unchanged campaign payload/ETag behavior (FR-022, FR-023, FR-026).
- [x] T032 [US6] Move manual activation and outbox replay into advanced recovery, then run focused lint, production build, route smoke, and authenticated browser inspection where available; record evidence in `validation.md` (FR-025).
