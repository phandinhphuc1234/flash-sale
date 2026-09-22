package com.philia.flashsale.inventory.stock.application.usecase;

import com.philia.flashsale.inventory.stock.application.port.in.ListInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.port.out.ListInventoryPort;
import com.philia.flashsale.inventory.stock.application.query.ListInventoryQuery;
import com.philia.flashsale.inventory.stock.application.result.InventoryPageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only inventory collection use case kept separate from stock command orchestration. */
@Service
public class InventoryQueryService implements ListInventoryUseCase {
    private final ListInventoryPort listInventory;

    public InventoryQueryService(ListInventoryPort listInventory) {
        this.listInventory = listInventory;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryPageResult list(ListInventoryQuery query) {
        return listInventory.list(query);
    }
}
