package com.philia.flashsale.payment.payment.application.exception;

import com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory;

/** Sanitized provider failure used to select a stable public outcome. */
public final class HostedCheckoutProviderException extends RuntimeException {
    private final ProviderFailureCategory category;

    public HostedCheckoutProviderException(ProviderFailureCategory category) {
        super("hosted checkout provider operation failed: " + category);
        this.category = category;
    }

    public ProviderFailureCategory category() {
        return category;
    }
}
