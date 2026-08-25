package com.philia.flashsale.order.purchasesaga.application.exception;

/** Non-retryable failure for an invalid or contradictory PaymentFailed fact. */
public final class InvalidPaymentFailureException extends RuntimeException {
    public InvalidPaymentFailureException(String message) { super(message); }
}
