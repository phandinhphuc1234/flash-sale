# Feature Specification: Authentication Session MVP

**Feature Branch**: `015-authentication-session-mvp`
**Created**: 2026-07-25
**Status**: Approved
**Owner**: Flash Sale platform owner
**Reviewers**: platform/security reviewer
**Input**: User description: "Use authentication-service-flash-sale-final.md to create or improve the Authentication Service MVP plan and check missing HTTP status codes."

> This security-sensitive feature uses the Flash Sale risk profile. It extends the verified JWT/JWKS
> trust foundation; it does not replace that contract.

> **Architecture amendment (2026-07-26):** The implementation keeps the approved behavior and
> contracts but is organized package-by-feature. `account` and `session` are the first-level business
> capabilities; Clean/Hex boundaries remain inside them. Technical capabilities (`security`,
> `throttle`, `cleanup`, `websupport`, and `observability`) remain separate only when they are shared
> or operationally distinct. This amendment changes package paths only, not observable behavior.

> **Refactor verification (2026-07-26):** The old layer-first Java locations and scaffold markers
> are removed. The move updates package declarations/imports and test paths only. No domain rule,
> HTTP/JWT contract, persistence schema, migration, dependency, or business outcome is changed.
> Conditional bean guards only prevent infrastructure-dependent adapters from loading in a
> dependency-free test/bootstrap context.

## Problem and Scope

**Business problem**: The repository can publish and validate a trusted JWT public key, but users
cannot yet create an account, authenticate, maintain a bounded login session, renew access, or end a
session. Consequently, real clients cannot obtain tokens issued by this system.

**Goal**: Provide a small first-party authentication capability that safely supports browser account
registration, password login, short-lived access tokens, rotating refresh credentials, session
revocation, and abuse controls without turning the service into a general OAuth2/OIDC provider.

**In scope**:

- Public account registration that can create only a normal user account.
- Login by normalized email or an optional normalized username with a password.
- Fifteen-minute RS256 access-token issuance under Feature 014's issuer, audience, `kid`, claim, and
  authority contract.
- Browser refresh credentials held in an HttpOnly cookie, stored only as a one-way hash on the
  server, and rotated after each successful refresh.
- Refresh reuse detection, concurrent-refresh correctness, and compromise of the affected session.
- Logout of the current session and logout of every session owned by the authenticated account.
- Temporary login abuse controls using the existing Gateway rate limiter plus an account-identifier
  throttle owned by authentication-service.
- Service-owned account/session/refresh-credential persistence and cleanup retention.
- Gateway routes, authorization rules, trace propagation, and pass-through behavior required for
  the new authentication endpoints.
- Explicit success and failure HTTP outcomes for every endpoint in this scope.

**Out of scope**:

- OAuth2 Authorization Server or OpenID Connect provider behavior, social login, consent, token
  introspection, or third-party client registration.
- Email verification, password reset/recovery, password change, OTP, MFA, and access-token blacklist.
- User profile, avatar, phone, addresses, cart, order, payment, or other business data.
- Device/session listing UI, security-event store, Kafka events/outbox, automatic signing-key
  rotation, or security dashboards.
- Public administrator creation or role assignment. Public registration always creates a normal
  user; administrator provisioning requires a separate approved mechanism.
- Native-mobile refresh-token storage and transport; Phase 1 defines the browser cookie contract.

## Clarifications

### Session 2026-07-25

- Q: What exact registration credential policy applies? → A: Email required, username optional,
  password 12–128 Unicode code points, and password input is never trimmed.
- Q: What exact abuse-control bundle applies? → A: Gateway login limit 10/min/IP, refresh limit
  30/min/IP, account-identifier limit 5 failed attempts/15 minutes followed by a 15-minute cooldown;
  Gateway IP limits fail open with a metric when Redis is unavailable, while the Auth account
  throttle fails closed with HTTP 503.
