package com.philia.flashsale.inventory.stock.adapter.in.web.request;

import com.philia.flashsale.inventory.stock.domain.model.StockAdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** HTTP request used to adjust a variant's physical stock. */
public record AdjustStockRequest(
        @NotNull UUID requestId,
        @NotNull StockAdjustmentType type,
        @Positive long quantity,
        @NotBlank @Size(max = 500) String reason
) {
}
