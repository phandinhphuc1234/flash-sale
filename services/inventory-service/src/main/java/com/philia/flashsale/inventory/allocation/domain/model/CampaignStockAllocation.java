package com.philia.flashsale.inventory.allocation.domain.model;

import com.philia.flashsale.inventory.allocation.domain.exception.AllocationDomainException;
import java.time.Instant;
import java.util.UUID;

/** Aggregate representing one campaign allocation for one variant. */
public final class CampaignStockAllocation {
    private final UUID id;
    private final UUID requestId;
    private final UUID campaignId;
    private final UUID inventoryItemId;
    private final UUID variantId;
    private final long allocatedQuantity;
    private long soldQuantity;
    private long returnedQuantity;
    private AllocationStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant reconciledAt;

    public CampaignStockAllocation(UUID id, UUID requestId, UUID campaignId, UUID inventoryItemId,
                                   UUID variantId, long allocatedQuantity, long soldQuantity,
                                   long returnedQuantity, AllocationStatus status, Instant createdAt,
                                   Instant updatedAt, Instant reconciledAt) {
        this.id = id;
        this.requestId = requestId;
        this.campaignId = campaignId;
        this.inventoryItemId = inventoryItemId;
        this.variantId = variantId;
        this.allocatedQuantity = allocatedQuantity;
        this.soldQuantity = soldQuantity;
        this.returnedQuantity = returnedQuantity;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.reconciledAt = reconciledAt;
    }

    public static CampaignStockAllocation active(UUID id, UUID requestId, UUID campaignId,
                                                  UUID inventoryItemId, UUID variantId,
                                                  long quantity, Instant now) {
        if (quantity <= 0) {
            throw new AllocationDomainException("Allocation quantity must be positive");
        }
        return new CampaignStockAllocation(id, requestId, campaignId, inventoryItemId, variantId,
                quantity, 0, 0, AllocationStatus.ACTIVE, now, now, null);
    }

    public void release(Instant now) {
        requireActive();
        returnedQuantity = allocatedQuantity;
        status = AllocationStatus.RELEASED;
        updatedAt = now;
    }

    public void reconcile(long sold, long returned, Instant now) {
        requireActive();
        if (sold < 0 || returned < 0 || sold + returned != allocatedQuantity) {
            throw new AllocationDomainException("Sold plus returned must equal allocated quantity");
        }
        soldQuantity = sold;
        returnedQuantity = returned;
        status = AllocationStatus.RECONCILED;
        reconciledAt = now;
        updatedAt = now;
    }

    private void requireActive() {
        if (status != AllocationStatus.ACTIVE) {
            throw new AllocationDomainException("Allocation is not active");
        }
    }

    public UUID id() { return id; }
    public UUID requestId() { return requestId; }
    public UUID campaignId() { return campaignId; }
    public UUID inventoryItemId() { return inventoryItemId; }
    public UUID variantId() { return variantId; }
    public long allocatedQuantity() { return allocatedQuantity; }
    public long soldQuantity() { return soldQuantity; }
    public long returnedQuantity() { return returnedQuantity; }
    public AllocationStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant reconciledAt() { return reconciledAt; }
}
