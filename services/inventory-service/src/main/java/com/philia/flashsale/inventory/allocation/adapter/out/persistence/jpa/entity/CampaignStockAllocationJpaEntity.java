package com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.inventory.allocation.domain.model.AllocationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campaign_stock_allocations")
public class CampaignStockAllocationJpaEntity {
    @Id private UUID id;
    @Column(name = "request_id", nullable = false, unique = true) private UUID requestId;
    @Column(name = "campaign_id", nullable = false) private UUID campaignId;
    @Column(name = "inventory_item_id", nullable = false) private UUID inventoryItemId;
    @Column(name = "variant_id", nullable = false) private UUID variantId;
    @Column(name = "allocated_quantity", nullable = false) private long allocatedQuantity;
    @Column(name = "sold_quantity", nullable = false) private long soldQuantity;
    @Column(name = "returned_quantity", nullable = false) private long returnedQuantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllocationStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "reconciled_at") private Instant reconciledAt;

    protected CampaignStockAllocationJpaEntity() {
    }

    public CampaignStockAllocationJpaEntity(
            UUID id,
            UUID requestId,
            UUID campaignId,
            UUID inventoryItemId,
            UUID variantId,
            long allocatedQuantity,
            long soldQuantity,
            long returnedQuantity,
            AllocationStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant reconciledAt) {
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

    public void applyState(
            long soldQuantity,
            long returnedQuantity,
            AllocationStatus status,
            Instant updatedAt,
            Instant reconciledAt) {
        this.soldQuantity = soldQuantity;
        this.returnedQuantity = returnedQuantity;
        this.status = status;
        this.updatedAt = updatedAt;
        this.reconciledAt = reconciledAt;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getCampaignId() { return campaignId; }
    public UUID getInventoryItemId() { return inventoryItemId; }
    public UUID getVariantId() { return variantId; }
    public long getAllocatedQuantity() { return allocatedQuantity; }
    public long getSoldQuantity() { return soldQuantity; }
    public long getReturnedQuantity() { return returnedQuantity; }
    public AllocationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getReconciledAt() { return reconciledAt; }
}
