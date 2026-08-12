package com.philia.flashsale.flashsale.reservation.application.port.in;

import java.time.Instant;

public interface ExpireReservationsUseCase {
    void expireDueReservations(Instant now);
}
