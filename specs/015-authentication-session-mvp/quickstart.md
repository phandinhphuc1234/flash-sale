# Quickstart: Authentication Session MVP

**Feature**: `015-authentication-session-mvp`  
**Status**: Development/VPS validation guide. The service skeleton, core MVP paths, module
verification, migration profile, Gateway verification, and full reactor checks have evidence;
the remaining security-regression and real-environment smoke gates are still tracked in
`tasks.md`.

This guide proves the implemented Authentication MVP through the public Gateway boundary. It does
not authorize public Internet exposure until the remaining hardening, real `.env`, smoke, and drift
gates in `tasks.md` are complete.

## 1. Prerequisites

- Java 21;
- Docker Engine with Docker Compose;
- OpenSSL available in PowerShell;
- repository root as the current directory;
- free local ports `8080`, `5432`, `6379`, and `29092`.

Do not commit generated keys, `infra/docker/.env`, cookie jars, access tokens, refresh tokens, or
test credentials.

## 2. Prepare Local Secrets

Create the signing key pair outside the repository. The private key uses PKCS#8 and the public key
uses X.509 SubjectPublicKeyInfo, matching the Feature 014 trust contract.

```powershell
$secretRoot = Join-Path $HOME '.flash-sale-secrets\auth'
New-Item -ItemType Directory -Force -Path $secretRoot | Out-Null

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 `
  -out (Join-Path $secretRoot 'jwt-private.pem')
openssl pkey -in (Join-Path $secretRoot 'jwt-private.pem') -pubout `
  -out (Join-Path $secretRoot 'jwt-public.pem')
```

Copy the environment template and replace every `REPLACE_WITH_...` value:

```powershell
Copy-Item infra\docker\.env.example infra\docker\.env
```

Configure these environment values in addition to the database, Redis, Gateway HMAC, issuer,
audience, and key ID values:

| Value | Requirement |
|-------|-------------|
| `AUTH_JWT_KEY_DIR` | Absolute host path to `$HOME\.flash-sale-secrets\auth`; Compose mounts it read-only |
| `AUTH_THROTTLE_HMAC_SECRET` | Base64 encoding of at least 32 random bytes; different from the Gateway HMAC secret |
| `AUTH_TRUSTED_ORIGINS` | Exact browser origins, for example `http://localhost:3000`; never `*` with credentials |
| `AUTH_COOKIE_SECURE` | `false` only for plain-HTTP local development; `true` outside local |
| `AUTH_LIQUIBASE_ENABLED` | `true` when intentionally applying the Auth migration |

Do not place private-key PEM text directly in `.env`. Compose mounts the directory at
`/run/secrets/auth-jwt` and points Auth configuration at the two mounted resources.

## 3. Build and Verify

Run the focused checks before the whole reactor:

```powershell
.\mvnw.cmd -pl services/authentication-service -am verify
.\mvnw.cmd -pl services/authentication-service -am verify -Pauth-migration-it
.\mvnw.cmd -pl services/api-gateway -am verify
.\mvnw.cmd clean verify
```

Required automated evidence includes:

- PostgreSQL migration, uniqueness, retention, and refresh-row locking;
- Argon2id encode/verify and JWT/JWKS claim compatibility;
- one-success maximum for concurrent use of the same refresh token;
- strict reuse compromise and session-chain revocation;
- account rolling-window/cooldown concurrency using real Redis;
- Gateway login/refresh burst quotas and downstream pass-through;
- no password, token, cookie, private key, or raw account identifier in responses/log captures.

## 4. Validate and Start the Local Stack

Validate interpolation before building containers:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml config
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml build authentication-service api-gateway

# Apply Auth's service-owned schema once before starting the app.
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml run --rm --no-deps `
  -e SPRING_LIQUIBASE_ENABLED=true `
  -e SPRING_MAIN_KEEP_ALIVE=false `
  authentication-service --spring.main.web-application-type=none

# Start only the Auth path and its backing services while developing Authentication.
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps up `
  postgres redis authentication-service api-gateway
```

The expected public base URL is `http://localhost:8080`. Direct service ports are for debugging and
must not become the frontend contract.

## 5. Happy-Path Smoke

Use a disposable local account. The request examples deliberately enter through the Gateway and
send the approved Origin. Store the refresh cookie only in a disposable untracked file.

```powershell
$base = 'http://localhost:8080'
$origin = 'http://localhost:3000'
$cookieJar = Join-Path $env:TEMP 'flash-sale-auth-cookies.txt'
$email = "learner-$([guid]::NewGuid().ToString('N'))@example.test"
$password = 'Local-study-password-2026!'

curl.exe -i -X POST "$base/api/v1/auth/register" `
  -H "Origin: $origin" -H 'Content-Type: application/json' `
  --data "{\"email\":\"$email\",\"password\":\"$password\"}"

curl.exe -i -c $cookieJar -X POST "$base/api/v1/auth/login" `
  -H "Origin: $origin" -H 'Content-Type: application/json' `
  --data "{\"login\":\"$email\",\"password\":\"$password\"}"

curl.exe -i -b $cookieJar -c $cookieJar -X POST "$base/api/v1/auth/refresh" `
  -H "Origin: $origin"

