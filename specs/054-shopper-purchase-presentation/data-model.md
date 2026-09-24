# Data Model: Shopper Purchase Presentation

## Order line name snapshot

Owner: `order-service`. The existing `order_lines` row retains its `id`, `order_id`, `variant_id`, quantity, unit price and line amount. A new, forward-only Order Liquibase changeset adds:

| Field | Type | Rule |
|---|---|---|
| `product_name_snapshot` | `VARCHAR(255)` | Nullable, immutable after insertion; null for historical rows or an unavailable Product lookup. |
| `variant_name_snapshot` | `VARCHAR(255)` | Nullable, immutable after insertion; null for historical rows or an unavailable Product lookup. |

Do not backfill from Product, introduce a Product foreign key, or change money columns. Order owns these historical labels; Product continues to own current catalog names. The already-existing nullable `product_id`, `sku_snapshot`, and `display_name_snapshot` columns are not repurposed by this feature.

## Regular purchase intake

The existing `regular_purchase_requests.snapshot_payload` JSONB holds each submitted line and its later validated metadata. Add optional `productName` and `variantName` to each line only when Product validation succeeds. Legacy payloads without these keys decode as null. The request fingerprint remains a hash of shopper-submitted source, variant, quantity, expected price/currency and Cart versions, never of Product-owned names. `PRODUCT_VALIDATED`, `HOLD_ACQUIRED`, `ACCEPTED`, and recovery preserve the captured names; retries must not replace them with current catalog values.

## Flash Sale accepted purchase

No Kafka schema or event data changes. After `PurchaseAcceptedV1` is validated, the Order creation use case requests an optional display-name snapshot from Product using `variantId`. If Product returns trustworthy names, the newly inserted Order line stores them. Failure/absence stores null and does not change event fingerprint, inbox key, saga state, accepted monetary fields, or outbox payload. On duplicate delivery, existing Order/inbox state wins; the stored names are never rewritten.

## Read model and UI

`OrderItemResult` and `OrderItemResponse` gain nullable `productName` and `variantName` while keeping `variantId`, `quantity`, `unitPrice`, and `lineAmount`. A client displays captured names when available; otherwise it renders a neutral item label with `variantId` as secondary identification. The list-order response remains unchanged.

## Migration safety

Expand step: add nullable columns only; deploy migration before the new Order image. The old Order image can continue inserting rows without names. A rollback to the old image is schema-compatible, but already-written names remain in place. No automatic destructive down-migration or historical name backfill is permitted. Verification covers old rows, old regular-intake JSON, new writes, read responses, and replays.
