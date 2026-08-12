package com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.entity.OutboxEventJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
}
