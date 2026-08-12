package com.philia.flashsale.campaign.scheduleoperation.application.exception;

import java.util.UUID;

/** Raised when a retained idempotency key is reused for a different request identity. */
public final class ScheduleOperationRequestConflictException extends RuntimeException {

    private final UUID campaignId;

    public ScheduleOperationRequestConflictException(UUID campaignId) {
        super("The schedule request conflicts with the retained idempotency record");
        this.campaignId = campaignId;
    }

    public UUID campaignId() {
        return campaignId;
    }
}
