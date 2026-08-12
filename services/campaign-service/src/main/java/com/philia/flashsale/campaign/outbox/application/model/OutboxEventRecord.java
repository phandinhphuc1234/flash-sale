package com.philia.flashsale.campaign.outbox.application.model;

import java.time.Instant;
import java.util.UUID;

/** Application view of durable outbox metadata used by recovery and operator actions. */
public record OutboxEventRecord(
        UUID id,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        String eventKey,
        String payload,
        OutboxPublishStatus publishStatus,
        int retryCount,
        Instant nextAttemptAt,
        Instant occurredAt,
        Instant publishedAt,
        String lastError,
        int requeueCount,
        String requeuedBy,
        Instant requeuedAt,
        String traceId,
        Instant createdAt,
        Instant updatedAt) {
}
