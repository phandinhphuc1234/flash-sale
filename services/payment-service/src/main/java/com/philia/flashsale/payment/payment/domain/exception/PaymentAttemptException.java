package com.philia.flashsale.payment.payment.domain.exception;

/** Raised when a Checkout attempt cannot legally transition or be allocated. */
public final class PaymentAttemptException extends PaymentDomainException {

    public PaymentAttemptException(String message) {
        super(message);
    }
}
