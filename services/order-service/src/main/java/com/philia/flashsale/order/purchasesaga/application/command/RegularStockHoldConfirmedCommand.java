package com.philia.flashsale.order.purchasesaga.application.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Framework-free Inventory confirmation fact accepted by the regular Order Saga boundary. */
public record RegularStockHoldConfirmedCommand(
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
        UUID sagaId,
        UUID orderId,
        UUID purchaseRequestId,
        UUID holdId,
        UUID paymentId,
        Instant transitionedAt,
        List<RegularStockHoldConfirmedLine> items,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate,
        String fingerprint) {

    public RegularStockHoldConfirmedCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(transitionedAt, "transitionedAt");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(fingerprint, "fingerprint");
        items = List.copyOf(items);
        if (eventVersion != 1 || aggregateVersion < 1 || sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("invalid RegularStockHoldConfirmed envelope");
        }
        if (items.isEmpty() || items.size() > 20
                || items.stream().map(RegularStockHoldConfirmedLine::variantId).distinct().count() != items.size()) {
            throw new IllegalArgumentException("regular hold confirmation lines must be distinct and contain one through 20 items");
        }
        if (!sagaId.equals(purchaseRequestId) || !sagaId.equals(correlationId)) {
            throw new IllegalArgumentException("regular hold confirmation Saga identity is invalid");
        }
    }
}
