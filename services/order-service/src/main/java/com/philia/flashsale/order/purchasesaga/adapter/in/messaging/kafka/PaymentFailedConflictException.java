package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable failure for contradictory PaymentFailed facts. */
public final class PaymentFailedConflictException extends RuntimeException {
    public PaymentFailedConflictException(String message) { super(message); }
}
