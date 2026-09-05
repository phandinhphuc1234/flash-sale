package com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.entity.OrderLineJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Service-owned persistence query for immutable Order line snapshots. */
public interface OrderLineJpaRepository extends JpaRepository<OrderLineJpaEntity, UUID> {
    List<OrderLineJpaEntity> findByOrder_IdOrderByIdAsc(UUID orderId);
}
