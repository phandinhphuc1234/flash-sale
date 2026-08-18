package com.philia.flashsale.payment.payment.application.model.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral create command. It contains only server-owned payment facts. */
public record HostedCheckoutCreateRequest(
        String paymentId,
        String orderId,
        String attemptId,
        BigDecimal amount,
        String currency,
        String providerIdempotencyKey,
        Instant paymentDeadline,
        String successUrl,
        String cancelUrl,
        Map<String, String> metadata) {

    public HostedCheckoutCreateRequest {
        requireText(paymentId, "paymentId");
        requireText(orderId, "orderId");
        requireText(attemptId, "attemptId");
        requireText(providerIdempotencyKey, "providerIdempotencyKey");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        requireText(currency, "currency");
        Objects.requireNonNull(paymentDeadline, "paymentDeadline");
        requireText(successUrl, "successUrl");
        requireText(cancelUrl, "cancelUrl");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
