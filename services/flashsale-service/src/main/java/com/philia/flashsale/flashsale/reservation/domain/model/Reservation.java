package com.philia.flashsale.flashsale.reservation.domain.model;

import com.philia.flashsale.flashsale.reservation.domain.exception.InvalidReservationStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Reservation aggregate protecting the one-way RESERVED -> EXPIRED lifecycle. */
public final class Reservation {
    private final AcceptedReservationSnapshot snapshot;
    private ReservationStatus status;

    private Reservation(AcceptedReservationSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.status = ReservationStatus.RESERVED;
    }

    public static Reservation reserved(AcceptedReservationSnapshot snapshot) {
        return new Reservation(snapshot);
    }

    public void expire(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == ReservationStatus.EXPIRED) {
            return;
        }
        if (now.isBefore(snapshot.expiresAt())) {
            throw new InvalidReservationStateException("Reservation cannot expire before its expiry instant");
        }
        status = ReservationStatus.EXPIRED;
    }

    public UUID reservationId() { return snapshot.reservationId(); }
    public UUID purchaseRequestId() { return snapshot.purchaseRequestId(); }
    public ReservationStatus status() { return status; }
    public AcceptedReservationSnapshot snapshot() { return snapshot; }
}
