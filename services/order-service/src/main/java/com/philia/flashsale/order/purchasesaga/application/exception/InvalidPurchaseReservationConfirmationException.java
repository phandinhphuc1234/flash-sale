package com.philia.flashsale.order.purchasesaga.application.exception;

/** Signals a contradictory or non-applicable reservation confirmation fact. */
public final class InvalidPurchaseReservationConfirmationException extends RuntimeException {
    public InvalidPurchaseReservationConfirmationException(String message) {
        super(message);
    }
}
