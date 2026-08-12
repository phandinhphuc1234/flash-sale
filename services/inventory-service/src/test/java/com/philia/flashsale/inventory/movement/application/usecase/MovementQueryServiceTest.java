package com.philia.flashsale.inventory.movement.application.usecase;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.movement.application.port.out.LoadStockMovementPort;
import com.philia.flashsale.inventory.movement.application.query.ListStockMovementsQuery;
import com.philia.flashsale.inventory.movement.application.result.StockMovementPage;
import com.philia.flashsale.inventory.stock.application.port.in.GetInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.query.GetInventoryQuery;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovementQueryServiceTest {
    @Test
    void resolvesInventoryThenLoadsAFrameworkNeutralPage() {
        GetInventoryUseCase getInventory = mock(GetInventoryUseCase.class);
        LoadStockMovementPort loadMovement = mock(LoadStockMovementPort.class);
        MovementQueryService service = new MovementQueryService(getInventory, loadMovement);

        UUID inventoryItemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        var query = new ListStockMovementsQuery(variantId, 1, 20);
        var inventory = new InventoryResult(inventoryItemId, variantId, "SKU-1", 10, 0, 10);
        var expected = new StockMovementPage(List.of(), 1, 20, 0);
        when(getInventory.get(new GetInventoryQuery(variantId))).thenReturn(inventory);
        when(loadMovement.findByInventoryItemId(inventoryItemId, 1, 20)).thenReturn(expected);

        var result = service.list(query);

        assertSame(expected, result);
        verify(loadMovement).findByInventoryItemId(inventoryItemId, 1, 20);
    }
}
