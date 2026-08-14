package com.philia.flashsale.flashsale.reservation.application.port.in;

import com.philia.flashsale.flashsale.reservation.application.query.GetOwnedReservationQuery;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDetailsResult;
import java.util.Optional;

/** Driving port for a shopper to retrieve only their durable reservation. */
public interface GetOwnedReservationUseCase {
    Optional<ReservationDetailsResult> getOwnedReservation(GetOwnedReservationQuery query);
}
