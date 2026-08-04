package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.domain.event.CampaignActivated;
import java.time.Instant;

/** Outbound boundary for durable lifecycle event records. */
public interface SaveCampaignLifecycleEventPort {

    void saveActivated(CampaignActivated event, String traceId, Instant createdAt);
}