- Q: How are cookie-authenticated refresh/logout requests protected from CSRF? → A: SameSite=Lax
  plus a configured trusted-Origin/Referer check; all refresh cookies remain HttpOnly and are Secure
  outside the local profile.

## Baseline References and Requirement Delta

| Reference | Governing content | Confirmed delta |
|-----------|-------------------|-----------------|
| [Feature 014 spec](../014-authentication-jwt-trust-foundation/spec.md) | Verified RS256 issuer, audience, JWKS, `kid`, claims, `CATALOG_ADMIN`, and 401/403 trust semantics | Add private-key signing and token issuance; do not change trust values |
| [Feature 014 trust contract](../014-authentication-jwt-trust-foundation/contracts/jwt-jwks-trust.md) | JWT uses `authorities`; default issuer is `http://authentication-service:8080`; audience is `flash-sale-api` | Account roles are mapped into the existing authority contract instead of introducing an incompatible `roles`-only token |
| [Gateway error contract](../011-gateway-error-handling/contracts/gateway-error-http.md) | Gateway-owned failures use `{code,message,traceId}` and downstream responses pass through | Auth-service owns auth business errors; gateway continues to own edge failures and must not rewrite an auth-service response |
| User-provided Authentication Service design | Accounts, device sessions, opaque refresh credentials, rotation/reuse detection, TTLs, and Redis abuse controls | Correct rotation ordering, complete HTTP outcomes, repository-compatible trace terminology, and explicit risk decisions are added |

## User Scenarios & Testing

### User Story 1 - Register a normal account (Priority: P1)

A shopper creates an account with an email, optional username, and password so the account can later
authenticate. A public caller cannot choose an administrator role.

**Why this priority**: A first-party account is the identity root for every later login session.

**Independent Test**: Register a new account, verify the returned public account data, and prove that
case-insensitive duplicates are rejected without storing a raw password.

**Use-case references**: `UC-AUTH-001`

**Acceptance Scenarios**:

1. **Given** unused normalized identifiers and a valid password, **When** a shopper registers, **Then**
   one active normal account is created and HTTP 201 returns only public account data.
2. **Given** an email or supplied username that differs from an existing value only by casing or
   surrounding identifier whitespace, **When** registration is attempted, **Then** no second account
   is created and HTTP 409 returns `AUTH_ACCOUNT_ALREADY_EXISTS`.
3. **Given** any caller-supplied role or authority field, **When** registration is attempted, **Then**
   the request is rejected with HTTP 400 and cannot create elevated access.

---

### User Story 2 - Log in without account enumeration (Priority: P1)

A registered shopper logs in using email or username and receives a short-lived access token plus a
browser refresh cookie. Invalid credentials, a disabled account, and a durably locked account do not
reveal which condition occurred. The separate Redis cooldown remains the approved public 429
abuse-control outcome.

**Why this priority**: Login is the only Phase 1 path that establishes a new authenticated session.

**Independent Test**: Authenticate a valid account and then compare the public status, code, and
message for unknown account, wrong password, disabled account, and durable account-lock cases;
verify the separate Redis cooldown returns the approved 429 response.

**Use-case references**: `UC-AUTH-002`

**Acceptance Scenarios**:

1. **Given** an active account and correct password, **When** login succeeds, **Then** exactly one
   active session and one current refresh credential are created atomically, HTTP 200 returns a
   15-minute bearer access token, and the raw refresh value is sent only in the approved cookie.
2. **Given** an unknown identifier, wrong password, disabled account, or durably locked account, **When**
   login is attempted, **Then** no session is created and the same HTTP 401
   `AUTH_INVALID_CREDENTIALS` response is returned.
3. **Given** an identifier whose failed-login budget is in its approved cooldown, **When** login is
   attempted, **Then** no password-dependent outcome or session is produced and HTTP 429
   `AUTH_TOO_MANY_ATTEMPTS` includes `Retry-After`.
