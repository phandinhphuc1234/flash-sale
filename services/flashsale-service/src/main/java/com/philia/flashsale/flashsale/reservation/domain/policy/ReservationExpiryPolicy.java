package com.philia.flashsale.flashsale.reservation.domain.policy;

import java.time.Instant;
import java.util.Objects;

/** Pure eligibility rule: the boundary instant itself is already expired. */
public final class ReservationExpiryPolicy {
    public boolean isDue(Instant expiresAt, Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(Objects.requireNonNull(expiresAt, "expiresAt"));
    }
}
