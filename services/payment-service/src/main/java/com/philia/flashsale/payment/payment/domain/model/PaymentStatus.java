package com.philia.flashsale.payment.payment.domain.model;

/** Payment aggregate lifecycle approved by Feature 021. */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    UNKNOWN,
    SUCCEEDED,
    FAILED,
    EXPIRED
}
