package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Wire command with the same durable identity on every explicit retry/resume. */
public record InventoryRegularHoldRequest(
        UUID holdId,
        UUID purchaseRequestId,
        UUID orderId,
        UUID shopperId,
        Instant requestedAt,
        List<InventoryRegularHoldItemRequest> items) {
    public record InventoryRegularHoldItemRequest(UUID variantId, long quantity) { }
}
