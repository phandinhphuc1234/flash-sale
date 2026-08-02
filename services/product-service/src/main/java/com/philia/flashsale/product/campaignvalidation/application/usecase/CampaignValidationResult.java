package com.philia.flashsale.product.campaignvalidation.application.usecase;

import java.math.BigDecimal;
import java.util.UUID;

public record CampaignValidationResult(
        UUID productId,
        UUID variantId,
        String sku,
        String productStatus,
        String variantStatus,
        boolean sellable,
        BigDecimal basePrice,
        String currency) { }
