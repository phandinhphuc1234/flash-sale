package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

/** Non-retryable failure for malformed, unsupported, or contract-invalid PaymentRequested data. */
public final class PaymentRequestedRecordException extends RuntimeException {

    public PaymentRequestedRecordException(String message) {
        super(message);
    }

    public PaymentRequestedRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
