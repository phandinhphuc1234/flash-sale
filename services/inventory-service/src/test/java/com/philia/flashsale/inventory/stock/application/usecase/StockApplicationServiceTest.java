package com.philia.flashsale.inventory.stock.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.movement.application.port.out.LoadStockMovementPort;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.domain.model.MovementType;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import com.philia.flashsale.inventory.stock.application.command.AdjustStockCommand;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import com.philia.flashsale.inventory.stock.domain.model.StockAdjustmentType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StockApplicationServiceTest {
    @Test
    void increaseStockPersistsTheAggregateAndRecordsOneMovement() {
        LoadInventoryItemPort loadInventory = mock(LoadInventoryItemPort.class);
        SaveInventoryItemPort saveInventory = mock(SaveInventoryItemPort.class);
        LoadStockMovementPort loadMovement = mock(LoadStockMovementPort.class);
        RecordStockMovementPort recordMovement = mock(RecordStockMovementPort.class);
        StockApplicationService service = new StockApplicationService(
                loadInventory, saveInventory, loadMovement, recordMovement);

        UUID variantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        InventoryItem item = new InventoryItem(
                UUID.randomUUID(), variantId, "SKU-1", 10, 0, 0, now, now);
        when(loadMovement.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(loadInventory.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(item));
        when(saveInventory.save(item)).thenReturn(item);
        when(recordMovement.record(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.adjust(new AdjustStockCommand(
                requestId, variantId, StockAdjustmentType.INCREASE, 5, "restock"));

        assertEquals(15, result.onHandQuantity());
        verify(saveInventory).save(item);
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(recordMovement).record(movement.capture());
        assertEquals(MovementType.STOCK_ADJUSTED_UP, movement.getValue().movementType());
        assertEquals(5, movement.getValue().onHandDelta());
        assertEquals(15, movement.getValue().onHandAfter());
    }
}
