package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseRequestJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.PurchaseRequestJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.port.out.FindDueReservationPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationExpiryPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/** Owns PostgreSQL row locking and the one-way durable expiry transition. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class ReservationExpiryJpaAdapter implements FindDueReservationPort, PersistReservationExpiryPort {
    private final FlashSaleReservationJpaRepository reservations;
    private final PurchaseRequestJpaRepository purchaseRequests;
    private final FlashSaleObservability observability;

    public ReservationExpiryJpaAdapter(FlashSaleReservationJpaRepository reservations,
            PurchaseRequestJpaRepository purchaseRequests) {
        this(reservations, purchaseRequests, FlashSaleObservability.noop());
    }

    @Autowired
    public ReservationExpiryJpaAdapter(FlashSaleReservationJpaRepository reservations,
            PurchaseRequestJpaRepository purchaseRequests, FlashSaleObservability observability) {
        this.reservations = Objects.requireNonNull(reservations, "reservations");
        this.purchaseRequests = Objects.requireNonNull(purchaseRequests, "purchaseRequests");
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    @Override
    public List<ReservationExpiryCandidate> findDue(Instant now, int batchSize) {
        return observability.observe(FlashSaleObservability.Operation.POSTGRES_EXPIRY,
                () -> findDuePersisted(now, batchSize));
    }

    private List<ReservationExpiryCandidate> findDuePersisted(Instant now, int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("batchSize must be in 1..100");
        }
        return reservations.findTop100ByExpiresAtLessThanEqualOrderByExpiresAtAsc(now).stream()
                .limit(batchSize)
                .map(this::candidate)
                .toList();
    }

    @Override
    @Transactional
    public boolean persistExpiry(ReservationExpiryCandidate candidate, Instant now) {
        return observability.observe(FlashSaleObservability.Operation.POSTGRES_EXPIRY,
                () -> persistDurableExpiry(candidate, now));
    }

    private boolean persistDurableExpiry(ReservationExpiryCandidate candidate, Instant now) {
        var request = purchaseRequests.findWithLockById(candidate.purchaseRequestId()).orElse(null);
        if (request == null) {
            return false;
        }
        if (request.getOutcome() == PurchaseRequestJpaEntity.Outcome.EXPIRED) {
            return true;
        }
        var reservation = reservations.findWithLockById(candidate.reservationId()).orElse(null);
        if (reservation == null || now.isBefore(reservation.getExpiresAt())) {
            return false;
        }
        reservation.expire(now);
        return true;
    }

    private ReservationExpiryCandidate candidate(FlashSaleReservationJpaEntity reservation) {
        return new ReservationExpiryCandidate(reservation.getPurchaseRequestId(), reservation.getId(),
                reservation.getCampaignId(), reservation.getVariantId(), reservation.getUserId(),
                reservation.getQuantity(), reservation.getExpiresAt());
    }
}
