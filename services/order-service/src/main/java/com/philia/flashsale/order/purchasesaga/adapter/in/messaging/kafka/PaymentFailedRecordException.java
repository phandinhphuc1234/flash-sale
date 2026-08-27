package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable failure for malformed PaymentFailed.v1 records. */
public final class PaymentFailedRecordException extends RuntimeException {
    public PaymentFailedRecordException(String message) { super(message); }
}
