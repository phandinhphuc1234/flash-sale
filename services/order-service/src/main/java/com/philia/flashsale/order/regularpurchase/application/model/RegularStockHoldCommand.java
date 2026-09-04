package com.philia.flashsale.order.regularpurchase.application.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Stable Order-owned command identity for one idempotent Inventory regular-stock hold. */
public record RegularStockHoldCommand(
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        UUID shopperId,
        Instant requestedAt,
        List<RegularStockHoldLine> items) {

    public RegularStockHoldCommand {
        Objects.requireNonNull(holdId, "holdId is required");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(shopperId, "shopperId is required");
        Objects.requireNonNull(requestedAt, "requestedAt is required");
        if (items == null || items.isEmpty() || items.size() > 20) {
            throw new IllegalArgumentException("regular stock hold needs one to twenty items");
        }
        items = items.stream().sorted(Comparator.comparing(RegularStockHoldLine::variantId)).toList();
        if (items.stream().map(RegularStockHoldLine::variantId).distinct().count() != items.size()) {
            throw new IllegalArgumentException("regular stock hold items must be distinct");
        }
    }

    public record RegularStockHoldLine(UUID variantId, long quantity) {
        public RegularStockHoldLine {
            Objects.requireNonNull(variantId, "variantId is required");
            if (quantity < 1 || quantity > 10) {
                throw new IllegalArgumentException("regular stock hold quantity must be between one and ten");
            }
        }
    }
}
