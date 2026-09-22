# Implementation Plan: Account Profile and Session Menu

**Branch**: `codex/account-menu-profile` | **Date**: 2026-09-18 | **Spec**: [spec.md](spec.md)

## Summary

Add a small Authentication-owned account query and profile update backed by the `users` table, then
consume it from QuickCart's account menu and profile page. Keep session revocation in the existing
Authentication use cases and make all-session logout a confirmed security action. Add one additive
Liquibase migration for nullable contact fields; no User Service, dependency, event, or infrastructure
change is required. Email changes remain deferred until a verified security flow is approved.

## Technical Context

**Language/Version**: Java 21 / Spring Boot 3.x; Next.js 15 / React 19
**Primary Dependencies**: Existing Spring Security resource server, common-web envelopes, Next.js and Tailwind; no new production dependency
**Storage**: Existing Authentication-owned PostgreSQL `users` table with nullable `full_name`, `phone`, and `address` columns
**Testing**: JUnit/MockMvc contract tests, Authentication module verification, frontend Next production build
**Target Platform**: Existing API Gateway and local/cloud frontend
**Project Type**: Multi-service backend plus nested frontend web application
**Performance Goals**: One bounded account read after login/refresh; no new fan-out
**Constraints**: `Cache-Control: no-store`, existing `X-Trace-Id`, no secret/token exposure, backend authorization remains authoritative, email is read-only, contact lengths are bounded
**Scale/Scope**: One authenticated profile per browser session; no device-management UI in this slice

## Constitution Check

- **Specification traceability**: `spec.md`, contract, plan, tasks, and quickstart are approved by the implementation request.
- **Service ownership**: Account read and session revocation remain in Authentication; JPA types stay in its persistence adapter.
- **Communication**: The frontend uses the existing Gateway route; no discovery or cross-service database access.
- **Data and messaging**: No PostgreSQL schema, Redis, Kafka, outbox, stock, or payment behavior changes.
- **Root infrastructure ownership**: No infra changes.
- **Observability**: Existing trace filter, no-store response, security handlers, and auth metrics remain in force.
- **Contracts and dependencies**: Add one documented HTTP response contract; add no dependency.
- **Validation**: Unit/web contract tests, module verify, and frontend build are required; load testing is not applicable to a profile read.

## Design

### Backend package placement

```text
services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/
├── application/profile/
│   ├── AccountProfile.java
│   ├── LoadAccountProfilePort.java
│   ├── LoadAccountProfileUseCase.java
│   ├── LoadAccountProfileService.java
│   ├── UpdateAccountProfilePort.java
│   ├── UpdateAccountProfileUseCase.java
│   └── UpdateAccountProfileService.java
├── adapter/in/web/
│   ├── AccountProfileController.java
│   ├── AccountProfileResponse.java
│   ├── UpdateAccountProfileRequest.java
│   ├── UpdateAccountDetailsRequest.java
│   └── AccountProfileWebMapper.java
└── adapter/out/persistence/
    └── JpaAccountProfileAdapter.java

services/authentication-service/src/main/resources/db/changelog/changes/
└── 004-add-account-profile-contact-fields.sql
```

The application services accept a verified subject UUID, load or update the Authentication-owned
`Account` through narrow output ports, and produce an application `AccountProfile`. The controller
owns `Jwt` extraction and HTTP mapping. The JPA adapter owns repository access and domain mapping.
Configuration wires both use cases. The legacy username-only endpoint remains compatible while the
new profile endpoint updates the bounded contact fields; email changes are outside this contract.

### Frontend design

- Add `AccountMenu` and `AccountSecurityPanel` (with an inline confirmation dialog) in the QuickCart nested repository.
- Extend `AppContext` with a safe `accountProfile` loaded after login and refresh through `/auth/me`.
- Keep a login fallback for temporary `/me` failure; never decode secrets or invent roles.
- Add `/account/profile` and `/account/security` read-only pages; current/all logout uses existing context methods.
- The profile page may submit the username update; email remains visibly read-only.
- Keep `/seller` in a separate admin shell and expose its link only for `ROLE_ADMIN`.

