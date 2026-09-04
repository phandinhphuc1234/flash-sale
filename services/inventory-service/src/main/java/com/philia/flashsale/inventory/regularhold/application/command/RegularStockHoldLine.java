package com.philia.flashsale.inventory.regularhold.application.command;

import java.util.Objects;
import java.util.UUID;

/** Requested regular quantity before Inventory canonicalizes and locks variant rows. */
public record RegularStockHoldLine(UUID variantId, long quantity) {
    public RegularStockHoldLine {
        Objects.requireNonNull(variantId, "variantId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Regular hold line quantity must be positive");
        }
    }
}
