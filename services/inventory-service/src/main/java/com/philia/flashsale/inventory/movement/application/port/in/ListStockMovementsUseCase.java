package com.philia.flashsale.inventory.movement.application.port.in;

import com.philia.flashsale.inventory.movement.application.query.ListStockMovementsQuery;
import com.philia.flashsale.inventory.movement.application.result.StockMovementPage;

public interface ListStockMovementsUseCase {
    StockMovementPage list(ListStockMovementsQuery query);
}
