package com.philia.flashsale.campaign.outbox.application.exception;

import com.philia.flashsale.campaign.outbox.application.model.OutboxPublishStatus;
import java.util.UUID;

/** Raised when requeue is attempted before the event reaches the terminal FAILED state. */
public class CampaignOutboxEventInvalidStatusException extends RuntimeException {

    public CampaignOutboxEventInvalidStatusException(UUID eventId, OutboxPublishStatus status) {
        super("Campaign outbox event " + eventId + " cannot be requeued from " + status);
    }
}
