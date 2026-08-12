package com.philia.flashsale.campaign.outbox.application.exception;

import java.util.UUID;

/** Raised when an operator references an event that is not owned by the requested Campaign. */
public class CampaignOutboxEventNotFoundException extends RuntimeException {

    public CampaignOutboxEventNotFoundException(UUID eventId) {
        super("Campaign outbox event was not found: " + eventId);
    }
}
