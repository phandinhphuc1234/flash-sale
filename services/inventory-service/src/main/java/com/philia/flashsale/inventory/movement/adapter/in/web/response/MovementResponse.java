package com.philia.flashsale.inventory.movement.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

/** HTTP representation of one immutable inventory movement. */
public record MovementResponse(
        UUID id,
        UUID requestId,
        UUID allocationId,
        String type,
        long onHandDelta,
        long allocatedDelta,
        long onHandAfter,
        long allocatedAfter,
        String reason,
        Instant createdAt
) {
}
