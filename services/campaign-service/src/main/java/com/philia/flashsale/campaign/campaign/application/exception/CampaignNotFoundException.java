package com.philia.flashsale.campaign.campaign.application.exception;

import java.util.UUID;

/** Raised when an administrative command targets a missing Campaign. */
public final class CampaignNotFoundException extends RuntimeException {

    private final UUID campaignId;

    public CampaignNotFoundException(UUID campaignId) {
        super("Campaign was not found: " + campaignId);
        this.campaignId = campaignId;
    }

    public UUID campaignId() {
        return campaignId;
    }
}
