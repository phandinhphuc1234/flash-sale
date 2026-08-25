package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.ReservationCommandInboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.ReservationCommandInboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationConfirmationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for the atomic reservation-confirmation boundary. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationConfirmationJpaAdapter implements PersistReservationConfirmationPort {
    private final FlashSaleReservationJpaRepository reservations;
    private final ReservationCommandInboxJpaRepository inbox;
    private final PurchaseEventOutboxJpaRepository outbox;
    private final Clock clock;

    public ReservationConfirmationJpaAdapter(FlashSaleReservationJpaRepository reservations,
            ReservationCommandInboxJpaRepository inbox, PurchaseEventOutboxJpaRepository outbox) {
        this(reservations, inbox, outbox, Clock.systemUTC());
    }

    @Autowired
    public ReservationConfirmationJpaAdapter(FlashSaleReservationJpaRepository reservations,
            ReservationCommandInboxJpaRepository inbox, PurchaseEventOutboxJpaRepository outbox, Clock clock) {
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public ReservationConfirmationResult confirm(ConfirmReservationCommand command) {
        var existing = inbox.findById(command.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.getPayloadFingerprint().equals(command.payloadFingerprint())) {
                throw new IllegalStateException("reservation command id conflicts with a different payload");
            }
            var existingReservation = reservations.findById(existing.getReservationId()).orElseThrow(
                    () -> new IllegalStateException("reservation for command inbox row does not exist"));
            return new ReservationConfirmationResult(existing.getReservationId(), existingReservation.getCampaignId(),
                    command.commandId(),
                    existing.getResultEventId(), ReservationConfirmationResult.Status.ALREADY_CONFIRMED,
                    existingProcessedAt(existing));
        }

        var reservation = reservations.findWithLockById(command.reservationId()).orElseThrow(
                () -> new IllegalStateException("reservation does not exist"));
        assertIdentity(reservation, command);
        Instant now = clock.instant();
        if (reservation.getStatus() != FlashSaleReservationJpaEntity.Status.CONFIRMED
                && !reservation.confirm(now)) {
            throw new IllegalStateException("reservation is not confirmable before expiry");
        }
        UUID resultEventId = UUID.nameUUIDFromBytes(("purchase-reservation-confirmed:" + command.commandId())
                .getBytes(StandardCharsets.UTF_8));
        inbox.save(ReservationCommandInboxJpaEntity.confirmed(command, resultEventId, now));
        outbox.save(PurchaseEventOutboxJpaEntity.confirmed(command, reservation, resultEventId, now));
        return new ReservationConfirmationResult(command.reservationId(), reservation.getCampaignId(), command.commandId(), resultEventId,
                ReservationConfirmationResult.Status.CONFIRMED, now);
    }

    private void assertIdentity(FlashSaleReservationJpaEntity reservation, ConfirmReservationCommand command) {
        if (!reservation.getPurchaseRequestId().equals(command.purchaseRequestId())) {
            throw new IllegalStateException("purchase request does not own reservation");
        }
    }

    private Instant existingProcessedAt(ReservationCommandInboxJpaEntity existing) {
        // The retry result is only used to drive Redis reconciliation; its exact timestamp
        // is not part of the idempotency identity. Use the injected clock for a fresh result.
        return clock.instant();
    }
}
