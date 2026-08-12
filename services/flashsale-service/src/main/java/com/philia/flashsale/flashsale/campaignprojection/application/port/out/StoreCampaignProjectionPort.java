package com.philia.flashsale.flashsale.campaignprojection.application.port.out;

import com.philia.flashsale.flashsale.campaignprojection.application.result.CampaignProjectionUpdateResult;
import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignSaleProjection;
import java.time.Instant;
import java.util.UUID;

/** Atomic capability for applying version-guarded Campaign facts to Redis. */
public interface StoreCampaignProjectionPort {
    CampaignProjectionUpdateResult applyScheduled(CampaignSaleProjection projection);

    CampaignProjectionUpdateResult applyActivated(
            UUID campaignId, long aggregateVersion, Instant startsAt, Instant endsAt, Instant updatedAt);

    CampaignProjectionUpdateResult applyRecovered(CampaignSaleProjection projection);
}
