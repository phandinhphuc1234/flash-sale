package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import java.util.Optional;
import java.util.UUID;

/** Outbound boundary for loading Campaign aggregates owned by Campaign Service. */
public interface LoadCampaignPort {

    Optional<Campaign> findById(UUID campaignId);
}
