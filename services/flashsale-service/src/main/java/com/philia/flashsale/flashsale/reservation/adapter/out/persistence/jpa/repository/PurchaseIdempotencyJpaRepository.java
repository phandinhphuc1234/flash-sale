package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseIdempotencyJpaEntity;
import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseIdempotencyJpaId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseIdempotencyJpaRepository extends JpaRepository<PurchaseIdempotencyJpaEntity, PurchaseIdempotencyJpaId> {
    @Query("select record from PurchaseIdempotencyJpaEntity record where record.retainedUntil <= :at order by record.retainedUntil asc")
    List<PurchaseIdempotencyJpaEntity> findTop100ByRetainedUntilLessThanEqualOrderByRetainedUntilAsc(
            @Param("at") Instant at);
}
