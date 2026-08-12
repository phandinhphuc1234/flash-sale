package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.command.ReplaceCampaignMetadataCommand;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;

/** Inbound boundary for replacing draft metadata with optimistic version protection. */
public interface ReplaceCampaignMetadataUseCase {

    CampaignDetailResult replaceMetadata(ReplaceCampaignMetadataCommand command);
}
