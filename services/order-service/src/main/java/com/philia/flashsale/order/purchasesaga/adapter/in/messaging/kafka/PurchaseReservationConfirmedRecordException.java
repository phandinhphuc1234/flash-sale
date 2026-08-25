package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable schema/envelope rejection for a reservation result. */
public final class PurchaseReservationConfirmedRecordException extends RuntimeException {
    public PurchaseReservationConfirmedRecordException(String message) { super(message); }
}
