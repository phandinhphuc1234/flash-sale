package com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.entity.OutboxEventJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event from OutboxEventJpaEntity event
            where event.aggregateType = :aggregateType
              and event.status = 'PENDING'
              and event.nextAttemptAt <= :now
              and (event.claimUntil is null or event.claimUntil < :now)
            order by event.occurredAt asc, event.id asc
            """)
    List<OutboxEventJpaEntity> findDueForClaim(@Param("aggregateType") String aggregateType,
            @Param("now") Instant now, Pageable pageable);
}
