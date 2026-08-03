package com.philia.flashsale.inventory.stock.domain.model;

import com.philia.flashsale.inventory.stock.domain.exception.InventoryDomainException;
import com.philia.flashsale.inventory.stock.domain.exception.InsufficientStockException;
import java.time.Instant;
import java.util.UUID;

/** Aggregate protecting physical and campaign-allocated stock invariants. */
public final class InventoryItem {
    private final UUID id;
    private final UUID variantId;
    private final String skuSnapshot;
    private long onHandQuantity;
    private long campaignAllocatedQuantity;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public InventoryItem(UUID id, UUID variantId, String skuSnapshot, long onHandQuantity,
                         long campaignAllocatedQuantity, long version, Instant createdAt,
                         Instant updatedAt) {
        if (onHandQuantity < 0 || campaignAllocatedQuantity < 0
                || campaignAllocatedQuantity > onHandQuantity) {
            throw new InventoryDomainException("Invalid inventory balance");
        }
        this.id = id;
        this.variantId = variantId;
        this.skuSnapshot = skuSnapshot;
        this.onHandQuantity = onHandQuantity;
        this.campaignAllocatedQuantity = campaignAllocatedQuantity;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static InventoryItem initialize(UUID id, UUID variantId, String skuSnapshot, Instant now) {
        return new InventoryItem(id, variantId, skuSnapshot, 0, 0, 0, now, now);
    }

    public long availableQuantity() {
        return onHandQuantity - campaignAllocatedQuantity;
    }

    public void increaseStock(long quantity, Instant now) {
        requirePositive(quantity);
        onHandQuantity = Math.addExact(onHandQuantity, quantity);
        touch(now);
    }

    public void decreaseStock(long quantity, Instant now) {
        requirePositive(quantity);
        long next = onHandQuantity - quantity;
        if (next < campaignAllocatedQuantity) {
            throw new InventoryDomainException("Stock decrease cannot fall below allocated quantity");
        }
        onHandQuantity = next;
        touch(now);
    }

    public void allocate(long quantity, Instant now) {
        requirePositive(quantity);
        if (quantity > availableQuantity()) {
            throw new InsufficientStockException();
        }
        campaignAllocatedQuantity = Math.addExact(campaignAllocatedQuantity, quantity);
        touch(now);
    }

    public void releaseAllocation(long quantity, Instant now) {
        requirePositive(quantity);
        if (quantity > campaignAllocatedQuantity) {
            throw new InventoryDomainException("Allocated stock cannot become negative");
        }
        campaignAllocatedQuantity -= quantity;
        touch(now);
    }

    public void reconcile(long soldQuantity, long allocatedQuantity, Instant now) {
        requireNonNegative(soldQuantity);
        requirePositive(allocatedQuantity);
        if (soldQuantity > allocatedQuantity || allocatedQuantity > campaignAllocatedQuantity) {
            throw new InventoryDomainException("Invalid reconciliation quantities");
        }
        onHandQuantity -= soldQuantity;
        campaignAllocatedQuantity -= allocatedQuantity;
        touch(now);
    }

    private void requirePositive(long quantity) {
        if (quantity <= 0) {
            throw new InventoryDomainException("Quantity must be positive");
        }
    }

    private void requireNonNegative(long quantity) {
        if (quantity < 0) {
            throw new InventoryDomainException("Quantity must not be negative");
        }
    }

    private void touch(Instant now) {
        updatedAt = now;
        version++;
    }

    public UUID id() { return id; }
    public UUID variantId() { return variantId; }
    public String skuSnapshot() { return skuSnapshot; }
    public long onHandQuantity() { return onHandQuantity; }
    public long campaignAllocatedQuantity() { return campaignAllocatedQuantity; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
