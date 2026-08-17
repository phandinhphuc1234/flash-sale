package com.philia.flashsale.payment.payment.domain.exception;

/** Raised when a Payment aggregate snapshot violates an invariant. */
public final class InvalidPaymentException extends PaymentDomainException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
