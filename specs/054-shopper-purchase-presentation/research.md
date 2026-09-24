# Research: Shopper Purchase Presentation

**Status**: Design input, 2026-09-23. Decisions below are constrained by the owner-approved behavior in `spec.md`.

## Order name source

**Decision**: For Buy Now and Cart, capture `productName` and `variantName` from the existing Product purchase-quote response when Product validation succeeds. Persist them in the Order-owned resumable intake before creating the stock hold; carry them into the Order line at acceptance. For Flash Sale, use a best-effort Order-to-Product lookup at Order creation. A failed or untrustworthy lookup yields absent names and does not stop Order/saga creation.

**Rationale**: `ProductPurchaseQuote` and the private `POST /internal/v1/catalog/variants/purchase-quotes` response already contain both names. `PurchaseAcceptedV1` has only `variantId`. The chosen behavior does not require a new browser route, Kafka event version, or Flash Sale hot-path Product call. The regular intake can be recovered from `regular_purchase_requests.snapshot_payload` after `PRODUCT_VALIDATED` and `HOLD_ACQUIRED`; names must survive that recovery.

**Alternatives considered**: Current-catalog lookup on every Order read would rewrite history; rejected by owner. Making Product availability a prerequisite for Flash Sale Order creation would couple payment saga progress to presentation data; rejected by owner. Enriching `PurchaseAcceptedV1` would change producer/event compatibility and was deferred by owner. A second quote lookup after a regular stock hold was rejected because it can return a different name and loses the already-validated checkpoint snapshot.

## Durable data and compatibility

**Decision**: Add nullable `product_name_snapshot` and `variant_name_snapshot` columns to Order-owned `order_lines` with a new forward-only Liquibase changeset. Keep existing rows null; do not backfill them from current Product state. Add nullable names to the existing regular-intake JSON structure without changing the shopper request fingerprint. Add nullable `productName` and `variantName` fields to each public Order item response, preserving every existing field and the current success envelope/status/headers.

**Rationale**: The existing `display_name_snapshot VARCHAR(300)` cannot hold two independent full Product names (each may be 255 characters). Separate nullable columns preserve semantic identity and avoid truncation or destructive data rewrites. The existing unused snapshot columns remain untouched for compatibility. Old readers ignore the additive response fields; new readers handle null for legacy and failed best-effort Flash Sale lookups.

**Alternatives considered**: Concatenating names into `display_name_snapshot` loses independent fields and may exceed 300 characters. Destructive backfill would invent historical names and was rejected by owner.

## Replay and failure behavior

**Decision**: Names are presentation metadata, not part of the accepted-purchase fingerprint, stock hold, price, or outbox event. Existing inbox/purchase/reservation deduplication remains authoritative. Replayed events may attempt a new read, but must not update the persisted Order line. The Flash Sale Product call is outside the Order persistence transaction and catches only its lookup failures; malformed purchase events and DB errors retain their existing failure handling.

**Rationale**: Name lookup must not alter acceptance identity or block the saga. A later Product rename must not overwrite the first stored snapshot. The current Feign Product client already handles OAuth2 client credentials, trace header, and bounded HTTP behavior; reuse it through an Order-owned display-name port, without adding a dependency.

**Alternatives considered**: Performing the lookup inside a database transaction would hold locks across HTTP. Catching all runtime failures around Order creation would hide real persistence and event errors.

## Frontend presentation

**Decision**: Use one small status-label helper for existing Order/Payment/Reservation state values and an unknown-state fallback. Display Order `productName`/`variantName` as the primary text when present; otherwise use a neutral item label and show a shortened or accessible `variantId` as secondary identification. Product cards use the minimum of displayable variant prices with an explicit `From` label for multiple variants. Remove the unverified Flash Sale badge and label the campaign-ID input as a manual/demo path.

**Rationale**: These changes keep server-owned price and purchase states untouched and do not imply campaign membership. Existing selected-variant detail price remains the source shown on the detail page.

**Alternatives considered**: Treating an arbitrary first variant as a universal price and showing raw UUIDs/statuses as primary text were the reported usability problems.
