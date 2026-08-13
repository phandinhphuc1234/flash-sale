package com.philia.flashsale.flashsale.reservation.application.port.in;

import com.philia.flashsale.flashsale.reservation.application.command.SubmitReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationSubmissionResult;

/** Driving port for the public reservation submission flow. */
public interface SubmitReservationUseCase {
    ReservationSubmissionResult submit(SubmitReservationCommand command);
}
