package com.philia.flashsale.product.campaignvalidation.adapter.in.web;

import com.philia.flashsale.product.campaignvalidation.application.usecase.CampaignValidationResult;
import java.math.BigDecimal;
import java.util.UUID;

public record CampaignValidationResponse(
        UUID productId,
        UUID variantId,
        String sku,
        String productStatus,
        String variantStatus,
        boolean sellable,
        BigDecimal basePrice,
        String currency) {
    static CampaignValidationResponse from(CampaignValidationResult result) {
        return new CampaignValidationResponse(result.productId(), result.variantId(), result.sku(),
                result.productStatus(), result.variantStatus(), result.sellable(), result.basePrice(), result.currency());
    }
}
