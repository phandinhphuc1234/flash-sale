package com.philia.flashsale.campaign.campaign.application.exception;

import java.util.UUID;

/** Raised when a draft mutation races with an active schedule operation. */
public final class CampaignOperationInProgressException extends RuntimeException {

    private final UUID campaignId;

    public CampaignOperationInProgressException(UUID campaignId) {
        super("A Campaign operation is already in progress: " + campaignId);
        this.campaignId = campaignId;
    }

    public UUID campaignId() {
        return campaignId;
    }
}
