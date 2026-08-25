package com.philia.flashsale.order.purchasesaga.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Framework-free PaymentSucceeded fact accepted by the Order Saga boundary. */
public record PaymentSucceededCommand(
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
        UUID paymentId,
        UUID orderId,
        BigDecimal amount,
        String currency,
        Instant paidAt,
        String provider,
        String providerSessionId,
        String providerPaymentIntentId,
        String sourceTopic,
        int sourcePartition,
        long sourceOffset,
        String traceparent,
        String tracestate,
        String fingerprint) {

    public PaymentSucceededCommand {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(causationId, "causationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(paidAt, "paidAt");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerSessionId, "providerSessionId");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (eventVersion != 1 || aggregateVersion <= 0 || sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("invalid PaymentSucceeded envelope");
        }
        if (amount.signum() <= 0 || currency.length() != 3) {
            throw new IllegalArgumentException("invalid PaymentSucceeded business values");
        }
    }
}
