package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.command.EndCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignLifecycleResult;

/** Driving boundary for automatic Campaign ending. */
public interface EndCampaignUseCase {

    CampaignLifecycleResult end(EndCampaignCommand command);
}
