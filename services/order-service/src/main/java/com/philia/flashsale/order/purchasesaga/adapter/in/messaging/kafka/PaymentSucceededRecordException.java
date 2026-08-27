package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable failure for malformed PaymentSucceeded.v1 records. */
public final class PaymentSucceededRecordException extends RuntimeException {
    public PaymentSucceededRecordException(String message) { super(message); }
}
