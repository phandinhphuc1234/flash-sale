package com.philia.flashsale.payment.payment.domain.exception;

/** Base exception for invariant violations owned by the Payment domain. */
public class PaymentDomainException extends IllegalArgumentException {

    public PaymentDomainException(String message) {
        super(message);
    }

    public PaymentDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
