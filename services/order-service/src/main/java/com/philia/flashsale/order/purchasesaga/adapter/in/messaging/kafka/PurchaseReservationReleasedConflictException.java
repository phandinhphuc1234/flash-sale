package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Signals a release fact whose identity conflicts with the durable Order Saga. */
public final class PurchaseReservationReleasedConflictException extends IllegalStateException {
    public PurchaseReservationReleasedConflictException(String message) { super(message); }
}
