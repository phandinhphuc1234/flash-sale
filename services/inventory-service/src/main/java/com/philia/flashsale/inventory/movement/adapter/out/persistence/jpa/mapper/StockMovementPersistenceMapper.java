package com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa.mapper;

import com.philia.flashsale.inventory.movement.adapter.out.persistence.jpa.entity.StockMovementJpaEntity;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import org.springframework.stereotype.Component;

@Component
public class StockMovementPersistenceMapper {
    public StockMovementJpaEntity toEntity(StockMovement movement) {
        return new StockMovementJpaEntity(
                movement.id(), movement.requestId(), movement.inventoryItemId(),
                movement.allocationId(), movement.referenceType(), movement.referenceId(),
                movement.movementType(), movement.onHandDelta(), movement.allocatedDelta(),
                movement.onHandAfter(), movement.allocatedAfter(), movement.reason(),
                movement.createdAt());
    }

    public StockMovement toDomain(StockMovementJpaEntity entity) {
        return new StockMovement(
                entity.getId(), entity.getRequestId(), entity.getInventoryItemId(),
                entity.getAllocationId(), entity.getReferenceType(), entity.getReferenceId(),
                entity.getMovementType(), entity.getOnHandDelta(), entity.getAllocatedDelta(),
                entity.getOnHandAfter(), entity.getAllocatedAfter(), entity.getReason(),
                entity.getCreatedAt());
    }
}
