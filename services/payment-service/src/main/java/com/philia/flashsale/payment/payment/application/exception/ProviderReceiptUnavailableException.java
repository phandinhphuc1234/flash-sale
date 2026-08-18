package com.philia.flashsale.payment.payment.application.exception;

/** Durable receipt storage is unavailable; the provider must retry the webhook. */
public final class ProviderReceiptUnavailableException extends RuntimeException {
    public ProviderReceiptUnavailableException(Throwable cause) {
        super("provider receipt storage unavailable", cause);
    }
}
