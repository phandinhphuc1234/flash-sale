# Admin Campaign HTTP Contract

**Owner**: Campaign Service  
**Ingress**: API Gateway only  
**Base path**: `/api/v1/admin/campaigns`  
**Authority**: `SCOPE_CAMPAIGN_ADMIN` at Gateway and Campaign Service

## Common headers

```http
Authorization: Bearer <administrator-access-token>
X-Trace-Id: <non-blank value, maximum 128 characters>
Content-Type: application/json
```

Mutations of an existing Campaign also require a quoted numeric version:

```http
If-Match: "3"
```

Schedule additionally requires:

```http
Idempotency-Key: <non-blank value, maximum 128 characters>
```

Campaign Service returns the effective `X-Trace-Id` response header. Successful Campaign responses
are direct DTOs, not a shared envelope. Mutation/detail responses expose the current quoted version
as `ETag`.

## Common error body

```json
{
  "code": "CAMPAIGN_VERSION_CONFLICT",
  "message": "The campaign version is stale",
  "traceId": "trace-id",
  "fieldErrors": null
}
```

Validation may include:

```json
{
  "code": "CAMPAIGN_VALIDATION_FAILED",
  "message": "Request validation failed",
  "traceId": "trace-id",
  "fieldErrors": [
    { "field": "name", "message": "must not be blank" }
  ]
}
```

| Failure class | HTTP status |
|---|---:|
| malformed/invalid request, missing/malformed required header | 400 |
| missing/invalid/expired administrator token | 401 |
| authenticated without `SCOPE_CAMPAIGN_ADMIN` | 403 |
| Campaign/Product resource not found | 404 |
| lifecycle/version/idempotency/allocation business conflict | 409 |
| Product, Inventory, or Authentication dependency unavailable | 503 |
| unexpected failure | 500 |

No error body includes credentials, bearer tokens, internal exception names, SQL, or stack traces.

## Campaign representation

```json
{
  "id": "6e80db14-2355-4ce4-9932-f0bf1735f192",
  "code": "FLASH-SALE-2026-08-01-IPHONE",
  "name": "Flash Sale iPhone",
  "status": "SCHEDULED",
  "startAt": "2026-08-01T05:30:00Z",
  "endAt": "2026-08-01T07:30:00Z",
  "scheduledAt": "2026-07-30T02:00:00Z",
  "activatedAt": null,
  "endedAt": null,
  "version": 3,
  "item": {
    "productId": "249887bf-1a20-4861-8f97-4226e880f40b",
    "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57",
    "inventoryAllocationId": "21aa1d8a-3037-4f2c-aa0e-19ee4f2901ef",
    "variantSku": "IPHONE-16-128-BLACK",
    "basePrice": 22990000.0000,
    "currency": "VND",
    "campaignPrice": 19900000.0000,
    "requestedQuantity": 1000,
    "allocatedQuantity": 1000,
    "purchaseLimitPerUser": 1
  },
  "createdAt": "2026-07-29T15:30:00Z",
  "updatedAt": "2026-07-30T02:00:00Z"
}
```

All instants serialize in ISO-8601 UTC. Money is JSON numeric and maps to `BigDecimal`; it is never
mapped through `double`.

## 1. Create draft

```http
POST /api/v1/admin/campaigns
```

```json
{
  "code": "flash-sale-2026-08-01-iphone",
  "name": "Flash Sale iPhone",
  "startAt": "2026-08-01T05:30:00Z",
  "endAt": "2026-08-01T07:30:00Z"
}
```

Rules: code required/max 64 and normalized uppercase; name required/max 200; both instants required;
`startAt < endAt`. Create does not require future start.

Success: `201 Created`, `Location: /api/v1/admin/campaigns/{id}`, `ETag: "0"`, Campaign body.

Specific errors: `CAMPAIGN_VALIDATION_FAILED` (400), `CAMPAIGN_CODE_ALREADY_EXISTS` (409).

## 2. Replace draft metadata

```http
PATCH /api/v1/admin/campaigns/{campaignId}
If-Match: "0"
```

```json
{
  "name": "Updated name",
  "startAt": "2026-08-01T06:00:00Z",
  "endAt": "2026-08-01T08:00:00Z"
}
```

