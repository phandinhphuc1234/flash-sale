package com.philia.flashsale.flashsale.reservation.domain.model;

import com.philia.flashsale.flashsale.reservation.domain.exception.InvalidReservationStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Reservation aggregate protecting the one-way confirmation/expiry lifecycle. */
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
        if (status == ReservationStatus.EXPIRED || status == ReservationStatus.CONFIRMED) {
            return;
        }
        if (now.isBefore(snapshot.expiresAt())) {
            throw new InvalidReservationStateException("Reservation cannot expire before its expiry instant");
        }
        status = ReservationStatus.EXPIRED;
    }

    /** Confirms a paid reservation before its safety deadline. */
    public void confirm(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == ReservationStatus.CONFIRMED) {
            return;
        }
        if (status != ReservationStatus.RESERVED) {
            throw new InvalidReservationStateException("Only a reserved reservation can be confirmed");
        }
        if (!now.isBefore(snapshot.expiresAt())) {
            throw new InvalidReservationStateException("An expired reservation cannot be confirmed");
        }
        status = ReservationStatus.CONFIRMED;
    }

    public UUID reservationId() { return snapshot.reservationId(); }
    public UUID purchaseRequestId() { return snapshot.purchaseRequestId(); }
    public ReservationStatus status() { return status; }
    public AcceptedReservationSnapshot snapshot() { return snapshot; }
}
