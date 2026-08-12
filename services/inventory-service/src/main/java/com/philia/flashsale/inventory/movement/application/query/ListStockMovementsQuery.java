package com.philia.flashsale.inventory.movement.application.query;

import java.util.UUID;

/** Application paging query; it deliberately contains no Spring Data type. */
public record ListStockMovementsQuery(UUID variantId, int page, int size) {
    public ListStockMovementsQuery {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "page must be >= 0 and size must be between 1 and 100");
        }
    }
}
