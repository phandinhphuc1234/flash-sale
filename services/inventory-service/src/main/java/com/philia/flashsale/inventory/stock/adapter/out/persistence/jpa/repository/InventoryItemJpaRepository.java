package com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.entity.InventoryItemJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemJpaRepository extends JpaRepository<InventoryItemJpaEntity, UUID> {
    Page<InventoryItemJpaEntity> findAll(Pageable pageable);
    Optional<InventoryItemJpaEntity> findByVariantId(UUID variantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItemJpaEntity i where i.variantId = :variantId")
    Optional<InventoryItemJpaEntity> findWithLockByVariantId(@Param("variantId") UUID variantId);
}
