package com.philia.flashsale.campaign.outbox.application.port.out;

import com.philia.flashsale.campaign.outbox.application.model.OutboxRequeueResult;
import java.time.Instant;
import java.util.UUID;

/** Requeues one terminal event without recreating the business transition. */
public interface RequeueCampaignOutboxEventPort {

    OutboxRequeueResult requeue(UUID campaignId, UUID eventId, String actor, Instant now);
}
