package com.philia.flashsale.flashsale.reservation.application.port.in;

import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;

public interface ReleaseReservationUseCase {
    ReservationReleaseResult release(ReleaseReservationCommand command);
}
