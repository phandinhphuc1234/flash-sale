package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.kafka;

/** Signals a malformed or unsupported reservation command at the Kafka boundary. */
public final class ConfirmReservationRecordException extends IllegalArgumentException {
    public ConfirmReservationRecordException(String message) {
        super(message);
    }
}
