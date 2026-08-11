package com.philia.flashsale.flashsale.campaignprojection.application.port.in;

import com.philia.flashsale.flashsale.campaignprojection.application.command.ApplyCampaignScheduledCommand;
import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;

/** Applies an approved CampaignScheduled fact to the local availability projection. */
public interface ProjectCampaignScheduleUseCase {
    CampaignProjectionUpdateResult project(ApplyCampaignScheduledCommand command);
}
