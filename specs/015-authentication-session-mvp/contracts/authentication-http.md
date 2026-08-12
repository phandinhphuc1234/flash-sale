# HTTP Contract: Authentication Session MVP

**Status**: Approved with the Feature 015 plan
**Feature**: [015 Authentication Session MVP](../spec.md)
**Base path through api-gateway**: `/api/v1/auth`

This is a first-party JSON/cookie contract, not an OAuth2 Authorization Server or OpenID Connect
contract. Public clients call it through api-gateway. Feature 014 remains canonical for JWT/JWKS
verification.

## 1. Ownership and routing

Gateway route order is significant:

| Route ID | Predicate | Purpose |
|----------|-----------|---------|
| `authentication-login` | `POST /api/v1/auth/login` | Gateway IP quota 10/minute |
| `authentication-refresh` | `POST /api/v1/auth/refresh` | Gateway IP quota 30/minute |
| `authentication-api` | `/api/v1/auth/**` | Register, logout, logout-all, and method/error forwarding |

All three forward to `${AUTHENTICATION_SERVICE_URL:http://authentication-service:8080}`. Exact
routes must appear before the wildcard route.

Gateway owns failures before a downstream response exists: edge authentication/authorization,
Gateway rate limit, selected-route connection failure, and unexpected Gateway errors. Feature 015
does not introduce the response-timeout taxonomy deferred by Feature 011.
Authentication-service owns request validation, account credential/session behavior, trusted-origin
checks, account throttle, and persistence failures. Once authentication-service returns an HTTP
response, Gateway forwards status, headers, and body bytes without translating them.

## 2. Common request and response rules

### Headers

| Header | Rule |
|--------|------|
| `Content-Type` | `application/json` required for register/login; refresh/logout/logout-all have no request body |
| `X-Trace-Id` | Client value is optional at Gateway. Gateway normalizes a valid value or generates one, then forwards one non-blank value of at most 128 characters |
| `Origin` | Required and exact-match trusted for refresh/current logout unless same-origin `Referer` fallback is valid |
| `Referer` | Used only when `Origin` is absent; only its parsed origin is compared |
| `Authorization` | Required only for logout-all: `Bearer <Feature-014 JWT>` |
| `Cookie` | `refresh_token` used only by refresh and current logout |

Authentication responses include `X-Trace-Id` with the same value. Non-empty JSON responses also
carry the same flat `traceId`; no `requestId` alias exists.

Token-bearing responses use:

```http
Cache-Control: no-store
Pragma: no-cache
```

### Success envelope

Non-204 success responses use:

```json
{
  "data": {},
  "traceId": "6d0fa3a0-c4c5-4bbc-a116-3b1be6bc7bca"
}
```

### Error envelope

Authentication-service errors use:

```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "traceId": "6d0fa3a0-c4c5-4bbc-a116-3b1be6bc7bca"
}
```

Messages are fixed client-safe text. Stack traces, exception types, field values, account existence,
token/cookie values, and internal revoke reasons are never returned.

## 3. Refresh cookie

Cookie name: `refresh_token`

Login/refresh sets:

```http
Set-Cookie: refresh_token=<opaque-value>; Path=/api/v1/auth; Max-Age=<1..604800>; HttpOnly; Secure; SameSite=Lax
```

Rules:

- raw value is never present in JSON;
- `Secure` is mandatory except in the explicit local profile;
- cookie is host-only: no `Domain` attribute;
- `Max-Age` is the remaining whole-second lifetime capped by seven days and session absolute expiry;
- rotation overwrites the cookie with the successor value;
- all logout outcomes and revocation failures clear it using `Max-Age=0` and the same Path/SameSite/
  Secure attributes;
- responses containing or clearing it use `Cache-Control: no-store`.

## 4. Register

```http
POST /api/v1/auth/register
Content-Type: application/json
```

Request:

```json
{
  "email": "user@example.com",
  "username": "flashbuyer",
  "password": "correct horse battery staple"
}
```

Validation:

- `email`: required, syntactically valid, maximum 320 characters before normalization;
- `username`: optional; when present, trimmed value must be non-blank and at most 100 characters;
- `password`: required, 12–128 Unicode code points, counted exactly without trim/normalization;
- `role`, `roles`, `authority`, or `authorities`: forbidden and produce 400;
- normalized email and non-null normalized username are case-insensitively unique.

