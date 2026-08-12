package com.philia.flashsale.inventory.movement.adapter.in.web.mapper;

import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.inventory.movement.adapter.in.web.response.MovementResponse;
import com.philia.flashsale.inventory.movement.application.result.StockMovementPage;
import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import org.springframework.stereotype.Component;

/** Converts application movement pages to the shared HTTP pagination envelope. */
@Component
public class MovementWebMapper {
    public PageResponse<MovementResponse> toResponse(StockMovementPage page) {
        var content = page.content().stream().map(this::toResponse).toList();
        return PageResponse.of(content, page.page(), page.size(), page.totalElements());
    }

    private MovementResponse toResponse(StockMovement movement) {
        return new MovementResponse(
                movement.id(), movement.requestId(), movement.allocationId(),
                movement.movementType().name(), movement.onHandDelta(), movement.allocatedDelta(),
                movement.onHandAfter(), movement.allocatedAfter(), movement.reason(),
                movement.createdAt());
    }
}
