package com.philia.flashsale.payment.payment.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Application-owned representation of the trusted PaymentRequested.v1 business command. */
public record AcceptPaymentRequestCommand(
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
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        Instant paymentDeadline,
        String traceparent,
        String tracestate) {

    public AcceptPaymentRequestCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(paymentDeadline, "paymentDeadline");
    }
}
