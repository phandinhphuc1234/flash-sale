package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import java.util.UUID;

/** Narrow Inventory allocation payload parsed from the shared success envelope. */
public record InventoryAllocationResponse(
        UUID id,
        UUID requestId,
        UUID campaignId,
        UUID variantId,
        long allocatedQuantity,
        long soldQuantity,
        long returnedQuantity,
        String status) {
}
