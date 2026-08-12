# HTTP Response Contract v1

## Success

Use `ApiResponse<T>`:

```json
{"success":true,"code":"SUCCESS","message":"...","data":{},"timestamp":"..."}
```

## Error

Use `ApiErrorResponse`:

```json
{"success":false,"errorCode":"SERVICE_ERROR","message":"Safe message","errors":null,"timestamp":"..."}
```

Validation uses `errors: [{"field":"name","message":"..."}]`.

## Status and headers

Use 200/201/202 for enveloped success, 204 with no body, 400/401/403/404/409/415/429 for documented
caller/resource failures, and 500/503 for safe server/dependency failures. `X-Trace-Id` is propagated
and echoed. 201 may include `Location`; 429 includes `Retry-After` when known; sensitive/auth errors
use `Cache-Control: no-store`.

## Response matrix

| Scenario | HTTP status | JSON body | Required headers | Redaction rule |
|---|---:|---|---|---|
| Successful read or command | 200 | `ApiResponse<T>` | `X-Trace-Id` | No secrets or persistence objects |
| Resource created | 201 | `ApiResponse<T>` | `X-Trace-Id`, optional `Location` | No credentials or tokens |
| Accepted asynchronous work | 202 | `ApiResponse<T>` | `X-Trace-Id` | No internal job details unless explicitly contracted |
| No content | 204 | No body | `X-Trace-Id` when a response is emitted | No body is serialized |
| Validation or malformed request | 400 | `ApiErrorResponse` with optional `FieldViolation` list | `X-Trace-Id` | Do not expose framework exception text or request payload |
| Authentication failure | 401 | `ApiErrorResponse` | `X-Trace-Id`, `Cache-Control: no-store` where sensitive | Do not reveal whether an account exists |
| Authorization failure | 403 | `ApiErrorResponse` | `X-Trace-Id`, `Cache-Control: no-store` where sensitive | Do not expose roles, policies, or security internals |
| Missing resource | 404 | `ApiErrorResponse` | `X-Trace-Id` | Do not expose query, schema, or storage details |
| Conflict or duplicate command | 409 | `ApiErrorResponse` | `X-Trace-Id` | Return stable service-owned code only |
| Unsupported media type | 415 | `ApiErrorResponse` | `X-Trace-Id` | Do not echo raw parser details |
| Rate limited | 429 | `ApiErrorResponse` | `X-Trace-Id`, `Retry-After` when known | Do not expose limiter keys or Redis internals |
| Dependency or infrastructure failure | 500/503 | `ApiErrorResponse` | `X-Trace-Id` | Never expose stack traces, SQL, hostnames, or secrets |

Every JSON response in the matrix MUST omit `traceId`; correlation is available through the
`X-Trace-Id` header only.

## Compatibility

Q1 A is approved. The canonical contract carries correlation only in the `X-Trace-Id` header;
success and error JSON MUST NOT emit a `traceId` field. Legacy endpoint wrappers are migrated in
their planned service task, with a coordinated backend rollout acceptable for this self-project.
Future W3C `traceparent` propagation can be introduced at the transport boundary without changing
business JSON envelopes.