curl.exe -i -b $cookieJar -c $cookieJar -X POST "$base/api/v1/auth/logout" `
  -H "Origin: $origin"
```

For logout-all, copy the access token returned by login or refresh into a temporary shell variable;
never paste it into a tracked file:

```powershell
$accessToken = '<temporary-access-token>'
curl.exe -i -X POST "$base/api/v1/auth/logout-all" `
  -H "Authorization: Bearer $accessToken" -H "Origin: $origin"
```

## 6. Expected HTTP Outcomes

| Scenario | Owner | Expected result |
|----------|-------|-----------------|
| Register valid account | Auth | `201`, `{data,traceId}` |
| Register invalid/role-like input | Auth | `400 AUTH_VALIDATION_FAILED` |
| Register duplicate normalized identifier | Auth | `409 AUTH_ACCOUNT_ALREADY_EXISTS` |
| Login succeeds | Auth | `200`, access body plus HttpOnly refresh cookie |
| Wrong, unknown, locked, or disabled login | Auth | identical `401 AUTH_INVALID_CREDENTIALS` |
| Fifth failed identifier attempt in 15 minutes | Auth | `429 AUTH_TOO_MANY_ATTEMPTS` plus `Retry-After` |
| More than 10 login requests/minute/direct IP | Gateway | `429 RATE_LIMIT_EXCEEDED` plus `Retry-After` |
| More than 30 refresh requests/minute/direct IP | Gateway | `429 RATE_LIMIT_EXCEEDED` plus `Retry-After` |
| Refresh without an exact trusted Origin/Referer | Auth | `403 AUTH_CROSS_SITE_REQUEST_REJECTED` |
| Anonymous caller uses an unknown Auth path | Gateway | `401 UNAUTHENTICATED`; not proxied |
| Authenticated caller uses an unknown Auth path | Gateway | `403 ACCESS_DENIED`; not proxied |
| Valid refresh rotation | Auth | `200`, new access body and replaced refresh cookie |
| Replay retired refresh token | Auth | `401 AUTH_REFRESH_REUSE_DETECTED`; session compromised |
| Logout current/all succeeds or repeats | Auth | empty `204` plus clearing refresh cookie |
| Missing/invalid bearer on logout-all | Gateway | `401 UNAUTHENTICATED` |
| Auth unavailable after route acceptance | Gateway | `503 DOWNSTREAM_UNAVAILABLE` |

Every non-empty error body must be exactly `{code,message,traceId}`. Gateway-generated errors use
Gateway codes; downstream Auth errors pass through unchanged. Every response must carry the same
`X-Trace-Id` value as its non-empty body.

## 7. Failure-Mode Smoke

### Redis failure split

Start the stack, complete one successful login, then stop only Redis:

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml stop redis
```

Expected behavior:

- Gateway direct-IP acquisition fails open only for the typed Redis-unavailable case and increments
  its failure metric;
- Auth login fails closed with `503 AUTHENTICATION_UNAVAILABLE` before creating a new session;
- PostgreSQL remains the session/refresh source of truth;
- restarting Redis restores new-login capability without database reconciliation.

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml start redis
```

### Refresh ambiguity

The concurrent same-cookie case belongs in an automated PostgreSQL integration test, not a casual
manual race. The proof must show at most one `200`; under the approved strict rule, a later replay
returns `401 AUTH_REFRESH_REUSE_DETECTED` and compromises the session. A client that loses a refresh
response must log in again instead of retrying the retired cookie.

## 8. Operational Checks

After smoke testing, verify:

- `/.well-known/jwks.json` contains only public RSA material and the approved `kid`;
- liveness, readiness, and Prometheus endpoints remain Actuator auto-configured;
- response headers for login/refresh prevent caching;
- the refresh cookie is HttpOnly, host-only, Path `/api/v1/auth`, SameSite=Lax, and Secure outside
  local;
- metrics have bounded tags and never use email, username, IP, session ID, or token values;
- logs contain trace ID and stable outcome codes but no secret or raw account identifier.

## 9. Cleanup

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yml --profile apps down
Remove-Item -LiteralPath (Join-Path $env:TEMP 'flash-sale-auth-cookies.txt') -ErrorAction SilentlyContinue
```

Do not remove the PostgreSQL volume during ordinary rollback; Feature 015 preserves authentication
tables for forward recovery. Delete local secret files only when intentionally rotating or
decommissioning the environment.
