package com.philia.flashsale.payment.payment.adapter.in.web.response;

import com.philia.flashsale.payment.payment.domain.model.FailureReason;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public owner Payment view; provider identifiers and Checkout URLs are absent by design. */
public record PaymentDetailsResponse(
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
}
