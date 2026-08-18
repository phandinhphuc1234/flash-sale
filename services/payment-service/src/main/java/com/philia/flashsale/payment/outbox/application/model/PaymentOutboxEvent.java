package com.philia.flashsale.payment.outbox.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable application snapshot of one durable Payment result awaiting publication. */
public record PaymentOutboxEvent(
        UUID eventId,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        String topicName,
        UUID messageKey,
        String payload,
        String traceparent,
        String tracestate,
        String status,
        int attemptCount,
        Instant nextAttemptAt,
        String leaseOwner,
        Instant leaseUntil,
        Instant publishedAt,
        Instant createdAt,
        String lastErrorCode) {

    public PaymentOutboxEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        if (aggregateVersion <= 0) {
            throw new IllegalArgumentException("aggregateVersion must be positive");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (eventVersion != 1) {
            throw new IllegalArgumentException("only event version 1 is supported");
        }
        if (topicName == null || topicName.isBlank()) {
            throw new IllegalArgumentException("topicName must not be blank");
        }
        Objects.requireNonNull(messageKey, "messageKey");
        Objects.requireNonNull(payload, "payload");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
