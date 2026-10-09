# Research: P0 Storefront Completion

## Decision 1: Public campaign composition

**Decision**: Serve Campaign-owned scheduling snapshots without runtime Product/Inventory calls.

**Rationale**: Each campaign already freezes product ID, variant ID, SKU, base price, campaign price,
allocation, and per-user limit. These values are sufficient for a truthful offer card when combined
with the customer-facing campaign name. A public request therefore remains one bounded database
read and continues to work if Product Service is unavailable.

**Alternatives rejected**:

- Per-campaign Product calls: creates N+1 latency and turns a Product outage into a Flash Sale outage.
- A new event-fed storefront projection: duplicates state and adds consumers/outbox contracts beyond
  P0 needs.
- Querying Product tables from Campaign: violates service data ownership.

## Decision 2: Lifecycle and visibility

**Decision**: Discovery queries only `SCHEDULED` and `ACTIVE` records whose end time is in the
future. Presentation derives `UPCOMING`, `LIVE`, or `ENDED` from timestamps and stored status.
Detail permits scheduled, active, and ended campaigns but treats draft campaigns as not found.

## Decision 3: Pagination

**Decision**: `page` is zero-based, `size` defaults to 12 and is capped at 50. Ordering is start time
ascending, then campaign UUID ascending. Optional `phase=LIVE|UPCOMING|ALL` defaults to `ALL`.

## Decision 4: Error/envelope compatibility

**Decision**: Success uses `ApiResponse<T>` and `PageResponse<T>`. Campaign-owned failures continue
through its existing error handler and `X-Trace-Id` behavior rather than attempting a broad error
contract migration in this feature.

## Decision 5: Storefront behavior

**Decision**: Add `/flash-sale` and `/flash-sale/[id]`; retain the existing reservation result route.
Unauthenticated shoppers are sent to `/login?returnTo=<campaign-detail>`. Loading/error/not-found
states expose no raw provider or infrastructure message.

## Decision 6: Unsupported controls

**Decision**: Remove Newsletter and Wishlist controls. Remove the address page from the supported
journey and make its old URL not found. Help, Contact, and Privacy become real static routes.
