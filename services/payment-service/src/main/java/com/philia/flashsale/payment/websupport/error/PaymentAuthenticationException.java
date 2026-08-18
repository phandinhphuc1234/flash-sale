package com.philia.flashsale.payment.websupport.error;

/** Raised when an authenticated owner identity cannot be converted to the canonical UUID. */
public final class PaymentAuthenticationException extends RuntimeException {
    public PaymentAuthenticationException() {
        super("Authentication is required");
    }
}