Response `201 Created`:

```json
{
  "data": {
    "userId": "4cf4ca3c-bb36-4b39-a356-4e46f48be58f",
    "email": "user@example.com",
    "username": "flashbuyer",
    "role": "ROLE_USER",
    "status": "ACTIVE"
  },
  "traceId": "trace-register-001"
}
```

No refresh cookie or access token is issued by registration.

## 5. Login

```http
POST /api/v1/auth/login
Content-Type: application/json
```

Request:

```json
{
  "login": "user@example.com",
  "password": "correct horse battery staple",
  "deviceName": "Chrome on laptop"
}
```

Validation:

- `login`: required/non-blank, at most 320 characters, normalized as email or username lookup input;
- `password`: required and 12–128 code points, never trimmed;
- `deviceName`: optional, trimmed, maximum 150 characters.

Response `200 OK` plus refresh Set-Cookie:

```json
{
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "expiresIn": 900
  },
  "traceId": "trace-login-001"
}
```

Unknown identifier, wrong password, `LOCKED`, and `DISABLED` all return exactly:

```http
HTTP/1.1 401 Unauthorized
```

```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "traceId": "trace-login-001"
}
```

The Redis identifier policy is evaluated for existing and non-existing identifiers. During cooldown,
the response is 429 rather than the durable-account 401.

## 6. Refresh

```http
POST /api/v1/auth/refresh
Cookie: refresh_token=<current-opaque-value>
Origin: <approved-origin>
```

No request body is accepted.

Response `200 OK` plus rotated refresh Set-Cookie:

```json
{
  "data": {
    "tokenType": "Bearer",
    "accessToken": "new-eyJ...",
    "expiresIn": 900
  },
  "traceId": "trace-refresh-001"
}
```

Missing, unknown, expired, or revoked values and inactive/expired sessions return 401
`AUTH_REFRESH_TOKEN_INVALID`. A known already-used/replaced value first commits compromise/revocation
for its session, then returns 401 `AUTH_REFRESH_REUSE_DETECTED` and clears the cookie.

### Unknown-outcome retry warning

Refresh is intentionally not replay-idempotent. If a client loses the first response after the
server commits rotation, blindly retrying the old cookie triggers reuse detection and compromises
that session. The client must return to login after an ambiguous refresh outcome.

## 7. Logout current session

```http
POST /api/v1/auth/logout
Cookie: refresh_token=<opaque-value>
Origin: <approved-origin>
```

No request body is accepted.

Success and idempotent repeat:

```http
HTTP/1.1 204 No Content
Set-Cookie: refresh_token=; Path=/api/v1/auth; Max-Age=0; HttpOnly; Secure; SameSite=Lax
Cache-Control: no-store
```

The response body is empty. Missing, unknown, or already-revoked cookie values do not reveal session
existence and return the same 204. A database failure returns 503 and still emits the clearing cookie;
the client must not interpret that as confirmed server-side revocation.

## 8. Logout all sessions

```http
POST /api/v1/auth/logout-all
Authorization: Bearer <access-token>
```

The target account ID is the verified JWT `sub`; no body/user ID parameter is accepted.

Success and idempotent repeat return 204 with an empty body and a clearing refresh cookie. A missing
or invalid bearer token is rejected by Gateway as `UNAUTHENTICATED`/401 before proxying. Durable
revocation failure returns auth-owned 503 and clears the cookie.

Already-issued access tokens are not revoked by logout/logout-all and may remain usable until their
15-minute expiry. This is an explicit consequence of the Phase 1 no-blacklist/no-introspection scope.

## 9. JWT issuance compatibility

JWT protected header:

```json
{
  "alg": "RS256",
  "typ": "at+jwt",
  "kid": "<configured-key-id>"
}
```

Required claims:

```json
{
  "iss": "http://authentication-service:8080",
  "sub": "4cf4ca3c-bb36-4b39-a356-4e46f48be58f",
  "aud": ["flash-sale-api"],
  "iat": 1784937600,
  "exp": 1784938500,
  "jti": "bf7ec23f-e7b5-47e7-9404-af59da44fecc",
  "authorities": ["ROLE_USER"]
}
```

