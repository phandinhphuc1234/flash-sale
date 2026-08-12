package com.philia.flashsale.inventory.stock.application.command;

import com.philia.flashsale.inventory.stock.domain.model.StockAdjustmentType;
import java.util.UUID;

/** Transport-independent intent to change physical stock. */
public record AdjustStockCommand(
        UUID requestId,
        UUID variantId,
        StockAdjustmentType type,
        long quantity,
        String reason
) {
}
