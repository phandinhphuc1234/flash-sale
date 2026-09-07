package com.philia.flashsale.order.purchasesaga.application.command;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Framework-free Inventory released/expired fact accepted by the regular Order Saga boundary. */
public record RegularStockHoldOutcomeCommand(
        UUID eventId, String eventType, int eventVersion, String producer, String aggregateType,
        UUID aggregateId, long aggregateVersion, UUID correlationId, UUID causationId, Instant occurredAt,
        UUID sagaId, UUID orderId, UUID purchaseRequestId, UUID holdId, String status, String reason,
        Instant transitionedAt, List<RegularStockHoldConfirmedLine> items, String sourceTopic,
        int sourcePartition, long sourceOffset, String traceparent, String tracestate, String fingerprint) {

    public static final Set<String> STATUSES = Set.of("RELEASED", "EXPIRED");

    public RegularStockHoldOutcomeCommand {
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
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(transitionedAt, "transitionedAt");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(fingerprint, "fingerprint");
        items = List.copyOf(items);
        if (eventVersion != 1 || aggregateVersion < 1 || sourcePartition < 0 || sourceOffset < 0
                || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("invalid regular hold outcome envelope");
        }
        if (items.isEmpty() || items.size() > 20
                || items.stream().map(RegularStockHoldConfirmedLine::variantId).distinct().count() != items.size()) {
            throw new IllegalArgumentException("regular hold outcome items must be distinct and contain one through 20 items");
        }
        if (!sagaId.equals(purchaseRequestId) || !sagaId.equals(correlationId)) {
            throw new IllegalArgumentException("regular hold outcome Saga identity is invalid");
        }
        if ("RELEASED".equals(status) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("released regular hold outcome requires a reason");
        }
    }
}
