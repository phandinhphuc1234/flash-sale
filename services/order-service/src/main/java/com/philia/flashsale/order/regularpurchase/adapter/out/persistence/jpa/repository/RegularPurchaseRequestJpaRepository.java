package com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.entity.RegularPurchaseRequestJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository boundary for Order-owned regular-purchase idempotency records. */
public interface RegularPurchaseRequestJpaRepository extends JpaRepository<RegularPurchaseRequestJpaEntity, UUID> {
    Optional<RegularPurchaseRequestJpaEntity> findByShopperIdAndIdempotencyKey(UUID shopperId, String idempotencyKey);
}
