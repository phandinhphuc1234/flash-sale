package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;
import java.time.Instant;

/** Commits the terminal PostgreSQL state before quota restoration is attempted. */
public interface PersistReservationExpiryPort {
    boolean persistExpiry(ReservationExpiryCandidate candidate, Instant now);
}
