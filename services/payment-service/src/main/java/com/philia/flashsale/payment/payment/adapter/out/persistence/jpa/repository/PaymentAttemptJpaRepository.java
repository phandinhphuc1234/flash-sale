package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentAttemptJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data boundary for Checkout attempts. */
public interface PaymentAttemptJpaRepository extends JpaRepository<PaymentAttemptJpaEntity, UUID> {

    List<PaymentAttemptJpaEntity> findByPayment_IdOrderByAttemptNumberAsc(UUID paymentId);

    long countByPayment_Id(UUID paymentId);

    Optional<PaymentAttemptJpaEntity> findByProviderIdempotencyKey(String providerIdempotencyKey);

    Optional<PaymentAttemptJpaEntity> findByProviderSessionId(String providerSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttemptJpaEntity a where a.id = :id")
    Optional<PaymentAttemptJpaEntity> findLockedById(@Param("id") UUID id);
}
