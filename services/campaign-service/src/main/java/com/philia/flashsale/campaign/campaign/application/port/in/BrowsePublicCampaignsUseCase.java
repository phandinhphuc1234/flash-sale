package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.query.BrowsePublicCampaignsQuery;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignPage;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignResult;

public interface BrowsePublicCampaignsUseCase {

    PublicCampaignPage<PublicCampaignResult> browse(BrowsePublicCampaignsQuery query);
}
