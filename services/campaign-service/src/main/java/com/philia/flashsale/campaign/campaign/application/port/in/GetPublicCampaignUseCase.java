package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.query.GetPublicCampaignQuery;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignResult;

public interface GetPublicCampaignUseCase {

    PublicCampaignResult get(GetPublicCampaignQuery query);
}
