package com.philia.flashsale.payment.payment.application.exception;

/** Missing and foreign-owned Payments deliberately share one non-enumerating outcome. */
public final class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException() {
        super("Payment not found");
    }
}
