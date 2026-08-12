package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.mapper;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseIdempotencyJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseRequestJpaEntity;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.time.Instant;

/** Explicit adapter-local mapping between the Redis winner snapshot and durable rows. */
public final class ReservationPersistenceMapper {
    public PurchaseRequestJpaEntity acceptedRequest(AcceptedReservationSnapshot snapshot) {
        return PurchaseRequestJpaEntity.accepted(snapshot);
    }

    public PurchaseRequestJpaEntity expiredRequest(AcceptedReservationSnapshot snapshot, Instant now) {
        return PurchaseRequestJpaEntity.expired(snapshot, now);
    }

    public FlashSaleReservationJpaEntity reservedReservation(AcceptedReservationSnapshot snapshot) {
        return FlashSaleReservationJpaEntity.reserved(snapshot);
    }

    public PurchaseIdempotencyJpaEntity idempotency(AcceptedReservationSnapshot snapshot) {
        return PurchaseIdempotencyJpaEntity.accepted(snapshot);
    }

    public PurchaseEventOutboxJpaEntity acceptedOutbox(AcceptedReservationSnapshot snapshot) {
        return PurchaseEventOutboxJpaEntity.accepted(snapshot);
    }
}
