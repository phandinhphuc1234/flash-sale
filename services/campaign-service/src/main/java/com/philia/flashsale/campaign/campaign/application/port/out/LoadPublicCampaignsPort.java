package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.application.query.PublicCampaignFilter;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignPage;
import com.philia.flashsale.campaign.campaign.application.result.PublicCampaignSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LoadPublicCampaignsPort {

    PublicCampaignPage<PublicCampaignSnapshot> loadVisible(
            PublicCampaignFilter filter, Instant now, int page, int size);

    Optional<PublicCampaignSnapshot> loadPublicDetail(UUID campaignId);
}
