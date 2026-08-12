package com.philia.flashsale.campaign.campaign.application.result;

/** Result of a lifecycle attempt; a losing automatic worker is an intentional no-op. */
public record CampaignLifecycleResult(CampaignDetailResult campaign, boolean transitioned) {
}
