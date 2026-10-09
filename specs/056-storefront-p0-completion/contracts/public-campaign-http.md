# Public Campaign HTTP Contract

All browser requests use API Gateway. JSON success bodies use the shared `ApiResponse<T>` envelope
and responses echo `X-Trace-Id` through existing filters.

## GET `/api/v1/campaigns`

Anonymous, bounded public discovery.

| Query | Default | Rules |
|---|---:|---|
| `phase` | `ALL` | `ALL`, `LIVE`, or `UPCOMING` |
| `page` | `0` | Integer >= 0 |
| `size` | `12` | Integer 1..50 |

Ordering: `startAt ASC, id ASC`.

The `data` field contains shared `PageResponse<PublicCampaignResponse>`. Each campaign contains
`id`, `name`, `phase`, `startAt`, `endAt`, `reservable`, `productId`, `variantId`, `variantSku`,
`basePrice`, `campaignPrice`, `currency`, `purchaseLimitPerUser`, and `presentationAvailable`.

Errors: `400` invalid phase/page/size; `500` safe unexpected Campaign failure.

## GET `/api/v1/campaigns/{campaignId}`

Anonymous public detail. Scheduled, active, and ended campaigns are readable. Draft or unknown IDs
return `404` so admin-only state is not disclosed.

Response `200`: `ApiResponse<PublicCampaignResponse>`. `phase` may be `UPCOMING`, `LIVE`, or
`ENDED`; `reservable` is false unless the authoritative active conditions hold.

Errors: `400` malformed UUID; `404` absent/non-public campaign; `500` safe unexpected failure.

## Existing reservation contract (unchanged)

`POST /api/v1/flash-sales/{campaignId}/reservations`

- Requires shopper authentication.
- Existing idempotency, quantity, owner, expiry, Order, and Payment behavior remains authoritative.
- Public Campaign data is discovery only and does not prove reservation or stock success.
