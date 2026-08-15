package com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderConsumerInboxJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for inbound event identity and fingerprint lookup. */
public interface OrderConsumerInboxJpaRepository extends JpaRepository<OrderConsumerInboxJpaEntity, UUID> {
    Optional<OrderConsumerInboxJpaEntity> findFirstByPurchaseRequestId(UUID purchaseRequestId);

    Optional<OrderConsumerInboxJpaEntity> findFirstByReservationId(UUID reservationId);
}
