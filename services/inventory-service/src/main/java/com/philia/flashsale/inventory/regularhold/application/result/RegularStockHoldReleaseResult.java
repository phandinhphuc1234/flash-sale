package com.philia.flashsale.inventory.regularhold.application.result;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;

/** Current state after a trusted release/expiry attempt. */
public record RegularStockHoldReleaseResult(
        RegularStockHold hold,
        boolean transitioned,
        String reason
) {
}
