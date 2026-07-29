package com.philia.flashsale.inventory.movement.application.result;

import com.philia.flashsale.inventory.movement.domain.model.StockMovement;
import java.util.List;

/** Framework-neutral page returned by the movement application boundary. */
public record StockMovementPage(
        List<StockMovement> content,
        int page,
        int size,
        long totalElements
) {
    public StockMovementPage {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
