# 02 - HTTP Response Body and Headers

**Document status**: Approved living design for Feature 013  
**Canonical contract**: [Active Gateway Catalog Rate Limit](../../specs/013-gateway-redis-rate-limiter/contracts/gateway-rate-limit-http.md)

## 1. Gateway-Owned Quota Rejection

The Gateway returns this response only when the Feature 013 limiter successfully evaluates the
`product-catalog` `GET` bucket and proves that one request cost is unavailable.

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: <positive-integer-delay-seconds>
Cache-Control: no-store
```

```json
{
  "success": false,
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests",
  "errors": null,
  "timestamp": "2026-08-01T00:00:00Z"
}
```

The body must not expose status, path, policy, balance, caller identity, HMAC digest, Redis
key/detail, downstream destination, exception data, or `traceId`. Correlation is carried in the
`X-Trace-Id` response header.

## 2. Header Rules

| Header | Feature 013 rule |
|--------|------------------|
| `Content-Type` | Exact media type `application/json`. |
| `Retry-After` | Decimal delay seconds: `max(1, ceil(retryAfterMs / 1000))`. |
| `Cache-Control` | Exact directive `no-store`. |
| `RateLimit`, `RateLimit-Policy`, `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset` | Do not emit. |
| `X-RateLimit-*` | Do not emit. |
| `X-Trace-Id` response header | Echoed/generated correlation value; JSON remains header-only. |

`Retry-After` is the earliest coordinator delay for the same one-unit request. It is not the time
until the bucket is full and is not an HTTP date. Before returning a rejection, Lua must prove the
finite Redis TTL covers the exact retry horizon; otherwise the state is invalid and the request
follows fail-open without `Retry-After`.

## 3. Non-Rejection Outcomes

| Outcome | Downstream call | Gateway quota/retry headers |
|---------|-----------------|-----------------------------|
| Allowed decision | Exactly once | None |
| Missing or unusable direct IP | Exactly once | None |
| Typed Redis timeout/connection/state/result/script/serialization failure | Exactly once | None |
| Limiter disabled | Existing routing behavior; catalog correlation still propagates downstream `X-Trace-Id` | None |
| Route or method unmatched | Existing behavior | None |
| Successful quota rejection | Zero | `Retry-After` and `Cache-Control: no-store` only |

Fail-open is not represented by a special body or a client-visible warning header.

## 4. Downstream Ownership

If the Gateway receives a normal response from downstream, that response remains downstream-owned.
This includes downstream HTTP 429. The Gateway must not wrap it, translate it, add quota headers, or
replace its cache headers merely because the status is 429.

## 5. Writer Boundary

`GatewayHttpErrorWriter` remains the single renderer for Gateway-owned error bodies. Its typed
rate-limit operation applies quota headers only for a successfully rendered
`RATE_LIMIT_EXCEEDED`. If serialization falls back to the safe Gateway 500 envelope, quota headers
must be removed and central error observation must run exactly once.

Expected Gateway-owned 429 is a normal high-volume limiter outcome, so bounded limiter metrics own
it; the generic per-error WARN path should not log every successful 429.

## 6. Standards

- [RFC 6585 section 4 - 429 Too Many Requests](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)
- [RFC 9110 section 10.2.3 - Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3)
