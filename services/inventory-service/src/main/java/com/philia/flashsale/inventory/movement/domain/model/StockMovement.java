package com.philia.flashsale.inventory.movement.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit fact for one successful inventory mutation. */
public record StockMovement(
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
        Instant createdAt
) {
}
