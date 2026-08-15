package com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderCreationOutboxJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data boundary for stable Order-created publication identities. */
public interface OrderCreationOutboxJpaRepository extends JpaRepository<OrderCreationOutboxJpaEntity, UUID> {
    Optional<OrderCreationOutboxJpaEntity> findByAggregateIdAndAggregateVersionAndEventType(
            UUID aggregateId, long aggregateVersion, String eventType);
}
