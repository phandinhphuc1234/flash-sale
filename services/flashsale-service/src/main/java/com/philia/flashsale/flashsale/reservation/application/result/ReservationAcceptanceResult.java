package com.philia.flashsale.flashsale.reservation.application.result;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.util.Objects;

/** Result of the request/Stream handoff attempting durable acceptance. */
public record ReservationAcceptanceResult(
        Outcome outcome,
        AcceptedReservationSnapshot snapshot,
        boolean handoffAcknowledged) {
    public ReservationAcceptanceResult {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome == Outcome.DURABLY_ACCEPTED && snapshot == null) {
            throw new IllegalArgumentException("durable acceptance requires a snapshot");
        }
        if (outcome != Outcome.DURABLY_ACCEPTED && snapshot != null) {
            throw new IllegalArgumentException("non-accepted outcomes cannot carry a snapshot");
        }
        if (outcome != Outcome.DURABLY_ACCEPTED && handoffAcknowledged) {
            throw new IllegalArgumentException("non-accepted outcomes cannot acknowledge handoff");
        }
    }

    public enum Outcome {
        DURABLY_ACCEPTED,
        ACCEPTANCE_PENDING,
        RESERVATION_EXPIRED
    }
}
