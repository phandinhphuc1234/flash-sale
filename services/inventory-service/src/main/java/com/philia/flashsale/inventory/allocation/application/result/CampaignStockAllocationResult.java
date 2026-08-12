package com.philia.flashsale.inventory.allocation.application.result;

import com.philia.flashsale.inventory.allocation.domain.model.CampaignStockAllocation;
import java.util.UUID;

public record CampaignStockAllocationResult(
        UUID id,
        UUID requestId,
        UUID campaignId,
        UUID variantId,
        long allocatedQuantity,
        long soldQuantity,
        long returnedQuantity,
        String status
) {
    public static CampaignStockAllocationResult from(CampaignStockAllocation allocation) {
        return new CampaignStockAllocationResult(
                allocation.id(), allocation.requestId(), allocation.campaignId(), allocation.variantId(),
                allocation.allocatedQuantity(), allocation.soldQuantity(), allocation.returnedQuantity(),
                allocation.status().name());
    }
}
