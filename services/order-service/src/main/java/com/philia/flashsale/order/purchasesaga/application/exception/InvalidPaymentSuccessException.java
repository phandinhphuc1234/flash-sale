package com.philia.flashsale.order.purchasesaga.application.exception;

/** Non-retryable failure for an invalid or contradictory PaymentSucceeded fact. */
public final class InvalidPaymentSuccessException extends RuntimeException {
    public InvalidPaymentSuccessException(String message) {
        super(message);
    }
}
