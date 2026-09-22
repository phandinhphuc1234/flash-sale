package com.philia.flashsale.inventory.stock.application.port.in;

import com.philia.flashsale.inventory.stock.application.query.ListInventoryQuery;
import com.philia.flashsale.inventory.stock.application.result.InventoryPageResult;

public interface ListInventoryUseCase {
    InventoryPageResult list(ListInventoryQuery query);
}
