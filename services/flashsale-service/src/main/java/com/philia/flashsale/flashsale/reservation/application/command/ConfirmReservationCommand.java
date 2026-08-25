package com.philia.flashsale.flashsale.reservation.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Framework-free command produced by the Order-owned Purchase Saga. */
public record ConfirmReservationCommand(
        UUID commandId,
        String commandType,
        int commandVersion,
        String producer,
        String aggregateType,
        UUID sagaId,
        long aggregateVersion,
        UUID correlationId,
        UUID causationId,
        Instant occurredAt,
        UUID orderId,
        UUID purchaseRequestId,
        UUID reservationId,
        UUID paymentId,
        Instant paidAt,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate,
        String payloadFingerprint) {

    public ConfirmReservationCommand {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(commandType, "commandType");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(payloadFingerprint, "payloadFingerprint");
        if (commandVersion != 1 || aggregateVersion <= 0 || sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("unsupported command metadata");
        }
        if (!payloadFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadFingerprint must be SHA-256 hex");
        }
    }
}
