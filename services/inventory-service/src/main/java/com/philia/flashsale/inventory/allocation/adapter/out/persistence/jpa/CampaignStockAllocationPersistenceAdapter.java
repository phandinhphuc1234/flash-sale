package com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.entity.CampaignStockAllocationJpaEntity;
import com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.mapper.CampaignStockAllocationPersistenceMapper;
import com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.repository.CampaignStockAllocationJpaRepository;
import com.philia.flashsale.inventory.allocation.application.port.out.LoadCampaignStockAllocationPort;
import com.philia.flashsale.inventory.allocation.application.port.out.SaveCampaignStockAllocationPort;
import com.philia.flashsale.inventory.allocation.domain.model.CampaignStockAllocation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CampaignStockAllocationPersistenceAdapter
        implements LoadCampaignStockAllocationPort, SaveCampaignStockAllocationPort {
    private final CampaignStockAllocationJpaRepository repository;
    private final CampaignStockAllocationPersistenceMapper mapper;

    public CampaignStockAllocationPersistenceAdapter(
            CampaignStockAllocationJpaRepository repository,
            CampaignStockAllocationPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<CampaignStockAllocation> findByRequestId(UUID requestId) {
        return repository.findByRequestId(requestId).map(mapper::toDomain);
    }

    @Override
    public CampaignStockAllocation save(CampaignStockAllocation allocation) {
        CampaignStockAllocationJpaEntity entity = repository.findById(allocation.id())
                .orElseGet(() -> mapper.newEntity(allocation));
        mapper.apply(allocation, entity);
        return mapper.toDomain(repository.save(entity));
    }
}
