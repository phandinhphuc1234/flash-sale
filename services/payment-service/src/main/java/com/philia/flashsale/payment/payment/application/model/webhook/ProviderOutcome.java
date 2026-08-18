package com.philia.flashsale.payment.payment.application.model.webhook;

import java.time.Instant;

/** Sanitized provider observation; no Stripe SDK object or sensitive payload crosses inward. */
public record ProviderOutcome(
        ProviderOutcomeState state,
        String providerSessionId,
        String providerPaymentIntentId,
        Instant observedAt) {

    public ProviderOutcome {
        if (state == null || observedAt == null) {
            throw new IllegalArgumentException("provider outcome state and time are required");
        }
    }
}
