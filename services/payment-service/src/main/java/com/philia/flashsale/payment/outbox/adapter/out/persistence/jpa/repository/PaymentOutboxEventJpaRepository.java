package com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.PaymentOutboxEventJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Durable outbox rows and adapter-local lease claim query. */
public interface PaymentOutboxEventJpaRepository extends JpaRepository<PaymentOutboxEventJpaEntity, UUID> {

    @Query(value = """
            select * from payment_outbox_events
            where (status = 'PENDING' and next_attempt_at <= :now)
               or (status = 'IN_PROGRESS' and lease_until < :now)
            order by next_attempt_at, created_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<PaymentOutboxEventJpaEntity> claimCandidates(
            @Param("now") Instant now, @Param("batchSize") int batchSize);

    List<PaymentOutboxEventJpaEntity> findByAggregateIdOrderByAggregateVersionAsc(UUID aggregateId);
}
