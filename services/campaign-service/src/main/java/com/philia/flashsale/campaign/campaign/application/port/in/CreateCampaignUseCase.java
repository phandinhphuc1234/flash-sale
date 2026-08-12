package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.command.CreateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;

/** Inbound boundary for creating an editable Campaign draft. */
public interface CreateCampaignUseCase {

    CampaignDetailResult create(CreateCampaignCommand command);
}
