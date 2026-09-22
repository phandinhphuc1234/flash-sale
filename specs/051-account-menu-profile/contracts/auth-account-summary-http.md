# Authentication Account Summary HTTP Contract

## `GET /api/v1/auth/me`

### Request

- Authentication: Bearer access token required.
- Body: none.
- Required headers: `X-Trace-Id` is propagated/echoed using the existing Authentication convention.

### Success

`200 OK`, `Cache-Control: no-store`, shared Authentication success envelope:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {
    "id": "4f6c4d9e-6bd2-4a63-8f1b-2d6d1e44c58a",
    "username": "phuc",
    "login": "phuc",
    "email": "phuc@example.com",
    "displayName": "Phuc",
    "fullName": "Phuc Nguyen",
    "phone": "+84901234567",
    "address": "Thu Duc, Ho Chi Minh City",
    "status": "ACTIVE",
    "authorities": ["ROLE_USER"]
  },
  "timestamp": "2026-09-18T00:00:00Z"
}
```

The response MUST NOT contain `accessToken`, `refreshCredential`, `refresh_token`, `passwordHash`,
`sessionId`, IP addresses, or user-agent values.

`username` is nullable for legacy accounts. When it is null, `login` and `displayName` use the
account email; clients must not render the technical `id` as the account name.

`fullName`, `phone`, and `address` are nullable user-editable contact fields. They are not used as
authorization claims or identity keys.

### Failure

- Missing/invalid bearer token: existing security `401` response.
- Verified subject not found or inactive: Authentication-owned bounded error using the existing error envelope; no persistence details are exposed.

## Existing logout contracts retained

- `POST /api/v1/auth/logout` remains current-session, idempotent, `204 No Content`, and clears the refresh cookie.
- `POST /api/v1/auth/logout-all` remains verified-subject-owned, `204 No Content`, and clears the refresh cookie.
- No request body is added to either endpoint.

## `PATCH /api/v1/auth/me`

### Request

- Authentication: Bearer access token required.
- Required headers: `X-Trace-Id` is propagated/echoed using the existing Authentication convention.
- Body:

```json
{ "username": "phuc.dev" }
```

`username` is required, trimmed, and at most 100 characters. The endpoint updates only the
authenticated account's username. `email`, `role`, `status`, password, and IDs are not accepted as
editable fields; sending an unknown field such as `email` is a validation failure.

### Success

`200 OK`, `Cache-Control: no-store`, shared Authentication success envelope containing the updated
account summary from `GET /api/v1/auth/me`.

### Failure

- Missing/invalid bearer token: existing security `401` response.
- Blank or overlong username: `400 AUTH_VALIDATION_FAILED` with field violations.
- Username already used by another account: `409 AUTH_ACCOUNT_ALREADY_EXISTS`.
- Verified subject not found: existing bounded `404 AUTH_ACCOUNT_NOT_FOUND` response.

## `PATCH /api/v1/auth/me/profile`

This additive endpoint updates the visible profile fields used by the storefront. The request may
contain one or more of `username`, `fullName`, `phone`, and `address`; at least one field is required.
Email, role, status, identifiers, password, and tokens are not accepted. Blank `fullName`, `phone`,
or `address` clears that optional stored value.

```http
PATCH /api/v1/auth/me/profile
Authorization: Bearer <accessToken>
X-Trace-Id: <optional-trace-id>
Content-Type: application/json
```

Example:

```json
{
  "username": "phuc.dev",
  "fullName": "Phuc Nguyen",
  "phone": "+84901234567",
  "address": "Thu Duc, Ho Chi Minh City"
}
```

`fullName` is limited to 150 characters, `phone` to 32 characters, and `address` to 500
characters. Success is `200 OK` with the updated profile in the standard envelope, `Cache-Control:
no-store`, and the echoed `X-Trace-Id`. Username conflicts retain `409 AUTH_ACCOUNT_ALREADY_EXISTS`.
