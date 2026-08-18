package com.philia.flashsale.payment.payment.application.exception;

/** Sanitized permanent failure at the Stripe webhook protocol boundary. */
public final class WebhookVerificationException extends RuntimeException {
    private final Reason reason;

    public WebhookVerificationException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public WebhookVerificationException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        MISSING_SIGNATURE,
        INVALID_SIGNATURE,
        STALE_SIGNATURE,
        MALFORMED_PAYLOAD,
        MODE_MISMATCH,
        API_VERSION_MISMATCH,
        UNSUPPORTED_ENCODING
    }
}
