# Additive Order item display contract

## Existing endpoint

`GET /api/v1/orders/{orderId}` remains owner-only, via `api-gateway`, with unchanged HTTP status, authentication, authorization, success/error envelope, `X-Trace-Id` handling, and cache policy. No new browser-to-Product route is introduced.

## Additive response fields

Each `data.items[]` keeps its existing `variantId`, `quantity`, `unitPrice`, and `lineAmount` fields. It additionally returns:

| Field | Type | Meaning |
|---|---|---|
| `productName` | string or null | Product name captured when the Order was created; null if not captured. |
| `variantName` | string or null | Variant name captured when the Order was created; null if not captured. |

Example named item:

```json
{
  "variantId": "711ffdce-0dfa-4b66-ad25-4e247037f3ec",
  "productName": "Flash Sale Shirt",
  "variantName": "Black / M",
  "quantity": 1,
  "unitPrice": 179000,
  "lineAmount": 179000
}
```

Example historical or best-effort-unavailable item: `productName` and `variantName` are both `null`. They are never populated by reading the current Product catalog during this GET request.

## Compatibility and verification

- Additive JSON fields within the existing v1 endpoint; no URL or envelope version change.
- Existing clients ignoring unknown fields continue to work. New clients must tolerate null/absent name fields while old Order images coexist.
- Owner-only access, 401/403/404 behavior, trace header, and monetary fields are unchanged.
- Contract tests cover a named line, a legacy unnamed line, and owner boundary. Documentation in `docs/api/frontend-integration-guide.md` is updated with implementation.
