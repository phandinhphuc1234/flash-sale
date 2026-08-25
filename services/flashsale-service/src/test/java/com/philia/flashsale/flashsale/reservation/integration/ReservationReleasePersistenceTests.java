package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.ReservationReleaseJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.ReservationCommandInboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.ReservationCommandInboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationReleasePersistenceTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final String FINGERPRINT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void releasesReservedReservationAndCreatesDurableResult() {
        var reservations = mock(FlashSaleReservationJpaRepository.class);
        var inbox = mock(ReservationCommandInboxJpaRepository.class);
        var outbox = mock(PurchaseEventOutboxJpaRepository.class);
        var reservation = FlashSaleReservationJpaEntity.reserved(snapshot(NOW.plusSeconds(300)));
        var command = command(reservation, "PROVIDER_TERMINAL_FAILURE");
        when(inbox.findById(command.commandId())).thenReturn(Optional.empty());
        when(reservations.findWithLockById(command.reservationId())).thenReturn(Optional.of(reservation));

        var result = new ReservationReleaseJpaAdapter(reservations, inbox, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)).release(command);

        assertThat(result.status()).isEqualTo(
                com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult.Status.RELEASED);
        assertThat(reservation.getStatus()).isEqualTo(FlashSaleReservationJpaEntity.Status.RELEASED);
        verify(inbox).save(org.mockito.ArgumentMatchers.any(ReservationCommandInboxJpaEntity.class));
        verify(outbox).save(org.mockito.ArgumentMatchers.any(PurchaseEventOutboxJpaEntity.class));
    }

    @Test
    void expiresReservedReservationWhenReleaseArrivesAfterExpiry() {
        var reservations = mock(FlashSaleReservationJpaRepository.class);
        var inbox = mock(ReservationCommandInboxJpaRepository.class);
        var outbox = mock(PurchaseEventOutboxJpaRepository.class);
        var reservation = FlashSaleReservationJpaEntity.reserved(snapshot(NOW.minusSeconds(1)));
        var command = command(reservation, "PAYMENT_DEADLINE_EXPIRED");
        when(inbox.findById(command.commandId())).thenReturn(Optional.empty());
        when(reservations.findWithLockById(command.reservationId())).thenReturn(Optional.of(reservation));

        var result = new ReservationReleaseJpaAdapter(reservations, inbox, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)).release(command);

        assertThat(result.status()).isEqualTo(
                com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult.Status.EXPIRED);
        assertThat(reservation.getStatus()).isEqualTo(FlashSaleReservationJpaEntity.Status.EXPIRED);
    }

    @Test
    void replayReturnsStoredResultWithoutWritingAnotherOutboxRow() {
        var reservations = mock(FlashSaleReservationJpaRepository.class);
        var inbox = mock(ReservationCommandInboxJpaRepository.class);
        var outbox = mock(PurchaseEventOutboxJpaRepository.class);
        var reservation = FlashSaleReservationJpaEntity.reserved(snapshot(NOW.plusSeconds(300)));
        var command = command(reservation, "PROVIDER_TERMINAL_FAILURE");
        var stored = ReservationCommandInboxJpaEntity.released(command, UUID.randomUUID(), NOW);
        when(inbox.findById(command.commandId())).thenReturn(Optional.of(stored));
        when(reservations.findById(command.reservationId())).thenReturn(Optional.of(reservation));

        var result = new ReservationReleaseJpaAdapter(reservations, inbox, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)).release(command);

        assertThat(result.resultEventId()).isEqualTo(stored.getResultEventId());
        verifyNoInteractions(outbox);
    }

    @Test
    void confirmedReservationCannotBeReleased() {
        var reservations = mock(FlashSaleReservationJpaRepository.class);
        var inbox = mock(ReservationCommandInboxJpaRepository.class);
        var outbox = mock(PurchaseEventOutboxJpaRepository.class);
        var reservation = FlashSaleReservationJpaEntity.reserved(snapshot(NOW.plusSeconds(300)));
        reservation.confirm(NOW);
        var command = command(reservation, "PROVIDER_TERMINAL_FAILURE");
        when(inbox.findById(command.commandId())).thenReturn(Optional.empty());
        when(reservations.findWithLockById(command.reservationId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> new ReservationReleaseJpaAdapter(reservations, inbox, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)).release(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirmed reservation");
    }

    private ReleaseReservationCommand command(FlashSaleReservationJpaEntity reservation, String reason) {
        UUID sagaId = UUID.randomUUID();
        return new ReleaseReservationCommand(UUID.randomUUID(), "ReleasePurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", sagaId, 1, sagaId, UUID.randomUUID(), NOW,
                UUID.randomUUID(), reservation.getPurchaseRequestId(), reservation.getId(), reason,
                "flashsale.purchase.commands.v1", 0, 1, null, null, FINGERPRINT);
    }

    private com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot snapshot(Instant expires) {
        return new com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SKU-1", new BigDecimal("20.0000"), "VND", 1, FINGERPRINT, FINGERPRINT,
                expires.minusSeconds(300), expires, expires.plusSeconds(3600), null, null);
    }
}
