package com.philia.flashsale.flashsale.reservation.application.result;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.util.Objects;

/** Typed result of the atomic admission boundary; infrastructure details never cross it. */
public record ReservationDecisionResult(Outcome outcome, AcceptedReservationSnapshot snapshot) {
    public ReservationDecisionResult {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome.isAccepted() && snapshot == null) {
            throw new IllegalArgumentException("accepted outcomes require a reservation snapshot");
        }
        if (!outcome.isAccepted() && snapshot != null) {
            throw new IllegalArgumentException("rejection outcomes cannot carry a reservation snapshot");
        }
    }

    public static ReservationDecisionResult accepted(Outcome outcome, AcceptedReservationSnapshot snapshot) {
        if (!outcome.isAccepted()) {
            throw new IllegalArgumentException("outcome is not an accepted result");
        }
        return new ReservationDecisionResult(outcome, snapshot);
    }

    public static ReservationDecisionResult rejected(Outcome outcome) {
        return new ReservationDecisionResult(outcome, null);
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
        VARIANT_NOT_ELIGIBLE(false),
        SOLD_OUT(false),
        PURCHASE_LIMIT_EXCEEDED(false);

        private final boolean accepted;

        Outcome(boolean accepted) {
            this.accepted = accepted;
        }

        public boolean isAccepted() {
            return accepted;
        }
    }
}
