# Data Model: P0 Storefront Completion

## PublicCampaignSummary

| Field | Source | Notes |
|---|---|---|
| `id` | Campaign | Stable UUID |
| `name` | Campaign | Customer-facing campaign name |
| `phase` | Derived | `LIVE` or `UPCOMING` in discovery |
| `startAt`, `endAt` | Campaign | ISO-8601 instants |
| `reservable` | Derived | True only when stored ACTIVE and current time is inside the window |
| `productId`, `variantId` | Campaign item snapshot | Stable references |
| `variantSku` | Campaign item snapshot | Nullable only for legacy/incomplete data |
| `basePrice`, `campaignPrice`, `currency` | Campaign item snapshot | Decimal strings/currency |
| `purchaseLimitPerUser` | Campaign item | Positive integer |
| `presentationAvailable` | Derived | False when required snapshot fields are missing |

## PublicCampaignDetail

Same shopper-safe fields as the summary. Detail may report `ENDED`; it never exposes requested or
allocated quantity, internal allocation ID, outbox state, actor, version, or recovery metadata.

## Invariants

- Draft campaigns are never public.
- Discovery never contains ended campaigns.
- Exact remaining quantity is never returned.
- `reservable=true` requires stored ACTIVE, `startAt <= now < endAt`, and a complete item snapshot.
- Missing snapshot data is represented explicitly; values are not fabricated.
- Ordering is `(startAt ASC, id ASC)`.

## Persistence impact

None. Existing Campaign and Campaign Item columns are sufficient; no migration is introduced.
