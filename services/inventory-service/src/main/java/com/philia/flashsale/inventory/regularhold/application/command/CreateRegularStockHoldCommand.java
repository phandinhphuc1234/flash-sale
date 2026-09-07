package com.philia.flashsale.inventory.regularhold.application.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Order-only intent to atomically hold regular stock using Inventory's current clock. */
public record CreateRegularStockHoldCommand(
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        UUID shopperId,
        Instant requestedAt,
        List<RegularStockHoldLine> items) {
    public CreateRegularStockHoldCommand {
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(shopperId, "shopperId");
        Objects.requireNonNull(requestedAt, "requestedAt");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Regular hold must contain at least one item");
        }
    }
}
