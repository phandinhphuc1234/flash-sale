package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.query.GetOwnedReservationQuery;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDetailsResult;
import java.util.Optional;

/** Loads a reservation only when its durable owner matches the authenticated shopper. */
public interface LoadOwnedReservationPort {
    Optional<ReservationDetailsResult> loadOwnedReservation(GetOwnedReservationQuery query);
}
