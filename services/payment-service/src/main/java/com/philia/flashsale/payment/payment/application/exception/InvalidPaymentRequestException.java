package com.philia.flashsale.payment.payment.application.exception;

/** Raised when a PaymentRequested command violates its approved envelope or snapshot contract. */
public final class InvalidPaymentRequestException extends IllegalArgumentException {

    public InvalidPaymentRequestException(String message) {
        super(message);
    }
}
