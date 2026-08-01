package com.philia.flashsale.campaign.campaign.application.result;

import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import java.math.BigDecimal;
import java.util.UUID;

/** Application-facing projection of the Campaign item without exposing persistence types. */
public record CampaignItemResult(
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

    public static CampaignItemResult from(CampaignItem item) {
        if (item == null) {
            return null;
        }
        return new CampaignItemResult(
                item.productId(),
                item.variantId(),
                item.inventoryAllocationId(),
                item.variantSkuSnapshot(),
                item.basePriceSnapshot() == null ? null : item.basePriceSnapshot().amount(),
                item.basePriceSnapshot() == null ? null : item.basePriceSnapshot().currency(),
                item.campaignPrice().amount(),
                item.requestedQuantity(),
                item.allocatedQuantity(),
                item.purchaseLimitPerUser());
    }
}
