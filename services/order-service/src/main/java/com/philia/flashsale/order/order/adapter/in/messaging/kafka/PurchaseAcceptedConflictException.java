package com.philia.flashsale.order.order.adapter.in.messaging.kafka;

/** Non-retryable failure for a valid event that contradicts an established Order identity. */
public class PurchaseAcceptedConflictException extends RuntimeException {

    public PurchaseAcceptedConflictException(String message) {
        super(message);
    }
}
