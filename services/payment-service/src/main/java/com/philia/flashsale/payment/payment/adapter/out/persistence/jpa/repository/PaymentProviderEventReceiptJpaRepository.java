package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentProviderEventReceiptJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Receipt storage plus adapter-local worker claim query. */
public interface PaymentProviderEventReceiptJpaRepository
        extends JpaRepository<PaymentProviderEventReceiptJpaEntity, UUID> {

    Optional<PaymentProviderEventReceiptJpaEntity> findByProviderEventId(String providerEventId);

    @Query(value = """
            select * from payment_provider_event_receipts
            where (processing_status = 'PENDING' and next_attempt_at <= :now)
               or (processing_status = 'IN_PROGRESS' and lease_until < :now)
            order by next_attempt_at, verified_at
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<PaymentProviderEventReceiptJpaEntity> claimCandidates(
            @Param("now") Instant now, @Param("batchSize") int batchSize);
}
