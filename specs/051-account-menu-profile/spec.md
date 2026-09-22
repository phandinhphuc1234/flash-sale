# Feature Specification: Account Profile and Session Menu

**Feature Branch**: `codex/account-menu-profile`
**Created**: 2026-09-18
**Status**: Approved for implementation
**Input**: User request: Replace the technical logout actions in the storefront navbar with an account menu, expose a small Authentication-owned profile summary, and provide safe current/all-session logout controls while keeping the future User Service boundary open.

## Problem and Scope

### Problem Statement

The storefront currently exposes `Logout` and `Logout all devices` as persistent navbar actions. This makes the customer-facing navigation look like an operator console and does not provide a place for basic account information. Authentication already owns the account identity and session revocation behavior, but the frontend has no stable account-summary contract for a profile menu.

### In Scope

- Add an authenticated Authentication endpoint that returns a safe account summary from the Authentication-owned account record.
- Keep the summary denormalized for this first slice: identity, username, login/email, display name, contact phone, delivery address, account status, and authorities.
- Add authenticated profile update endpoints for the display username and basic contact details. Email remains read-only until a separate verified-email change flow is approved.
- Replace persistent logout buttons with a responsive account menu and a profile/security page in the frontend.
- Keep current-session logout available and move all-session logout behind an explicit security confirmation.
- Show the Operations console entry only to an account carrying `ROLE_ADMIN`.
- Preserve the existing refresh-cookie, access-token, and session-revocation semantics.

### Out of Scope

- Creating a separate User Service.
- Changing email addresses or passwords, managing devices individually, or adding avatars backed by storage.
- Changing account roles, JWT signing, refresh-token rotation, database schema, Kafka, Redis, checkout, or payment behavior.
- Exposing refresh credentials, access tokens, password hashes, internal session IDs, or security audit details to the browser.

### Non-goals

- The profile summary is not a second source of truth. Authentication remains the owner until a future approved User Service feature changes that boundary.
- The account menu is not an authorization boundary. Backend authorization remains authoritative.

## Baseline References

- `services/authentication-service/src/main/java/com/philia/flashsale/authentication/account/domain/Account.java`
- `services/authentication-service/src/main/java/com/philia/flashsale/authentication/session/adapter/in/web/LogoutController.java`
- `services/authentication-service/src/main/java/com/philia/flashsale/authentication/session/adapter/in/web/LogoutAllController.java`
- `flash-sale frontend/QuickCart/components/Navbar.jsx`
- `flash-sale frontend/QuickCart/context/AppContext.jsx`

## Requirement Delta

### ADDED

- Authenticated `GET /api/v1/auth/me` returning the safe account summary in the existing `ApiResponse` envelope.
- Authenticated `PATCH /api/v1/auth/me` for changing the account username while preserving the email identity.
- Authenticated `PATCH /api/v1/auth/me/profile` for changing username, full name, phone, and address while preserving email and security fields.
- Storefront account menu with profile, orders, admin-console (role-gated), current logout, and security entry points.
- Explicit confirmation before all-session logout.

### MODIFIED

- **Before**: logout actions are persistent buttons in the global navbar.
- **After**: the navbar exposes one account trigger; logout actions live inside the account/security surfaces.

## User Scenarios & Testing

### User Story 1 - View account identity without an operator-looking navbar (Priority: P1)

As an authenticated shopper, I want to open one account menu and see my basic identity and normal account links, so that the storefront remains clean and understandable.

**Why this priority**: This is the visible UX problem and can be delivered independently of profile editing.
**Independent Test**: Sign in as a normal user, open the account trigger, and verify the profile summary, orders link, and current logout are present while the admin console is absent.

**Acceptance Scenarios**:

1. **Given** a valid access token, **when** the browser requests `/api/v1/auth/me`, **then** it receives only the account summary in the shared success envelope and no credential or secret fields.
2. **Given** an authenticated normal user, **when** the account trigger is opened, **then** the menu shows the display name/login, Profile, My orders, Security, and Sign out, but not Operations.
3. **Given** an unauthenticated request to `/api/v1/auth/me`, **when** it reaches Authentication, **then** the service returns the existing 401 security response.

