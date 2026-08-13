package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.port.in.GetOwnedReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadOwnedReservationPort;
import com.philia.flashsale.flashsale.reservation.application.query.GetOwnedReservationQuery;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDetailsResult;
import java.util.Objects;
import java.util.Optional;

/** Application boundary that keeps owner filtering delegated to the durable read port. */
public final class GetOwnedReservationService implements GetOwnedReservationUseCase {
    private final LoadOwnedReservationPort reservations;

    public GetOwnedReservationService(LoadOwnedReservationPort reservations) {
        this.reservations = Objects.requireNonNull(reservations, "reservations");
    }

    @Override
    public Optional<ReservationDetailsResult> getOwnedReservation(GetOwnedReservationQuery query) {
        return reservations.loadOwnedReservation(query);
    }
}
