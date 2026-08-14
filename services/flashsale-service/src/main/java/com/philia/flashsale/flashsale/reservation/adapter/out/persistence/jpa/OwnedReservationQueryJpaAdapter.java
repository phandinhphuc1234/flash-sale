package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository.FlashSaleReservationJpaRepository;
import com.philia.flashsale.flashsale.reservation.application.port.out.LoadOwnedReservationPort;
import com.philia.flashsale.flashsale.reservation.application.query.GetOwnedReservationQuery;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDetailsResult;
import com.philia.flashsale.flashsale.reservation.domain.model.ReservationStatus;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** PostgreSQL read adapter that never loads a foreign reservation before filtering its owner. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public class OwnedReservationQueryJpaAdapter implements LoadOwnedReservationPort {
    private final FlashSaleReservationJpaRepository reservations;

    public OwnedReservationQueryJpaAdapter(FlashSaleReservationJpaRepository reservations) {
        this.reservations = Objects.requireNonNull(reservations, "reservations");
    }

    @Override
    public Optional<ReservationDetailsResult> loadOwnedReservation(GetOwnedReservationQuery query) {
        return reservations.findByIdAndUserId(query.reservationId(), query.userId())
                .map(this::toResult);
    }

    private ReservationDetailsResult toResult(FlashSaleReservationJpaEntity reservation) {
        return new ReservationDetailsResult(
                reservation.getPurchaseRequestId(),
                reservation.getId(),
                reservation.getCampaignId(),
                reservation.getVariantId(),
                reservation.getSkuSnapshot(),
                reservation.getUnitPrice(),
                reservation.getCurrency(),
                reservation.getQuantity(),
                ReservationStatus.valueOf(reservation.getStatus().name()),
                reservation.getCreatedAt(),
                reservation.getExpiresAt());
    }
}
