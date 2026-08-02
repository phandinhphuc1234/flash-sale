package com.philia.flashsale.campaign.campaign.adapter.in.web.admin.response;

import java.math.BigDecimal;
import java.util.UUID;

/** HTTP representation of the optional one-item Campaign snapshot. */
public record CampaignItemResponse(
        UUID productId,
        UUID variantId,
        UUID inventoryAllocationId,
        String variantSku,
        BigDecimal basePrice,
        String currency,
        BigDecimal campaignPrice,
        long requestedQuantity,
        long allocatedQuantity,
        long purchaseLimitPerUser) {
}
