package com.philia.flashsale.campaign.campaign.application.result;

import java.util.UUID;

/** Inventory-owned allocation facts accepted by Campaign after response identity verification. */
public record CampaignInventoryAllocation(
        UUID inventoryAllocationId,
        UUID requestId,
        UUID campaignId,
        UUID variantId,
        long allocatedQuantity,
        long soldQuantity,
        long returnedQuantity,
        String status) {
}