All three fields are required; this is not JSON Merge Patch. Code cannot change. Success:
`200 OK`, next `ETag`, Campaign body.

Specific errors: `CAMPAIGN_VALIDATION_FAILED` (400), `CAMPAIGN_NOT_FOUND` (404),
`CAMPAIGN_INVALID_STATUS`, `CAMPAIGN_VERSION_CONFLICT`, or
`CAMPAIGN_OPERATION_IN_PROGRESS` (409).

## 3. Replace the one draft item

```http
PUT /api/v1/admin/campaigns/{campaignId}/item
If-Match: "1"
```

```json
{
  "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57",
  "campaignPrice": 19900000.0000,
  "requestedQuantity": 1000,
  "purchaseLimitPerUser": 1
}
```

This is local draft preparation: Product and Inventory are not called. Replacing the item resets all
snapshot/allocation values. Success: `200 OK`, next `ETag`, Campaign body.

Specific errors: price/quantity/purchase-limit validation (400), Campaign not found (404), or
invalid status/version/operation in progress (409).

## 4. Get admin detail

```http
GET /api/v1/admin/campaigns/{campaignId}
```

Success: `200 OK`, current `ETag`, Campaign body. Error: `CAMPAIGN_NOT_FOUND` (404).

## 5. Schedule

```http
POST /api/v1/admin/campaigns/{campaignId}/schedule
If-Match: "2"
Idempotency-Key: schedule-6e80db14-v2
Content-Type: application/json

{}
```

Success: `200 OK`, next `ETag`, Campaign body with status `SCHEDULED` and complete snapshot.

Replay rules:

- same key + same canonical request hash: return completed result or resume the same operation;
- same key + different hash: `409 CAMPAIGN_SCHEDULE_REQUEST_CONFLICT` without changed Inventory call;
- different key while an operation is active: `409 CAMPAIGN_OPERATION_IN_PROGRESS`;
- stable Inventory request ID is reused across every retry and recovery.

Specific errors:

- 400: `CAMPAIGN_START_TIME_IN_PAST`, `CAMPAIGN_ITEM_REQUIRED`,
  `CAMPAIGN_PRICE_INVALID`, request/header validation;
- 404: `CAMPAIGN_NOT_FOUND`, `PRODUCT_VARIANT_NOT_FOUND`;
- 409: `PRODUCT_VARIANT_NOT_SELLABLE`, `INVENTORY_INSUFFICIENT_STOCK`,
  `INVENTORY_ALLOCATION_REJECTED`, `INVENTORY_ALLOCATION_CONFLICT`,
  `CAMPAIGN_INVALID_STATUS`, `CAMPAIGN_VERSION_CONFLICT`,
  `CAMPAIGN_SCHEDULE_REQUEST_CONFLICT`, `CAMPAIGN_OPERATION_IN_PROGRESS`;
- 503: `PRODUCT_SERVICE_UNAVAILABLE`, `INVENTORY_SERVICE_UNAVAILABLE`,
  `AUTHENTICATION_SERVICE_UNAVAILABLE`.

## 6. Manual recovery activation

```http
POST /api/v1/admin/campaigns/{campaignId}/activate
If-Match: "3"
Content-Type: application/json

{}
```

Valid only for `SCHEDULED` with `startAt <= now < endAt` and a complete snapshot/allocation.
Success: `200 OK`, next `ETag`, Campaign body with status `ACTIVE`.

Specific errors: not found (404); invalid status, not started, already ended, or version conflict
(409). It never activates early.

## 7. Requeue a terminally failed lifecycle event

```http
POST /api/v1/admin/campaigns/{campaignId}/outbox-events/{eventId}/requeue
Content-Type: application/json

{}
```

Valid only when the event belongs to the Campaign and is `FAILED`. The command preserves event ID,
payload, type/version, aggregate/version, and key; resets the automatic attempt counter; increments
requeue count; records operator/time; and returns:

```json
{
  "eventId": "daef0922-2230-4706-bfd2-0c1116774ac4",
  "campaignId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
  "publishStatus": "PENDING",
  "retryCount": 0,
  "requeueCount": 1
}
```

Success: `200 OK`. Errors: `CAMPAIGN_EVENT_NOT_FOUND` (404) or
`CAMPAIGN_EVENT_INVALID_STATUS` (409).
