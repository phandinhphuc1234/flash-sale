package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wire response intentionally excludes Inventory persistence identifiers and operational stock data. */
public record InventoryRegularHoldResponse(
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        String status,
        Instant expiresAt,
        List<InventoryRegularHoldItemResponse> items) {
    public record InventoryRegularHoldItemResponse(UUID variantId, long quantity) { }
}
