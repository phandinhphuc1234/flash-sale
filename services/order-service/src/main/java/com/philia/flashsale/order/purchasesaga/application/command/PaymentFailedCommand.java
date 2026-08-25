package com.philia.flashsale.order.purchasesaga.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Framework-free terminal PaymentFailed fact accepted by the Order Saga boundary. */
public record PaymentFailedCommand(
        UUID eventId, String eventType, int eventVersion, String producer, String aggregateType,
        UUID aggregateId, long aggregateVersion, UUID correlationId, UUID causationId, Instant occurredAt,
        UUID paymentId, UUID orderId, BigDecimal amount, String currency, Instant failedAt,
        String reason, String provider, String providerSessionId, String sourceTopic, int sourcePartition,
        long sourceOffset, String traceparent, String tracestate, String fingerprint) {

    public static final Set<String> TERMINAL_REASONS = Set.of(
            "PAYMENT_DEADLINE_EXPIRED", "CHECKOUT_ATTEMPT_LIMIT_REACHED", "PROVIDER_TERMINAL_FAILURE");

    public PaymentFailedCommand {
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
        Objects.requireNonNull(failedAt, "failedAt");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (eventVersion != 1 || aggregateVersion <= 0 || sourcePartition < 0 || sourceOffset < 0) {
            throw new IllegalArgumentException("unsupported PaymentFailed metadata");
        }
        if (!TERMINAL_REASONS.contains(reason)) throw new IllegalArgumentException("unsupported failure reason");
        if (!currency.matches("[A-Z]{3}") || amount.signum() <= 0 || amount.scale() != 4) {
            throw new IllegalArgumentException("invalid PaymentFailed amount/currency");
        }
        if (!fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("fingerprint must be SHA-256 hex");
    }
}
