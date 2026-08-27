package com.philia.flashsale.flashsale.reservation.application.result;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Result of a durable reservation confirmation and its stable outcome identity. */
public record ReservationConfirmationResult(
        UUID reservationId,
        UUID campaignId,
        UUID commandId,
        UUID resultEventId,
        Status status,
        Instant confirmedAt) {

    public enum Status {
        CONFIRMED,
        ALREADY_CONFIRMED,
        RELEASED,
        EXPIRED;

        public boolean requiresConfirmationProjection() {
            return this == CONFIRMED || this == ALREADY_CONFIRMED;
        }
    }

    public ReservationConfirmationResult {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(resultEventId, "resultEventId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
    }
}