4. **Given** a public normal account, **When** an access token is issued, **Then** it cannot contain
   `CATALOG_ADMIN`; an existing administrator account maps to the verified Feature 014 authority
   contract without allowing the client to influence that mapping.

---

### User Story 3 - Rotate a refresh credential safely (Priority: P1)

An authenticated browser renews an expired or soon-to-expire access token without resending the
password. Each valid refresh value can succeed once only.

**Why this priority**: Short access-token lifetime is usable only when renewal is safe and bounded.

**Independent Test**: Refresh once, retry the old value, and race two requests using the same value;
verify at most one rotation commits and detected reuse compromises the affected session.

**Use-case references**: `UC-AUTH-003`

**Acceptance Scenarios**:

1. **Given** the current unexpired refresh credential of an active, unexpired session, **When** it is
   refreshed, **Then** HTTP 200 returns a new access token, replaces the cookie, retires the old
   credential, and creates exactly one new current credential in one transaction.
2. **Given** two concurrent refresh requests with the same current value, **When** they execute,
   **Then** at most one succeeds; subsequent observation of the already-used value marks that session
   compromised, revokes its refresh credentials, and returns HTTP 401
   `AUTH_REFRESH_REUSE_DETECTED`.
3. **Given** a missing, unknown, expired, or revoked refresh value, or an inactive/expired session,
   **When** refresh is attempted, **Then** HTTP 401 `AUTH_REFRESH_TOKEN_INVALID` is returned and no
   access token is issued.
4. **Given** a session less than 30 days old, **When** a credential rotates, **Then** its inactivity
   expiry is no later than seven days from rotation and never later than the session's absolute
   expiry.

---

### User Story 4 - End one or every login session (Priority: P1)

A user can end the current browser session using its refresh cookie or end every session using a
valid access token.

**Why this priority**: Users must be able to invalidate long-lived refresh access after device loss or
when leaving a shared device.

**Independent Test**: Create two sessions, log out one and prove only that session can no longer
refresh; then log out all and prove neither session can refresh.

**Use-case references**: `UC-AUTH-004`, `UC-AUTH-005`

**Acceptance Scenarios**:

1. **Given** a current refresh cookie, **When** logout is called, **Then** its session and refresh
   credentials are revoked, the browser cookie is cleared, and HTTP 204 has no response body.
2. **Given** the same logout is repeated or no usable current cookie is present, **When** logout is
   called again, **Then** it remains idempotent, clears the cookie, and returns HTTP 204 without
   revealing session existence.
3. **Given** a valid authenticated account with multiple sessions, **When** logout-all is called,
   **Then** every session for that account is revoked atomically, the current cookie is cleared, and
   HTTP 204 has no response body.
4. **Given** durable revocation cannot be completed, **When** logout or logout-all is attempted,
   **Then** the cookie is still cleared but the API returns HTTP 503 rather than claiming successful
   server-side revocation.

### Edge Cases and Failure Outcomes

- Email and username lookup is case-insensitive after canonical normalization; passwords are never
  trimmed, normalized, logged, or returned.
- A uniqueness race at registration produces the same 409 contract as a pre-query duplicate.
- Refresh rotation must retire the old current credential before or atomically with inserting the new
  current credential; the supplied pseudo-SQL order cannot violate the one-current-token invariant.
- A refresh value is never stored raw; access and refresh values, cookies, Authorization headers,
  password material, and signing private keys never appear in logs.
- PostgreSQL is the durable source of truth. Redis loss must never manufacture a valid identity,
  session, or token.
- Authentication-service errors are returned by authentication-service and passed through unchanged;
  failures generated before proxying remain gateway-owned errors.
- Every non-empty response and relevant operator record carries the gateway-propagated `traceId`;
  `requestId` is not introduced as a second correlation term.

## Requirements

### Functional Requirements

- **FR-001**: The service MUST support `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
  `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, and
  `POST /api/v1/auth/logout-all` through api-gateway.
