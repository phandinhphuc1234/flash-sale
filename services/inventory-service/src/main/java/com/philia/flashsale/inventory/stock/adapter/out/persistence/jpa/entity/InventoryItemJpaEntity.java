package com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
public class InventoryItemJpaEntity {
    @Id
    private UUID id;
    @Column(name = "variant_id", nullable = false, unique = true)
    private UUID variantId;
    @Column(name = "sku_snapshot", nullable = false, length = 100)
    private String skuSnapshot;
    @Column(name = "on_hand_quantity", nullable = false)
    private long onHandQuantity;
    @Column(name = "campaign_allocated_quantity", nullable = false)
    private long campaignAllocatedQuantity;
    @Column(nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryItemJpaEntity() {
    }

    public InventoryItemJpaEntity(
            UUID id,
            UUID variantId,
            String skuSnapshot,
            long onHandQuantity,
            long campaignAllocatedQuantity,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.variantId = variantId;
        this.skuSnapshot = skuSnapshot;
        this.onHandQuantity = onHandQuantity;
        this.campaignAllocatedQuantity = campaignAllocatedQuantity;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void applyState(
            String skuSnapshot,
            long onHandQuantity,
            long campaignAllocatedQuantity,
            long version,
            Instant updatedAt) {
        this.skuSnapshot = skuSnapshot;
        this.onHandQuantity = onHandQuantity;
        this.campaignAllocatedQuantity = campaignAllocatedQuantity;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getVariantId() { return variantId; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public long getOnHandQuantity() { return onHandQuantity; }
    public long getCampaignAllocatedQuantity() { return campaignAllocatedQuantity; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
