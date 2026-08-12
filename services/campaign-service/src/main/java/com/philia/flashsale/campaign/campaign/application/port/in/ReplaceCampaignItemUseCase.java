package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignItemCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;

/** Inbound boundary for replacing the single draft item. */
public interface ReplaceCampaignItemUseCase {

    CampaignDetailResult replaceItem(ReplaceCampaignItemCommand command);
}
