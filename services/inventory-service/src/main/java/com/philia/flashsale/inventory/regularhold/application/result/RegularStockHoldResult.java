package com.philia.flashsale.inventory.regularhold.application.result;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldItem;
import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Application result that distinguishes a first acceptance from a compatible replay. */
public record RegularStockHoldResult(
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        RegularStockHoldStatus status,
        Instant expiresAt,
        List<RegularStockHoldItemResult> items,
        boolean replayed) {
    public static RegularStockHoldResult from(RegularStockHold hold, boolean replayed) {
        return new RegularStockHoldResult(hold.id(), hold.purchaseRequestId(), hold.orderId(),
                hold.status(), hold.expiresAt(), hold.items().stream()
                        .map(RegularStockHoldResult::item)
                        .toList(), replayed);
    }

    private static RegularStockHoldItemResult item(RegularStockHoldItem item) {
        return new RegularStockHoldItemResult(item.variantId(), item.quantity());
    }
}
