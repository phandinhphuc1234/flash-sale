package com.philia.flashsale.campaign.campaign.application.query;

import java.util.UUID;

/** Identifies the Campaign snapshot requested by the trusted Flash Sale service. */
public record GetCampaignSnapshotQuery(UUID campaignId) {
}
