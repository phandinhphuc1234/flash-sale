package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.command.ScheduleCampaignCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;

/** Driving boundary for scheduling a validated, stock-backed Campaign. */
public interface ScheduleCampaignUseCase {

    CampaignDetailResult schedule(ScheduleCampaignCommand command);
}
