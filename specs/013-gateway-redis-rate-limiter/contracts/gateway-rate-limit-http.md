# HTTP Contract: Active Gateway Catalog Rate Limit

**Owner**: `api-gateway`  
**Feature**: `013-gateway-redis-rate-limiter`  
**Status**: Approved — approved by Gateway/Platform owner (user) on 2026-07-23  
**Baseline**: Additive amendment to [Feature 012](../../012-gateway-rate-limit-contract/contracts/gateway-rate-limit-error-http.md)

## 1. Contract scope

This contract applies only when the Gateway's Feature 013 limiter successfully evaluates the
`product-catalog` `GET` bucket and determines that one request cost is unavailable.

It does not apply to:

- a downstream service returning HTTP 429;
- Redis, identity, configuration, serialization, routing, security, or downstream failures;
- any route or method outside `product-catalog` `GET`;
- the disabled limiter.

Only successful atomic quota exhaustion can select this response.

## 2. Gateway-owned rejection

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: <positive-integer-delay-seconds>
Cache-Control: no-store
```

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests",
  "traceId": "<non-blank correlation value>"
}
```

### Body

| Field | Required | Exact semantics |
|-------|----------|-----------------|
| `code` | Yes | Exact value `RATE_LIMIT_EXCEEDED` |
| `message` | Yes | Exact value `Too many requests` |
| `traceId` | Yes | Existing Feature 011/012 normalized caller correlation value or generated fallback |

There are exactly three top-level fields. The body does not expose timestamp, status, path, quota,
remaining balance, policy, caller identity, HMAC digest, Redis key/detail, exception, or destination.

This field named `traceId` is the repository's current correlation contract. Feature 013 does not
claim that a Micrometer/OpenTelemetry span is installed and does not create a new response header
contract for `X-Trace-Id`.

At the catalog correlation boundary, before optional limiting, every matched `product-catalog` `GET`
receives one exchange-scoped correlation value: a normalized valid caller `X-Trace-Id`, or a
generated fallback when the header is missing/invalid. Allowed and fail-open requests carry that
value downstream; a Gateway-owned 429 reuses it in the body above. This public-catalog fallback
behavior does not change the stricter existing admin-catalog request-boundary contract.

### Required headers

| Header | Exact rule |
|--------|------------|
| `Content-Type` | Exact media type `application/json` |
| `Retry-After` | Decimal delay-seconds: `max(1, ceil(retryAfterMs / 1000))` |
| `Cache-Control` | Exact directive `no-store` |

`retryAfterMs` comes only from a successful atomic rejection and represents the earliest coordinator
delay at which the same one-unit request can have sufficient credit. It is not time until a full
bucket and is not an HTTP date. Enabled configuration bounds full refill to 24 hours so bucket expiry
cannot grant a fresh bucket earlier than this advertised horizon. Before any rejection, the atomic
decision also proves the bucket's current finite TTL covers that exact horizon; otherwise the state
is invalid and the request follows fail-open with no `Retry-After`.

### Forbidden accounting headers

Feature 013 emits none of the following on Gateway-owned 429 or allowed responses:

- `RateLimit`;
- `RateLimit-Policy`;
- `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`;
- any case variant of `X-RateLimit-*`.

## 3. Example

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 1
Cache-Control: no-store

{"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests","traceId":"7f59c8a9-fc56-4324-9b85-524d48e7702e"}
```

## 4. Non-rejection outcomes

| Outcome | Downstream invocation | Gateway quota/retry headers |
|---------|-----------------------|-----------------------------|
| Allowed decision | Exactly once | None |
| Missing/unusable direct IP | Exactly once | None |
| Typed Redis timeout/connection/state/result/script/serialization failure | Exactly once | None |
| Limiter disabled, matched catalog GET | Existing routing/quota behavior; correlation filter still forwards once with normalized/generated `X-Trace-Id` | None |
| Policy/route/method unmatched | Existing behavior | None |
| Successful quota rejection | Zero | Only `Retry-After` and `Cache-Control: no-store` above |

Fail-open is not represented by a special success body or client-visible warning header.

## 5. Downstream ownership

Every response obtained normally from downstream remains byte/header owned by that service. In
particular, a downstream 429 passes through its status, content type, body, and sentinel headers
unchanged. The Gateway must not add `Retry-After`, replace `Cache-Control`, remove downstream
accounting headers, or wrap the body merely because the status is 429.

## 6. Writer fallback and committed responses

- The existing `GatewayHttpErrorWriter` remains the only renderer of Gateway-owned error envelopes.
- Quota headers are applied only if rendering still produces `RATE_LIMIT_EXCEEDED`.
- If JSON serialization falls back to `GATEWAY_INTERNAL_ERROR`, no quota/retry header survives.
- A normally rendered expected `RATE_LIMIT_EXCEEDED` does not call the generic per-error WARN
  observation; bounded limiter metrics/Observation own that high-volume expected outcome. A
  serialization fallback to 500 invokes the central error observation exactly once, and other
  Gateway errors preserve Feature 011 behavior.
- If another owner has committed a response, Feature 013 does not rewrite it.

## 7. Compatibility and activation

- Feature 012's status, code, message, three-field body, and correlation semantics are unchanged.
- `Retry-After` and `Cache-Control: no-store` are additive only for an active Gateway-owned rejection.
- Existing successful responses receive no new accounting header.
- The limiter is disabled by default, so deploying code before activation preserves routing, quota,
  status/body, response-header and security behavior. The one intentional immediate delta is an
  additive normalized/generated `X-Trace-Id` on matched catalog requests sent downstream; it is not
  a new client-visible response header.
- Once this contract is approved, the Feature 012 contract receives an amendment pointer before
  production behavior emits the new headers; its verified historical body remains unchanged.

## 8. Contract tests

The implementation gate covers:

1. exact status, body, media type, delay-seconds, and `no-store`;
2. minimum/ceiling conversion for retry delay;
3. absence of every forbidden accounting header;
4. downstream invocation count zero on owned rejection;
5. allowed, identity-unavailable, and fail-open branches without quota headers;
6. downstream 429 byte/status/header pass-through;
7. serialization fallback without leftover quota headers;
8. public-catalog normalized/generated trace propagation and same-value rejection correlation;
9. no generic per-error WARN on expected owned 429, with one central observation on fallback 500.

## Standards

- [RFC 6585 section 4 — 429 Too Many Requests](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)
- [RFC 9110 section 10.2.3 — Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3)
