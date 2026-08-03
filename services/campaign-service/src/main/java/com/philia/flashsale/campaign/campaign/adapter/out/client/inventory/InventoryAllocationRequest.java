package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import java.util.UUID;

/** Inventory Service wire request; requestId is preserved across every schedule recovery. */
public record InventoryAllocationRequest(
        UUID requestId,
        UUID campaignId,
        UUID variantId,
        long quantity,
        String reason) {
}
