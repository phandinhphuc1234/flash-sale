package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity.RegularStockHoldItemJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegularStockHoldItemJpaRepository extends JpaRepository<RegularStockHoldItemJpaEntity, UUID> {
    List<RegularStockHoldItemJpaEntity> findByHoldIdOrderByVariantIdAsc(UUID holdId);

    @Query(value = """
            select coalesce(sum(i.quantity), 0)
            from regular_stock_hold_items i
            join regular_stock_holds h on h.id = i.hold_id
            where i.inventory_item_id = :inventoryItemId
              and h.status = 'HELD'
              and h.expires_at > :at
            """, nativeQuery = true)
    long sumActiveHeldQuantity(@Param("inventoryItemId") UUID inventoryItemId, @Param("at") Instant at);
}
