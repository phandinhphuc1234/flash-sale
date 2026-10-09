package com.philia.flashsale.campaign.campaign.application.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Shopper-safe Campaign view. Exact allocated or remaining quantity is intentionally absent. */
public record PublicCampaignResult(
        UUID id,
        String name,
        PublicCampaignState phase,
        Instant startAt,
        Instant endAt,
        boolean reservable,
        UUID productId,
        UUID variantId,
        String variantSku,
        BigDecimal basePrice,
        BigDecimal campaignPrice,
        String currency,
        long purchaseLimitPerUser,
        boolean presentationAvailable) {
}
