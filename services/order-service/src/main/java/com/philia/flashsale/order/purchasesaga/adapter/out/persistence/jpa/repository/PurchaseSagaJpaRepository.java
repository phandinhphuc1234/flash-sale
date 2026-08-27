package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data boundary for service-owned Purchase Saga identities. */
public interface PurchaseSagaJpaRepository extends JpaRepository<PurchaseSagaJpaEntity, UUID> {
    Optional<PurchaseSagaJpaEntity> findByOrderId(UUID orderId);
    Optional<PurchaseSagaJpaEntity> findByPurchaseRequestId(UUID purchaseRequestId);
    Optional<PurchaseSagaJpaEntity> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PurchaseSagaJpaEntity s where s.orderId = :orderId")
    Optional<PurchaseSagaJpaEntity> findLockedByOrderId(@Param("orderId") UUID orderId);
}
