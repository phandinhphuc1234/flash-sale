package com.philia.flashsale.inventory.movement.application.usecase;

import com.philia.flashsale.inventory.movement.application.port.in.ListStockMovementsUseCase;
import com.philia.flashsale.inventory.movement.application.port.out.LoadStockMovementPort;
import com.philia.flashsale.inventory.movement.application.query.ListStockMovementsQuery;
import com.philia.flashsale.inventory.movement.application.result.StockMovementPage;
import com.philia.flashsale.inventory.stock.application.port.in.GetInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.query.GetInventoryQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads immutable movement history through framework-neutral application contracts. */
@Service
public class MovementQueryService implements ListStockMovementsUseCase {
    private final GetInventoryUseCase getInventory;
    private final LoadStockMovementPort loadStockMovement;

    public MovementQueryService(
            GetInventoryUseCase getInventory,
            LoadStockMovementPort loadStockMovement) {
        this.getInventory = getInventory;
        this.loadStockMovement = loadStockMovement;
    }

    @Override
    @Transactional(readOnly = true)
    public StockMovementPage list(ListStockMovementsQuery query) {
        var inventory = getInventory.get(new GetInventoryQuery(query.variantId()));
        return loadStockMovement.findByInventoryItemId(
                inventory.inventoryItemId(), query.page(), query.size());
    }
}
