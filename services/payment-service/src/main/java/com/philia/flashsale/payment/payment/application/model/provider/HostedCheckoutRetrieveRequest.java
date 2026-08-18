package com.philia.flashsale.payment.payment.application.model.provider;

/** Provider-neutral lookup command based on the persisted provider Session identity. */
public record HostedCheckoutRetrieveRequest(String providerSessionId) {
    public HostedCheckoutRetrieveRequest {
        if (providerSessionId == null || providerSessionId.isBlank()) {
            throw new IllegalArgumentException("providerSessionId must not be blank");
        }
    }
}
