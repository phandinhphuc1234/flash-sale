package com.philia.flashsale.flashsale.campaignprojection.application.port.in;

import com.philia.flashsale.flashsale.campaignprojection.application.command.RecoverCampaignProjectionCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;

/** Rebuilds one projection from the approved Campaign snapshot recovery boundary. */
public interface RecoverCampaignProjectionUseCase {
    CampaignProjectionUpdateResult recover(RecoverCampaignProjectionCommand command);
}
