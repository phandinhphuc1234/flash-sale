package com.philia.flashsale.payment.payment.application.model.provider;

import java.time.Instant;

/** Sanitized provider result; SDK objects, URLs, and raw provider messages never cross the port. */
public record HostedCheckoutResult(
        ProviderCheckoutState state,
        String providerSessionId,
        String providerPaymentIntentId,
        String checkoutUrl,
        Instant providerExpiresAt,
        Instant observedAt,
        ProviderFailureCategory failureCategory) {

    public HostedCheckoutResult {
        if (state == null || observedAt == null) {
            throw new IllegalArgumentException("state and observedAt are required");
        }
        if (failureCategory != null && state != ProviderCheckoutState.UNKNOWN
                && state != ProviderCheckoutState.FAILED) {
            throw new IllegalArgumentException("failure category is only valid for failed/unknown results");
        }
    }

    public static HostedCheckoutResult open(String sessionId, String url, Instant expiresAt, Instant observedAt) {
        return new HostedCheckoutResult(ProviderCheckoutState.OPEN, sessionId, null, url, expiresAt,
                observedAt, null);
    }

    public static HostedCheckoutResult unknown(ProviderFailureCategory category, Instant observedAt) {
        return new HostedCheckoutResult(ProviderCheckoutState.UNKNOWN, null, null, null, null,
                observedAt, category);
    }
}
