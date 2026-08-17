package com.philia.flashsale.payment.payment.domain.model;

/** Lifecycle of one externally initiated hosted Checkout workflow. */
public enum PaymentAttemptStatus {
    CREATING,
    OPEN,
    PROCESSING,
    UNKNOWN,
    SUCCEEDED,
    FAILED,
    EXPIRED;

    public boolean unresolved() {
        return this == CREATING || this == OPEN || this == PROCESSING || this == UNKNOWN;
    }

    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == EXPIRED;
    }
}
