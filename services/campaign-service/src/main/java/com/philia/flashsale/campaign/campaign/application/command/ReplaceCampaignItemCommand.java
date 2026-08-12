package com.philia.flashsale.campaign.campaign.application.command;

import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import java.util.UUID;

/** Input for replacing the single editable item of a draft Campaign. */
public record ReplaceCampaignItemCommand(
        UUID campaignId,
        long expectedVersion,
        UUID variantId,
        CampaignMoney campaignPrice,
        long requestedQuantity,
        long purchaseLimitPerUser) {
}