### Runtime flow

```text
Login/refresh -> access token -> GET /api/v1/auth/me -> AccountProfileController
 -> LoadAccountProfileUseCase -> LoadAccountProfilePort -> users table
 -> AccountSummary -> AppContext -> AccountMenu

Profile form -> PATCH /api/v1/auth/me -> UpdateAccountProfileUseCase
 -> UpdateAccountProfilePort -> users table -> updated AccountSummary -> AccountMenu

Account menu -> current logout OR confirmed all-session logout
 -> existing Authentication revocation endpoint -> clear cookie + frontend state -> /login
```

## Compatibility and Security

- Existing login and refresh response shapes remain unchanged.
- `/me` is additive and protected by the existing resource-server chain.
- Logout endpoints remain `204` with existing cookie attributes.
- Profile response is `no-store`; no credentials, secrets, or session identifiers cross the boundary.
- Username updates are normalized and uniqueness-checked; email is returned but is not writable by
  this endpoint.
- The frontend role check is presentation-only; `/seller` APIs remain backend-authorized.

## Validation Strategy

1. Test application profile mapping and username/email display-name fallback.
2. Test username/profile update success, duplicate conflict, validation, no-store, trace header, and safe fields.
3. Test `/me` success envelope, no-store, trace header, no sensitive fields, and 401 boundary.
4. Run Authentication module verification.
5. Run QuickCart production build and HTTP route smoke after restarting the local server.
6. Audit every controller route for generated OpenAPI visibility and add operation metadata where it
   is missing; run the documentation verifier.

## Complexity Tracking

No constitution violations. A separate User Service, email-change verification flow, password
change model, and avatar storage are intentionally deferred.

## Approved campaign form follow-up (2026-09-22)

Implement FR-017–FR-020 in QuickCart `app/seller/campaigns/page.jsx` with the existing admin style.
Use `lib/campaignCode.js` for a suggestion formed from a normalized name, optional local start date,
and an eight-character random suffix retained for the mounted draft. Truncate the name portion to
keep the whole code within 64 characters. Generating a suggestion does not reserve it.

Use explicit generated/custom form state. Customize starts with the displayed suggestion; switching
back discards the custom override. Disable editing during submission. Send the same four fields
(`code`, `name`, `startAt`, `endAt`) through the context Gateway client, preserving input on failure.
Map `CAMPAIGN_CODE_ALREADY_EXISTS` to an inline code error and allow manual correction; do not retry
creation automatically. Remove the unreachable post-create schedule panel from this page; navigation
and scheduling remain in the existing detail page.

Validation: focused lint, QuickCart production build with no competing dev process, route smoke,
and browser form inspection where an authenticated session is available. Exercise normalization,
length, and suffix stability via a bounded Node check. Record results in `validation.md`; no Maven,
Kubernetes, load, or backend integration rerun is needed because no service code/contract changes.

## Approved campaign detail workflow follow-up (2026-09-22)

Implement FR-021–FR-026 in QuickCart `app/seller/campaigns/[id]/page.jsx` without changing Campaign
service contracts. Load the existing campaign and the public catalog in parallel. Flatten the catalog
only for presentation, then submit the selected variant UUID through the existing campaign-item PUT.
Keep `If-Match` quoting and the existing metadata, item, schedule, activate, and outbox endpoints.

Organize the route as a sequential operator workspace: a compact progress summary, editable campaign
details, a product/variant offer form, and a sticky launch checklist. Use catalog product name/code and
variant name/SKU/base price as selection labels. Show discount feedback when the base price is known.
Disable edits once the campaign leaves DRAFT. Put exceptional manual activation and outbox replay in
a collapsed advanced section; neither is a primary draft action.

Use an explicit local datetime formatter for `datetime-local` values rather than `toISOString()`, which
would render UTC as local time. Client checks provide immediate feedback, but server error envelopes
remain authoritative. Validation is focused ESLint, QuickCart production build with the dev process
stopped, route smoke, and authenticated browser inspection when a session is available. No Maven or
Kubernetes rerun is required because the change is presentation-only.
