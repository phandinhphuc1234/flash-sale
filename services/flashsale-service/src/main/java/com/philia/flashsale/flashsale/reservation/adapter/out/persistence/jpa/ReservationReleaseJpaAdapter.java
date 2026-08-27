package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.ReservationCommandInboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseEventOutboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.ReservationCommandInboxJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationReleasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL boundary for atomic release, inbox, and released outcome publication. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationReleaseJpaAdapter implements PersistReservationReleasePort {
    private static final String RELEASED = "RELEASED";
    private static final String EXPIRED = "EXPIRED";

    private final FlashSaleReservationJpaRepository reservations;
    private final ReservationCommandInboxJpaRepository inbox;
    private final PurchaseEventOutboxJpaRepository outbox;
    private final Clock clock;
    public ReservationReleaseJpaAdapter(FlashSaleReservationJpaRepository reservations, ReservationCommandInboxJpaRepository inbox,
            PurchaseEventOutboxJpaRepository outbox) { this(reservations, inbox, outbox, Clock.systemUTC()); }
    @Autowired
    public ReservationReleaseJpaAdapter(FlashSaleReservationJpaRepository reservations, ReservationCommandInboxJpaRepository inbox,
            PurchaseEventOutboxJpaRepository outbox, Clock clock) { this.reservations = Objects.requireNonNull(reservations); this.inbox = Objects.requireNonNull(inbox); this.outbox = Objects.requireNonNull(outbox); this.clock = Objects.requireNonNull(clock); }
    @Override @Transactional
    public ReservationReleaseResult release(ReleaseReservationCommand command) {
        var existing = inbox.findById(command.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.getPayloadFingerprint().equals(command.payloadFingerprint())) throw new IllegalStateException("reservation release command conflicts with a different payload");
            var reservation = reservations.findById(existing.getReservationId()).orElseThrow(() -> new IllegalStateException("reservation for command inbox does not exist"));
            return result(command, reservation, existing.getResultEventId(), statusFor(reservation), clock.instant());
        }
        var reservation = reservations.findWithLockById(command.reservationId()).orElseThrow(() -> new IllegalStateException("reservation does not exist"));
        if (!reservation.getPurchaseRequestId().equals(command.purchaseRequestId())) throw new IllegalStateException("purchase request does not own reservation");
        Instant now = clock.instant();
        String status;
        if (reservation.getStatus() == FlashSaleReservationJpaEntity.Status.RESERVED) {
            if (!now.isBefore(reservation.getExpiresAt())) {
                reservation.expire(now);
                status = EXPIRED;
            } else {
                reservation.release(now);
                status = RELEASED;
            }
        } else if (reservation.getStatus() == FlashSaleReservationJpaEntity.Status.EXPIRED) {
            status = EXPIRED;
        } else if (reservation.getStatus() == FlashSaleReservationJpaEntity.Status.RELEASED) {
            status = RELEASED;
        } else {
            throw new IllegalStateException("confirmed reservation cannot be released");
        }
        UUID resultEventId = UUID.nameUUIDFromBytes(("purchase-reservation-released:" + command.commandId()).getBytes(StandardCharsets.UTF_8));
        inbox.save(ReservationCommandInboxJpaEntity.released(command, resultEventId, now));
        outbox.save(PurchaseEventOutboxJpaEntity.released(command, reservation, resultEventId, now, status));
        return result(command, reservation, resultEventId, status, now);
    }
    private ReservationReleaseResult result(ReleaseReservationCommand command, FlashSaleReservationJpaEntity r, UUID resultId, String status, Instant at) {
        ReservationReleaseResult.Status value = RELEASED.equals(status)
                ? ReservationReleaseResult.Status.RELEASED
                : ReservationReleaseResult.Status.EXPIRED;
        if (RELEASED.equals(status) && r.getFinalizedAt() != null && !r.getFinalizedAt().equals(at)) {
            value = ReservationReleaseResult.Status.ALREADY_RELEASED;
        }
        if (EXPIRED.equals(status) && r.getFinalizedAt() != null
                && r.getStatus() == FlashSaleReservationJpaEntity.Status.EXPIRED) {
            value = ReservationReleaseResult.Status.ALREADY_EXPIRED;
        }
        return new ReservationReleaseResult(r.getId(), r.getCampaignId(), r.getUserId(), r.getVariantId(), r.getQuantity(), command.commandId(), resultId, value, command.reason(), at);
    }
    private String statusFor(FlashSaleReservationJpaEntity r) {
        return r.getStatus() == FlashSaleReservationJpaEntity.Status.RELEASED ? RELEASED : EXPIRED;
    }
}
