package com.philia.flashsale.inventory.allocation.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.inventory.allocation.application.command.AllocateCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.port.out.LoadCampaignStockAllocationPort;
import com.philia.flashsale.inventory.allocation.application.port.out.RecordAllocationOutboxPort;
import com.philia.flashsale.inventory.allocation.application.port.out.SaveCampaignStockAllocationPort;
import com.philia.flashsale.inventory.allocation.domain.model.CampaignStockAllocation;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.domain.model.MovementType;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import com.philia.flashsale.inventory.regularhold.application.port.out.LoadActiveRegularHoldQuantityPort;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CampaignAllocationApplicationServiceTest {
    @Test
    void allocationPersistsStockMovementAndOutboxIntent() {
        LoadInventoryItemPort loadInventory = mock(LoadInventoryItemPort.class);
        SaveInventoryItemPort saveInventory = mock(SaveInventoryItemPort.class);
        LoadCampaignStockAllocationPort loadAllocation = mock(LoadCampaignStockAllocationPort.class);
        SaveCampaignStockAllocationPort saveAllocation = mock(SaveCampaignStockAllocationPort.class);
        RecordStockMovementPort recordMovement = mock(RecordStockMovementPort.class);
        RecordAllocationOutboxPort recordOutbox = mock(RecordAllocationOutboxPort.class);
        LoadActiveRegularHoldQuantityPort activeRegularHolds = mock(LoadActiveRegularHoldQuantityPort.class);
        CampaignAllocationApplicationService service = new CampaignAllocationApplicationService(
                loadInventory, saveInventory, loadAllocation, saveAllocation,
                recordMovement, recordOutbox, activeRegularHolds);

        UUID requestId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant now = Instant.now();
        InventoryItem item = new InventoryItem(
                UUID.randomUUID(), variantId, "SKU-1", 100, 0, 0, now, now);
        when(loadAllocation.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(loadInventory.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(item));
        when(activeRegularHolds.activeHeldQuantity(eq(item.id()), any(Instant.class))).thenReturn(0L);
        when(saveInventory.save(item)).thenReturn(item);
        when(saveAllocation.save(any(CampaignStockAllocation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(recordMovement.record(any(StockMovement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.allocate(new AllocateCampaignStockCommand(
                requestId, campaignId, variantId, 80, "campaign allocation"));

        assertEquals(80, result.allocatedQuantity());
        assertEquals(20, item.availableQuantity());
        ArgumentCaptor<StockMovement> movement = ArgumentCaptor.forClass(StockMovement.class);
        verify(recordMovement).record(movement.capture());
        assertEquals(MovementType.CAMPAIGN_ALLOCATED, movement.getValue().movementType());
        verify(recordOutbox).record(
                eq("CAMPAIGN_STOCK_ALLOCATION"),
                eq(result.id()),
                eq("CampaignStockAllocated"),
                any(String.class),
                any(Instant.class));
    }
}
