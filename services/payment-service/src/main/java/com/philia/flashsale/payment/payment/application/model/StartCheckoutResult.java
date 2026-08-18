package com.philia.flashsale.payment.payment.application.model;

import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/** Safe owner-facing result; the URL is intentionally present only in this ephemeral result. */
public record StartCheckoutResult(Outcome outcome, UUID paymentId, PaymentStatus paymentStatus,
        String checkoutUrl, Instant paymentDeadline) {
    public enum Outcome {
        CREATED,
        REPLAYED,
        RECOVERING
    }

    public boolean hasCheckoutUrl() {
        return checkoutUrl != null && !checkoutUrl.isBlank();
    }
}
