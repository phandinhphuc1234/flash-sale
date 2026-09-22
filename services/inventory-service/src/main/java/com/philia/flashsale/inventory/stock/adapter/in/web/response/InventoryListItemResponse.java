package com.philia.flashsale.inventory.stock.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

public record InventoryListItemResponse(
        UUID variantId,
        String skuSnapshot,
        long onHandQuantity,
        long campaignAllocatedQuantity,
        long availableQuantity,
        Instant updatedAt) {
}
