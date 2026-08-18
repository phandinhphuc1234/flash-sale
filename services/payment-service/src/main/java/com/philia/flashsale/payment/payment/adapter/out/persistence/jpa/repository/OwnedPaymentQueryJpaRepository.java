package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentJpaEntity;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.projection.OwnedPaymentProjection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Read-only Spring Data boundary; owner filtering stays in PostgreSQL predicates. */
public interface OwnedPaymentQueryJpaRepository extends Repository<PaymentJpaEntity, UUID> {
    Optional<OwnedPaymentProjection> findByIdAndUserId(UUID id, UUID userId);

    Optional<OwnedPaymentProjection> findByOrderIdAndUserId(UUID orderId, UUID userId);
}
