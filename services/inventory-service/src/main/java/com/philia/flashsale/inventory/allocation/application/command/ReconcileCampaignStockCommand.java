package com.philia.flashsale.inventory.allocation.application.command;

import java.util.UUID;

public record ReconcileCampaignStockCommand(
        UUID requestId,
        long soldQuantity,
        long returnedQuantity,
        String reason
) {
}
