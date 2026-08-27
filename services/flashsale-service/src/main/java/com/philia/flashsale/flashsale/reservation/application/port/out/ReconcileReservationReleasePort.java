package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;

public interface ReconcileReservationReleasePort {
    void release(ReleaseReservationCommand command, ReservationReleaseResult result);
}
