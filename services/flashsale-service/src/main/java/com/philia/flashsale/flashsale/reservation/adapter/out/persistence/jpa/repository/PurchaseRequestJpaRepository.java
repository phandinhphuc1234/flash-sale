package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseRequestJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRequestJpaRepository extends JpaRepository<PurchaseRequestJpaEntity, UUID> {
    List<PurchaseRequestJpaEntity> findTop100ByOutcomeAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            PurchaseRequestJpaEntity.Outcome outcome, Instant at);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseRequest from PurchaseRequestJpaEntity purchaseRequest where purchaseRequest.id = :id")
    java.util.Optional<PurchaseRequestJpaEntity> findWithLockById(@Param("id") UUID id);
}
