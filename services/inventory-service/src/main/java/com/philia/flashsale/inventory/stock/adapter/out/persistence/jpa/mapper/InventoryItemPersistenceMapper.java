package com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.mapper;

import com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.entity.InventoryItemJpaEntity;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import org.springframework.stereotype.Component;

/** Maps only between the stock domain aggregate and its JPA representation. */
@Component
public class InventoryItemPersistenceMapper {
    public InventoryItem toDomain(InventoryItemJpaEntity entity) {
        return new InventoryItem(
                entity.getId(), entity.getVariantId(), entity.getSkuSnapshot(),
                entity.getOnHandQuantity(), entity.getCampaignAllocatedQuantity(),
                entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public InventoryItemJpaEntity newEntity(InventoryItem item) {
        return new InventoryItemJpaEntity(
                item.id(), item.variantId(), item.skuSnapshot(), item.onHandQuantity(),
                item.campaignAllocatedQuantity(), item.version(), item.createdAt(), item.updatedAt());
    }

    public void apply(InventoryItem item, InventoryItemJpaEntity entity) {
        entity.applyState(
                item.skuSnapshot(), item.onHandQuantity(), item.campaignAllocatedQuantity(),
                item.version(), item.updatedAt());
    }
}
