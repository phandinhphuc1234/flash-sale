package com.philia.flashsale.flashsale.outbox.application.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Framework-free snapshot of a durable publication intent. */
public record OutboxEvent(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        Map<String, Object> payload,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        String claimedBy,
        Instant claimUntil,
        Instant publishedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public OutboxEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        payload = Map.copyOf(payload);
    }
}
