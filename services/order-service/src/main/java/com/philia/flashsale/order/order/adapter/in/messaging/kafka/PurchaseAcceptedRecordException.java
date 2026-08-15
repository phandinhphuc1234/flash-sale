package com.philia.flashsale.order.order.adapter.in.messaging.kafka;

/** Non-retryable failure for a malformed or unsupported PurchaseAccepted record. */
public class PurchaseAcceptedRecordException extends RuntimeException {

    public PurchaseAcceptedRecordException(String message) {
        super(message);
    }

    public PurchaseAcceptedRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
