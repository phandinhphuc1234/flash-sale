package com.philia.flashsale.inventory.stock.adapter.in.web.response;

import java.util.UUID;

/** HTTP representation of the current inventory state for one variant. */
public record InventoryResponse(
        UUID variantId,
        String skuSnapshot,
        long onHandQuantity,
        long campaignAllocatedQuantity,
        long availableQuantity
) {
}
