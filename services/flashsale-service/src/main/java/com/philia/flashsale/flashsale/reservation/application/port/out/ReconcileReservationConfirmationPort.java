package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;

/** Applies the durable confirmation to the Redis hot-path projection idempotently. */
public interface ReconcileReservationConfirmationPort {
    void confirm(ConfirmReservationCommand command, ReservationConfirmationResult result);
}
