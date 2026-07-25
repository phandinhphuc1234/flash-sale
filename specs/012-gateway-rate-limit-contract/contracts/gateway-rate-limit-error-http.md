# HTTP Contract: Gateway Rate-Limit Rejection

**Owner**: `api-gateway`
**Feature**: `012-gateway-rate-limit-contract`
**Status**: Verified
**Compatibility**: Additive extension of Feature 011's Gateway-owned error contract

## Ownership

This contract applies only when a future Gateway-owned limiter deliberately rejects a request. It
does not apply merely because a downstream service returns HTTP 429. Any downstream HTTP response
obtained normally remains downstream-owned and passes through unchanged.

## Response

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
```

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests",
  "traceId": "<non-blank correlation value>"
}
```

| Field | Required | Exact semantics |
|-------|----------|-----------------|
| `code` | Yes | `RATE_LIMIT_EXCEEDED` |
| `message` | Yes | `Too many requests` |
| `traceId` | Yes | Feature 011's normalized caller correlation value or server-generated fallback |

No quota value, client identity, rate-limit key, Redis detail, exception information, or internal
destination may appear in the body.

## Header Semantics

- `Content-Type: application/json` is required.
- This feature does not define `Retry-After`, `RateLimit`, or legacy `X-RateLimit-*` values because
  no rate/window policy has been approved.
- A later limiter feature must update this contract before emitting such headers.

### Approved Amendment: Feature 013

Feature 013 approves the first active catalog limiter contract. When the Gateway owns a successful
catalog quota rejection, it adds only `Retry-After` and `Cache-Control: no-store`; it still emits no
`RateLimit*` or `X-RateLimit-*` accounting headers and still passes downstream-owned 429 responses
through unchanged.

Canonical amendment contracts:

- [Active Gateway Catalog Rate Limit](../../013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-http.md)
- [Gateway Catalog Rate Limit Configuration](../../013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-configuration.md)

## Trace and Distributed-Tracing Boundary

- Feature 012 preserves the existing `traceId` response-field behavior; it does not install tracing.
- The approved future runtime standard is Micrometer Tracing backed by the OpenTelemetry bridge,
  W3C `traceparent`/`tracestate` propagation, and OTLP export to the OpenTelemetry Collector.
- When that runtime feature is implemented, Gateway/application code uses the Micrometer `Tracer`
  abstraction rather than direct OpenTelemetry SDK coupling.
- Custom `X-Trace-Id` correlation must not be represented as equivalent to W3C distributed tracing.

## Compatibility

- The seven Feature 011 Gateway error rows are unchanged.
- A downstream 429 status/body is not wrapped or translated.
- Existing Product Admin trace validation and 400/401/403 behavior are unchanged.
- The new code may be deployed before any runtime limiter selects it.

## Deferred Rate-Limiter Decisions

- request key and trusted proxy behavior;
- route scope and exemptions;
- quota, burst, replenishment period, and TTL;
- atomicity and Redis data model;
- fail-open/fail-closed backend behavior;
- `Retry-After` and accounting headers;
- metrics, alerts, load tests, and rollback policy.
