package com.philia.flashsale.inventory.stock.application.result;

import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.time.Instant;
import java.util.UUID;

/** Read model for one row in the admin inventory collection. */
public record InventoryListItemResult(
        UUID variantId,
        String skuSnapshot,
        long onHandQuantity,
        long campaignAllocatedQuantity,
        long availableQuantity,
        Instant updatedAt) {

    public static InventoryListItemResult from(InventoryItem item) {
        return new InventoryListItemResult(
                item.variantId(),
                item.skuSnapshot(),
                item.onHandQuantity(),
                item.campaignAllocatedQuantity(),
                item.availableQuantity(),
                item.updatedAt());
    }
}
