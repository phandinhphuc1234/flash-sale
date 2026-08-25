package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.FlashSaleReservationJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FlashSaleReservationJpaRepository extends JpaRepository<FlashSaleReservationJpaEntity, UUID> {
    Optional<FlashSaleReservationJpaEntity> findByPurchaseRequestId(UUID purchaseRequestId);
    Optional<FlashSaleReservationJpaEntity> findByIdAndUserId(UUID id, UUID userId);
    List<FlashSaleReservationJpaEntity> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            FlashSaleReservationJpaEntity.Status status, Instant at);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from FlashSaleReservationJpaEntity reservation where reservation.id = :id")
    Optional<FlashSaleReservationJpaEntity> findWithLockById(@Param("id") UUID id);
}
