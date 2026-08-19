package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentClientIdempotencyJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Durable owner operation-key boundary; only digests are queried. */
public interface PaymentClientIdempotencyJpaRepository extends JpaRepository<PaymentClientIdempotencyJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentClientIdempotencyJpaEntity i where i.operation = :operation and i.keyDigest = :keyDigest")
    Optional<PaymentClientIdempotencyJpaEntity> findLockedByOperationAndKeyDigest(
            @Param("operation") String operation, @Param("keyDigest") String keyDigest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PaymentClientIdempotencyJpaEntity i where i.id = :id")
    Optional<PaymentClientIdempotencyJpaEntity> findLockedById(@Param("id") UUID id);
}
