package com.philia.flashsale.payment.payment.application.model.provider;

/** Provider state reduced to the states relevant to a hosted Checkout lifecycle. */
public enum ProviderCheckoutState {
    OPEN,
    PROCESSING,
    PAID,
    FAILED,
    EXPIRED,
    UNKNOWN
}
