package com.philia.flashsale.campaign.campaign.adapter.in.web.internal;

import com.philia.flashsale.campaign.campaign.adapter.in.web.internal.response.CampaignSnapshotResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** Transport contract for the private Flash Sale recovery endpoint. */
public interface CampaignSnapshotApi {

    ResponseEntity<CampaignSnapshotResponse> getSnapshot(UUID campaignId);
}
