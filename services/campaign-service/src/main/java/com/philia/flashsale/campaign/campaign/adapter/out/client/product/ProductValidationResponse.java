package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import java.math.BigDecimal;
import java.util.UUID;

/** Narrow Product Service response used only by the Campaign Product adapter. */
public record ProductValidationResponse(
        UUID productId,
        UUID variantId,
        String sku,
        String productStatus,
        String variantStatus,
        boolean sellable,
        BigDecimal basePrice,
        String currency) {
}
