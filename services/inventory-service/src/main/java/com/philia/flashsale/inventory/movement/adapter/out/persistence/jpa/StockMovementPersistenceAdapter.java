package com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa;

import com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa.mapper.StockMovementPersistenceMapper;
import com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa.repository.StockMovementJpaRepository;
import com.philia.flashsale.inventory.movement.application.port.out.LoadStockMovementPort;
import com.philia.flashsale.inventory.movement.application.port.out.RecordStockMovementPort;
import com.philia.flashsale.inventory.movement.application.result.StockMovementPage;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class StockMovementPersistenceAdapter
        implements LoadStockMovementPort, RecordStockMovementPort {
    private final StockMovementJpaRepository repository;
    private final StockMovementPersistenceMapper mapper;

    public StockMovementPersistenceAdapter(
            StockMovementJpaRepository repository,
            StockMovementPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public StockMovement record(StockMovement movement) {
        return mapper.toDomain(repository.save(mapper.toEntity(movement)));
    }

    @Override
    public StockMovementPage findByInventoryItemId(UUID inventoryItemId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        var result = repository.findByInventoryItemId(inventoryItemId, pageable);
        return new StockMovementPage(
                result.getContent().stream().map(mapper::toDomain).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public Optional<StockMovement> findByRequestId(UUID requestId) {
        return repository.findByRequestId(requestId).map(mapper::toDomain);
    }
}
