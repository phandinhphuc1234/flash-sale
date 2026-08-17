package com.philia.flashsale.payment.payment.domain.model;

/** Business-terminal failure reasons that may be published in PaymentFailed.v1. */
public enum FailureReason {
    PAYMENT_DEADLINE_EXPIRED,
    CHECKOUT_ATTEMPT_LIMIT_REACHED,
    PROVIDER_TERMINAL_FAILURE
}
