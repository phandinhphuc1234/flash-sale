package com.philia.flashsale.inventory.regularhold.application.result;

import com.philia.flashsale.inventory.regularhold.domain.model.RegularStockHold;

/** Current state after a trusted confirmation attempt; callers choose the correlated fact to emit. */
public record RegularStockHoldConfirmationResult(RegularStockHold hold, boolean transitioned) {
}
