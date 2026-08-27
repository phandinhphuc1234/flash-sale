package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadReservationReconciliationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReconciliationCandidate;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Adapter that owns the durable finalization backlog and its completion marker. */
@Component
@ConditionalOnExpression("'${flashsale.runtime.enabled:true}' == 'true' && '${flashsale.runtime.reconciliation-enabled:true}' == 'true'")
public class ReservationReconciliationJpaAdapter implements LoadReservationReconciliationPort {
    private final FlashSaleReservationJpaRepository reservations;

    public ReservationReconciliationJpaAdapter(FlashSaleReservationJpaRepository reservations) {
        this.reservations = Objects.requireNonNull(reservations, "reservations");
    }

    @Override
    public List<ReservationReconciliationCandidate> findPending(int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("batchSize must be in 1..100");
        return reservations.findTop100ByFinalizedAtIsNotNullAndRedisReconciledAtIsNullOrderByFinalizedAtAsc()
                .stream().limit(batchSize).map(this::candidate).toList();
    }

    @Override
    @Transactional
    public void markReconciled(UUID reservationId, Instant at) {
        reservations.findWithLockById(reservationId).ifPresent(entity -> {
            if (entity.getFinalizedAt() != null && entity.getRedisReconciledAt() == null) {
                entity.markRedisReconciled(at);
                reservations.save(entity);
            }
        });
    }

    private ReservationReconciliationCandidate candidate(FlashSaleReservationJpaEntity entity) {
        return new ReservationReconciliationCandidate(entity.getId(), entity.getCampaignId(), entity.getUserId(),
                entity.getVariantId(), entity.getQuantity(), switch (entity.getStatus()) {
                    case CONFIRMED -> ReservationReconciliationCandidate.Status.CONFIRMED;
                    case RELEASED -> ReservationReconciliationCandidate.Status.RELEASED;
                    case EXPIRED -> ReservationReconciliationCandidate.Status.EXPIRED;
                    case RESERVED -> throw new IllegalStateException("reserved row is not finalized");
                });
    }
}
