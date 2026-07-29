package com.philia.flashsale.inventory.stock.application.port.in;

import com.philia.flashsale.inventory.stock.application.query.GetInventoryQuery;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;

public interface GetInventoryUseCase {
    InventoryResult get(GetInventoryQuery query);
}
