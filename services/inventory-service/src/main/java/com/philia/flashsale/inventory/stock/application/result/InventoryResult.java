package com.philia.flashsale.inventory.stock.application.result;

import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.util.UUID;

/** Application-owned inventory state; the internal item ID is not exposed by the HTTP mapper. */
public record InventoryResult(
        UUID inventoryItemId,
        UUID variantId,
        String skuSnapshot,
        long onHandQuantity,
        long campaignAllocatedQuantity,
        long availableQuantity
) {
    public static InventoryResult from(InventoryItem item) {
        return new InventoryResult(
                item.id(),
                item.variantId(),
                item.skuSnapshot(),
                item.onHandQuantity(),
                item.campaignAllocatedQuantity(),
                item.availableQuantity()
        );
    }
}
