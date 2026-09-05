package com.philia.flashsale.order.purchasesaga.application.command;

import java.util.Objects;
import java.util.UUID;

/** Framework-free immutable stock line reported by Inventory after one regular hold is confirmed. */
public record RegularStockHoldConfirmedLine(UUID variantId, long quantity) {
    public RegularStockHoldConfirmedLine {
        Objects.requireNonNull(variantId, "variantId");
        if (quantity < 1 || quantity > 10) {
            throw new IllegalArgumentException("regular hold confirmation quantity must be one through ten");
        }
    }
}
