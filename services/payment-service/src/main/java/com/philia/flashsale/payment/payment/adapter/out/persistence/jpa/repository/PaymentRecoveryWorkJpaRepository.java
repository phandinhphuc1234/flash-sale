package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentRecoveryWorkJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Durable recovery queue with PostgreSQL worker claiming kept at the adapter boundary. */
public interface PaymentRecoveryWorkJpaRepository extends JpaRepository<PaymentRecoveryWorkJpaEntity, UUID> {

    @Query(value = """
            select * from payment_recovery_work
            where (status = 'PENDING' and next_attempt_at <= :now)
               or (status = 'IN_PROGRESS' and lease_until < :now)
            order by next_attempt_at, created_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<PaymentRecoveryWorkJpaEntity> claimCandidates(
            @Param("now") Instant now, @Param("batchSize") int batchSize);

    List<PaymentRecoveryWorkJpaEntity> findByPayment_IdOrderByCreatedAtAsc(UUID paymentId);
}
