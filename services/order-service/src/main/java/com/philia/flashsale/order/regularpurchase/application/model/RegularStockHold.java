package com.philia.flashsale.order.regularpurchase.application.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Exact, validated Inventory response that Order can use to continue its durable workflow. */
public record RegularStockHold(
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        String status,
        Instant expiresAt,
        List<RegularStockHoldItem> items) {

    public RegularStockHold {
        Objects.requireNonNull(holdId, "holdId is required");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (items == null || items.isEmpty() || items.size() > 20) {
            throw new IllegalArgumentException("regular stock hold response needs one to twenty items");
        }
        items = items.stream().sorted(Comparator.comparing(RegularStockHoldItem::variantId)).toList();
    }

    public record RegularStockHoldItem(UUID variantId, long quantity) {
        public RegularStockHoldItem {
            Objects.requireNonNull(variantId, "variantId is required");
            if (quantity < 1 || quantity > 10) {
                throw new IllegalArgumentException("regular stock hold response quantity must be between one and ten");
            }
        }
    }
}
