package com.philia.flashsale.payment.payment.application.model.query;

import com.philia.flashsale.payment.payment.domain.model.FailureReason;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Provider-independent Payment read model returned by the application core. */
public record PaymentDetailsResult(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant paymentDeadline,
        int attemptsUsed,
        FailureReason failureReason,
        Instant createdAt,
        Instant updatedAt) {
    public PaymentDetailsResult {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(paymentDeadline, "paymentDeadline");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (attemptsUsed < 0) {
            throw new IllegalArgumentException("attemptsUsed must not be negative");
        }
    }
}
