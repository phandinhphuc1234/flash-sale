package com.philia.flashsale.order.order.application.model;

import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Complete atomic-persistence candidate; it contains no JPA, Kafka, or Spring types. */
public record OrderCreationCandidate(
        UUID eventId,
        String eventType,
        int eventVersion,
        String producer,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        Order order,
        UUID outboxEventId,
        String fingerprint,
        String traceparent,
        String tracestate,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        Instant createdAt,
        PurchaseSaga purchaseSaga,
        UUID paymentRequestedOutboxEventId) {

    /** Backward-compatible constructor for Order-only callers and migration tests. */
    public OrderCreationCandidate(UUID eventId, String eventType, int eventVersion, String producer,
            String aggregateType, UUID aggregateId, long aggregateVersion, UUID correlationId,
            UUID causationId, Instant occurredAt, Order order, UUID outboxEventId, String fingerprint,
            String traceparent, String tracestate, String sourceTopic, int sourcePartition,
            long sourceOffset, Instant createdAt) {
        this(eventId, eventType, eventVersion, producer, aggregateType, aggregateId, aggregateVersion,
                correlationId, causationId, occurredAt, order, outboxEventId, fingerprint, traceparent,
                tracestate, sourceTopic, sourcePartition, sourceOffset, createdAt, null, null);
    }

    public OrderCreationCandidate {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(outboxEventId, "outboxEventId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("source position must not be negative");
        }
    }
}
