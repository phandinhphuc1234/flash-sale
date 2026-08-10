package com.philia.flashsale.inventory.allocation.application.usecase;

import com.philia.flashsale.inventory.allocation.application.command.AllocateCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.command.ReconcileCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.command.ReleaseCampaignStockCommand;
import com.philia.flashsale.inventory.allocation.application.exception.AllocationApplicationException;
import com.philia.flashsale.inventory.allocation.application.port.in.AllocateCampaignStockUseCase;
import com.philia.flashsale.inventory.allocation.application.port.in.ReconcileCampaignStockUseCase;
import com.philia.flashsale.inventory.allocation.application.port.in.ReleaseCampaignStockUseCase;
import com.philia.flashsale.inventory.allocation.application.port.out.LoadCampaignStockAllocationPort;
import com.philia.flashsale.inventory.allocation.application.port.out.RecordAllocationOutboxPort;
import com.philia.flashsale.inventory.allocation.application.port.out.SaveCampaignStockAllocationPort;
import com.philia.flashsale.inventory.allocation.application.result.CampaignStockAllocationResult;
import com.philia.flashsale.inventory.allocation.domain.model.AllocationStatus;
import com.philia.flashsale.inventory.allocation.domain.model.CampaignStockAllocation;
import com.philia.flashsale.inventory.allocation.domain.exception.AllocationRequestConflictException;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.domain.model.MovementType;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates campaign allocation, release, and reconciliation in one local transaction. */
@Service
public class CampaignAllocationApplicationService implements
        AllocateCampaignStockUseCase,
        ReleaseCampaignStockUseCase,
        ReconcileCampaignStockUseCase {

    private final LoadInventoryItemPort loadInventoryItem;
    private final SaveInventoryItemPort saveInventoryItem;
    private final LoadCampaignStockAllocationPort loadAllocation;
    private final SaveCampaignStockAllocationPort saveAllocation;
    private final RecordStockMovementPort recordStockMovement;
    private final RecordAllocationOutboxPort outboxRecorder;

    public CampaignAllocationApplicationService(
            LoadInventoryItemPort loadInventoryItem,
            SaveInventoryItemPort saveInventoryItem,
            LoadCampaignStockAllocationPort loadAllocation,
            SaveCampaignStockAllocationPort saveAllocation,
            RecordStockMovementPort recordStockMovement,
            RecordAllocationOutboxPort outboxRecorder) {
        this.loadInventoryItem = loadInventoryItem;
        this.saveInventoryItem = saveInventoryItem;
        this.loadAllocation = loadAllocation;
        this.saveAllocation = saveAllocation;
        this.recordStockMovement = recordStockMovement;
        this.outboxRecorder = outboxRecorder;
    }

    @Override
    @Transactional
    public CampaignStockAllocationResult allocate(AllocateCampaignStockCommand command) {
        var existing = loadAllocation.findByRequestId(command.requestId());
        if (existing.isPresent()) {
            return replayCompatibleAllocation(command, existing.get());
        }
        InventoryItem item = loadInventoryItem.findByVariantIdForUpdate(command.variantId())
                .orElseThrow(() -> AllocationApplicationException.notFound("Inventory item not found"));

        // A concurrent request may have committed while this transaction waited for the stock row.
        // Rechecking under that lock turns the stable request ID into a concurrency-safe replay.
        existing = loadAllocation.findByRequestId(command.requestId());
        if (existing.isPresent()) {
            return replayCompatibleAllocation(command, existing.get());
        }

        Instant now = Instant.now();
        item.allocate(command.quantity(), now);
        InventoryItem saved = saveInventoryItem.save(item);
        CampaignStockAllocation allocation = CampaignStockAllocation.active(
                UUID.randomUUID(), command.requestId(), command.campaignId(), item.id(),
                command.variantId(), command.quantity(), now);
        CampaignStockAllocation result = saveAllocation.save(allocation);
        recordMovement(saved, command.requestId(), result.id(), MovementType.CAMPAIGN_ALLOCATED,
                0, command.quantity(), command.reason(), now);
        recordOutbox(result.id(), "CampaignStockAllocated",
                "{\"requestId\":\"" + command.requestId() + "\",\"campaignId\":\""
                        + command.campaignId() + "\",\"variantId\":\"" + command.variantId()
                        + "\",\"quantity\":" + command.quantity() + "}", now);
        return CampaignStockAllocationResult.from(result);
    }

    private CampaignStockAllocationResult replayCompatibleAllocation(
            AllocateCampaignStockCommand command,
            CampaignStockAllocation prior) {
        if (!prior.campaignId().equals(command.campaignId())
                || !prior.variantId().equals(command.variantId())
                || prior.allocatedQuantity() != command.quantity()) {
            throw new AllocationRequestConflictException();
        }
        return CampaignStockAllocationResult.from(prior);
    }

    @Override
    @Transactional
    public CampaignStockAllocationResult release(ReleaseCampaignStockCommand command) {
        CampaignStockAllocation allocation = loadAllocation.findByRequestId(command.requestId())
                .orElseThrow(() -> AllocationApplicationException.notFound("Allocation not found"));
        if (allocation.status() == AllocationStatus.RELEASED) {
            return CampaignStockAllocationResult.from(allocation);
        }
        InventoryItem item = loadInventoryItem.findByVariantIdForUpdate(allocation.variantId())
                .orElseThrow(() -> AllocationApplicationException.notFound("Inventory item not found"));
        Instant now = Instant.now();
        allocation.release(now);
        item.releaseAllocation(allocation.allocatedQuantity(), now);
        InventoryItem saved = saveInventoryItem.save(item);
        CampaignStockAllocation result = saveAllocation.save(allocation);
        recordMovement(saved, derivedMovementRequestId("release", command.requestId()), result.id(),
                MovementType.CAMPAIGN_RELEASED, 0, -result.allocatedQuantity(), command.reason(), now);
        recordOutbox(result.id(), "CampaignStockReleased",
                "{\"requestId\":\"" + command.requestId() + "\"}", now);
        return CampaignStockAllocationResult.from(result);
    }

    @Override
    @Transactional
    public CampaignStockAllocationResult reconcile(ReconcileCampaignStockCommand command) {
        CampaignStockAllocation allocation = loadAllocation.findByRequestId(command.requestId())
                .orElseThrow(() -> AllocationApplicationException.notFound("Allocation not found"));
        if (allocation.status() == AllocationStatus.RECONCILED) {
            return CampaignStockAllocationResult.from(allocation);
        }
        InventoryItem item = loadInventoryItem.findByVariantIdForUpdate(allocation.variantId())
                .orElseThrow(() -> AllocationApplicationException.notFound("Inventory item not found"));
        Instant now = Instant.now();
        allocation.reconcile(command.soldQuantity(), command.returnedQuantity(), now);
        item.reconcile(command.soldQuantity(), allocation.allocatedQuantity(), now);
        InventoryItem saved = saveInventoryItem.save(item);
        CampaignStockAllocation result = saveAllocation.save(allocation);
        recordMovement(saved, derivedMovementRequestId("reconcile", command.requestId()), result.id(),
                MovementType.CAMPAIGN_RECONCILED, -command.soldQuantity(),
                -allocation.allocatedQuantity(), command.reason(), now);
        recordOutbox(result.id(), "CampaignStockReconciled",
                "{\"requestId\":\"" + command.requestId() + "\",\"sold\":"
                        + command.soldQuantity() + ",\"returned\":"
                        + command.returnedQuantity() + "}", now);
        return CampaignStockAllocationResult.from(result);
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

    private void recordOutbox(UUID aggregateId, String type, String payload, Instant now) {
        outboxRecorder.record("CAMPAIGN_STOCK_ALLOCATION", aggregateId, type, payload, now);
    }

    private UUID derivedMovementRequestId(String operation, UUID requestId) {
        return UUID.nameUUIDFromBytes(
                (operation + ":" + requestId).getBytes(StandardCharsets.UTF_8));
    }
}
