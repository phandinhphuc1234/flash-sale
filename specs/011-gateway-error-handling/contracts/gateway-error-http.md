# HTTP Contract: Gateway-Owned Errors

**Owner**: `api-gateway`
**Feature**: `011-gateway-error-handling`
**Status**: Approved
**Compatibility**: Additive extension of the existing Product Admin Gateway error body

## Ownership Boundary

This contract applies only when the Gateway itself rejects or cannot complete an exchange in an
approved category.

A downstream HTTP response remains owned by that service. When product-service or another routed
service returns 4xx or 5xx normally, the Gateway preserves its status and body bytes and does not
deserialize, translate, or wrap the response.

```text
HTTP response obtained from service -> downstream contract, pass through
No HTTP response because Gateway exchange failed -> this Gateway contract may apply
```

## Response Body

`Content-Type: application/json`

```json
{
  "code": "DOWNSTREAM_UNAVAILABLE",
  "message": "The requested service is temporarily unavailable",
  "traceId": "e5dc21a7-a4f1-40e5-b4b0-09a6d4f8fa12"
}
```

| Field | Type | Required | Semantics |
|-------|------|----------|-----------|
| `code` | string | Yes | One exact value from the table below |
| `message` | string | Yes | Exact safe message from the table below; never a raw exception message |
| `traceId` | string | Yes | Non-blank normalized caller trace or server-generated correlation ID |

No additional service-business fields are defined by this contract.

## Approved Status and Code Mapping

| HTTP status | Code | Exact message | Applies when |
|-------------|------|---------------|--------------|
| 400 | `INVALID_ADMIN_REQUEST` | `X-Trace-Id must be non-blank and no longer than 128 characters` | Product Admin request has a missing, blank, or overlong first trace-header value |
| 401 | `UNAUTHENTICATED` | `Authentication is required` | Credentials are missing, malformed, expired, have a bad signature, or otherwise fail verification without an infrastructure cause |
| 403 | `CATALOG_ADMIN_REQUIRED` | `CATALOG_ADMIN authority is required` | Authenticated caller lacks the required authority on `/api/v1/admin/catalog/**` |
| 403 | `ACCESS_DENIED` | `Access is denied` | Authenticated caller reaches another deny-by-default path, including an unknown path |
| 503 | `DOWNSTREAM_UNAVAILABLE` | `The requested service is temporarily unavailable` | An HTTP(S) route was selected but no downstream HTTP response was obtained because the connection/exchange failed |
| 503 | `AUTHENTICATION_UNAVAILABLE` | `Authentication is temporarily unavailable` | JWT/JWKS verification infrastructure failed; this does not assert that the token is invalid |
| 500 | `GATEWAY_INTERNAL_ERROR` | `The gateway could not process the request` | An uncommitted Gateway exception has no more specific approved classification |

## Header Semantics

### `X-Trace-Id`

- The first header value is trimmed.
- A non-blank value of at most 128 characters is preserved.
- If the first value is absent, blank, or longer than 128 characters, the Gateway generates one
  server correlation ID for the error exchange.
- Product Admin still returns `INVALID_ADMIN_REQUEST` for invalid caller input; the generated ID is
  for diagnosis and does not bypass validation.
- Repeated values preserve the existing first-value behavior. A later value does not replace an
  invalid first value.

### `WWW-Authenticate`

A 401 `UNAUTHENTICATED` response includes:

```http
WWW-Authenticate: Bearer
```

`AUTHENTICATION_UNAVAILABLE` is a 503 infrastructure outcome and does not claim the token is invalid;
it does not add an invalid-token Bearer challenge.

## Unknown Paths

The Gateway remains deny-by-default and does not expose whether an unknown route exists:

- anonymous caller -> 401 `UNAUTHENTICATED`;
- authenticated caller -> 403 `ACCESS_DENIED`.

The configured operational endpoints `/actuator`, `/actuator/health`,
`/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, and
`/actuator/prometheus` remain public. Any other path under `/actuator/**` is an unknown path and
follows the 401/403 rules above.

The generic 403 must not use the Product-specific `CATALOG_ADMIN_REQUIRED` code.

## Safe-Message Rule

Gateway bodies must not include:

- bearer tokens or claims;
- request bodies;
- exception class or raw exception message;
- internal service hostname, IP address, or port;
- signing key, JWKS URL/content, or provider payload;
- stack traces.

Operator evidence is correlated using `traceId` and records only approved safe fields. Trace/path
values are quoted and reversibly escaped at the logger boundary so control characters cannot forge
additional log records.

## Committed and Partial Responses

If the response is already committed, Feature 011 does not attempt a second status/body write. The
original failure is propagated through the reactive chain. A partially received downstream response
must not be replaced by a Gateway envelope after its response has committed.

## Serialization Fallback

If normal error-envelope serialization fails before the response commits, the Gateway uses a
minimal JSON encoder to return status 500 with `GATEWAY_INTERNAL_ERROR`, its fixed safe message, and
the same resolved non-blank `traceId`. Neither the serialization exception nor the original failure
message appears in that body.

## Explicitly Deferred Outcomes

This contract does not define a Gateway code for:

- route-not-found 404;
- configured response timeout 504;
- rate-limit 429;
- request-size 413;
- circuit-breaker open state;
- retry exhaustion or fallback data.

Those capabilities require their own approved policy/contract. An explicit framework
`ResponseStatusException` outside this taxonomy is delegated rather than reclassified by Feature
011.

## Compatibility Examples

### Downstream Product error — unchanged

If product-service returns:

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json

{"serviceCode":"SKU_CONFLICT","details":["sku-1"]}
```

the Gateway forwards that status and body bytes. It does not return `GATEWAY_INTERNAL_ERROR` or
`DOWNSTREAM_UNAVAILABLE`.

### Routed connection failure — Gateway-owned

If a Product route is selected but the Gateway cannot obtain any HTTP response:

```http
HTTP/1.1 503 Service Unavailable
Content-Type: application/json

{"code":"DOWNSTREAM_UNAVAILABLE","message":"The requested service is temporarily unavailable","traceId":"<non-blank>"}
```
