package com.philia.flashsale.flashsale.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.flashsale.reservation.domain.exception.InvalidReservationStateException;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import com.philia.flashsale.flashsale.reservation.domain.model.PurchaseOutcome;
import com.philia.flashsale.flashsale.reservation.domain.model.PurchaseRequest;
import com.philia.flashsale.flashsale.reservation.domain.model.Reservation;
import com.philia.flashsale.flashsale.reservation.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationDomainTests {
    private static final Instant ACCEPTED_AT = Instant.parse("2026-08-10T12:00:00Z");
    private static final Instant EXPIRES_AT = ACCEPTED_AT.plusSeconds(300);

    @Test
    void reservationExpiresOnlyAtOrAfterItsEligibilityBoundary() {
        Reservation reservation = Reservation.reserved(snapshot());

        assertThatThrownBy(() -> reservation.expire(ACCEPTED_AT.plusSeconds(299)))
                .isInstanceOf(InvalidReservationStateException.class);
        reservation.expire(EXPIRES_AT);
        reservation.expire(EXPIRES_AT.plusSeconds(1));

        assertThat(reservation.status()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void purchaseRequestCannotBeAcceptedAfterItExpires() {
        PurchaseRequest purchase = PurchaseRequest.pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, hash(), EXPIRES_AT);

        assertThatThrownBy(() -> purchase.accept(EXPIRES_AT.plusNanos(1)))
                .isInstanceOf(InvalidReservationStateException.class);
        purchase.accept(ACCEPTED_AT);
        assertThat(purchase.outcome()).isEqualTo(PurchaseOutcome.ACCEPTED);
    }

    @Test
    void acceptedSnapshotKeepsExactFourDecimalMoneyAndIdentity() {
        AcceptedReservationSnapshot snapshot = snapshot();

        assertThat(snapshot.unitPrice()).isEqualByComparingTo(new BigDecimal("19.9900"));
        assertThat(snapshot.quantity()).isEqualTo(2);
        assertThat(snapshot.purchaseRequestId()).isNotEqualTo(snapshot.reservationId());
    }

    @Test
    void confirmsAnActiveReservationAndMakesReplayIdempotent() {
        Reservation reservation = Reservation.reserved(snapshot());

        reservation.confirm(ACCEPTED_AT.plusSeconds(1));
        reservation.confirm(ACCEPTED_AT.plusSeconds(2));

        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void cannotConfirmAfterTheReservationDeadline() {
        Reservation reservation = Reservation.reserved(snapshot());

        assertThatThrownBy(() -> reservation.confirm(EXPIRES_AT))
                .isInstanceOf(InvalidReservationStateException.class);
    }

    private AcceptedReservationSnapshot snapshot() {
        return new AcceptedReservationSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 2, hash(), hash(), ACCEPTED_AT, EXPIRES_AT,
                EXPIRES_AT.plusSeconds(24 * 60 * 60), "00-trace", "");
    }

    private String hash() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }
}
