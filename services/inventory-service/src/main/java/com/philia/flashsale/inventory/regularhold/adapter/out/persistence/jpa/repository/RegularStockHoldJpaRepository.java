package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity.RegularStockHoldJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegularStockHoldJpaRepository extends JpaRepository<RegularStockHoldJpaEntity, UUID> {
    Optional<RegularStockHoldJpaEntity> findByPurchaseRequestId(UUID purchaseRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hold from RegularStockHoldJpaEntity hold where hold.id = :holdId")
    Optional<RegularStockHoldJpaEntity> findWithLockById(@Param("holdId") UUID holdId);

    Optional<RegularStockHoldJpaEntity> findByOrderId(UUID orderId);

    /** Claims only due HELD rows; SKIP LOCKED keeps concurrent expiry workers bounded and non-blocking. */
    @Query(value = """
            SELECT * FROM regular_stock_holds
            WHERE status = 'HELD' AND expires_at <= :now
            ORDER BY expires_at ASC, id ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<RegularStockHoldJpaEntity> findDueForExpiry(@Param("now") Instant now, @Param("limit") int limit);
}
