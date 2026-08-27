package com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.order.purchasesaga.adapter.out.persistence.jpa.entity.PurchaseSagaInboxJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Durable inbox boundary for Payment and reservation participant facts. */
public interface PurchaseSagaInboxJpaRepository extends JpaRepository<PurchaseSagaInboxJpaEntity, UUID> {
    Optional<PurchaseSagaInboxJpaEntity> findByEventId(UUID eventId);
}
