package com.philia.flashsale.flashsale.reservation.application.port.in;

import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;

/** Inbound capability for the Order-owned reservation confirmation command. */
public interface ConfirmReservationUseCase {
    ReservationConfirmationResult confirm(ConfirmReservationCommand command);
}
