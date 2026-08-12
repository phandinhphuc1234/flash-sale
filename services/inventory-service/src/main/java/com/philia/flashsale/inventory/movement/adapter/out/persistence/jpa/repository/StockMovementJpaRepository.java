package com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa.entity.StockMovementJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementJpaEntity, UUID> {
    Page<StockMovementJpaEntity> findByInventoryItemId(UUID inventoryItemId, Pageable pageable);

    Optional<StockMovementJpaEntity> findByRequestId(UUID requestId);
}
