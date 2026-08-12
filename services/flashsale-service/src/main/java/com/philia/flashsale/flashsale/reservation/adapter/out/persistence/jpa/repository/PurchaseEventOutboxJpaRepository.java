package com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.flashsale.reservation.adapter.out.persistence.jpa.entity.PurchaseEventOutboxJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseEventOutboxJpaRepository extends JpaRepository<PurchaseEventOutboxJpaEntity, UUID> { }
