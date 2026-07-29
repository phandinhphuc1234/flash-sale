package com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.repository;

import com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.entity.CampaignStockAllocationJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignStockAllocationJpaRepository
        extends JpaRepository<CampaignStockAllocationJpaEntity, UUID> {
    Optional<CampaignStockAllocationJpaEntity> findByRequestId(UUID requestId);
}