- **FR-002**: Register, login, refresh, and current-session logout MUST be public routes in the sense
  that they do not require an access token; logout-all MUST require a Feature 014-valid access token.
- **FR-003**: Public registration MUST create only an active normal-user account and MUST reject
  caller-controlled role/authority fields with HTTP 400 so privilege elevation is impossible.
- **FR-004**: Registration MUST require an email and a password of 12–128 Unicode code points;
  username is optional. Password input MUST be counted and encoded exactly as supplied without
  trimming or Unicode normalization.
- **FR-005**: Email MUST be trimmed and case-normalized for lookup/uniqueness while preserving the
  display form; an optional username MUST use one documented normalization rule; password input MUST
  not be trimmed or normalized.
- **FR-006**: Passwords MUST be encoded with Argon2id and only the encoded hash may be persisted.
  Concrete cost parameters belong in the approved plan and must be configurable/tested.
- **FR-007**: Login MUST accept one identifier that can resolve either normalized email or normalized
  username and MUST return the same 401 code/message for unknown account, wrong password, disabled
  account, and durable account lock. The approved Redis cooldown is a separate 429 abuse-control
  outcome applied equally to existing and non-existing identifiers.
- **FR-008**: A successful login MUST atomically update the account's last-login time and create one
  active login session with exactly one current refresh credential.
- **FR-009**: Access tokens MUST have a 15-minute lifetime and conform to Feature 014. Persisted
  `ROLE_USER` maps to `authorities=["ROLE_USER"]`; persisted `ROLE_ADMIN` maps to
  `authorities=["ROLE_ADMIN","CATALOG_ADMIN"]`. The client cannot influence this mapping.
- **FR-010**: Refresh credentials MUST be opaque cryptographically random values; only a SHA-256 hash
  of the raw value may be persisted. Raw values may exist only long enough to set the client cookie.
- **FR-011**: Refresh inactivity lifetime MUST be seven days, login-session absolute lifetime MUST be
  30 days, and a rotated credential MUST expire at the earlier boundary.
- **FR-012**: Refresh rotation MUST be serialized per credential/session so one current value can
  commit at most one successful rotation. Reuse of a retired credential MUST compromise and revoke
  only its owning session.
- **FR-013**: Current-session logout MUST be idempotent and logout-all MUST revoke every session owned
  by the verified JWT subject. Neither endpoint may revoke another account's session.
- **FR-014**: Gateway MUST limit login to 10 requests/minute/IP and refresh to 30
  requests/minute/IP. Authentication-service MUST allow at most five failed login attempts per
  normalized-identifier hash in a rolling 15-minute window; the fifth failure starts a 15-minute
  cooldown. A request during cooldown returns HTTP 429 `AUTH_TOO_MANY_ATTEMPTS` with `Retry-After`.
  Gateway IP policies fail open and record a metric when Redis is unavailable; the Auth
  account-identifier throttle fails closed with HTTP 503.
- **FR-015**: Account identifiers and client IP addresses used in Redis keys MUST be represented by a
  stable keyed hash or equivalent non-reversible key; raw email, username, and IP values MUST NOT be
  Redis key material.
- **FR-016**: The browser refresh cookie MUST be HttpOnly, path-scoped to `/api/v1/auth`, bounded by
  the refresh expiry, `Secure=true` outside the local profile, and absent from response bodies.
- **FR-017**: Refresh and current-session logout MUST accept the refresh cookie only when `Origin`
  matches a configured trusted origin, or when an absent `Origin` is accompanied by a same-origin
  `Referer`. A request with an untrusted origin/referrer, or with neither header, MUST return HTTP
  403 `AUTH_CROSS_SITE_REQUEST_REJECTED`. The cookie MUST use `SameSite=Lax`; the check is required
  because Gateway's general reactive CSRF support remains disabled.
- **FR-018**: Expired refresh/session records MUST remain available for 30 days to support reuse
  analysis, then become eligible for cleanup. Cleanup MUST never remove active records.
