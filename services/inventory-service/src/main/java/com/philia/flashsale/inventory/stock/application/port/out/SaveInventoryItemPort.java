package com.philia.flashsale.inventory.stock.application.port.out;

import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;

public interface SaveInventoryItemPort {
    InventoryItem save(InventoryItem item);
}
