package com.philia.flashsale.campaign.campaign.application.result;

import java.math.BigDecimal;
import java.util.UUID;

/** Product truth required by Campaign without exposing Product transport or persistence models. */
public record ValidatedCampaignVariant(
        UUID productId,
        UUID variantId,
        String sku,
        String productStatus,
        String variantStatus,
        boolean sellable,
        BigDecimal basePrice,
        String currency) {
}
