package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.ReservationConfirmationJpaAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.ReservationCommandInboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.ReservationCommandInboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationConfirmationPersistenceTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final String FINGERPRINT = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void confirmsReservationAndCreatesInboxAndOutcomeInOneAdapterBoundary() {
        var reservations = mock(FlashSaleReservationJpaRepository.class);
        var inbox = mock(ReservationCommandInboxJpaRepository.class);
        var outbox = mock(PurchaseEventOutboxJpaRepository.class);
        var reservation = FlashSaleReservationJpaEntity.reserved(snapshot());
        var command = command(reservation);
        when(inbox.findById(command.commandId())).thenReturn(Optional.empty());
        when(reservations.findWithLockById(command.reservationId())).thenReturn(Optional.of(reservation));
        var adapter = new ReservationConfirmationJpaAdapter(reservations, inbox, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = adapter.confirm(command);

        assertThat(result.status()).isEqualTo(com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult.Status.CONFIRMED);
        assertThat(reservation.getStatus()).isEqualTo(FlashSaleReservationJpaEntity.Status.CONFIRMED);
        verify(inbox).save(org.mockito.ArgumentMatchers.any(ReservationCommandInboxJpaEntity.class));
        verify(outbox).save(org.mockito.ArgumentMatchers.any(PurchaseEventOutboxJpaEntity.class));
    }

    @Test
    void sameCommandIdAndFingerprintReusesTheStoredResult() {
        var reservations = mock(FlashSaleReservationJpaRepository.class);
        var inbox = mock(ReservationCommandInboxJpaRepository.class);
        var outbox = mock(PurchaseEventOutboxJpaRepository.class);
        var reservation = FlashSaleReservationJpaEntity.reserved(snapshot());
        var command = command(reservation);
        UUID resultEventId = UUID.randomUUID();
        var stored = ReservationCommandInboxJpaEntity.confirmed(command, resultEventId, NOW);
        when(inbox.findById(command.commandId())).thenReturn(Optional.of(stored));
        when(reservations.findById(command.reservationId())).thenReturn(Optional.of(reservation));

        var result = new ReservationConfirmationJpaAdapter(reservations, inbox, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)).confirm(command);

        assertThat(result.resultEventId()).isEqualTo(resultEventId);
        assertThat(result.status()).isEqualTo(com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult.Status.ALREADY_CONFIRMED);
    }

    @Test
    void sameCommandIdWithDifferentPayloadIsRejected() {
        var reservations = mock(FlashSaleReservationJpaRepository.class);
        var inbox = mock(ReservationCommandInboxJpaRepository.class);
        var outbox = mock(PurchaseEventOutboxJpaRepository.class);
        var reservation = FlashSaleReservationJpaEntity.reserved(snapshot());
        var original = command(reservation);
        var stored = ReservationCommandInboxJpaEntity.confirmed(original, UUID.randomUUID(), NOW);
        when(inbox.findById(original.commandId())).thenReturn(Optional.of(stored));
        var conflicting = new ConfirmReservationCommand(original.commandId(), original.commandType(), 1,
                original.producer(), original.aggregateType(), original.sagaId(), original.aggregateVersion(),
                original.correlationId(), original.causationId(), original.occurredAt(), original.orderId(),
                original.purchaseRequestId(), original.reservationId(), UUID.randomUUID(), original.paidAt(),
                original.sourceTopic(), original.sourcePartition(), original.sourceOffset(), null, null,
                "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd");

        assertThatThrownBy(() -> new ReservationConfirmationJpaAdapter(reservations, inbox, outbox,
                Clock.fixed(NOW, ZoneOffset.UTC)).confirm(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different payload");
    }

    private ConfirmReservationCommand command(FlashSaleReservationJpaEntity reservation) {
        return new ConfirmReservationCommand(UUID.randomUUID(), "ConfirmPurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", UUID.randomUUID(), 2L, UUID.randomUUID(), UUID.randomUUID(), NOW,
                UUID.randomUUID(), reservation.getPurchaseRequestId(), reservation.getId(), UUID.randomUUID(), NOW,
                "flashsale.purchase.commands.v1", 0, 1L, null, null, FINGERPRINT);
    }

    private com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot snapshot() {
        Instant expires = NOW.plusSeconds(300);
        return new com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SKU-1", new BigDecimal("20.0000"), "VND", 1, FINGERPRINT, FINGERPRINT,
                NOW.minusSeconds(1), expires, expires.plusSeconds(3600), null, null);
    }
}
