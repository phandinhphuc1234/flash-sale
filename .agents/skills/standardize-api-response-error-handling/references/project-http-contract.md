# Flash Sale HTTP Contract Baseline

This reference describes the current shared target, not permission to make a breaking migration.
Use approved feature artifacts for any endpoint contract change.

## Shared generic types

`libs/common-web` owns the generic HTTP models:

| Type | Current fields | Role |
|---|---|---|
| `ApiResponse<T>` | `success`, `code`, `message`, `data`, `timestamp` | Successful JSON body |
| `ApiErrorResponse` | `success`, `errorCode`, `message`, `errors`, `timestamp` | Failed JSON body |
| `FieldViolation` | `field`, `message` | One validation failure |
| `PageResponse<T>` | `data`, `page` | Paginated payload |
| `PageMeta` | `number`, `size`, `totalElements`, `totalPages`, `hasNext` | Pagination metadata |

Example success body:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Operation completed successfully",
  "data": {},
  "timestamp": "2026-08-01T00:00:00Z"
}
```

Example validation body:

```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "errors": [{"field": "quantity", "message": "must be greater than 0"}],
  "timestamp": "2026-08-01T00:00:00Z"
}
```

For a paginated endpoint, return `ApiResponse<PageResponse<ItemResponse>>`; the page is therefore
inside the top-level `data` field.

## Trace and headers

`X-Trace-Id` is the repository's current HTTP correlation header. The Gateway normalizes/generates
it, services propagate it downstream, and service-owned responses should echo it. It is a
compatibility correlation identifier, not a replacement for future W3C/OpenTelemetry trace context.

Do not add `traceId` to the shared generic envelopes without an approved, versioned contract
migration. Several legacy endpoint groups currently include it in their body, so its removal or
shape change is observable.

Use these additional headers only when the endpoint semantics require them:

| Situation | Header |
|---|---|
| New resource created | `Location` |
| Rate limited | `Retry-After` plus approved rate-limit headers |
| Auth/session or sensitive response | `Cache-Control: no-store` |
| JSON response | `Content-Type: application/json` |

## Status baseline

| Result | HTTP status |
|---|---:|
| Read or synchronous update succeeds | 200 |
| Resource created | 201 |
| Command accepted for asynchronous processing | 202 |
| Successful delete with no body | 204 |
| Invalid request, header, query, or body | 400 |
| Unauthenticated | 401 |
| Authenticated but forbidden | 403 |
| Resource absent | 404 |
| Documented state/version/idempotency conflict | 409 |
| Unsupported media type | 415 |
| Rate limited | 429 |
| Safe service/dependency unavailability | 503 |
| Unexpected server failure | 500 |

## Ownership and existing migration gaps

The envelope type is shared; error semantics are not. Keep `AuthenticationErrorCode`,
`CampaignErrorCode`, `GatewayErrorCode`, Product codes, and Inventory codes in their owning service.

Existing code is only partially standardized:

| Area | Current condition | Required migration direction |
|---|---|---|
| Inventory | Uses shared envelopes | Add safe, explicit error mapping and full trace/header handling when the contract is migrated. |
| Authentication | Uses `AuthenticationApiResponse` and `AuthenticationErrorResponse` with body `traceId` | Plan a compatibility-aware migration to `common-web`; preserve cookie and `no-store` behavior. |
| Campaign | Uses `CampaignErrorResponse` with body `traceId` and field errors | Plan a compatibility-aware migration to `common-web`; preserve its request trace header. |
| Product | Uses several feature-local error records | Migrate each public catalog/admin endpoint group together; do not merge business error codes across features. |
| API Gateway | WebFlux writer uses `GatewayErrorResponse` | Migrate Gateway-owned failures separately; never wrap or overwrite downstream bodies already being proxied. |

The migration table is planning guidance only. It does not authorize a public contract change by
itself.
