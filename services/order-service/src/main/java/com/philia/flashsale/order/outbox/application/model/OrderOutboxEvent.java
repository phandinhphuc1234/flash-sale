package com.philia.flashsale.order.outbox.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Framework-free immutable snapshot of an Order-created publication intent. */
public record OrderOutboxEvent(
        UUID eventId,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        String eventKey,
        UUID correlationId,
        UUID causationId,
        String payload,
        String traceparent,
        String tracestate,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        String claimedBy,
        Instant claimUntil,
        Instant publishedAt,
        String lastError,
        Instant occurredAt,
        Instant createdAt,
        Instant updatedAt) {

    public OrderOutboxEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(eventKey, "eventKey");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (payload.isBlank()) {
            throw new IllegalArgumentException("outbox payload must not be blank");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
    }
}
