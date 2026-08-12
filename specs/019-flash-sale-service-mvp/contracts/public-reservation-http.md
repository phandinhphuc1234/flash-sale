# Public Flash Sale Reservation HTTP Contract

**Version**: v1  
**Ingress**: Client -> API Gateway -> `flashsale-service`  
**Authentication**: Bearer access token; Flash Sale validates it independently

## Common Rules

- Base path: `/api/v1/flash-sales`.
- The JWT `sub` is the shopper identity. A user ID in query/body is forbidden.
- JWT issuer and audience must match the approved public trust contract (`flash-sale-api`).
- Success uses `com.philia.flashsale.common.web.ApiResponse<T>`.
- Error uses `com.philia.flashsale.common.web.ApiErrorResponse`.
- Trace identity is returned in `X-Trace-Id`; it is not duplicated in the JSON body.
- Gateway forwards `Authorization`, `Idempotency-Key`, W3C trace headers, `X-Trace-Id`, path, query,
  and body without interpreting reservation business rules.

## Submit Reservation

```http
POST /api/v1/flash-sales/{campaignId}/reservations
Authorization: Bearer <access-token>
Idempotency-Key: <case-sensitive 1..128 character value>
Content-Type: application/json
```

Request body:

```json
{
  "variantId": "9d5f6eb7-d954-4fcb-a2ab-a7647ef9af21",
  "quantity": 1
}
```

Validation:

- `campaignId` and `variantId` are UUID values.
- `quantity` is a positive 64-bit integer.
- `Idempotency-Key` is required, nonblank after validation, case-sensitive, and at most 128
  characters. The raw value is not logged or stored.

### New durable acceptance

```http
HTTP/1.1 202 Accepted
Location: /api/v1/flash-sales/reservations/661bb83f-f333-4550-ab09-e87ccded3cba
X-Trace-Id: 01J...
```

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Reservation accepted",
  "data": {
    "purchaseRequestId": "f74df2f3-286e-45e1-a4e2-5fa7489df440",
    "reservationId": "661bb83f-f333-4550-ab09-e87ccded3cba",
    "campaignId": "7cc05861-c4bf-40f0-adde-ab6ca74e0e83",
    "variantId": "9d5f6eb7-d954-4fcb-a2ab-a7647ef9af21",
    "quantity": 1,
    "status": "RESERVED",
    "expiresAt": "2026-08-10T12:05:00Z"
  },
  "timestamp": "2026-08-10T12:00:00Z"
}
```

`202` is legal only after purchase, reservation, idempotency, and outbox rows commit in PostgreSQL.

### Identical replay

Returns `202 Accepted` with the same `purchaseRequestId`, `reservationId`, business snapshot,
`expiresAt`, and `Location`. It does not decrement quota, increment the user counter, create another
Stream entry, or create another outbox event.

### Redis winner awaiting PostgreSQL

```http
HTTP/1.1 503 Service Unavailable
Retry-After: 1
X-Trace-Id: 01J...
```

```json
{
  "success": false,
  "errorCode": "FLASH_SALE_ACCEPTANCE_PENDING",
  "message": "The reservation decision is being recovered. Retry with the same idempotency key.",
  "errors": [],
  "timestamp": "2026-08-10T12:00:00Z"
}
```

The caller must retry the exact request with the same key. The response intentionally omits internal
Stream and database details and does not claim durable acceptance.

## Query Owned Reservation

```http
GET /api/v1/flash-sales/reservations/{reservationId}
Authorization: Bearer <access-token>
```

Success:

```http
HTTP/1.1 200 OK
X-Trace-Id: 01J...
```

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Reservation retrieved",
  "data": {
    "purchaseRequestId": "f74df2f3-286e-45e1-a4e2-5fa7489df440",
    "reservationId": "661bb83f-f333-4550-ab09-e87ccded3cba",
    "campaignId": "7cc05861-c4bf-40f0-adde-ab6ca74e0e83",
    "variantId": "9d5f6eb7-d954-4fcb-a2ab-a7647ef9af21",
    "sku": "PHONE-BLACK-128",
    "unitPrice": 12990000.0000,
    "currency": "VND",
    "quantity": 1,
    "status": "RESERVED",
    "acceptedAt": "2026-08-10T12:00:00Z",
    "expiresAt": "2026-08-10T12:05:00Z"
  },
  "timestamp": "2026-08-10T12:00:01Z"
}
```

Unknown and foreign-owned reservation IDs both return the same `404
FLASH_SALE_RESERVATION_NOT_FOUND` contract.

## Error Matrix

| HTTP | Error code | Trigger |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | Invalid UUID/body/quantity or Bean Validation failure. |
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Missing, blank, or oversized header. |
| 401 | `AUTHENTICATION_REQUIRED` | Missing/invalid/expired JWT. |
| 403 | `ACCESS_DENIED` | Authenticated token lacks required public access. |
| 404 | `FLASH_SALE_RESERVATION_NOT_FOUND` | Missing or foreign-owned reservation. |
| 409 | `FLASH_SALE_IDEMPOTENCY_CONFLICT` | Same scoped key with different canonical request. |
| 409 | `FLASH_SALE_CAMPAIGN_NOT_ACTIVE` | Campaign not active, not started, or ended. |
| 409 | `FLASH_SALE_VARIANT_NOT_ELIGIBLE` | Variant is not in the Campaign projection. |
| 409 | `FLASH_SALE_SOLD_OUT` | Remaining quota is smaller than requested quantity. |
| 409 | `FLASH_SALE_PURCHASE_LIMIT_EXCEEDED` | Per-user Campaign limit would be exceeded. |
| 409 | `FLASH_SALE_RESERVATION_EXPIRED` | Expiry became terminal before durable acceptance. |
| 503 | `FLASH_SALE_PROJECTION_UNAVAILABLE` | Projection missing/stale/recovery-required; fail closed. |
| 503 | `FLASH_SALE_REDIS_UNAVAILABLE` | Hot-path Redis unavailable; fail closed. |
| 503 | `FLASH_SALE_ACCEPTANCE_PENDING` | Redis winner exists but PostgreSQL acceptance is not yet durable. |
| 500 | `INTERNAL_ERROR` | Sanitized unexpected server failure. |

Validation errors populate shared `FieldViolation` entries. Security and infrastructure errors must
not expose JWT, Redis, database, Kafka, or Schema Registry detail.

## Content and Cache Rules

- Responses are JSON UTF-8.
- Reservation POST and lookup responses use `Cache-Control: no-store`.
- Client-provided `X-Trace-Id` is accepted only according to the Gateway correlation policy; W3C
  tracing remains authoritative internally.

