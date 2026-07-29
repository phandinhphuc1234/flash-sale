package com.philia.flashsale.inventory.stock.application.port.in;

import com.philia.flashsale.inventory.stock.application.command.InitializeInventoryCommand;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;

public interface InitializeInventoryUseCase {
    InventoryResult initialize(InitializeInventoryCommand command);
}
