package com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa.entity;

import com.philia.flashsale.inventory.movement.domain.model.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovementJpaEntity {
    @Id private UUID id;
    @Column(name = "inventory_item_id", nullable = false) private UUID inventoryItemId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "allocation_id") private UUID allocationId;
    @Column(name = "reference_type", length = 50) private String referenceType;
    @Column(name = "reference_id") private UUID referenceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40)
    private MovementType movementType;
    @Column(name = "on_hand_delta", nullable = false) private long onHandDelta;
    @Column(name = "allocated_delta", nullable = false) private long allocatedDelta;
    @Column(name = "on_hand_after", nullable = false) private long onHandAfter;
    @Column(name = "allocated_after", nullable = false) private long allocatedAfter;
    @Column(length = 500) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected StockMovementJpaEntity() {
    }

    public StockMovementJpaEntity(
            UUID id,
            UUID requestId,
            UUID inventoryItemId,
            UUID allocationId,
            String referenceType,
            UUID referenceId,
            MovementType movementType,
            long onHandDelta,
            long allocatedDelta,
            long onHandAfter,
            long allocatedAfter,
            String reason,
            Instant createdAt) {
        this.id = id;
        this.requestId = requestId;
        this.inventoryItemId = inventoryItemId;
        this.allocationId = allocationId;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.movementType = movementType;
        this.onHandDelta = onHandDelta;
        this.allocatedDelta = allocatedDelta;
        this.onHandAfter = onHandAfter;
        this.allocatedAfter = allocatedAfter;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public UUID getInventoryItemId() { return inventoryItemId; }
    public UUID getAllocationId() { return allocationId; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public MovementType getMovementType() { return movementType; }
    public long getOnHandDelta() { return onHandDelta; }
    public long getAllocatedDelta() { return allocatedDelta; }
    public long getOnHandAfter() { return onHandAfter; }
    public long getAllocatedAfter() { return allocatedAfter; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
