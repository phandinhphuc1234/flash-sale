package com.philia.flashsale.order.purchasesaga.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Framework-free Flash Sale release result accepted by the Order Saga boundary. */
public record PurchaseReservationReleasedCommand(
        UUID eventId, String eventType, int eventVersion, String producer, String aggregateType,
        UUID aggregateId, long aggregateVersion, UUID correlationId, UUID causationId, Instant occurredAt,
        UUID sagaId, UUID orderId, UUID purchaseRequestId, UUID reservationId, String reservationStatus,
        String reason, Instant releasedAt, String sourceTopic, int sourcePartition, long sourceOffset,
        String traceparent, String tracestate, String fingerprint) {
    public static final Set<String> STATUSES = Set.of("RELEASED", "EXPIRED");
    public PurchaseReservationReleasedCommand {
        Objects.requireNonNull(eventId, "eventId"); Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producer, "producer"); Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId"); Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId"); Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(sagaId, "sagaId"); Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId"); Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(reservationStatus, "reservationStatus"); Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(releasedAt, "releasedAt"); Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (eventVersion != 1 || aggregateVersion <= 0 || sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("invalid PurchaseReservationReleased envelope");
        }
        if (!STATUSES.contains(reservationStatus) || !sagaId.equals(purchaseRequestId)
                || !sagaId.equals(correlationId)) throw new IllegalArgumentException("release identity is inconsistent");
    }
}
