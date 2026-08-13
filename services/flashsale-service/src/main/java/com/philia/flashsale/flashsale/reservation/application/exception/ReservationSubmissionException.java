package com.philia.flashsale.flashsale.reservation.application.exception;

import com.philia.flashsale.flashsale.reservation.application.result.ReservationSubmissionResult;

/** Expected public-submit outcome translated to HTTP only by the inbound web adapter. */
public final class ReservationSubmissionException extends RuntimeException {
    private final ReservationSubmissionResult.Outcome outcome;

    public ReservationSubmissionException(ReservationSubmissionResult.Outcome outcome) {
        super(outcome.name());
        this.outcome = outcome;
    }

    public ReservationSubmissionResult.Outcome outcome() {
        return outcome;
    }
}
