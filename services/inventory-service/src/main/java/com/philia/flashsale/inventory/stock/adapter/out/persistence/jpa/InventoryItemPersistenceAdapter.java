package com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.entity.InventoryItemJpaEntity;
import com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.mapper.InventoryItemPersistenceMapper;
import com.philia.flashsale.inventory.stock.adapter.out.persistence.jpa.repository.InventoryItemJpaRepository;
import com.philia.flashsale.inventory.stock.application.port.out.LoadInventoryItemPort;
import com.philia.flashsale.inventory.stock.application.port.out.SaveInventoryItemPort;
import com.philia.flashsale.inventory.stock.domain.model.InventoryItem;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InventoryItemPersistenceAdapter
        implements LoadInventoryItemPort, SaveInventoryItemPort {
    private final InventoryItemJpaRepository repository;
    private final InventoryItemPersistenceMapper mapper;

    public InventoryItemPersistenceAdapter(
            InventoryItemJpaRepository repository,
            InventoryItemPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<InventoryItem> findByVariantId(UUID variantId) {
        return repository.findByVariantId(variantId).map(mapper::toDomain);
    }

    @Override
    public Optional<InventoryItem> findByVariantIdForUpdate(UUID variantId) {
        return repository.findWithLockByVariantId(variantId).map(mapper::toDomain);
    }

    @Override
    public InventoryItem save(InventoryItem item) {
        InventoryItemJpaEntity entity = repository.findById(item.id())
                .orElseGet(() -> mapper.newEntity(item));
        mapper.apply(item, entity);
        return mapper.toDomain(repository.save(entity));
    }
}