### User Story 2 - Use safe session controls (Priority: P1)

As a shopper, I want current logout to be easy to find and all-session logout to require confirmation, so that I can leave the current device or protect the account after a suspected exposure without accidental clicks.

**Why this priority**: Session termination is a security control, not only a visual preference.
**Independent Test**: Trigger current logout and all-session logout, verify the correct Authentication endpoint is called, the refresh cookie is cleared, client state is reset, and the user returns to login.

**Acceptance Scenarios**:

1. **Given** an active session, **when** the user chooses Sign out, **then** Authentication revokes the current refresh-token chain, clears the refresh cookie, and the frontend clears its access token and cart state.
2. **Given** an active session, **when** the user chooses Sign out all devices, **then** a destructive confirmation is shown before the request is sent.
3. **Given** the user confirms all-session logout, **when** Authentication completes revocation by the verified JWT subject, **then** the refresh cookie is cleared, all owned sessions are revoked, and the frontend redirects to login.
4. **Given** the user cancels the confirmation, **when** the dialog closes, **then** no logout request is made and the current session remains usable.

### User Story 3 - Separate admin operations from customer navigation (Priority: P2)

As an administrator, I want a clear link to the Operations console without exposing it to normal shoppers, so that catalog operations remain separate from storefront browsing.

**Why this priority**: Role-aware navigation prevents confusion while preserving the existing admin workflows.
**Independent Test**: Sign in with `ROLE_ADMIN` and verify Operations is visible and `/seller` loads; sign in with `ROLE_USER` and verify Operations is absent and direct `/seller` access is redirected.

**Acceptance Scenarios**:

1. **Given** an account summary containing `ROLE_ADMIN`, **when** the account menu is opened, **then** Operations console is visible.
2. **Given** an account without `ROLE_ADMIN`, **when** the user navigates directly to `/seller`, **then** the frontend redirects to login and backend admin APIs still enforce authorization.

### User Story 4 - Update the visible account profile (Priority: P2)

As an authenticated shopper, I want to change my visible name and contact details, so that my
account profile is useful without exposing technical account metadata.

**Why this priority**: Username editing completes the basic account experience without introducing
email-change security or a separate User Service.
**Independent Test**: Send a valid profile update with the authenticated account token, then read
`/api/v1/auth/me` and verify the editable fields changed while the email and authorities did not.

**Acceptance Scenarios**:

1. **Given** a valid access token and valid profile fields, **when** the shopper patches `/api/v1/auth/me/profile`, **then** Authentication returns `200`, the updated safe profile, `Cache-Control: no-store`, and the request trace header.
2. **Given** a username already used by another account, **when** the shopper submits it, **then** Authentication returns `409 AUTH_ACCOUNT_ALREADY_EXISTS` and leaves the account unchanged.
3. **Given** a blank or overlong username, **when** the shopper submits it, **then** Authentication returns `400 AUTH_VALIDATION_FAILED` with a field violation.
4. **Given** a request containing an email field, **when** it reaches the profile update endpoint, **then** the request is rejected because email changes require a separate verified flow.
5. **Given** a request containing blank optional contact fields, **when** it reaches the profile update endpoint, **then** the stored contact value is cleared without changing immutable identity or security fields.

### Edge Cases

- The account summary request fails after login; the storefront remains signed in but shows a bounded account-summary error and does not invent a display name beyond the login fallback.
- The access token expires while the menu is open; the existing refresh flow is used once, then the menu becomes signed out if refresh fails.
- The account has no username; display name falls back to the email/login without revealing a credential.
- All-session logout is repeated or the refresh cookie is already absent; the existing idempotent logout behavior is preserved.
- A malformed or unexpected profile payload is rejected by the frontend mapper without rendering secrets or raw server errors.

## Requirements

### Functional Requirements

