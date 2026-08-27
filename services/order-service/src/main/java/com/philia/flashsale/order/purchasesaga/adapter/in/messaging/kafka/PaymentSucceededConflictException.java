package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable failure for contradictory PaymentSucceeded facts. */
public final class PaymentSucceededConflictException extends RuntimeException {
    public PaymentSucceededConflictException(String message) { super(message); }
}
