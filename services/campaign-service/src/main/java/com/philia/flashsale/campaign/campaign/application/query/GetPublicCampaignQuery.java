package com.philia.flashsale.campaign.campaign.application.query;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Public Campaign detail query with an explicit evaluation time. */
public record GetPublicCampaignQuery(UUID campaignId, Instant now) {

    public GetPublicCampaignQuery {
        Objects.requireNonNull(campaignId, "Campaign id is required");
        Objects.requireNonNull(now, "Current time is required");
    }
}
