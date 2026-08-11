package com.philia.flashsale.flashsale.campaignprojection.application.port.in;

import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignActivatedCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;

/** Applies an approved CampaignActivated fact without initializing missing quota. */
public interface ProjectCampaignActivationUseCase {
    CampaignProjectionUpdateResult project(ApplyCampaignActivatedCommand command);
}
