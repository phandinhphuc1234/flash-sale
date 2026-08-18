package com.philia.flashsale.payment.payment.application.exception;

/** Stable application outcome for an owner Checkout request. */
public class PaymentCheckoutException extends RuntimeException {
    private final Outcome outcome;

    public PaymentCheckoutException(Outcome outcome) {
        super(outcome.name());
        this.outcome = outcome;
    }

    public Outcome outcome() {
        return outcome;
    }

    public enum Outcome {
        NOT_FOUND,
        IDEMPOTENCY_CONFLICT,
        NOT_PAYABLE,
        DEADLINE_PASSED,
        ATTEMPT_LIMIT_REACHED,
        CHECKOUT_IN_PROGRESS,
        PROVIDER_UNAVAILABLE,
        INVALID_IDEMPOTENCY_KEY
    }
}
