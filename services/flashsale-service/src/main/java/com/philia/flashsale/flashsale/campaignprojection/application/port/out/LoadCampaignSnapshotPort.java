package com.philia.flashsale.flashsale.campaignprojection.application.port.out;

import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.util.Optional;
import java.util.UUID;

/** Loads a service-owned, validated Campaign snapshot for control-plane recovery. */
public interface LoadCampaignSnapshotPort {
    Optional<CampaignSaleProjection> load(UUID campaignId);
}
