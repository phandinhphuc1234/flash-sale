# Internal Campaign Snapshot HTTP Contract

**Owner**: Campaign Service  
**Caller**: Flash Sale Service only  
**Route**: not exposed by API Gateway

## Request

```http
GET /internal/v1/campaigns/{campaignId}/snapshot
Authorization: Bearer <flashsale-service-access-token>
X-Trace-Id: <trace-id>
```

Token requirements:

- issuer: approved Authentication issuer;
- subject: `flashsale-service`;
- audience: `flash-sale-internal-api`;
- authority: `SCOPE_campaign.snapshot.read`;
- valid RS256 signature and time window.

## Success

```http
200 OK
Content-Type: application/json
X-Trace-Id: trace-id
```

```json
{
  "campaignId": "6e80db14-2355-4ce4-9932-f0bf1735f192",
  "campaignCode": "FLASH-SALE-2026-08-01-IPHONE",
  "status": "SCHEDULED",
  "startAt": "2026-08-01T05:30:00Z",
  "endAt": "2026-08-01T07:30:00Z",
  "aggregateVersion": 3,
  "item": {
    "variantId": "92a1ab56-35f9-48bd-9fb1-7cc197f07c57",
    "inventoryAllocationId": "21aa1d8a-3037-4f2c-aa0e-19ee4f2901ef",
    "variantSku": "IPHONE-16-128-BLACK",
    "campaignPrice": 19900000.0000,
    "currency": "VND",
    "allocatedQuantity": 1000,
    "purchaseLimitPerUser": 1
  }
}
```

Only `SCHEDULED`, `ACTIVE`, or `ENDED` Campaigns have a snapshot. The response excludes Inventory
request ID, schedule-operation state, audit actors, and outbox metadata. It is for cache
rebuild/recovery, not the per-purchase hot path.

## Failures

| Condition | Status | Code |
|---|---:|---|
| missing/invalid/expired token or wrong audience/subject | 401 | service security entry-point code |
| valid token without snapshot scope | 403 | service access-denied code |
| missing Campaign, DRAFT Campaign, or incomplete snapshot | 404 | `CAMPAIGN_SNAPSHOT_NOT_FOUND` |
| unexpected error | 500 | `CAMPAIGN_INTERNAL_ERROR` |

An administrator token or `campaign-service` token cannot authorize this endpoint.
