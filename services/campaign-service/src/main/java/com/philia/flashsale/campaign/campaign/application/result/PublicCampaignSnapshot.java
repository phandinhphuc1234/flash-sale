package com.philia.flashsale.campaign.campaign.application.result;

import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Campaign-owned data loaded for public presentation before lifecycle derivation. */
public record PublicCampaignSnapshot(
        UUID id,
        String name,
        CampaignStatus storedStatus,
        Instant startAt,
        Instant endAt,
        UUID productId,
        UUID variantId,
        String variantSku,
        BigDecimal basePrice,
        BigDecimal campaignPrice,
        String currency,
        long purchaseLimitPerUser) {
}
