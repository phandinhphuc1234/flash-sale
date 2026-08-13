package com.philia.flashsale.flashsale.reservation.application.result;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.util.Objects;

/** Transport-neutral result of hot-path admission followed by durable acceptance. */
public record ReservationSubmissionResult(Outcome outcome, AcceptedReservationSnapshot snapshot) {
    public ReservationSubmissionResult {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome.isAccepted() && snapshot == null) {
            throw new IllegalArgumentException("accepted submission requires a snapshot");
        }
        if (!outcome.isAccepted() && snapshot != null) {
            throw new IllegalArgumentException("rejected submission cannot carry a snapshot");
        }
    }

    public boolean isAccepted() {
        return outcome.isAccepted();
    }

    public enum Outcome {
        ACCEPTED_NEW(true),
        ACCEPTED_REPLAY(true),
        IDEMPOTENCY_CONFLICT(false),
        CAMPAIGN_UNKNOWN(false),
        CAMPAIGN_RECOVERY_REQUIRED(false),
        CAMPAIGN_NOT_ACTIVE(false),
        CAMPAIGN_NOT_STARTED(false),
        CAMPAIGN_ENDED(false),
        RESERVATION_EXPIRED(false),
        VARIANT_NOT_ELIGIBLE(false),
        SOLD_OUT(false),
        PURCHASE_LIMIT_EXCEEDED(false),
        PROJECTION_UNAVAILABLE(false),
        REDIS_UNAVAILABLE(false),
        ACCEPTANCE_PENDING(false);

        private final boolean accepted;

        Outcome(boolean accepted) {
            this.accepted = accepted;
        }

        public boolean isAccepted() {
            return accepted;
        }
    }
}