- **FR-001**: Authentication MUST expose `GET /api/v1/auth/me` only to authenticated callers.
- **FR-002**: The profile response MUST contain `id`, `username`, `login`, `email`, `displayName`, `fullName`, `phone`, `address`, `status`, and `authorities`, and MUST omit passwords, tokens, cookies, session IDs, and internal persistence fields.
- **FR-003**: `displayName` MUST use `fullName` when present, otherwise the account username, and otherwise the account email/login.
- **FR-004**: The profile endpoint MUST use the Authentication-owned users table through an application output port; no other service database may be queried.
- **FR-005**: The response MUST use the existing Authentication success envelope and trace-header behavior, with `Cache-Control: no-store`.
- **FR-006**: The storefront MUST render one account trigger instead of persistent Logout and Logout-all buttons.
- **FR-007**: Current logout MUST continue calling `/api/v1/auth/logout`; all-session logout MUST continue calling `/api/v1/auth/logout-all` and remain subject-owned by the verified JWT.
- **FR-008**: All-session logout MUST require an explicit confirmation action and MUST clear local authentication/cart state after a successful response.
- **FR-009**: Operations navigation MUST be visible only when the presentation state contains `ROLE_ADMIN`; backend authorization remains authoritative.
- **FR-010**: Profile and security pages MUST be read-only except for session termination in this feature.
- **FR-011**: Authentication MUST preserve `PATCH /api/v1/auth/me` as the username-only compatibility endpoint; `email` MUST remain unchanged by this endpoint.
- **FR-012**: Authentication MUST expose authenticated `PATCH /api/v1/auth/me/profile` for updating `username`, `fullName`, `phone`, and `address`; at least one editable field MUST be supplied.
- **FR-013**: A successful profile update MUST preserve the account UUID, email, role, status, and password hash, and MUST return the updated safe profile using the shared success envelope.
- **FR-014**: Username updates MUST enforce the existing normalized uniqueness rule and return `AUTH_ACCOUNT_ALREADY_EXISTS` with HTTP `409` on conflict.
- **FR-015**: `fullName` MUST be at most 150 characters, `phone` at most 32 characters, and `address` at most 500 characters; blank contact values MAY clear the stored value.
- **FR-016**: The update endpoints MUST use `Cache-Control: no-store`, echo `X-Trace-Id`, and never expose credentials or persistence internals.

### Key Entities

- **AccountSummary**: A safe, read-only presentation of Authentication-owned account identity and authorities.
- **Session termination**: The existing current-session or all-owned-session revocation operation; it does not create a new persistence model.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A signed-in shopper can find profile, orders, and current logout from one account trigger without any persistent logout buttons in the storefront navbar.
- **SC-002**: 100% of profile responses in contract tests omit access tokens, refresh credentials, passwords, and session identifiers.
- **SC-003**: All-session logout cannot be submitted without the confirmation action, and a confirmed request ends all sessions owned by the verified account subject.
- **SC-004**: Normal users never see Operations in the account menu, while administrators can reach the existing console in one interaction.

## Dependencies and Compatibility

- The API Gateway already routes `/api/v1/auth/**` to Authentication; no route change is required.
- The existing logout endpoint statuses, refresh-cookie attributes, and error envelope remain unchanged.
- The frontend will call `/api/v1/auth/me` after login and refresh; failure is non-fatal to the storefront session.

## Assumptions

- The Authentication users table contains the email, optional username, role, and status; this change adds nullable `full_name`, `phone`, and `address` columns owned by Authentication.
- A future User Service can implement the same frontend `AccountSummary` shape without changing the navbar contract.
- Email changes are intentionally deferred until a verified email-change flow with re-authentication and session policy is approved.
- The current `ROLE_ADMIN` JWT authority is sufficient for presentation gating; the backend continues to enforce all admin operations.

## Constitutional Constraints

- **Service ownership**: Authentication owns account reads and session revocation; no cross-service database access.
- **External ingress**: The new endpoint is reached through the existing API Gateway auth wildcard route.
- **API/event contracts**: Add one documented HTTP response contract; no Kafka contract changes.
- **Durable and hot-path data**: No schema, Redis, stock, or payment changes.
- **Messaging reliability**: No Kafka consumers/producers or outbox changes.
- **Root infrastructure ownership**: No infra changes; service config and frontend remain in their owners.
- **Observability**: Preserve `X-Trace-Id`, no-store behavior, and existing Authentication metrics/error handling.
- **Verification**: Authentication unit/web contract tests, module verification, and frontend production build are required; no load test is applicable.
- **Architecture decisions**: No service-boundary change or ADR required; the feature is an Authentication-owned read capability and frontend composition.

