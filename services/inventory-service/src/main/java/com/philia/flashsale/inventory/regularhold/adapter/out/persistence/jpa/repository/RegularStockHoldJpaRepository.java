package com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.regularhold.adapter.out.persistence.jpa.entity.RegularStockHoldJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
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
}