- **FR-019**: The JWKS endpoint and existing trust values MUST remain compatible with Feature 014;
  issued tokens MUST not use the attachment's conflicting example issuer or a `roles`-only claim.
- **FR-020**: Gateway MUST forward method, path, body, cookie, Authorization when applicable, and
  normalized `X-Trace-Id`; Set-Cookie and downstream status/body MUST reach the client unchanged.
- **FR-021**: Gateway-owned authentication, authorization, rate-limit, selected-route connection,
  and internal failures MUST retain the verified Gateway contracts. Authentication-service MUST own
  its validation, credential, session, and persistence failure codes. Unknown public paths retain
  Feature 011's deny-by-default Gateway 401/403 behavior. A new Gateway response-timeout taxonomy
  remains outside this feature.

### HTTP Outcome Requirements

The detailed request/response schema will be canonical in `contracts/authentication-http.md` after
the blocking decisions are resolved. The plan MUST cover at least these statuses:

| Boundary and condition | HTTP | Stable public code/body rule |
|------------------------|-----:|------------------------------|
| Register succeeds | 201 | Public account result; no credential or password field |
| Login succeeds | 200 | Access-token result plus refresh Set-Cookie |
| Refresh succeeds | 200 | New access-token result plus rotated refresh Set-Cookie |
| Logout/logout-all succeeds or idempotently repeats | 204 | Empty body; cookie cleared |
| Malformed JSON, invalid field, or unsupported caller role field | 400 | `AUTH_VALIDATION_FAILED` |
| Login fails for any public credential/account-state reason | 401 | `AUTH_INVALID_CREDENTIALS` |
| Refresh value/session is missing, unknown, expired, or revoked | 401 | `AUTH_REFRESH_TOKEN_INVALID` |
| Retired refresh value is reused | 401 | `AUTH_REFRESH_REUSE_DETECTED` after session compromise commits |
| Logout-all bearer token is missing/invalid | 401 | Gateway-owned `UNAUTHENTICATED` |
| Cookie-origin/CSRF policy rejects the request | 403 | Auth-owned `AUTH_CROSS_SITE_REQUEST_REJECTED` |
| Anonymous caller requests an unknown Auth path | 401 | Gateway-owned `UNAUTHENTICATED`; not proxied |
| Authenticated caller requests an unknown Auth path | 403 | Gateway-owned `ACCESS_DENIED`; not proxied |
| Method is unsupported for a known auth path | 405 | Stable error envelope, no framework detail leakage |
| Normalized email or supplied username already exists | 409 | `AUTH_ACCOUNT_ALREADY_EXISTS` |
| Request content type is unsupported | 415 | Stable error envelope, no framework detail leakage |
| Gateway IP policy is exhausted | 429 | Gateway-owned `RATE_LIMIT_EXCEEDED` plus `Retry-After` |
| Auth account-identifier cooldown is active | 429 | Auth-owned `AUTH_TOO_MANY_ATTEMPTS` plus `Retry-After` |
| Gateway cannot obtain an HTTP response from the selected Auth route | 503 | Gateway-owned `DOWNSTREAM_UNAVAILABLE` |
| Required auth dependency is unavailable or revocation cannot persist | 503 | `AUTHENTICATION_UNAVAILABLE`; no false success |
| Unexpected auth-service failure | 500 | `AUTH_INTERNAL_ERROR`; safe message only |
| Unexpected uncommitted Gateway failure | 500 | Gateway-owned `GATEWAY_INTERNAL_ERROR`; safe message only |

All error responses owned by this feature MUST use `{code,message,traceId}`. HTTP 204 responses have
no body. Successful non-204 response schemas and cookie attributes are finalized in the HTTP
contract; no parallel `requestId` field is introduced.

### Non-Functional Requirements

- **NFR-001**: Under concurrent use of the same refresh value, the persistence invariant permits at
  most one successful rotation and zero parallel current credentials.
