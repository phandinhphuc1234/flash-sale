package com.philia.flashsale.payment.payment.application.model.recovery;

import java.time.Instant;
import java.util.UUID;

/** Sanitized result used by scheduled drivers and operational metrics. */
public record ReconcilePaymentResult(
        UUID workId,
        UUID paymentId,
        UUID attemptId,
        Outcome outcome,
        String reason,
        Instant observedAt) {

    public enum Outcome {
        CONVERGED,
        DEFERRED,
        MANUAL_REVIEW,
        NO_OP
    }
}
