package com.philia.flashsale.campaign.outbox.application.port.out;

import com.philia.flashsale.campaign.outbox.application.model.OutboxClaim;

/** Publishes a claimed event; the Kafka adapter is introduced in the following B6 slice. */
public interface PublishCampaignOutboxEventPort {

    void publish(OutboxClaim event);
}
