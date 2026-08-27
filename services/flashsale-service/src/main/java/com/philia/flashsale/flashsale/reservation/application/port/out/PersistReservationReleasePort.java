package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;

public interface PersistReservationReleasePort {
    ReservationReleaseResult release(ReleaseReservationCommand command);
}
