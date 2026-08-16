package com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Read-only Spring Data boundary for owner-scoped order queries. */
public interface OwnedOrderQueryJpaRepository extends Repository<OrderJpaEntity, UUID> {

    Optional<OrderJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    Page<OrderJpaEntity> findByUserId(UUID userId, Pageable pageable);
}
