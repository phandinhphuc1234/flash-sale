package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for service-owned Purchase Saga identities. */
public interface PurchaseSagaJpaRepository extends JpaRepository<PurchaseSagaJpaEntity, UUID> {
    Optional<PurchaseSagaJpaEntity> findByOrderId(UUID orderId);
    Optional<PurchaseSagaJpaEntity> findByPurchaseRequestId(UUID purchaseRequestId);
    Optional<PurchaseSagaJpaEntity> findByReservationId(UUID reservationId);
}
