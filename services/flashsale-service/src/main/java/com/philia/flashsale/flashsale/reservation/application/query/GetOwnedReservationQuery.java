package com.philia.flashsale.flashsale.reservation.application.query;

import java.util.Objects;
import java.util.UUID;

/** Owner-scoped identity for a durable reservation lookup. */
public record GetOwnedReservationQuery(UUID reservationId, UUID userId) {
    public GetOwnedReservationQuery {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(userId, "userId");
    }
}
