package com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.entity.PaymentCommandInboxJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Durable deduplication boundary for PaymentRequested commands. */
public interface PaymentCommandInboxJpaRepository extends JpaRepository<PaymentCommandInboxJpaEntity, UUID> {

    Optional<PaymentCommandInboxJpaEntity> findByOrderId(UUID orderId);
}
