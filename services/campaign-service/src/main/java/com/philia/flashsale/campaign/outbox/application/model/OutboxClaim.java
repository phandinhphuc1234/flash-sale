package com.philia.flashsale.campaign.outbox.application.model;

import java.time.Instant;
import java.util.UUID;

/** A leased outbox row returned to a future publisher adapter. */
public record OutboxClaim(
        UUID id,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        String eventKey,
        String payload,
        String traceId,
        Instant occurredAt,
        Instant claimedUntil) {
}