- **NFR-002**: Tests and log inspection MUST find zero raw passwords, password hashes, access tokens,
  refresh values, cookie values, Authorization headers, or private-key material in application logs.
- **NFR-003**: Authentication requests MUST preserve one non-blank trace identifier from gateway to
  authentication-service response and operator-side failure evidence.
- **NFR-004**: Account, session, and refresh state MUST remain independently buildable, migratable,
  and deployable within authentication-service; no other service may query its schema.

### Business Rules and Invariants

- **INV-001**: One normalized email identifies at most one account; one non-null normalized username
  identifies at most one account.
- **INV-002**: Public registration can never create or grant administrator authority.
- **INV-003**: An active session has at most one current, unexpired, unrevoked refresh credential.
- **INV-004**: One refresh value can produce at most one successful rotation.
- **INV-005**: A compromised, revoked, or expired session cannot issue another access token.
- **INV-006**: Session ownership is derived from durable state and verified JWT subject, never from a
  caller-supplied user identifier.
- **INV-007**: Redis is never the source of truth for accounts, sessions, refresh credentials, or
  authorization.

### Key Entities and Domain Model Delta

- **Account**: Authentication-owned identity and credential record. It has a stable user identity,
  normalized login identifiers, password proof, account role, and account state.
- **Login Session**: One bounded login on a device/browser. It owns the absolute session lifetime and
  can be active, revoked, or compromised.
- **Refresh Credential**: A single-use renewal secret represented durably only by its hash. Its
  lifecycle is current, rotated, revoked, or expired; rotation links predecessor and successor.
- **Added domain terms**: `Account`, `Login Session`, `Refresh Credential`, `Rotation`, and
  `Compromised Session` are canonical. `Refresh Token` remains acceptable at the HTTP boundary, but
  it must not imply that the raw secret is stored.
- **Changed states or transitions**: successful login creates an active session/current credential;
  refresh rotates current to retired and creates a successor; reuse moves the session to compromised;
  logout moves active sessions to revoked; time makes sessions/credentials expired without requiring
  a stored EXPIRED state.

## Distributed-System Risk Decisions

| Risk area | Decision and required behavior | Requirement/scenario reference |
|-----------|--------------------------------|--------------------------------|
| Money/payment | N/A; authentication neither owns nor changes money/payment state | Scope |
| Inventory/oversell | N/A; authentication neither owns nor changes catalog/stock state | Scope |
| Concurrency | Row-level serialization/transaction semantics must guarantee one successful refresh rotation; no Redis lock | US3, FR-012, INV-003/004 |
| Idempotency/deduplication | Logout is idempotent; refresh is deliberately single-use and replay triggers compromise rather than result replay | US3/US4, FR-012/013 |
| Consistency/ordering | Account/session/refresh changes required by one operation commit atomically in authentication PostgreSQL | FR-008/012/013 |
| Retry/timeout/compensation | Clients may retry logout safely; they must not blindly retry refresh with a value whose first outcome is unknown because strict reuse detection applies | US3/US4 |
| Security/authorization | Generic login failure, no caller-selected role, local JWT verification, hashed refresh storage, secret-safe logging, approved IP/account abuse controls, and trusted-origin enforcement | FR-003/006/007/009/010/014/017 |
| TTL/quota/retention | Access 15 minutes; refresh inactivity 7 days; session absolute 30 days; expired record retention 30 days; login 10/min/IP, refresh 30/min/IP, account failure window/cooldown 15 minutes | FR-009/011/014/018 |

## Dependencies and Compatibility

- **Upstream dependencies**: browser client through api-gateway; gateway trace and rate-limit
  capabilities; deployment-supplied RSA private/public key pair.
- **Downstream consumers**: api-gateway and resource services that already validate Feature 014
  tokens; no consumer receives authentication database access.
