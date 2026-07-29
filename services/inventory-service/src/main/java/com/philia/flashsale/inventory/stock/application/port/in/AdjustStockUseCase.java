package com.philia.flashsale.inventory.stock.application.port.in;

import com.philia.flashsale.inventory.stock.application.command.AdjustStockCommand;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;

public interface AdjustStockUseCase {
    InventoryResult adjust(AdjustStockCommand command);
}
