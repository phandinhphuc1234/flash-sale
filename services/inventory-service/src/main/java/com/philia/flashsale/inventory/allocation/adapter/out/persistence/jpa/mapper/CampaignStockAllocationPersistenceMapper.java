package com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.mapper;

import com.philia.flashsale.inventory.allocation.adapter.out.persistence.jpa.entity.CampaignStockAllocationJpaEntity;
import com.philia.flashsale.inventory.allocation.domain.model.CampaignStockAllocation;
import org.springframework.stereotype.Component;

@Component
public class CampaignStockAllocationPersistenceMapper {
    public CampaignStockAllocation toDomain(CampaignStockAllocationJpaEntity entity) {
        return new CampaignStockAllocation(
                entity.getId(), entity.getRequestId(), entity.getCampaignId(),
                entity.getInventoryItemId(), entity.getVariantId(), entity.getAllocatedQuantity(),
                entity.getSoldQuantity(), entity.getReturnedQuantity(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getReconciledAt());
    }

    public CampaignStockAllocationJpaEntity newEntity(CampaignStockAllocation allocation) {
        return new CampaignStockAllocationJpaEntity(
                allocation.id(), allocation.requestId(), allocation.campaignId(),
                allocation.inventoryItemId(), allocation.variantId(), allocation.allocatedQuantity(),
                allocation.soldQuantity(), allocation.returnedQuantity(), allocation.status(),
                allocation.createdAt(), allocation.updatedAt(), allocation.reconciledAt());
    }

    public void apply(
            CampaignStockAllocation allocation,
            CampaignStockAllocationJpaEntity entity) {
        entity.applyState(
                allocation.soldQuantity(), allocation.returnedQuantity(), allocation.status(),
                allocation.updatedAt(), allocation.reconciledAt());
    }
}
