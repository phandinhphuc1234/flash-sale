package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.application.command.AllocateCampaignInventoryCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;

/** Allocates the full Campaign quota using a durable, retry-stable Inventory request identity. */
public interface AllocateCampaignInventoryPort {

    CampaignInventoryAllocation allocate(AllocateCampaignInventoryCommand command, String traceId);
}
