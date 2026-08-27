package com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for durable Order identities. */
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {
    Optional<OrderJpaEntity> findByPurchaseRequestId(UUID purchaseRequestId);

    Optional<OrderJpaEntity> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderJpaEntity o where o.id = :id")
    Optional<OrderJpaEntity> findLockedById(@Param("id") UUID id);
}
