package com.philia.flashsale.payment.payment.adapter.in.messaging.kafka;

/** Non-retryable failure for a valid command that contradicts an established Payment identity. */
public final class PaymentRequestedConflictException extends RuntimeException {

    public PaymentRequestedConflictException(String message) {
        super(message);
    }
}
