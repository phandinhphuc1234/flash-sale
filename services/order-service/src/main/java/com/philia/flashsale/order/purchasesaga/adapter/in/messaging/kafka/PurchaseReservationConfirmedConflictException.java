package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable contradiction for a previously seen reservation result identity. */
public final class PurchaseReservationConfirmedConflictException extends RuntimeException {
    public PurchaseReservationConfirmedConflictException(String message) { super(message); }
}
