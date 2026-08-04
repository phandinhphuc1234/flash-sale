package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.command.ActivateCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignLifecycleResult;

/** Driving boundary for automatic or manual Campaign activation. */
public interface ActivateCampaignUseCase {

    CampaignLifecycleResult activate(ActivateCampaignCommand command);
}
