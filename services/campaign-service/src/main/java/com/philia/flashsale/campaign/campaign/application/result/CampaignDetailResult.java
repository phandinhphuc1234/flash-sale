package com.philia.flashsale.campaign.campaign.application.result;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.time.Instant;
import java.util.UUID;

/** Stable application result used by future HTTP adapters for Campaign detail responses. */
public record CampaignDetailResult(
        UUID id,
        String code,
        String name,
        CampaignStatus status,
        Instant startAt,
        Instant endAt,
        Instant scheduledAt,
        Instant activatedAt,
        Instant endedAt,
        long version,
        CampaignItemResult item,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt) {

    public static CampaignDetailResult from(Campaign campaign) {
        return new CampaignDetailResult(
                campaign.id(),
                campaign.code(),
                campaign.name(),
                campaign.status(),
                campaign.startAt(),
                campaign.endAt(),
                campaign.scheduledAt(),
                campaign.activatedAt(),
                campaign.endedAt(),
                campaign.version(),
                CampaignItemResult.from(campaign.item()),
                campaign.createdBy(),
                campaign.updatedBy(),
                campaign.createdAt(),
                campaign.updatedAt());
    }
}
