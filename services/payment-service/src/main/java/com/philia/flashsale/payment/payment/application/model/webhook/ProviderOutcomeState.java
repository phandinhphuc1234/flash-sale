package com.philia.flashsale.payment.payment.application.model.webhook;

/** Provider truth reduced to the states relevant to Payment convergence. */
public enum ProviderOutcomeState {
    PAID,
    UNPAID,
    PROCESSING,
    EXPIRED,
    UNKNOWN
}
