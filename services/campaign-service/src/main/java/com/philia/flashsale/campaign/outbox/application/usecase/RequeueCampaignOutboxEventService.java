package com.philia.flashsale.campaign.outbox.application.usecase;

import com.philia.flashsale.campaign.outbox.application.model.OutboxRequeueResult;
import com.philia.flashsale.campaign.outbox.application.port.out.RequeueCampaignOutboxEventPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Requeues one terminal lifecycle notification. The persistence adapter performs the conditional
 * update so the event identity and payload remain unchanged under concurrent operator requests.
 */
@Service
public class RequeueCampaignOutboxEventService {

    private final RequeueCampaignOutboxEventPort requeuePort;

    public RequeueCampaignOutboxEventService(RequeueCampaignOutboxEventPort requeuePort) {
        this.requeuePort = requeuePort;
    }

    @Transactional
    public OutboxRequeueResult requeue(UUID campaignId, UUID eventId, String actor) {
        String safeActor = actor == null || actor.isBlank() ? "unknown-operator" : actor;
        return requeuePort.requeue(campaignId, eventId, safeActor, Instant.now());
    }
}
