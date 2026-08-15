# Public Order Query HTTP Contract

**Status**: Proposed by Feature 020; must be approved before implementation
**Version**: v1
**Ingress**: Client -> API Gateway -> `order-service`
**Authentication**: Bearer access token; Gateway and Order independently validate the public trust
contract

## Common Rules

- Base path: `/api/v1/orders`.
- Only `GET` is supported. `POST`, `PUT`, `PATCH`, and `DELETE` are absent.
- The JWT `sub` claim is the shopper identity. A user ID in the path, query, body, or identity header
  is forbidden.
- JWT issuer, RS256 signature, JWKS, audience `flash-sale-api`, `typ=at+jwt`, expiry/time claims, and
  nonblank subject follow the existing public trust contract.
- Success uses `com.philia.flashsale.common.web.ApiResponse<T>`.
- Failure uses `com.philia.flashsale.common.web.ApiErrorResponse`.
- Pagination uses `com.philia.flashsale.common.web.PageResponse<T>` and `PageMeta`.
- `X-Trace-Id` is returned as a header and is not duplicated in the JSON body.
- Successful and failed Order query responses use `Cache-Control: no-store`.

## 1. Get Owned Order

```http
GET /api/v1/orders/{orderId}
Authorization: Bearer <access-token>
X-Trace-Id: <optional-compatible-trace-id>
```

Path validation:

- `orderId` must be a UUID.

Owner success:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store
X-Trace-Id: 01J...
```

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Order retrieved",
  "data": {
    "id": "ddde6200-6252-4989-b604-770511911467",
    "orderNumber": "FS-20260815-A7K3P2",
    "purchaseRequestId": "f74df2f3-286e-45e1-a4e2-5fa7489df440",
    "reservationId": "91bce018-c192-4b05-843c-a0cb0e3c13fb",
    "campaignId": "9ee74be7-5bc5-4d24-a227-15a8da79f24a",
    "status": "PENDING_PAYMENT",
    "currency": "VND",
    "subtotalAmount": 199000.0000,
    "totalAmount": 199000.0000,
    "acceptedAt": "2026-08-15T15:00:01Z",
    "reservationExpiresAt": "2026-08-15T15:05:01Z",
    "items": [
      {
        "variantId": "409c8dbf-14d2-4944-aa13-22118c986d94",
        "quantity": 1,
        "unitPrice": 199000.0000,
        "lineAmount": 199000.0000
      }
    ],
    "createdAt": "2026-08-15T15:00:02Z",
    "updatedAt": "2026-08-15T15:00:02Z"
  },
  "timestamp": "2026-08-15T15:00:03Z"
}
```

The public response intentionally omits `userId`, Kafka position, event/inbox/outbox identity,
database row version, retry state, and internal diagnostics.

Unknown and foreign-owned Orders return the same result:

```http
HTTP/1.1 404 Not Found
Cache-Control: no-store
X-Trace-Id: 01J...
```

```json
{
  "success": false,
  "errorCode": "ORDER_NOT_FOUND",
  "message": "Order not found",
  "errors": [],
  "timestamp": "2026-08-15T15:00:03Z"
}
```

The service performs one owner-scoped lookup equivalent to `orderId + jwtSubject`; it does not load
an Order first and return a distinguishable authorization response.

## 2. List Current Shopper's Orders

```http
GET /api/v1/orders?page=0&size=20
Authorization: Bearer <access-token>
```

Rules:

- `page` is optional, zero-based, and defaults to `0`.
- `size` is optional, defaults to `20`, must be positive, and has maximum `100`.
- No `userId` query parameter is accepted.
- No status filter is present in Feature 020 because the feature owns only `PENDING_PAYMENT`.
- Ordering is `createdAt DESC`, then Order `id DESC` for deterministic ties.
- A page beyond the last page returns HTTP 200 with empty `data` and accurate page metadata.

Success:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Orders retrieved",
  "data": {
    "data": [
      {
        "id": "ddde6200-6252-4989-b604-770511911467",
        "orderNumber": "FS-20260815-A7K3P2",
        "status": "PENDING_PAYMENT",
        "currency": "VND",
        "totalAmount": 199000.0000,
        "reservationExpiresAt": "2026-08-15T15:05:01Z",
        "createdAt": "2026-08-15T15:00:02Z"
      }
    ],
    "page": {
      "number": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  },
  "timestamp": "2026-08-15T15:00:03Z"
}
```

## Error Matrix

| HTTP | Error code | Trigger |
|-----:|------------|---------|
| 400 | `VALIDATION_FAILED` | Invalid UUID, negative page, non-positive size, size over 100, or unsupported query parameter |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, malformed, invalid-signature, wrong-issuer, wrong-audience, wrong-type, expired, or missing-subject JWT |
| 403 | `ACCESS_DENIED` | Authenticated principal lacks the route authority required by the existing public policy |
| 404 | `ORDER_NOT_FOUND` | Order absent or owned by another shopper |
| 405 | `ORDER_METHOD_NOT_ALLOWED` | Mutation method attempted on the Order query route |
| 500 | `ORDER_INTERNAL_ERROR` | Sanitized unexpected failure |
| 503 | `ORDER_DATABASE_UNAVAILABLE` | Order database is unavailable and the query cannot be served |

Validation errors populate the shared `FieldViolation` list. Security and infrastructure failures
must not disclose token, database, Kafka, Schema Registry, SQL, stack-trace, or ownership detail.

## Gateway Contract

```text
route id: order-public
path: /api/v1/orders/**
methods: GET
target: ${ORDER_SERVICE_URL:http://order-service:8080}
authentication: required
```

Gateway forwards `Authorization`, W3C trace headers, compatible `X-Trace-Id`, path, and query. It
does not parse Order ownership or replace Order Service authorization.

## Compatibility

- Additive optional response fields may be introduced only with contract-test review.
- Existing fields are not renamed, removed, narrowed, or given a different meaning within v1.
- A future status filter or lifecycle state requires the feature that owns those states and must
  preserve clients that omit the filter.
- A future payment endpoint does not become part of this base contract implicitly.
