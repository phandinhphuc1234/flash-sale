package com.philia.flashsale.product.campaignvalidation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Product-owned read model used to decide whether a variant can enter a campaign. */
public record CampaignVariantSnapshot(
        UUID productId,
        UUID variantId,
        String sku,
        String productStatus,
        OffsetDateTime publishedAt,
        String variantStatus,
        BigDecimal basePrice,
        String currency) { }
