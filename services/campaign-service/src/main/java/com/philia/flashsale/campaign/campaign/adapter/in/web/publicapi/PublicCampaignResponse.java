package com.philia.flashsale.campaign.campaign.adapter.in.web.publicapi;

import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignResult;
import java.time.Instant;
import java.util.UUID;

/** Public Campaign wire model. Operational allocation and recovery fields are intentionally absent. */
public record PublicCampaignResponse(
        UUID id,
        String name,
        String phase,
        Instant startAt,
        Instant endAt,
        boolean reservable,
        UUID productId,
        UUID variantId,
        String variantSku,
        String basePrice,
        String campaignPrice,
        String currency,
        long purchaseLimitPerUser,
        boolean presentationAvailable) {

    static PublicCampaignResponse from(PublicCampaignResult result) {
        return new PublicCampaignResponse(
                result.id(),
                result.name(),
                result.phase().name(),
                result.startAt(),
                result.endAt(),
                result.reservable(),
                result.productId(),
                result.variantId(),
                result.variantSku(),
                result.basePrice() == null ? null : result.basePrice().toPlainString(),
                result.campaignPrice() == null ? null : result.campaignPrice().toPlainString(),
                result.currency(),
                result.purchaseLimitPerUser(),
                result.presentationAvailable());
    }
}
