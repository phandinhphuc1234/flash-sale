package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Signals a malformed or unsupported reservation-release fact at the Kafka boundary. */
public final class PurchaseReservationReleasedRecordException extends IllegalArgumentException {
    public PurchaseReservationReleasedRecordException(String message) { super(message); }
}
