package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.util.Optional;
import java.util.UUID;

/** Loads only complete, non-draft Campaign state for the internal recovery boundary. */
public interface LoadCampaignSnapshotPort {

    Optional<Campaign> findCompleteSnapshotById(UUID campaignId);
}
