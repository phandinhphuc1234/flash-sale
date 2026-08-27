package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.ReservationCommandInboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.ReservationCommandInboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
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
                    existing.getResultEventId(), replayStatus(existingReservation),
                    existingProcessedAt(existing));
        }

        var reservation = reservations.findWithLockById(command.reservationId()).orElseThrow(
                () -> new IllegalStateException("reservation does not exist"));
        assertIdentity(reservation, command);
        Instant now = clock.instant();
        ConfirmationOutcome outcome = applyOutcome(reservation, now);
        UUID resultEventId = resultEventId(command, outcome.status());
        inbox.save(ReservationCommandInboxJpaEntity.confirmed(command, resultEventId, now));
        outbox.save(outcomeEvent(command, reservation, resultEventId, now, outcome));
        return new ReservationConfirmationResult(command.reservationId(), reservation.getCampaignId(),
                command.commandId(), resultEventId, outcome.status(), now);
    }

    private ConfirmationOutcome applyOutcome(FlashSaleReservationJpaEntity reservation, Instant now) {
        return switch (reservation.getStatus()) {
            case RESERVED -> {
                if (reservation.confirm(now)) {
                    yield new ConfirmationOutcome(ReservationConfirmationResult.Status.CONFIRMED, null, true);
                }
                if (!reservation.expire(now)) {
                    throw new IllegalStateException("reservation is not confirmable before expiry");
                }
                yield new ConfirmationOutcome(ReservationConfirmationResult.Status.EXPIRED,
                        "RESERVATION_EXPIRED", true);
            }
            case CONFIRMED -> new ConfirmationOutcome(
                    ReservationConfirmationResult.Status.ALREADY_CONFIRMED, null, false);
            case RELEASED -> new ConfirmationOutcome(
                    ReservationConfirmationResult.Status.RELEASED, priorReleaseReason(reservation), false);
            case EXPIRED -> new ConfirmationOutcome(
                    ReservationConfirmationResult.Status.EXPIRED, "RESERVATION_EXPIRED", false);
        };
    }

    private PurchaseEventOutboxJpaEntity outcomeEvent(ConfirmReservationCommand command,
            FlashSaleReservationJpaEntity reservation, UUID resultEventId, Instant now,
            ConfirmationOutcome outcome) {
        long aggregateVersion = outcome.transitioned()
                ? reservation.getVersion() + 1
                : reservation.getVersion();
        if (outcome.status().requiresConfirmationProjection()) {
            return PurchaseEventOutboxJpaEntity.confirmed(
                    command, reservation, resultEventId, now, aggregateVersion);
        }
        return PurchaseEventOutboxJpaEntity.currentStateReleased(command, reservation, resultEventId,
                now, outcome.status().name(), outcome.reason(), aggregateVersion);
    }

    private UUID resultEventId(ConfirmReservationCommand command,
            ReservationConfirmationResult.Status status) {
        String prefix = status.requiresConfirmationProjection()
                ? "purchase-reservation-confirmed:"
                : "purchase-reservation-released:";
        return UUID.nameUUIDFromBytes((prefix + command.commandId()).getBytes(StandardCharsets.UTF_8));
    }

    private String priorReleaseReason(FlashSaleReservationJpaEntity reservation) {
        Object reason = outbox.findFirstByAggregateIdAndEventTypeOrderByCreatedAtDesc(
                        reservation.getId(), "PurchaseReservationReleased")
                .map(PurchaseEventOutboxJpaEntity::getPayload)
                .map(payload -> payload.get("reason"))
                .orElseThrow(() -> new IllegalStateException(
                        "released reservation is missing its durable release reason"));
        String value = reason.toString();
        if (!ReleaseReservationCommand.REASONS.contains(value)) {
            throw new IllegalStateException("released reservation has an unsupported release reason");
        }
        return value;
    }

    private ReservationConfirmationResult.Status replayStatus(
            FlashSaleReservationJpaEntity reservation) {
        return switch (reservation.getStatus()) {
            case CONFIRMED -> ReservationConfirmationResult.Status.ALREADY_CONFIRMED;
            case RELEASED -> ReservationConfirmationResult.Status.RELEASED;
            case EXPIRED -> ReservationConfirmationResult.Status.EXPIRED;
            case RESERVED -> throw new IllegalStateException(
                    "processed confirmation command points to a non-final reservation");
        };
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

    private record ConfirmationOutcome(
            ReservationConfirmationResult.Status status,
            String reason,
            boolean transitioned) { }
}
