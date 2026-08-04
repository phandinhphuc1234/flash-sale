package com.philia.flashsale.campaign.outbox.application.port.out;

import com.philia.flashsale.campaign.outbox.application.model.OutboxEventRecord;
import java.util.Optional;
import java.util.UUID;

/** Loads an outbox event through an application-owned model. */
public interface LoadCampaignOutboxEventPort {

    Optional<OutboxEventRecord> find(UUID campaignId, UUID eventId);
}
