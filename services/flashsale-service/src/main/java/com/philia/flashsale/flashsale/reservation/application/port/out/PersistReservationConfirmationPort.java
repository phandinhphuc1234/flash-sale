package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;

/** Atomically persists reservation state, command inbox, and confirmed outcome intent. */
public interface PersistReservationConfirmationPort {
    ReservationConfirmationResult confirm(ConfirmReservationCommand command);
}
