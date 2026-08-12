package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.query.GetCampaignDetailQuery;
import com.philia.flashsale.campaign.campaign.application.result.CampaignDetailResult;

/** Inbound boundary for reading administrative Campaign detail. */
public interface GetCampaignDetailUseCase {

    CampaignDetailResult getDetail(GetCampaignDetailQuery query);
}
