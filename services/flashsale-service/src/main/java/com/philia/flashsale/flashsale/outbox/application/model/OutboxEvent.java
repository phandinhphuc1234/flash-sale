package com.philia.flashsale.flashsale.outbox.application.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        UUID causationId,
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
        // JSONB payloads may contain JSON nulls for optional trace context such as tracestate.
        // Keep the snapshot immutable without applying Map.copyOf's stricter non-null-value rule.
        payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** Compatibility constructor for legacy accepted-event callers without causation. */
    public OutboxEvent(UUID eventId, String aggregateType, UUID aggregateId, long aggregateVersion,
            String eventType, int eventVersion, Map<String, Object> payload, String status,
            int attemptCount, Instant nextAttemptAt, String claimedBy, Instant claimUntil,
            Instant publishedAt, String lastError, Instant createdAt, Instant updatedAt) {
        this(eventId, aggregateType, aggregateId, aggregateVersion, eventType, eventVersion,
                null, payload, status, attemptCount, nextAttemptAt, claimedBy, claimUntil,
                publishedAt, lastError, createdAt, updatedAt);
    }
}
