package com.philia.flashsale.payment.payment.application.model.provider;

/** Normalized provider failure categories safe for application policy and telemetry. */
public enum ProviderFailureCategory {
    INVALID_REQUEST,
    AUTHENTICATION,
    RATE_LIMITED,
    NOT_FOUND,
    TIMEOUT,
    TRANSIENT,
    UNKNOWN
}
