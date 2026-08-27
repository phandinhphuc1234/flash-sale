package com.philia.flashsale.order.purchasesaga.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Framework-free Flash Sale confirmation fact accepted by the Order Saga boundary. */
public record PurchaseReservationConfirmedCommand(
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
        UUID reservationId,
        UUID paymentId,
        Instant confirmedAt,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate,
        String fingerprint) {

    public PurchaseReservationConfirmedCommand {
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
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (eventVersion != 1 || aggregateVersion <= 0 || sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("invalid PurchaseReservationConfirmed envelope");
        }
        if (!sagaId.equals(purchaseRequestId) || !sagaId.equals(correlationId)) {
            throw new IllegalArgumentException("Saga identity is inconsistent");
        }
    }
}
