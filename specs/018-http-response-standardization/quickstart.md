# Feature 018 Quickstart

## Prerequisites

- Java 21 and Docker are available.
- The repository wrapper is used for all Maven commands.

## Verification

Run affected modules after each service migration:

```powershell
.\mvnw.cmd -pl services/authentication-service -am verify
.\mvnw.cmd -pl services/product-service -am verify
.\mvnw.cmd -pl services/campaign-service -am verify
.\mvnw.cmd -pl services/inventory-service -am verify
```

Run the cross-service gate after all four migrations:

```powershell
.\mvnw.cmd -pl services/api-gateway,services/authentication-service,services/product-service,services/campaign-service,services/inventory-service -am verify
.\mvnw.cmd clean verify
```

## Expected checks

- Success bodies match `common-web.ApiResponse`.
- Error bodies match `common-web.ApiErrorResponse`.
- Status, `X-Trace-Id`, `Location`, `Retry-After`, and `Cache-Control` are asserted where applicable.
- Gateway proxy tests prove downstream bodies are not wrapped or overwritten.

## Verification evidence

Validated sequentially on 2026-08-01 with the repository Maven wrapper; every command exited 0:

| Module | Command | Result |
|---|---|---|
| Authentication | `./mvnw -pl services/authentication-service -am verify` | 44 tests passed |
| Product | `./mvnw -pl services/product-service -am verify` | 32 tests passed |
| Campaign | `./mvnw -pl services/campaign-service -am verify` | 28 tests passed |
| Inventory | `./mvnw -pl services/inventory-service -am verify` | 19 tests passed |
| Gateway | `./mvnw -pl services/api-gateway -am test` | Full module suite passed |

The final reactor gate also passed:

```text
./mvnw clean verify
BUILD SUCCESS (06:23 min; all 12 reactor modules succeeded)
```

## Contract matrix

| Check | Expected result |
|---|---|
| 200/201/202 success | `ApiResponse<T>` with `success=true`; 201 may include `Location` |
| 204 no content | Empty response body; no envelope is serialized |
| 400 validation | `ApiErrorResponse` with safe message and optional `FieldViolation` entries |
| 401/403 security | Standard `ApiErrorResponse`; sensitive responses use `Cache-Control: no-store` |
| 404/409/415 | Stable service-owned error code and no framework/storage details |
| 429 rate limit | `ApiErrorResponse` and `Retry-After` when a retry duration is known |
| 500/503 failure | Safe generic message; no stack trace, SQL, hostname, token, or secret |
| Trace correlation | `X-Trace-Id` is echoed; JSON never contains `traceId` |
