package com.philia.flashsale.flashsale.reservation.domain.exception;

/** Raised when a reservation lifecycle transition would violate its invariant. */
public final class InvalidReservationStateException extends RuntimeException {
    public InvalidReservationStateException(String message) {
        super(message);
    }
}