Administrator accounts receive `authorities=["ROLE_ADMIN","CATALOG_ADMIN"]`. The issuer is the
Feature 014 configured issuer, not the attachment's illustrative `https://auth.flashsale.local`.
No `roles`-only alternative is emitted.

## 10. Complete HTTP status/error matrix

### Authentication-service-owned outcomes

| HTTP | Code | Endpoint/condition | Required headers/body behavior |
|-----:|------|--------------------|--------------------------------|
| 201 | success envelope | Register committed | Public account data only |
| 200 | success envelope | Login committed | Access token, refresh cookie, `no-store` |
| 200 | success envelope | Refresh rotation committed | New access token/cookie, `no-store` |
| 204 | no body | Logout/logout-all committed or idempotent repeat | Clearing cookie, `no-store` |
| 400 | `AUTH_VALIDATION_FAILED` | Malformed JSON, missing/invalid field, forbidden privilege field, or unexpected body on bodyless endpoint | Safe fixed message and trace |
| 401 | `AUTH_INVALID_CREDENTIALS` | Unknown login, wrong password, locked/disabled account | Identical public response |
| 401 | `AUTH_REFRESH_TOKEN_INVALID` | Missing/unknown/expired/revoked credential or inactive/expired session | No token; clear cookie when present |
| 401 | `AUTH_REFRESH_REUSE_DETECTED` | Used/replaced credential replay | Commit compromise first; clear cookie |
| 403 | `AUTH_CROSS_SITE_REQUEST_REJECTED` | Refresh/logout source origin cannot be trusted | No session mutation |
| 405 | `AUTH_METHOD_NOT_ALLOWED` | Known auth path with unsupported method | Stable error envelope; `Allow` header when framework supplies it |
| 409 | `AUTH_ACCOUNT_ALREADY_EXISTS` | Normalized email or supplied username duplicate, including uniqueness race | No account created |
| 415 | `AUTH_UNSUPPORTED_MEDIA_TYPE` | Register/login media type unsupported | Stable error envelope |
| 429 | `AUTH_TOO_MANY_ATTEMPTS` | Identifier cooldown active/created | `Retry-After: <positive-seconds>`, `Cache-Control: no-store` |
| 500 | `AUTH_INTERNAL_ERROR` | Unexpected unclassified auth-service failure | Safe message only |
| 503 | `AUTHENTICATION_UNAVAILABLE` | Required database/Redis/signing operation unavailable or durable revocation failed | No false success; logout paths clear cookie |

### Gateway-owned outcomes relevant to auth routes

| HTTP | Code | Condition | Ownership rule |
|-----:|------|-----------|----------------|
| 401 | `UNAUTHENTICATED` | Logout-all bearer missing/invalid, or anonymous caller requests an unknown Auth path | Gateway security response; not proxied |
| 403 | `ACCESS_DENIED` | Authenticated caller requests an unknown Auth path or is denied by another approved Gateway rule | Gateway security response; not proxied |
| 429 | `RATE_LIMIT_EXCEEDED` | Login/refresh direct-IP token bucket exhausted | Feature 013 body and `Retry-After`; not proxied |
| 503 | `DOWNSTREAM_UNAVAILABLE` | Gateway cannot connect to authentication-service | Gateway availability response |
| 500 | `GATEWAY_INTERNAL_ERROR` | Unexpected uncommitted Gateway failure | Feature 011 safe envelope |

Gateway Redis coordinator timeout/connection/typed failure for the approved IP policies is
fail-open with a metric, so it does not generate a 503. Authentication-service's identifier-throttle
Redis failure is fail-closed and does generate auth-owned 503.

## 11. Compatibility

- Additive `/api/v1/auth/**` routes; Product Catalog paths do not change.
- Feature 014 JWKS and JWT validation contract does not change.
- Feature 011/013 Gateway error and rate-limit envelopes do not change.
- Feature 011 unknown-path behavior remains deny-by-default: anonymous 401, authenticated 403; an
  unknown public Auth path is not proxied to produce an Auth-owned 404.
- No shared JPA/domain type or cross-service database access is introduced.
- Breaking field, cookie, claim, or status changes require a versioned path or explicit approved
  compatibility amendment.