## Approval and History

- 2026-09-18 — Scope approved by project owner in implementation request.
- 2026-09-18 — Username update and Swagger coverage audit added by the implementation request; email remains read-only.
- 2026-09-22 — Basic editable profile fields (`fullName`, `phone`, `address`) and a dedicated profile update endpoint added by the implementation request; technical status/authority fields are no longer primary storefront profile content.

## Approved admin UX follow-up: Campaign draft form

**Approval**: 2026-09-22 — The owner accepted the proposed name-first form, generated campaign
code, and optional customization with "okie bạn sửa giúp mình với". This additive UI task is
tracked with the ongoing admin shell work; Campaign service behavior remains governed by its
existing contract in `docs/api/frontend-integration-guide.md`, section 7.

### User Story 5 — Create a campaign without inventing a code

As an administrator, I want a suggested campaign code and an optional edit control so that I can
focus on the campaign name and sale window.

- **FR-017**: Show the campaign name first, then an automatically suggested internal campaign code
  with a Customize action and short explanation that it is unique and fixed after creation.
- **FR-018**: Suggested codes must fit the existing required, uppercase, maximum-64-character
  contract. A short random suffix may reduce collisions; the backend remains the uniqueness authority.
- **FR-019**: Explicitly customized codes must survive subsequent name/date edits. Users can return
  to the suggestion. Errors must preserve the form and display code conflicts beside the code field.
- **FR-020**: Preserve the existing draft creation payload and navigation to campaign detail. Show
  the browser time zone and reject an end time that is not later than the start time.

**Acceptance**: Verify the name-first layout; automatic suggestions for Vietnamese, punctuation-only,
and long names; custom-code preservation/reset; conflict feedback; unchanged draft POST fields; and
mobile layout. No schema, event, scheduling, allocation, authorization, or dependency change.

## Approved admin UX follow-up: Campaign detail workflow

**Approval**: 2026-09-22 — The owner requested that the campaign detail page be redesigned for
ordinary operators after reviewing the current UUID-driven, four-card layout.

### User Story 6 — Configure and launch a campaign without technical identifiers

As an administrator, I want to choose a product and sellable variant by recognizable business
information and see a clear launch checklist so that I can prepare a flash sale without copying UUIDs
or confusing recovery actions with the normal workflow.

- **FR-021**: The campaign detail page MUST present the normal workflow in order: campaign details,
  flash-sale offer, then schedule/readiness. Status, internal code, and version remain visible as
  supporting context.
- **FR-022**: Offer configuration MUST replace the raw Variant UUID input with product and variant
  selectors populated from the existing public catalog contract. Variant labels MUST include SKU and
  base price, while the submitted campaign-item payload remains unchanged.
- **FR-023**: The page MUST validate positive campaign price and quantities, per-user limit not greater
  than requested quantity, campaign price below a known base price, and end time after start time before
  sending a request. Backend validation remains authoritative.
- **FR-024**: Campaign metadata and item editing MUST be disabled outside `DRAFT`. Scheduling MUST be
  the primary action only when the draft has an item; manual activation MUST only be offered for a
  `SCHEDULED` campaign and be clearly identified as an exceptional operation.
- **FR-025**: Outbox requeue and manual activation MUST be separated from the normal form inside a
  collapsed advanced/recovery section with explanatory copy.
- **FR-026**: Datetime-local controls MUST display local wall-clock values without applying a hidden UTC
  shift, and the page MUST show the current browser time zone.

**Acceptance**: Verify catalog loading and product/variant selection, unchanged Gateway payloads and
`If-Match` behavior, draft-only editing, launch checklist/status actions, local datetime handling,
advanced recovery placement, focused lint, production build, and responsive layout. No backend,
database, event, authorization, or Campaign API contract change.
