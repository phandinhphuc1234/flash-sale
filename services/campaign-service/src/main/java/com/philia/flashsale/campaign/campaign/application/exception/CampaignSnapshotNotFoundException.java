package com.philia.flashsale.campaign.campaign.application.exception;

import java.util.UUID;

/** Signals that a Campaign is missing, draft, or not complete enough for recovery. */
public class CampaignSnapshotNotFoundException extends RuntimeException {

    public CampaignSnapshotNotFoundException(UUID campaignId) {
        super("Campaign snapshot was not found: " + campaignId);
    }
}
