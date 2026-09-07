package com.philia.flashsale.inventory.regularhold.application.model;

import java.time.Instant;
import java.util.UUID;

/** Infrastructure-neutral leased fact that remains owned by Inventory's regular-hold capability. */
public record RegularHoldOutboxEvent(
        UUID eventId,
        String eventType,
        long aggregateVersion,
        UUID holdId,
        String eventKey,
        UUID correlationId,
        UUID causationId,
        String traceparent,
        String tracestate,
        String payload
) {
}
