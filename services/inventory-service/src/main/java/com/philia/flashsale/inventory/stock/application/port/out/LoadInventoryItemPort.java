package com.philia.flashsale.inventory.stock.application.port.out;

import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.util.Optional;
import java.util.UUID;

/** Loads inventory with either read-only or explicit write-lock intent. */
public interface LoadInventoryItemPort {
    Optional<InventoryItem> findByVariantId(UUID variantId);

    Optional<InventoryItem> findByVariantIdForUpdate(UUID variantId);
}
