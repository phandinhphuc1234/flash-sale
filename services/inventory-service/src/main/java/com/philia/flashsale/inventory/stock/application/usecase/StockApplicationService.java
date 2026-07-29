package com.philia.flashsale.inventory.stock.application.usecase;

import com.philia.flashsale.inventory.movement.application.port.out.LoadStockMovementPort;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.domain.model.MovementType;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import com.philia.flashsale.inventory.stock.application.command.AdjustStockCommand;
import com.philia.flashsale.inventory.stock.application.command.InitializeInventoryCommand;
import com.philia.flashsale.inventory.stock.application.exception.StockApplicationException;
import com.philia.flashsale.inventory.stock.application.port.in.AdjustStockUseCase;
import com.philia.flashsale.inventory.stock.application.port.in.GetInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.port.in.InitializeInventoryUseCase;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.query.GetInventoryQuery;
import com.philia.flashsale.inventory.stock.application.result.InventoryResult;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import com.philia.flashsale.inventory.stock.domain.model.StockAdjustmentType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates inventory initialization, adjustment, and read use cases. */
@Service
public class StockApplicationService
        implements InitializeInventoryUseCase, AdjustStockUseCase, GetInventoryUseCase {

    private final LoadInventoryItemPort loadInventoryItem;
    private final SaveInventoryItemPort saveInventoryItem;
    private final LoadStockMovementPort loadStockMovement;
    private final RecordStockMovementPort recordStockMovement;

    public StockApplicationService(
            LoadInventoryItemPort loadInventoryItem,
            SaveInventoryItemPort saveInventoryItem,
            LoadStockMovementPort loadStockMovement,
            RecordStockMovementPort recordStockMovement) {
        this.loadInventoryItem = loadInventoryItem;
        this.saveInventoryItem = saveInventoryItem;
        this.loadStockMovement = loadStockMovement;
        this.recordStockMovement = recordStockMovement;
    }

    @Override
    @Transactional
    public InventoryResult initialize(InitializeInventoryCommand command) {
        if (loadInventoryItem.findByVariantIdForUpdate(command.variantId()).isPresent()) {
            throw new StockApplicationException("Inventory already initialized");
        }
        Instant now = Instant.now();
        InventoryItem item = InventoryItem.initialize(
                UUID.randomUUID(), command.variantId(), command.skuSnapshot(), now);
        if (command.quantity() > 0) {
            item.increaseStock(command.quantity(), now);
        }
        InventoryItem saved = saveInventoryItem.save(item);
        if (command.quantity() > 0) {
            recordMovement(saved, UUID.randomUUID(), null, MovementType.STOCK_IN,
                    command.quantity(), 0, command.reason(), now);
        }
        return InventoryResult.from(saved);
    }

    @Override
    @Transactional
    public InventoryResult adjust(AdjustStockCommand command) {
        var previous = loadStockMovement.findByRequestId(command.requestId());
        InventoryItem item = loadInventoryItem.findByVariantIdForUpdate(command.variantId())
                .orElseThrow(() -> new StockApplicationException("Inventory item not found"));
        if (previous.isPresent()) {
            if (!previous.get().inventoryItemId().equals(item.id())) {
                throw new StockApplicationException(
                        "Request ID was already used for another inventory item");
            }
            return InventoryResult.from(item);
        }
        if (command.quantity() <= 0) {
            throw new StockApplicationException("Adjustment quantity must be positive");
        }
        long delta = command.type() == StockAdjustmentType.INCREASE
                ? command.quantity() : -command.quantity();
        Instant now = Instant.now();
        if (delta > 0) {
            item.increaseStock(delta, now);
        } else {
            item.decreaseStock(Math.abs(delta), now);
        }
        InventoryItem saved = saveInventoryItem.save(item);
        recordMovement(saved, command.requestId(), null,
                delta > 0 ? MovementType.STOCK_ADJUSTED_UP : MovementType.STOCK_ADJUSTED_DOWN,
                delta, 0, command.reason(), now);
        return InventoryResult.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryResult get(GetInventoryQuery query) {
        InventoryItem item = loadInventoryItem.findByVariantId(query.variantId())
                .orElseThrow(() -> new StockApplicationException("Inventory item not found"));
        return InventoryResult.from(item);
    }

    private void recordMovement(
            InventoryItem item,
            UUID requestId,
            UUID allocationId,
            MovementType type,
            long onHandDelta,
            long allocatedDelta,
            String reason,
            Instant now) {
        recordStockMovement.record(new StockMovement(
                UUID.randomUUID(), requestId, item.id(), allocationId,
                "INVENTORY_ITEM", item.id(), type, onHandDelta, allocatedDelta,
                item.onHandQuantity(), item.campaignAllocatedQuantity(), reason, now));
    }
}
