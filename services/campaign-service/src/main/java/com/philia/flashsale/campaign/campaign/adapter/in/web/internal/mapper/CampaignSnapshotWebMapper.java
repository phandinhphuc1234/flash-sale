package com.philia.flashsale.campaign.campaign.adapter.in.web.internal.mapper;

import com.philia.flashsale.campaign.campaign.adapter.in.web.internal.response.CampaignSnapshotResponse;
import com.philia.flashsale.campaign.campaign.application.result.CampaignSnapshotResult;
import org.springframework.stereotype.Component;

/** Maps the application snapshot result to the private HTTP representation. */
@Component
public class CampaignSnapshotWebMapper {

    public CampaignSnapshotResponse toResponse(CampaignSnapshotResult result) {
        return CampaignSnapshotResponse.from(result);
    }
}
