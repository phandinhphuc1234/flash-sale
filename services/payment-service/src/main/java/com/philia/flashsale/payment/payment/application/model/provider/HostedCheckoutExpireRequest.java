package com.philia.flashsale.payment.payment.application.model.provider;

/** Provider-neutral expire command for an unpaid hosted Session. */
public record HostedCheckoutExpireRequest(String providerSessionId) {
    public HostedCheckoutExpireRequest {
        if (providerSessionId == null || providerSessionId.isBlank()) {
            throw new IllegalArgumentException("providerSessionId must not be blank");
        }
    }
}
