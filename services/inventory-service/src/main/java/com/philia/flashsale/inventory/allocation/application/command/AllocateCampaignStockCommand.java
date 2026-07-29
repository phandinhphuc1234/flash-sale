package com.philia.flashsale.inventory.allocation.application.command;

import java.util.UUID;

public record AllocateCampaignStockCommand(
        UUID requestId,
        UUID campaignId,
        UUID variantId,
        long quantity,
        String reason
) {
}
