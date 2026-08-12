package com.philia.flashsale.inventory.movement.application.port.out;

import com.philia.flashsale.inventory.movement.domain.model.StockMovement;

public interface RecordStockMovementPort {
    StockMovement record(StockMovement movement);
}
