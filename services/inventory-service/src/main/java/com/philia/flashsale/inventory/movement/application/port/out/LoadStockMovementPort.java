package com.philia.flashsale.inventory.movement.application.port.out;

import com.philia.flashsale.inventory.movement.application.result.StockMovementPage;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import java.util.Optional;
import java.util.UUID;

public interface LoadStockMovementPort {
    Optional<StockMovement> findByRequestId(UUID requestId);

    StockMovementPage findByInventoryItemId(UUID inventoryItemId, int page, int size);
}
