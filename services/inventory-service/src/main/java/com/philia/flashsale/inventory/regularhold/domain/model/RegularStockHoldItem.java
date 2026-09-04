package com.philia.flashsale.inventory.regularhold.domain.model;

import com.philia.flashsale.inventory.regularhold.domain.exception.RegularStockHoldDomainException;
import java.util.Objects;
import java.util.UUID;

/** One canonical variant quantity inside an Inventory-owned regular stock hold. */
public record RegularStockHoldItem(UUID inventoryItemId, UUID variantId, long quantity, String skuSnapshot) {
    public RegularStockHoldItem {
        Objects.requireNonNull(inventoryItemId, "inventoryItemId");
        Objects.requireNonNull(variantId, "variantId");
        if (quantity <= 0) {
            throw new RegularStockHoldDomainException("Regular hold quantity must be positive");
        }
        if (skuSnapshot == null || skuSnapshot.isBlank() || skuSnapshot.length() > 100) {
            throw new RegularStockHoldDomainException("Regular hold SKU snapshot must be nonblank and bounded");
        }
    }
}