- **Compatibility promise**: Feature 014 issuer, audience, algorithm, JWKS path, `kid`,
  `authorities`, `CATALOG_ADMIN`, and 401/403 semantics remain unchanged. New `/api/v1/auth/**`
  routes are additive. The gateway passes service responses through rather than translating them.

## Success Criteria

### Measurable Outcomes

- **SC-001**: The contract/integration suite demonstrates registration, login, refresh, current
  logout, and logout-all through gateway with 100% of the specified success/status outcomes.
- **SC-002**: A concurrency test using the same refresh value produces no more than one successful
  rotation and leaves no more than one current credential for the session.
- **SC-003**: Unknown account, wrong password, disabled account, and durably locked account are
  indistinguishable by public HTTP status, code, and message in every contract test.
- **SC-004**: Secret-leakage tests and repository scanning find zero raw credential/token/private-key
  values in durable storage, logs, tracked configuration, and JWKS responses.
- **SC-005**: A valid issued administrator token remains accepted by the existing
  `CATALOG_ADMIN` gateway/product boundary without changing Feature 014 consumers.

## Assumptions

- Phase 1 browser clients call authentication endpoints through the same public gateway origin.
- Display email casing is preserved while lookup uses the normalized value.
- Username is optional and, when supplied, participates in case-insensitive uniqueness and login.
- Administrator accounts may exist through an external/manual controlled process, but implementing
  that provisioning mechanism is not part of this feature.
- Kafka and an authentication outbox are unnecessary because Phase 1 publishes no integration event.

## Human Decisions Required

No open decisions. Q1–Q3 were approved by the platform/security owner on 2026-07-25 and are recorded
under Clarifications.

## Constitutional Constraints

- **Service ownership**: authentication-service owns account, password, session, and refresh data in
  its own PostgreSQL schema; gateway and downstream services access none of it.
- **External ingress**: public authentication calls enter through api-gateway. JWKS remains the
  narrow metadata endpoint established by Feature 014.
- **API/event contracts**: a feature-local HTTP contract is required before implementation; no Kafka
  event contract is added.
- **Durable and hot-path data**: PostgreSQL is durable truth. Redis stores only bounded abuse-control
  state and is not an identity/session store. No flash-sale Redis Lua behavior changes.
- **Messaging reliability**: N/A; no producer, consumer, or durable-followed-by-event workflow is in
  Phase 1.
- **Root infrastructure ownership**: shared PostgreSQL/Redis/Compose/Kubernetes secret wiring belongs
  under root `infra/`; service migrations and runtime configuration stay in authentication-service.
- **Observability**: existing declarative liveness, readiness, and Prometheus endpoints remain;
  important auth requests propagate trace ID and expose safe outcome metrics without credential data.
- **Verification**: domain/application unit tests, migration/persistence integration tests,
  web/gateway contract tests, refresh concurrency tests, secret-log tests, affected-module Maven
  verification, and local Compose smoke testing. Kubernetes dry-run applies only when a later
  approved feature adds Kubernetes manifests; Feature 015 adds none. A broad load test is deferred,
  but the approved rate-limit behavior requires focused burst tests.
- **Architecture decisions**: Feature 014/ADR 0005 remains authoritative for JWT trust. This feature
  requires an ADR for refresh-cookie/session/rotation ownership and security boundary before code.

## Approval and Change History

| Date | Change | Author | Approver | Status |
|------|--------|--------|----------|--------|
| 2026-07-25 | Initial risk-profile draft from the supplied Authentication Service design; added compatibility corrections and HTTP outcome audit | Codex | Platform/security owner pending | Draft |
| 2026-07-25 | Approved Q1 A, Q2 A, and Q3 A; resolved credential, abuse-control, Redis-failure, and cookie-origin behavior | Codex | Platform/security owner | Approved for Planning |
| 2026-07-25 | Approved as the implementation behavior baseline together with the Feature 015 plan and ADR 0006 | Codex | Platform/security owner | Approved |
