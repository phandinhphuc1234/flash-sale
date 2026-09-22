package com.philia.flashsale.inventory.stock.application.port.out;

import com.philia.flashsale.inventory.stock.application.query.ListInventoryQuery;
import com.philia.flashsale.inventory.stock.application.result.InventoryPageResult;

public interface ListInventoryPort {
    InventoryPageResult list(ListInventoryQuery query);
}
