package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "regular_stock_hold_items")
public class RegularStockHoldItemJpaEntity {
    @Id private UUID id;
    @Column(name = "hold_id", nullable = false) private UUID holdId;
    @Column(name = "inventory_item_id", nullable = false) private UUID inventoryItemId;
    @Column(name = "variant_id", nullable = false) private UUID variantId;
    @Column(nullable = false) private long quantity;
    @Column(name = "sku_snapshot", nullable = false, length = 100) private String skuSnapshot;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected RegularStockHoldItemJpaEntity() { }

    public RegularStockHoldItemJpaEntity(UUID id, UUID holdId, UUID inventoryItemId, UUID variantId,
            long quantity, String skuSnapshot, Instant createdAt) {
        this.id = id;
        this.holdId = holdId;
        this.inventoryItemId = inventoryItemId;
        this.variantId = variantId;
        this.quantity = quantity;
        this.skuSnapshot = skuSnapshot;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getHoldId() { return holdId; }
    public UUID getInventoryItemId() { return inventoryItemId; }
    public UUID getVariantId() { return variantId; }
    public long getQuantity() { return quantity; }
    public String getSkuSnapshot() { return skuSnapshot; }
    public Instant getCreatedAt() { return createdAt; }
}
