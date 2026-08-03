package com.philia.flashsale.campaign.campaign.application.port.in;

import com.philia.flashsale.campaign.campaign.application.query.GetCampaignSnapshotQuery;
import com.philia.flashsale.campaign.campaign.application.result.CampaignSnapshotResult;

/** Inbound boundary for Flash Sale recovery/cache rebuilding. */
public interface GetCampaignSnapshotUseCase {

    CampaignSnapshotResult getSnapshot(GetCampaignSnapshotQuery query);
}
