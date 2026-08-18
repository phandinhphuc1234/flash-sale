package com.philia.flashsale.payment.payment.application.exception;

/** Missing and foreign Payments deliberately share the same public outcome. */
public final class PaymentCheckoutNotFoundException extends PaymentCheckoutException {
    public PaymentCheckoutNotFoundException() {
        super(Outcome.NOT_FOUND);
    }
}
